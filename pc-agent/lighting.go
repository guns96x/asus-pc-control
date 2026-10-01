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

func handleLighting(w http.ResponseWriter, r *http.Request) {
	_, kbErr := ReadAsusKeyboardBrightness()
	raw, ledErr := deviceRead(asusStatusLED)
	ledSupported := ledErr == nil && devicePresent(raw)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"keyboard_supported": kbErr == nil, "laptop_indicators_supported": ledSupported, "monitor_indicator_supported": false, "message": "Індикатор монітора: меню System Setup → Power Indicator → OFF. Індикатори живлення/заряджання ноутбука не мають підтвердженого програмного керування. Статус екранів показує останній запит, а не фізичне підтвердження."})
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
	add("Клавіатура", SetAsusKeyboardBrightness(0))
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
