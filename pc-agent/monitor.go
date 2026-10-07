package main

import (
	"errors"
	"fmt"
	"strings"
	"sync"
	"syscall"
	"time"
	"unsafe"
)

var (
	user32                   = syscall.NewLazyDLL("user32.dll")
	kernel32                 = syscall.NewLazyDLL("kernel32.dll")
	dxva2                    = syscall.NewLazyDLL("dxva2.dll")
	procGetSystemPowerStatus = kernel32.NewProc("GetSystemPowerStatus")
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
type DisplayInfo struct {
	Name           string `json:"name"`
	Capabilities   string `json:"capabilities,omitempty"`
	PowerSupported bool   `json:"power_supported"`
	Power          uint32 `json:"power"`
	Error          string `json:"error,omitempty"`
}

var monitorMu sync.Mutex
var requestedMonitorSleeping bool
var activeConsoleSession = consoleSessionActive
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
		ok, _, err := dxva2.NewProc("GetNumberOfPhysicalMonitorsFromHMONITOR").Call(h, uintptr(unsafe.Pointer(&count)))
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
		ok, _, err = dxva2.NewProc("GetPhysicalMonitorsFromHMONITOR").Call(h, uintptr(count), uintptr(unsafe.Pointer(&monitors[0])))
		if ok == 0 {
			failures = append(failures, err.Error())
			return
		}
		defer dxva2.NewProc("DestroyPhysicalMonitors").Call(uintptr(count), uintptr(unsafe.Pointer(&monitors[0])))
		for _, m := range monitors {
			visit(m)
		}
	}
	defer func() { currentMonitorVisitor = nil }()
	ok, _, err := user32.NewProc("EnumDisplayMonitors").Call(0, 0, monitorCallback, 0)
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
	ok, _, err := dxva2.NewProc("GetVCPFeatureAndVCPFeatureReply").Call(uintptr(m.HPhysicalMonitor), uintptr(code), uintptr(unsafe.Pointer(&kind)), uintptr(unsafe.Pointer(&current)), uintptr(unsafe.Pointer(&max)))
	if ok == 0 {
		return 0, err
	}
	return current, nil
}
func ProbeDisplays() []DisplayInfo {
	monitorMu.Lock()
	defer monitorMu.Unlock()
	info := []DisplayInfo{}
	err := visitPhysicalMonitors(func(m PhysicalMonitor) {
		entry := DisplayInfo{Name: syscall.UTF16ToString(m.Description[:])}
		power, e := readVCP(m, 0xD6)
		entry.Power = power
		entry.PowerSupported = e == nil
		var length uint32
		ok, _, _ := dxva2.NewProc("GetCapabilitiesStringLength").Call(uintptr(m.HPhysicalMonitor), uintptr(unsafe.Pointer(&length)))
		if ok != 0 && length > 0 && length < 65536 {
			buf := make([]byte, length)
			ok, _, _ = dxva2.NewProc("CapabilitiesRequestAndCapabilitiesReply").Call(uintptr(m.HPhysicalMonitor), uintptr(unsafe.Pointer(&buf[0])), uintptr(length))
			if ok != 0 {
				entry.Capabilities = strings.TrimRight(string(buf), "\x00")
			}
		}
		if e != nil {
			entry.Error = e.Error()
		}
		info = append(info, entry)
	})
	if len(info) == 0 {
		message := "Фізичні монітори недоступні; увійдіть у Windows на ноуті без RDP"
		if err != nil {
			message += " (" + err.Error() + ")"
		}
		info = append(info, DisplayInfo{Name: "Windows display session", Error: message})
	}
	return info
}
func consoleSessionActive() bool {
	var session uint32
	pid, _, _ := kernel32.NewProc("GetCurrentProcessId").Call()
	ok, _, _ := kernel32.NewProc("ProcessIdToSessionId").Call(pid, uintptr(unsafe.Pointer(&session)))
	console, _, _ := kernel32.NewProc("WTSGetActiveConsoleSessionId").Call()
	return ok != 0 && console != ^uintptr(0) && uint32(console) == session
}
func commandDisplays(sleep, useDdc bool) error {
	monitorMu.Lock()
	defer monitorMu.Unlock()
	if !activeConsoleSession() {
		return errors.New("Агент працює поза фізичною сесією ASUS. Увійдіть у Windows на ноуті без RDP")
	}
	// Assert both requests before any screen command. Keep them after wake as well:
	// Windows can turn displays off independently of the last command sent by this app.
	if err := globalKeepAwake.RequireDisplay(); err != nil {
		return fmt.Errorf("Не вдалося захистити віддалений доступ від сну: %w", err)
	}
	if sleep {
		if err := ShowBlankScreen(); err != nil {
			if !requestedMonitorSleeping {
				globalKeepAwake.ReleaseDisplay()
			}
			return err
		}
		dimLaptopPanel()
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
					ok, _, _ := dxva2.NewProc("SetVCPFeature").Call(uintptr(m.HPhysicalMonitor), 0xD6, value)
					if ok != 0 {
						break
					}
					time.Sleep(100 * time.Millisecond)
				}
			}
		})
	}
	// SC_MONITORPOWER enters Modern Standby on this laptop. Keep the Windows
	// display logically on and blank it instead; external panels use hardware DDC.
	if !sleep {
		if err := HideBlankScreen(); err != nil {
			return err
		}
		restoreLaptopPanel()
		globalKeepAwake.ReleaseDisplay()
	}
	requestedMonitorSleeping = sleep
	return nil
}
func SleepDisplays(useDdc bool) error { return commandDisplays(true, useDdc) }
func WakeDisplays(useDdc bool) error  { return commandDisplays(false, useDdc) }
func IsMonitorSleeping() bool {
	monitorMu.Lock()
	defer monitorMu.Unlock()
	return requestedMonitorSleeping
}
