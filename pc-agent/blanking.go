package main

import (
	"fmt"
	"runtime"
	"sync"
	"syscall"
	"unsafe"
)

type blankWindowClass struct {
	Style                         uint32
	Proc                          uintptr
	ClassExtra, WindowExtra       int32
	Instance, Icon, Cursor, Brush uintptr
	Menu, Name                    *uint16
}

type blankWindowMessage struct {
	Window         uintptr
	Message        uint32
	WParam, LParam uintptr
	Time           uint32
	X, Y           int32
	Private        uint32
}

type blankScreenCommand struct {
	show bool
	done chan error
}

const blankScreenMessage = 0x8001

var blankScreenMu sync.Mutex
var blankScreenOnce sync.Once
var blankScreenStarted bool
var blankScreenReady = make(chan struct{})
var blankScreenExited = make(chan struct{})
var blankScreenCommands = make(chan blankScreenCommand, 1)
var blankScreenWindow uintptr
var blankScreenError error
var blankPreviousForeground uintptr

var blankScreenCallback uintptr

func init() { blankScreenCallback = syscall.NewCallback(blankWindowProc) }

func blankWindowProc(window uintptr, message uint32, wParam, lParam uintptr) uintptr {
	switch message {
	case blankScreenMessage:
		command := <-blankScreenCommands
		var err error
		if command.show {
			foreground, _, _ := user32.NewProc("GetForegroundWindow").Call()
			if foreground != window {
				blankPreviousForeground = foreground
			}
			metric := func(index uintptr) uintptr {
				value, _, _ := user32.NewProc("GetSystemMetrics").Call(index)
				return uintptr(int64(int32(value)))
			}
			ok, _, callErr := user32.NewProc("SetWindowPos").Call(window, ^uintptr(0), metric(76), metric(77), metric(78), metric(79), 0x0040)
			if ok == 0 {
				err = fmt.Errorf("blank screen positioning: %v", callErr)
			}
			user32.NewProc("SetForegroundWindow").Call(window)
			user32.NewProc("UpdateWindow").Call(window)
		} else {
			user32.NewProc("ShowWindow").Call(window, 0)
			if blankPreviousForeground != 0 {
				user32.NewProc("SetForegroundWindow").Call(blankPreviousForeground)
			}
		}
		command.done <- err
		return 0
	case 0x0100: // WM_KEYDOWN: Escape is the local recovery action.
		if wParam == 27 {
			go func() { _ = WakeDisplays(true) }()
		}
	case 0x0010: // Alt-F4 is another local recovery action.
		go func() { _ = WakeDisplays(true) }()
		return 0
	case 0x0020: // Hide the cursor while the screens are intentionally blank.
		user32.NewProc("SetCursor").Call(0)
		return 1
	}
	result, _, _ := user32.NewProc("DefWindowProcW").Call(window, uintptr(message), wParam, lParam)
	return result
}

func blankScreenThread() {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	defer close(blankScreenExited)
	instance, _, _ := kernel32.NewProc("GetModuleHandleW").Call(0)
	brush, _, _ := syscall.NewLazyDLL("gdi32.dll").NewProc("GetStockObject").Call(4)
	name, _ := syscall.UTF16PtrFromString("AsusControlBlankScreen")
	class := blankWindowClass{Proc: blankScreenCallback, Instance: instance, Brush: brush, Name: name}
	atom, _, err := user32.NewProc("RegisterClassW").Call(uintptr(unsafe.Pointer(&class)))
	if atom == 0 {
		blankScreenError = fmt.Errorf("blank screen registration: %v", err)
		close(blankScreenReady)
		return
	}
	defer user32.NewProc("UnregisterClassW").Call(uintptr(unsafe.Pointer(name)), instance)
	window, _, err := user32.NewProc("CreateWindowExW").Call(0x88, uintptr(unsafe.Pointer(name)), uintptr(unsafe.Pointer(name)), 0x80000000, 0, 0, 1, 1, 0, 0, instance, 0)
	if window == 0 {
		blankScreenError = fmt.Errorf("blank screen creation: %v", err)
		close(blankScreenReady)
		return
	}
	blankScreenWindow = window
	defer user32.NewProc("DestroyWindow").Call(window)
	close(blankScreenReady)
	var message blankWindowMessage
	for {
		result, _, _ := user32.NewProc("GetMessageW").Call(uintptr(unsafe.Pointer(&message)), 0, 0, 0)
		if int32(result) <= 0 {
			return
		}
		user32.NewProc("TranslateMessage").Call(uintptr(unsafe.Pointer(&message)))
		user32.NewProc("DispatchMessageW").Call(uintptr(unsafe.Pointer(&message)))
	}
}

func setBlankScreen(show bool) error {
	blankScreenMu.Lock()
	defer blankScreenMu.Unlock()
	if !show && !blankScreenStarted {
		return nil
	}
	blankScreenOnce.Do(func() { blankScreenStarted = true; go blankScreenThread() })
	<-blankScreenReady
	if blankScreenError != nil {
		return blankScreenError
	}
	select {
	case <-blankScreenExited:
		return fmt.Errorf("blank screen thread stopped")
	default:
	}
	command := blankScreenCommand{show: show, done: make(chan error, 1)}
	blankScreenCommands <- command
	ok, _, err := user32.NewProc("PostMessageW").Call(blankScreenWindow, blankScreenMessage, 0, 0)
	if ok == 0 {
		<-blankScreenCommands
		return fmt.Errorf("blank screen command: %v", err)
	}
	select {
	case err := <-command.done:
		return err
	case <-blankScreenExited:
		return fmt.Errorf("blank screen thread stopped")
	}
}

func ShowBlankScreen() error { return setBlankScreen(true) }
func HideBlankScreen() error { return setBlankScreen(false) }
