package main

import (
	"os"
	"time"
)

type SystemInfo struct {
	Hostname             string `json:"hostname"`
	IsACPlugged          bool   `json:"is_ac_plugged"`
	BatteryPercent       int    `json:"battery_percent"`
	IsCharging           bool   `json:"is_charging"`
	MonitorSleeping      bool   `json:"monitor_sleeping"`
	MonitorStateVerified bool   `json:"monitor_state_verified"`
	KeyboardLevel        int    `json:"keyboard_level"`
	Timestamp            int64  `json:"timestamp"`
}

type powerStatus struct {
	ACPlugged      bool
	BatteryPercent int
	Charging       bool
}

func GetSystemStatus() SystemInfo {
	hostname, _ := os.Hostname()
	power, ok := readPowerStatus()
	if !ok {
		power = powerStatus{ACPlugged: true, BatteryPercent: 100}
	}
	return SystemInfo{
		Hostname:        hostname,
		IsACPlugged:     power.ACPlugged,
		BatteryPercent:  power.BatteryPercent,
		IsCharging:      power.Charging,
		MonitorSleeping: IsMonitorSleeping(),
		KeyboardLevel:   GetAsusKeyboardBrightness(),
		Timestamp:       time.Now().Unix(),
	}
}
