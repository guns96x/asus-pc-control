package main

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"
)

var methodError = errors.New("Потрібен POST-запит")

type LightingAction struct {
	Name    string `json:"name"`
	Success bool   `json:"success"`
	Message string `json:"message"`
}

func turnOffScrollLockIfActive() bool {
	procGetKeyState := user32.NewProc("GetKeyState")
	state, _, _ := procGetKeyState.Call(0x91) // VK_SCROLL
	// Low-order bit indicates whether toggle key is ON
	if state&1 != 0 {
		procKeybdEvent := user32.NewProc("keybd_event")
		procKeybdEvent.Call(0x91, 0, 0, 0)
		procKeybdEvent.Call(0x91, 0, 2, 0)
		return true
	}
	return false
}

func handleLighting(w http.ResponseWriter, r *http.Request) {
	_, kbErr := ReadAsusKeyboardBrightness()
	raw, ledErr := deviceRead(asusStatusLED)
	ledSupported := ledErr == nil && devicePresent(raw)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"keyboard_supported":          kbErr == nil,
		"laptop_indicators_supported": ledSupported,
		"monitor_indicator_supported": false,
		"message":                     "Внутрішня клавіатура ASUS TUF: повний контроль (0-3). Зовнішня USB-клавіатура: апаратний контролер живиться від шини USB +5V (вимикається комбінацією клавіш Fn на клавіатурі). Індикатор монітора: меню System Setup → Power Indicator → OFF.",
	})
}
func handleDarkMode(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		apiError(w, http.StatusMethodNotAllowed, methodError)
		return
	}
	actions := []LightingAction{}
	add := func(name string, err error) {
		a := LightingAction{Name: name, Success: err == nil, Message: "Команда виконана"}
		if err != nil {
			a.Message = err.Error()
		}
		actions = append(actions, a)
	}
	add("Внутрішня клавіатура", SetAsusKeyboardBrightness(0))
	slToggled := turnOffScrollLockIfActive()
	slMsg := "ScrollLock уже вимкнено"
	if slToggled {
		slMsg = "ScrollLock вимкнено"
	}
	actions = append(actions, LightingAction{
		Name:    "Зовнішня клавіатура",
		Success: true,
		Message: slMsg + "; апаратні RGB-клавіатури вимикаються через комбінацію Fn на клавіатурі",
	})
	// Do not write unknown firmware LED registers or undocumented monitor VCP codes.
	actions = append(actions, LightingAction{Name: "Індикатори ноутбука", Message: "Немає підтвердженого програмного керування"}, LightingAction{Name: "Індикатор монітора", Message: "Вимкніть у System Setup → Power Indicator → OFF"})
	add("Екрани", SleepDisplays(config.EnableDDCCI))
	completed := []string{}
	pending := []string{}
	for _, a := range actions {
		if a.Success {
			completed = append(completed, a.Name)
		} else {
			pending = append(pending, a.Name+": "+a.Message)
		}
	}
	message := "Виконано: " + strings.Join(completed, ", ") + ". Потребує уваги: " + strings.Join(pending, "; ")
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusMultiStatus)
	_ = json.NewEncoder(w).Encode(map[string]any{"success": false, "message": message, "actions": actions})
}
