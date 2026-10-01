package main

import (
	"log"
	"os/exec"
	"time"
	"unsafe"
)

type SYSTEM_POWER_STATUS struct {
	ACLineStatus        byte
	BatteryFlag         byte
	BatteryLifePercent  byte
	SystemStatusFlag    byte
	BatteryLifeTime     uint32
	BatteryFullLifeTime uint32
}

func readPowerStatus() (powerStatus, bool) {
	var sps SYSTEM_POWER_STATUS
	r, _, _ := procGetSystemPowerStatus.Call(uintptr(unsafe.Pointer(&sps)))
	if r == 0 {
		return powerStatus{}, false
	}
	status := powerStatus{ACPlugged: sps.ACLineStatus == 1, BatteryPercent: 100, Charging: sps.BatteryFlag&8 != 0}
	// 255 means unknown; keep the previous "full" default instead of reporting 255%.
	if sps.BatteryLifePercent <= 100 {
		status.BatteryPercent = int(sps.BatteryLifePercent)
	}
	return status, true
}

func HibernateSystem() {
	log.Println("[Power] Initiating deep S4 hibernation in 500ms...")
	go func() {
		time.Sleep(500 * time.Millisecond)
		if err := exec.Command("shutdown", "/h").Run(); err != nil {
			log.Printf("[Power] shutdown /h failed: %v, attempting fallback SetSuspendState...", err)
			procSetSuspendState.Call(1, 0, 0) // Hibernate, Force, DisableWakeEvent
		}
	}()
}
