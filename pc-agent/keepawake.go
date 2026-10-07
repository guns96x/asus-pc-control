package main

import (
	"fmt"
	"log"
	"runtime"
	"sync"
	"syscall"
	"unsafe"
)

// The union in the Windows REASON_CONTEXT occupies 24 bytes on Win64.
type ReasonContext struct {
	Version  uint32
	Flags    uint32
	Reason   uintptr
	Reserved [2]uintptr
}

const (
	powerRequestDisplayRequired     = 0
	powerRequestContextSimpleString = 1
	powerRequestSystemRequired      = 1
	powerRequestExecutionRequired   = 3
)

var (
	procPowerCreateRequest = kernel32.NewProc("PowerCreateRequest")
	procPowerSetRequest    = kernel32.NewProc("PowerSetRequest")
	procPowerClearRequest  = kernel32.NewProc("PowerClearRequest")
)

type powerRequestOps struct {
	create func() (syscall.Handle, error)
	set    func(syscall.Handle, int) error
	clear  func(syscall.Handle, int) error
	close  func(syscall.Handle) error
}

var windowsPowerRequests = &powerRequestOps{
	create: func() (syscall.Handle, error) {
		reason, err := syscall.UTF16PtrFromString("ASUS Control: keep remote control available while screens are off")
		if err != nil {
			return 0, err
		}
		ctx := ReasonContext{Flags: powerRequestContextSimpleString, Reason: uintptr(unsafe.Pointer(reason))}
		h, _, callErr := procPowerCreateRequest.Call(uintptr(unsafe.Pointer(&ctx)))
		runtime.KeepAlive(reason)
		if h == 0 || syscall.Handle(h) == syscall.InvalidHandle {
			return 0, fmt.Errorf("PowerCreateRequest: %v", callErr)
		}
		return syscall.Handle(h), nil
	},
	set: func(h syscall.Handle, kind int) error {
		ok, _, err := procPowerSetRequest.Call(uintptr(h), uintptr(kind))
		if ok == 0 {
			return fmt.Errorf("PowerSetRequest(%d): %v", kind, err)
		}
		return nil
	},
	clear: func(h syscall.Handle, kind int) error {
		ok, _, err := procPowerClearRequest.Call(uintptr(h), uintptr(kind))
		if ok == 0 {
			return fmt.Errorf("PowerClearRequest(%d): %v", kind, err)
		}
		return nil
	},
	close: syscall.CloseHandle,
}

type KeepAwakeController struct {
	mu              sync.Mutex
	active          bool
	displayRequired bool
	hRequest        syscall.Handle
	ops             *powerRequestOps
}

func (k *KeepAwakeController) RequireDisplay() error {
	if err := k.Acquire(); err != nil {
		return err
	}
	k.mu.Lock()
	defer k.mu.Unlock()
	if k.displayRequired {
		return nil
	}
	if err := k.ops.set(k.hRequest, powerRequestDisplayRequired); err != nil {
		return err
	}
	k.displayRequired = true
	return nil
}

func (k *KeepAwakeController) ReleaseDisplay() {
	k.mu.Lock()
	defer k.mu.Unlock()
	if !k.displayRequired {
		return
	}
	if err := k.ops.clear(k.hRequest, powerRequestDisplayRequired); err != nil {
		log.Print(err)
	}
	k.displayRequired = false
}

var globalKeepAwake = &KeepAwakeController{}

func (k *KeepAwakeController) IsActive() bool {
	k.mu.Lock()
	defer k.mu.Unlock()
	return k.active
}

func (k *KeepAwakeController) Acquire() error {
	k.mu.Lock()
	defer k.mu.Unlock()
	if k.active {
		return nil
	}
	if k.ops == nil {
		k.ops = windowsPowerRequests
	}
	h, err := k.ops.create()
	if err != nil {
		return err
	}
	if err := k.ops.set(h, powerRequestSystemRequired); err != nil {
		_ = k.ops.close(h)
		return err
	}
	if err := k.ops.set(h, powerRequestExecutionRequired); err != nil {
		_ = k.ops.clear(h, powerRequestSystemRequired)
		_ = k.ops.close(h)
		return err
	}
	k.hRequest, k.active = h, true
	return nil
}

func (k *KeepAwakeController) Release() {
	k.mu.Lock()
	defer k.mu.Unlock()
	if !k.active {
		return
	}
	if k.displayRequired {
		if err := k.ops.clear(k.hRequest, powerRequestDisplayRequired); err != nil {
			log.Print(err)
		}
		k.displayRequired = false
	}
	for _, kind := range []int{powerRequestExecutionRequired, powerRequestSystemRequired} {
		if err := k.ops.clear(k.hRequest, kind); err != nil {
			log.Print(err)
		}
	}
	if err := k.ops.close(k.hRequest); err != nil {
		log.Print(err)
	}
	k.hRequest, k.active = 0, false
}
