package main

import (
	"errors"
	"fmt"
	"runtime"
	"strings"
	"syscall"
	"time"
	"unsafe"
)

const (
	HWND_BROADCAST  = 0xFFFF
	WM_SYSCOMMAND   = 0x0112
	SC_MONITORPOWER = 0xF170
)

type PhysicalMonitor struct {
	HPhysicalMonitor syscall.Handle
	Description      [128]uint16
}

// Guarded by monitorMu.
var ddcPowerDisplays = map[string]bool{}
var currentMonitorVisitor func(uintptr)

// A single callback avoids leaking one syscall callback allocation on each API call.
var monitorCallback = syscall.NewCallback(func(h, a, b, c uintptr) uintptr {
	if currentMonitorVisitor != nil {
		currentMonitorVisitor(h)
	}
	return 1
})

func visitPhysicalMonitors(visit func(PhysicalMonitor)) error {
	var failures []string
	currentMonitorVisitor = func(h uintptr) {
		var count uint32
		ok, _, err := procGetNumberOfPhysicalMonitorsFromHMONITOR.Call(h, uintptr(unsafe.Pointer(&count)))
		if ok == 0 {
			failures = append(failures, err.Error())
			return
		}
		if count == 0 {
			return
		}
		if count > 64 {
			failures = append(failures, "Некоректна кількість фізичних моніторів")
			return
		}
		monitors := make([]PhysicalMonitor, count)
		ok, _, err = procGetPhysicalMonitorsFromHMONITOR.Call(h, uintptr(count), uintptr(unsafe.Pointer(&monitors[0])))
		if ok == 0 {
			failures = append(failures, err.Error())
			return
		}
		defer procDestroyPhysicalMonitors.Call(uintptr(count), uintptr(unsafe.Pointer(&monitors[0])))
		for _, m := range monitors {
			visit(m)
		}
	}
	defer func() { currentMonitorVisitor = nil }()
	ok, _, err := procEnumDisplayMonitors.Call(0, 0, monitorCallback, 0)
	if ok == 0 {
		return fmt.Errorf("Перелік екранів недоступний: %w", err)
	}
	if len(failures) > 0 {
		return errors.New(strings.Join(failures, "; "))
	}
	return nil
}

func readVCP(m PhysicalMonitor, code byte) (uint32, error) {
	var kind, current, max uint32
	ok, _, err := procGetVCPFeatureAndVCPFeatureReply.Call(uintptr(m.HPhysicalMonitor), uintptr(code), uintptr(unsafe.Pointer(&kind)), uintptr(unsafe.Pointer(&current)), uintptr(unsafe.Pointer(&max)))
	if ok == 0 {
		return 0, err
	}
	return current, nil
}

func probePhysicalDisplays() ([]DisplayInfo, error) {
	info := []DisplayInfo{}
	err := visitPhysicalMonitors(func(m PhysicalMonitor) {
		entry := DisplayInfo{Name: syscall.UTF16ToString(m.Description[:])}
		power, e := readVCP(m, 0xD6)
		entry.Power = power
		entry.PowerSupported = e == nil
		var length uint32
		ok, _, _ := procGetCapabilitiesStringLength.Call(uintptr(m.HPhysicalMonitor), uintptr(unsafe.Pointer(&length)))
		if ok != 0 && length > 0 && length < 65536 {
			buf := make([]byte, length)
			ok, _, _ = procCapabilitiesRequestAndCapabilitiesReply.Call(uintptr(m.HPhysicalMonitor), uintptr(unsafe.Pointer(&buf[0])), uintptr(length))
			if ok != 0 {
				entry.Capabilities = strings.TrimRight(string(buf), "\x00")
			}
		}
		if e != nil {
			entry.Error = e.Error()
		}
		info = append(info, entry)
	})
	return info, err
}

func consoleSessionActive() bool {
	var session uint32
	pid, _, _ := procGetCurrentProcessId.Call()
	ok, _, _ := procProcessIdToSessionId.Call(pid, uintptr(unsafe.Pointer(&session)))
	console, _, _ := procWTSGetActiveConsoleSessionId.Call()
	return ok != 0 && console != ^uintptr(0) && uint32(console) == session
}

func sendDisplayPower(sleep, useDdc bool) error {
	if !sleep {
		procSetThreadExecutionState.Call(3)
		var result uintptr
		procSendMessageTimeoutW.Call(HWND_BROADCAST, WM_SYSCOMMAND, SC_MONITORPOWER, ^uintptr(0), 2, 100, uintptr(unsafe.Pointer(&result)))
		time.Sleep(150 * time.Millisecond)
	}
	if useDdc {
		_ = visitPhysicalMonitors(func(m PhysicalMonitor) {
			// Internal laptop panels need the Windows power command, not DDC/CI.
			name := syscall.UTF16ToString(m.Description[:])
			_, readErr := readVCP(m, 0xD6)
			if readErr == nil {
				ddcPowerDisplays[name] = true
			}
			// Powered-off monitors may stop answering reads. Reuse verified support to wake them.
			if readErr == nil || (!sleep && (ddcPowerDisplays[name] || name == "ASUS VG259QL5A")) {
				value := uintptr(1)
				if sleep {
					value = 4
				}
				for attempt := 0; attempt < 3; attempt++ {
					ok, _, _ := procSetVCPFeature.Call(uintptr(m.HPhysicalMonitor), 0xD6, value)
					if ok != 0 {
						break
					}
					time.Sleep(100 * time.Millisecond)
				}
			}
		})
	}
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	value := ^uintptr(0)
	if sleep {
		value = 2
	} else {
		procSetThreadExecutionState.Call(3)
	}
	var result uintptr
	// ABORTIFHUNG and a bounded per-window timeout replace the unbounded SendMessage.
	ok, _, err := procSendMessageTimeoutW.Call(HWND_BROADCAST, WM_SYSCOMMAND, SC_MONITORPOWER, value, 2, 100, uintptr(unsafe.Pointer(&result)))
	if ok == 0 {
		return fmt.Errorf("Windows не підтвердила прийняття команди екранам: %w", err)
	}
	return nil
}
