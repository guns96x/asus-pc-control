package main

import (
	"log"
	"os"
	"os/exec"
	"syscall"
	"time"
	"unsafe"
)

var (
	procGetSystemPowerStatus = kernel32.NewProc("GetSystemPowerStatus")
)

type SYSTEM_POWER_STATUS struct {
	ACLineStatus        byte
	BatteryFlag         byte
	BatteryLifePercent  byte
	SystemStatusFlag    byte
	BatteryLifeTime     uint32
	BatteryFullLifeTime uint32
}

type SystemInfo struct {
	Hostname        string `json:"hostname"`
	IsACPlugged     bool   `json:"is_ac_plugged"`
	BatteryPercent  int    `json:"battery_percent"`
	IsCharging      bool   `json:"is_charging"`
	MonitorSleeping bool   `json:"monitor_sleeping"`
	KeyboardLevel   int    `json:"keyboard_level"`
	Timestamp       int64  `json:"timestamp"`
}

func GetSystemStatus() SystemInfo {
	hostname, _ := os.Hostname()

	var sps SYSTEM_POWER_STATUS
	r, _, _ := procGetSystemPowerStatus.Call(uintptr(unsafe.Pointer(&sps)))

	isAC := true
	batPct := 100
	isCharging := false

	if r != 0 {
		isAC = sps.ACLineStatus == 1
		if sps.BatteryLifePercent <= 100 {
			batPct = int(sps.BatteryLifePercent)
		}
		isCharging = (sps.BatteryFlag & 8) != 0
	}

	return SystemInfo{
		Hostname:        hostname,
		IsACPlugged:     isAC,
		BatteryPercent:  batPct,
		IsCharging:      isCharging,
		MonitorSleeping: IsMonitorSleeping(),
		KeyboardLevel:   GetAsusKeyboardBrightness(),
		Timestamp:       time.Now().Unix(),
	}
}

func HibernateSystem() {
	log.Println("[Power] Initiating deep S4 hibernation in 500ms...")
	go func() {
		time.Sleep(500 * time.Millisecond)
		cmd := exec.Command("shutdown", "/h")
		if err := cmd.Run(); err != nil {
			log.Printf("[Power] shutdown /h failed: %v, attempting fallback SetSuspendState...", err)
			powrprof := syscall.NewLazyDLL("powrprof.dll")
			procSetSuspendState := powrprof.NewProc("SetSuspendState")
			procSetSuspendState.Call(1, 0, 0) // Hibernate, Force, DisableWakeEvent
		}
	}()
}
