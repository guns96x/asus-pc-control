package main

import (
	"log"
	"sync"
	"syscall"
	"time"
	"unsafe"
)

var (
	user32   = syscall.NewLazyDLL("user32.dll")
	kernel32 = syscall.NewLazyDLL("kernel32.dll")
	dxva2    = syscall.NewLazyDLL("dxva2.dll")

	procSendMessage               = user32.NewProc("SendMessageW")
	procEnumDisplayMonitors       = user32.NewProc("EnumDisplayMonitors")
	procMouseEvent                = user32.NewProc("mouse_event")
	procSetThreadExecutionState   = kernel32.NewProc("SetThreadExecutionState")
	procGetNumberOfPhysMonitors   = dxva2.NewProc("GetNumberOfPhysicalMonitorsFromHMONITOR")
	procGetPhysicalMonitors       = dxva2.NewProc("GetPhysicalMonitorsFromHMONITOR")
	procDestroyPhysicalMonitors   = dxva2.NewProc("DestroyPhysicalMonitors")
	procSetVCPFeature             = dxva2.NewProc("SetVCPFeature")
	procGetVCPFeature             = dxva2.NewProc("GetVCPFeatureAndVCPFeatureReply")
)

const (
	HWND_BROADCAST     = 0xFFFF
	WM_SYSCOMMAND      = 0x0112
	SC_MONITORPOWER    = 0xF170
	MONITOR_OFF        = 2
	MONITOR_ON         = -1
	MOUSEEVENTF_MOVE   = 0x0001
	ES_SYSTEM_REQUIRED = 0x00000001
	ES_DISPLAY_REQUIRED= 0x00000002
)

type PhysicalMonitor struct {
	HPhysicalMonitor syscall.Handle
	Description      [128]uint16
}

type MonitorState struct {
	mu        sync.Mutex
	IsSleeping bool
}

var currentMonitorState MonitorState

func setDDCVCP(vcpCode byte, value uint32) {
	cb := syscall.NewCallback(func(hMonitor, hdc, lprc, data uintptr) uintptr {
		var count uint32
		r, _, _ := procGetNumberOfPhysMonitors.Call(hMonitor, uintptr(unsafe.Pointer(&count)))
		if r == 0 || count == 0 {
			return 1
		}

		monitors := make([]PhysicalMonitor, count)
		r, _, _ = procGetPhysicalMonitors.Call(hMonitor, uintptr(count), uintptr(unsafe.Pointer(&monitors[0])))
		if r != 0 {
			for _, pm := range monitors {
				procSetVCPFeature.Call(uintptr(pm.HPhysicalMonitor), uintptr(vcpCode), uintptr(value))
			}
			procDestroyPhysicalMonitors.Call(uintptr(count), uintptr(unsafe.Pointer(&monitors[0])))
		}
		return 1
	})

	procEnumDisplayMonitors.Call(0, 0, cb, 0)
}

func SleepDisplays(useDdc bool) {
	currentMonitorState.mu.Lock()
	currentMonitorState.IsSleeping = true
	currentMonitorState.mu.Unlock()

	log.Println("[Monitor] Sending display sleep signal (SC_MONITORPOWER 2)...")
	procSendMessage.Call(HWND_BROADCAST, WM_SYSCOMMAND, SC_MONITORPOWER, MONITOR_OFF)

	if useDdc {
		log.Println("[Monitor] Sending DDC/CI Power Off (VCP 0xD6 = 4)...")
		setDDCVCP(0xD6, 4)
	}
}

func WakeDisplays(useDdc bool) {
	currentMonitorState.mu.Lock()
	currentMonitorState.IsSleeping = false
	currentMonitorState.mu.Unlock()

	log.Println("[Monitor] Waking displays...")

	// 1. Thread execution state
	procSetThreadExecutionState.Call(ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED)

	// 2. SysCommand On (-1 = 0xFFFFFFFF)
	procSendMessage.Call(HWND_BROADCAST, WM_SYSCOMMAND, SC_MONITORPOWER, ^uintptr(0))

	// 3. Mouse nudge
	procMouseEvent.Call(MOUSEEVENTF_MOVE, 1, 0, 0, 0)
	time.Sleep(20 * time.Millisecond)
	procMouseEvent.Call(MOUSEEVENTF_MOVE, ^uintptr(0), 0, 0, 0) // -1 relative move

	// 4. DDC/CI Wake
	if useDdc {
		log.Println("[Monitor] Sending DDC/CI Power On (VCP 0xD6 = 1)...")
		setDDCVCP(0xD6, 1)
	}
}

func IsMonitorSleeping() bool {
	currentMonitorState.mu.Lock()
	defer currentMonitorState.mu.Unlock()
	return currentMonitorState.IsSleeping
}
