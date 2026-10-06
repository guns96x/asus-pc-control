package main

import (
	"runtime"
	"sync"
	"syscall"
	"unsafe"
)

type ReasonContext struct {
	Version uint32
	Flags   uint32
	Reason  uintptr
}

const (
	powerRequestContextSimpleString = 1
	powerRequestSystemRequired      = 1
	powerRequestExecutionRequired   = 3

	esContinuous       = 0x80000000
	esSystemRequired   = 0x00000001
	esAwayModeRequired = 0x00000040
)

var (
	procPowerCreateRequest = kernel32.NewProc("PowerCreateRequest")
	procPowerSetRequest    = kernel32.NewProc("PowerSetRequest")
	procPowerClearRequest  = kernel32.NewProc("PowerClearRequest")
	procSetThreadExecState = kernel32.NewProc("SetThreadExecutionState")
)

type KeepAwakeController struct {
	mu           sync.Mutex
	active       bool
	hRequest     syscall.Handle
	threadStopCh chan struct{}
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

	reasonStr, err := syscall.UTF16PtrFromString("ASUS PC Control: Display sleeping, keeping system and execution active")
	if err == nil {
		ctx := ReasonContext{
			Version: 0,
			Flags:   powerRequestContextSimpleString,
			Reason:  uintptr(unsafe.Pointer(reasonStr)),
		}
		h, _, _ := procPowerCreateRequest.Call(uintptr(unsafe.Pointer(&ctx)))
		if h != 0 && h != ^uintptr(0) {
			k.hRequest = syscall.Handle(h)
			_, _, _ = procPowerSetRequest.Call(h, uintptr(powerRequestSystemRequired))
			_, _, _ = procPowerSetRequest.Call(h, uintptr(powerRequestExecutionRequired))
		}
	}

	// Dedicated OS thread worker for SetThreadExecutionState
	k.threadStopCh = make(chan struct{})
	stopCh := k.threadStopCh

	go func() {
		runtime.LockOSThread()
		defer runtime.UnlockOSThread()

		// Tell Windows to keep system and away mode continuously active
		procSetThreadExecState.Call(uintptr(esContinuous | esSystemRequired | esAwayModeRequired))

		<-stopCh

		// Clear thread execution state back to normal
		procSetThreadExecState.Call(uintptr(esContinuous))
	}()

	k.active = true
	return nil
}

func (k *KeepAwakeController) Release() {
	k.mu.Lock()
	defer k.mu.Unlock()

	if !k.active {
		return
	}

	if k.hRequest != 0 && k.hRequest != syscall.InvalidHandle {
		procPowerClearRequest.Call(uintptr(k.hRequest), uintptr(powerRequestSystemRequired))
		procPowerClearRequest.Call(uintptr(k.hRequest), uintptr(powerRequestExecutionRequired))
		syscall.CloseHandle(k.hRequest)
		k.hRequest = 0
	}

	if k.threadStopCh != nil {
		close(k.threadStopCh)
		k.threadStopCh = nil
	}

	k.active = false
}
