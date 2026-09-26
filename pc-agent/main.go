package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"strconv"
	"strings"
)

var config *Config

func authMiddleware(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")

		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusOK)
			return
		}

		authHeader := r.Header.Get("Authorization")
		token := ""
		if strings.HasPrefix(authHeader, "Bearer ") {
			token = strings.TrimPrefix(authHeader, "Bearer ")
		} else {
			token = r.URL.Query().Get("token")
		}

		if config.AuthToken != "" && token != config.AuthToken {
			http.Error(w, `{"error":"unauthorized"}`, http.StatusUnauthorized)
			return
		}

		next(w, r)
	}
}

func handleStatus(w http.ResponseWriter, r *http.Request) {
	status := GetSystemStatus()
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(status)
}

func handleMonitorSleep(w http.ResponseWriter, r *http.Request) {
	SleepDisplays(config.EnableDDCCI)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "monitor_sleeping": true})
}

func handleMonitorWake(w http.ResponseWriter, r *http.Request) {
	WakeDisplays(config.EnableDDCCI)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "monitor_sleeping": false})
}

func handleMonitorToggle(w http.ResponseWriter, r *http.Request) {
	if IsMonitorSleeping() {
		WakeDisplays(config.EnableDDCCI)
	} else {
		SleepDisplays(config.EnableDDCCI)
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "monitor_sleeping": IsMonitorSleeping()})
}

func handleKeyboardGet(w http.ResponseWriter, r *http.Request) {
	level := GetAsusKeyboardBrightness()
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "level": level})
}

func handleKeyboardSet(w http.ResponseWriter, r *http.Request) {
	levelStr := r.URL.Query().Get("level")
	if levelStr == "" {
		var req struct {
			Level int `json:"level"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err == nil {
			levelStr = strconv.Itoa(req.Level)
		}
	}

	level, err := strconv.Atoi(levelStr)
	if err != nil {
		level = 3
	}

	_ = SetAsusKeyboardBrightness(level)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "level": level})
}

func handleKeyboardToggle(w http.ResponseWriter, r *http.Request) {
	nextLevel := ToggleAsusKeyboardBacklight()
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "level": nextLevel})
}

func handleHibernate(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "message": "entering deep hibernation"})
	HibernateSystem()
}

func printLocalIPs(port int) {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return
	}
	fmt.Println("==================================================")
	fmt.Printf(" ASUS PC Control Agent listening on port :%d\n", port)
	fmt.Println(" Available endpoints:")
	for _, a := range addrs {
		if ipnet, ok := a.(*net.IPNet); ok && !ipnet.IP.IsLoopback() {
			if ipnet.IP.To4() != nil {
				fmt.Printf("  -> http://%s:%d\n", ipnet.IP.String(), port)
			}
		}
	}
	fmt.Println(" Auth Token:", config.AuthToken)
	fmt.Println("==================================================")
}

func main() {
	var err error
	config, err = loadConfig("config.json")
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	http.HandleFunc("/api/status", authMiddleware(handleStatus))
	http.HandleFunc("/api/monitor/sleep", authMiddleware(handleMonitorSleep))
	http.HandleFunc("/api/monitor/wake", authMiddleware(handleMonitorWake))
	http.HandleFunc("/api/monitor/toggle", authMiddleware(handleMonitorToggle))
	http.HandleFunc("/api/keyboard", authMiddleware(handleKeyboardGet))
	http.HandleFunc("/api/keyboard/level", authMiddleware(handleKeyboardSet))
	http.HandleFunc("/api/keyboard/toggle", authMiddleware(handleKeyboardToggle))
	http.HandleFunc("/api/power/hibernate", authMiddleware(handleHibernate))

	// Direct mobile download and OTA routes
	http.HandleFunc("/app.apk", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/vnd.android.package-archive")
		w.Header().Set("Content-Disposition", "attachment; filename=\"AsusControl.apk\"")
		http.ServeFile(w, r, "AsusControl.apk")
	})

	http.HandleFunc("/api/app/version", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{
			"version_code":  2,
			"version_name":  "1.1.0",
			"download_url":  "/app.apk",
			"release_notes": "Вбудовано систему OTA оновлення в один клік",
		})
	})

	addr := fmt.Sprintf("0.0.0.0:%d", config.Port)
	printLocalIPs(config.Port)

	if err := http.ListenAndServe(addr, nil); err != nil {
		log.Fatalf("Server error: %v", err)
	}
}
