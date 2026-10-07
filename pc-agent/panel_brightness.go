package main

import (
	"context"
	"fmt"
	"log"
	"os/exec"
	"strconv"
	"strings"
	"syscall"
	"time"
)

// Access is serialized by monitorMu. Restore the user's actual brightness on wake.
var savedPanelBrightness = -1

func panelCommand(script string) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 4*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	return cmd.Output()
}

func dimLaptopPanel() {
	if savedPanelBrightness >= 0 {
		return
	}
	output, err := panelCommand(`$ErrorActionPreference='Stop'; $panel=Get-CimInstance -Namespace root/wmi -ClassName WmiMonitorBrightness | Where-Object Active | Select-Object -First 1; if ($null -eq $panel) {exit 1}; $methods=Get-CimInstance -Namespace root/wmi -ClassName WmiMonitorBrightnessMethods | Where-Object {$_.InstanceName -eq $panel.InstanceName}; if ($null -eq $methods) {exit 1}; $level=[int]$panel.CurrentBrightness; $result=Invoke-CimMethod -InputObject $methods -MethodName WmiSetBrightness -Arguments @{Timeout=[uint32]0;Brightness=[byte]0}; if ($null -ne $result.ReturnValue -and $result.ReturnValue -ne 0) {exit 1}; Write-Output $level`)
	if err != nil {
		log.Printf("[Display] panel dimming unavailable: %v", err)
		return
	}
	level, err := strconv.Atoi(strings.TrimSpace(string(output)))
	if err == nil && level >= 0 && level <= 100 {
		savedPanelBrightness = level
	}
}

func restoreLaptopPanel() {
	if savedPanelBrightness < 0 {
		return
	}
	_, err := panelCommand(fmt.Sprintf(`$ErrorActionPreference='Stop'; Get-CimInstance -Namespace root/wmi -ClassName WmiMonitorBrightnessMethods | Invoke-CimMethod -MethodName WmiSetBrightness -Arguments @{Timeout=[uint32]0;Brightness=[byte]%d} | Out-Null`, savedPanelBrightness))
	if err != nil {
		log.Printf("[Display] panel brightness restore failed: %v", err)
		return
	}
	savedPanelBrightness = -1
}
