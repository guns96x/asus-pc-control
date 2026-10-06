package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sync"
	"syscall"
)

const (
	ASUS_PERF_MODE_DEVID uint32 = 0x00120075

	PerfModeBalanced int = 0
	PerfModeTurbo    int = 1
	PerfModeSilent   int = 2
)

var (
	perfMu          sync.Mutex
	lastAppliedMode = -1

	guidBalanced   = "381b4222-f694-41f0-9685-ff5bb260df2e"
	guidHighPerf   = "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c"
	guidPowerSaver = "a1841308-3541-4fab-bc81-f71556f20b4a"
)

func PerfModeName(mode int) string {
	switch mode {
	case PerfModeBalanced:
		return "balanced"
	case PerfModeTurbo:
		return "turbo"
	case PerfModeSilent:
		return "silent"
	default:
		return "unknown"
	}
}

func PerfModeTitle(mode int) string {
	switch mode {
	case PerfModeBalanced:
		return "Баланс"
	case PerfModeTurbo:
		return "Турбо"
	case PerfModeSilent:
		return "Тихий"
	default:
		return "Невідомий"
	}
}

func getGHelperConfigPath() string {
	appData := os.Getenv("APPDATA")
	if appData == "" {
		return ""
	}
	return filepath.Join(appData, "GHelper", "config.json")
}

func readGHelperMode() (int, bool) {
	cfgPath := getGHelperConfigPath()
	if cfgPath == "" {
		return -1, false
	}
	data, err := os.ReadFile(cfgPath)
	if err != nil {
		return -1, false
	}
	var cfg map[string]any
	if err := json.Unmarshal(data, &cfg); err != nil {
		return -1, false
	}
	if val, ok := cfg["performance_mode"]; ok {
		if fval, ok := val.(float64); ok {
			m := int(fval)
			if m >= 0 && m <= 2 {
				return m, true
			}
		}
	}
	return -1, false
}

func syncGHelperConfigFile(mode int) {
	cfgPath := getGHelperConfigPath()
	if cfgPath == "" {
		return
	}
	data, err := os.ReadFile(cfgPath)
	if err != nil {
		return
	}
	var cfg map[string]any
	if err := json.Unmarshal(data, &cfg); err != nil {
		return
	}

	cfg["performance_mode"] = mode
	// Also sync power status specific keys if present
	cfg["performance_0"] = mode
	cfg["performance_1"] = mode

	updated, err := json.MarshalIndent(cfg, "", "  ")
	if err == nil {
		_ = os.WriteFile(cfgPath, updated, 0644)
	}
}

func syncWindowsPowerScheme(mode int) {
	var targetGuid string
	switch mode {
	case PerfModeSilent:
		targetGuid = guidPowerSaver
	case PerfModeTurbo:
		targetGuid = guidHighPerf
	case PerfModeBalanced:
		targetGuid = guidBalanced
	default:
		return
	}

	cmd := exec.Command("powercfg", "/setactive", targetGuid)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	_ = cmd.Run()
}

// GetCurrentPerformanceMode reads the active performance mode.
func GetCurrentPerformanceMode() (int, string, error) {
	perfMu.Lock()
	defer perfMu.Unlock()

	// First try reading from G-Helper config (which is the source of truth for the desktop user)
	if mode, ok := readGHelperMode(); ok {
		lastAppliedMode = mode
		return mode, PerfModeName(mode), nil
	}

	// If we previously applied a valid mode during this process lifetime, report it
	if lastAppliedMode >= 0 && lastAppliedMode <= 2 {
		return lastAppliedMode, PerfModeName(lastAppliedMode), nil
	}

	// Verify hardware capability
	raw, err := readAsusDevice(ASUS_PERF_MODE_DEVID)
	if err != nil {
		return -1, "unknown", fmt.Errorf("помилка читання ASUS WMI: %w", err)
	}
	if !devicePresent(raw) {
		return -1, "unknown", errors.New("керування режимами не підтримується цим пристроєм")
	}

	// If neither config nor cached mode is available, report unknown rather than inventing Balanced
	return -1, "unknown", errors.New("поточний режим ще не визначено; оберіть потрібний режим у додатку")
}

// SetCurrentPerformanceMode applies the mode to ASUS hardware and syncs with G-Helper.
func SetCurrentPerformanceMode(mode int) error {
	if mode < 0 || mode > 2 {
		return fmt.Errorf("некоректний режим: %d (підтримуються 0: Баланс, 1: Турбо, 2: Тихий)", mode)
	}

	perfMu.Lock()
	defer perfMu.Unlock()

	// 1. Hardware call to ASUS ATKACPI
	if err := writeAsusDevice(ASUS_PERF_MODE_DEVID, uint32(mode)); err != nil {
		return fmt.Errorf("помилка ASUS ACPI при зміні режиму: %w", err)
	}

	// 2. Synchronize G-Helper config file so G-Helper reflects the new mode
	syncGHelperConfigFile(mode)

	// 3. Sync Windows Power Scheme (optional best-effort)
	syncWindowsPowerScheme(mode)

	lastAppliedMode = mode

	return nil
}
