package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
)

type AppRelease struct {
	VersionCode  int    `json:"version_code"`
	VersionName  string `json:"version_name"`
	DownloadURL  string `json:"download_url"`
	ReleaseNotes string `json:"release_notes"`
}

func loadAppRelease(manifestPath, apkPath string) (*AppRelease, error) {
	data, err := os.ReadFile(manifestPath)
	if err != nil {
		return nil, err
	}
	var release AppRelease
	if err := json.Unmarshal(data, &release); err != nil {
		return nil, err
	}
	if release.VersionCode < 1 || release.VersionName == "" || release.DownloadURL != "/app.apk" {
		return nil, fmt.Errorf("invalid app release manifest")
	}
	info, err := os.Stat(apkPath)
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() || info.Size() == 0 {
		return nil, fmt.Errorf("release APK is unavailable")
	}
	return &release, nil
}

func handleAppVersion(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet && r.Method != http.MethodHead {
		w.Header().Set("Allow", "GET, HEAD")
		apiError(w, http.StatusMethodNotAllowed, fmt.Errorf("Потрібен GET-запит"))
		return
	}
	release, err := loadAppRelease("app-release.json", "AsusControl.apk")
	if err != nil {
		apiError(w, http.StatusServiceUnavailable, fmt.Errorf("Оновлення додатку ще не підготовлене"))
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	if r.Method == http.MethodGet {
		_ = json.NewEncoder(w).Encode(release)
	}
}
