package main

import (
	"errors"
	"sync"
)

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

func ProbeDisplays() []DisplayInfo {
	monitorMu.Lock()
	defer monitorMu.Unlock()
	info, err := probePhysicalDisplays()
	if len(info) == 0 {
		message := "Фізичні монітори недоступні; увійдіть у Windows на ноуті без RDP"
		if err != nil {
			message += " (" + err.Error() + ")"
		}
		info = append(info, DisplayInfo{Name: "Windows display session", Error: message})
	}
	return info
}

func commandDisplays(sleep, useDdc bool) error {
	monitorMu.Lock()
	defer monitorMu.Unlock()
	if !activeConsoleSession() {
		return errors.New("Агент працює поза фізичною сесією ASUS. Увійдіть у Windows на ноуті без RDP")
	}
	if err := sendDisplayPower(sleep, useDdc); err != nil {
		return err
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
