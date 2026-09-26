package main

import (
	"fmt"
	"log"
	"os/exec"
	"strconv"
	"strings"
	"sync"
)

type KeyboardState struct {
	mu            sync.Mutex
	Brightness    int // 0 to 3
	IsInitialized bool
}

var currentKeyboardState KeyboardState

const (
	ASUS_KBD_BACKLIGHT_DEVID = 0x00050021
)

func SetAsusKeyboardBrightness(level int) error {
	if level < 0 {
		level = 0
	}
	if level > 3 {
		level = 3
	}

	currentKeyboardState.mu.Lock()
	currentKeyboardState.Brightness = level
	currentKeyboardState.IsInitialized = true
	currentKeyboardState.mu.Unlock()

	log.Printf("[Keyboard] Setting brightness to %d...", level)

	// Invoke Asus WMI method DEVS(0x00050021, level) via powershell
	psCmd := fmt.Sprintf(`
try {
    $wmi = Get-CimInstance -Namespace root\wmi -ClassName AsusAtkWmi_WMNB -ErrorAction Stop
    $argsDevs = @{
        Device_Arg = [uint32]0x%X
        Control_Status = [uint32]%d
    }
    $res = Invoke-CimMethod -InputObject $wmi -MethodName DEVS -Arguments $argsDevs -ErrorAction Stop
    Write-Output "OK:$($res.String)"
} catch {
    Write-Output "ERR:$($_.Exception.Message)"
}
`, ASUS_KBD_BACKLIGHT_DEVID, level)

	cmd := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd)
	out, err := cmd.CombinedOutput()
	outputStr := strings.TrimSpace(string(out))

	if err != nil || strings.HasPrefix(outputStr, "ERR:") {
		log.Printf("[Keyboard] WMI set returned notice: %s. State tracked internally.", outputStr)
		// We still keep the internal state so the mobile client reflects the requested state
		return nil
	}

	log.Printf("[Keyboard] WMI set successful: %s", outputStr)
	return nil
}

func GetAsusKeyboardBrightness() int {
	currentKeyboardState.mu.Lock()
	defer currentKeyboardState.mu.Unlock()

	if !currentKeyboardState.IsInitialized {
		// Attempt initial query
		psCmd := fmt.Sprintf(`
try {
    $wmi = Get-CimInstance -Namespace root\wmi -ClassName AsusAtkWmi_WMNB -ErrorAction Stop
    $argsDsts = @{ Device_Arg = [uint32]0x%X }
    $res = Invoke-CimMethod -InputObject $wmi -MethodName DSTS -Arguments $argsDsts -ErrorAction Stop
    Write-Output "VAL:$($res.Device_Status)"
} catch {
    Write-Output "ERR"
}
`, ASUS_KBD_BACKLIGHT_DEVID)

		cmd := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd)
		out, err := cmd.CombinedOutput()
		outputStr := strings.TrimSpace(string(out))
		if err == nil && strings.HasPrefix(outputStr, "VAL:") {
			valStr := strings.TrimPrefix(outputStr, "VAL:")
			if v, err := strconv.Atoi(valStr); err == nil {
				// Lowest 3 bits usually represent brightness level
				level := v & 0x07
				if level > 3 {
					level = 3
				}
				currentKeyboardState.Brightness = level
				currentKeyboardState.IsInitialized = true
				return level
			}
		}

		currentKeyboardState.Brightness = 3 // Default full brightness if unknown
		currentKeyboardState.IsInitialized = true
	}

	return currentKeyboardState.Brightness
}

func ToggleAsusKeyboardBacklight() int {
	current := GetAsusKeyboardBrightness()
	var next int
	if current > 0 {
		next = 0
	} else {
		next = 3
	}
	_ = SetAsusKeyboardBrightness(next)
	return next
}
