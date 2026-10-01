package main

import (
	"crypto/subtle"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

var config *Config

func authMiddleware(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// Header only: a query-string token would end up in browser history and proxy logs.
		token, _ := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
		if config.AuthToken != "" && subtle.ConstantTimeCompare([]byte(token), []byte(config.AuthToken)) != 1 {
			apiError(w, http.StatusUnauthorized, errors.New("unauthorized"))
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
	if !requirePost(w, r) {
		return
	}
	if err := SleepDisplays(config.EnableDDCCI); err != nil {
		apiError(w, http.StatusServiceUnavailable, err)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "monitor_sleeping": true, "monitor_state_verified": false})
}

func handleMonitorWake(w http.ResponseWriter, r *http.Request) {
	if !requirePost(w, r) {
		return
	}
	if err := WakeDisplays(config.EnableDDCCI); err != nil {
		apiError(w, http.StatusServiceUnavailable, err)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "monitor_sleeping": false, "monitor_state_verified": false})
}

func handleMonitorToggle(w http.ResponseWriter, r *http.Request) {
	if !requirePost(w, r) {
		return
	}
	if IsMonitorSleeping() {
		if err := WakeDisplays(config.EnableDDCCI); err != nil {
			apiError(w, http.StatusServiceUnavailable, err)
			return
		}
	} else {
		if err := SleepDisplays(config.EnableDDCCI); err != nil {
			apiError(w, http.StatusServiceUnavailable, err)
			return
		}
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "monitor_sleeping": IsMonitorSleeping()})
}

func handleKeyboardGet(w http.ResponseWriter, r *http.Request) {
	level, err := ReadAsusKeyboardBrightness()
	if err != nil {
		apiError(w, http.StatusServiceUnavailable, err)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "level": level})
}

func handleKeyboardSet(w http.ResponseWriter, r *http.Request) {
	if !requirePost(w, r) {
		return
	}
	levelStr := r.URL.Query().Get("level")
	if levelStr == "" {
		var req struct {
			Level *int `json:"level"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err == nil && req.Level != nil {
			levelStr = strconv.Itoa(*req.Level)
		}
	}

	level, err := strconv.Atoi(levelStr)
	if err != nil || level < 0 || level > 3 {
		apiError(w, http.StatusBadRequest, fmt.Errorf("Яскравість клавіатури має бути цілим числом від 0 до 3"))
		return
	}
	if err := SetAsusKeyboardBrightness(level); err != nil {
		apiError(w, http.StatusServiceUnavailable, err)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "level": level})
}

func handleKeyboardToggle(w http.ResponseWriter, r *http.Request) {
	if !requirePost(w, r) {
		return
	}
	nextLevel, err := ToggleAsusKeyboardBacklight()
	if err != nil {
		apiError(w, http.StatusServiceUnavailable, err)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "level": nextLevel})
}

func apiError(w http.ResponseWriter, code int, err error) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(map[string]any{"success": false, "error": err.Error()})
}

func requirePost(w http.ResponseWriter, r *http.Request) bool {
	if r.Method == http.MethodPost {
		return true
	}
	w.Header().Set("Allow", "POST")
	apiError(w, http.StatusMethodNotAllowed, methodError)
	return false
}

var hibernate = HibernateSystem

func handleHibernate(w http.ResponseWriter, r *http.Request) {
	// GET must never hibernate: link previews and prefetchers issue GETs on their own.
	if !requirePost(w, r) {
		return
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "message": "entering deep hibernation"})
	hibernate()
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
	fmt.Println(" Authentication enabled:", config.AuthToken != "")
	fmt.Println("==================================================")
}

func main() {
	// Scheduled tasks may start in System32; resolve config and APK beside the executable.
	exe, err := os.Executable()
	if err != nil {
		log.Fatalf("Failed to locate executable: %v", err)
	}
	if err := os.Chdir(filepath.Dir(exe)); err != nil {
		log.Fatalf("Failed to select agent directory: %v", err)
	}
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
	http.HandleFunc("/api/lighting", authMiddleware(handleLighting))
	http.HandleFunc("/api/lighting/dark", authMiddleware(handleDarkMode))
	http.HandleFunc("/api/monitor", authMiddleware(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(ProbeDisplays())
	}))
	http.HandleFunc("/api/power/hibernate", authMiddleware(handleHibernate))

	// Direct mobile download and OTA routes
	http.HandleFunc("/app.apk", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/vnd.android.package-archive")
		w.Header().Set("Content-Disposition", "attachment; filename=\"AsusControl.apk\"")
		http.ServeFile(w, r, "AsusControl.apk")
	})

	http.HandleFunc("/api/app/version", handleAppVersion)

	addr := fmt.Sprintf("0.0.0.0:%d", config.Port)
	printLocalIPs(config.Port)

	server := &http.Server{
		Addr:              addr,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		// The APK download over Tailscale is the slowest response.
		WriteTimeout: 5 * time.Minute,
		IdleTimeout:  2 * time.Minute,
	}
	if err := server.ListenAndServe(); err != nil {
		log.Fatalf("Server error: %v", err)
	}
}
