package main

import (
	"errors"
	"reflect"
	"syscall"
	"testing"
)

func TestKeepAwakeControllerLifecycle(t *testing.T) {
	k := &KeepAwakeController{}
	if k.IsActive() {
		t.Fatal("expected inactive controller initially")
	}

	if err := k.Acquire(); err != nil {
		t.Fatalf("Acquire failed: %v", err)
	}
	if !k.IsActive() {
		t.Fatal("expected active controller after Acquire")
	}

	// Idempotent acquire
	if err := k.Acquire(); err != nil {
		t.Fatalf("Second Acquire failed: %v", err)
	}

	k.Release()
	if k.IsActive() {
		t.Fatal("expected inactive controller after Release")
	}

	// Idempotent release
	k.Release()
}

func TestKeepAwakeDoesNotInventProtectionOnWindowsFailure(t *testing.T) {
	for _, failAt := range []string{"create", "system", "execution"} {
		t.Run(failAt, func(t *testing.T) {
			var cleared []int
			closed := 0
			ops := &powerRequestOps{
				create: func() (syscall.Handle, error) {
					if failAt == "create" {
						return 0, errors.New("create refused")
					}
					return 123, nil
				},
				set: func(_ syscall.Handle, kind int) error {
					if (failAt == "system" && kind == powerRequestSystemRequired) ||
						(failAt == "execution" && kind == powerRequestExecutionRequired) {
						return errors.New("request refused")
					}
					return nil
				},
				clear: func(_ syscall.Handle, kind int) error { cleared = append(cleared, kind); return nil },
				close: func(syscall.Handle) error { closed++; return nil },
			}
			k := &KeepAwakeController{ops: ops}
			if k.Acquire() == nil || k.IsActive() {
				t.Fatal("failed request reported as protection")
			}
			if failAt != "create" && closed != 1 {
				t.Fatal("request handle leaked")
			}
			if failAt == "execution" && !reflect.DeepEqual(cleared, []int{powerRequestSystemRequired}) {
				t.Fatal("partially acquired system request was not rolled back")
			}
		})
	}
}

func TestMonitorSleepAbortsBeforeDisplayCommandsWithoutProtection(t *testing.T) {
	oldConsole, oldGuard := activeConsoleSession, globalKeepAwake
	activeConsoleSession = func() bool { return true }
	globalKeepAwake = &KeepAwakeController{ops: &powerRequestOps{
		create: func() (syscall.Handle, error) { return 0, errors.New("no protection available") },
	}}
	t.Cleanup(func() { activeConsoleSession, globalKeepAwake = oldConsole, oldGuard })
	if SleepDisplays(true) == nil {
		t.Fatal("screen-off command accepted without sleep protection")
	}
}

func TestBlankScreenNeedsDisplayRequestWhileSystemRemainsProtectedAfterWake(t *testing.T) {
	var set, cleared []int
	ops := &powerRequestOps{
		create: func() (syscall.Handle, error) { return 123, nil },
		set:    func(_ syscall.Handle, kind int) error { set = append(set, kind); return nil },
		clear:  func(_ syscall.Handle, kind int) error { cleared = append(cleared, kind); return nil },
		close:  func(syscall.Handle) error { return nil },
	}
	k := &KeepAwakeController{ops: ops}
	if err := k.RequireDisplay(); err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(set, []int{powerRequestSystemRequired, powerRequestExecutionRequired, powerRequestDisplayRequired}) {
		t.Fatal("missing Modern Standby display protection")
	}
	k.ReleaseDisplay()
	if !k.IsActive() || !reflect.DeepEqual(cleared, []int{powerRequestDisplayRequired}) {
		t.Fatal("wake must keep the remote-control system requests")
	}
	k.Release()
}
