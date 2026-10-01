package main

import (
	"encoding/json"
	"errors"
	"net/http"
	"os"
)

// Written by the Gradle publishApk task next to AsusControl.apk, so OTA never advertises
// a version that differs from the APK actually served.
const appVersionFile = "app-version.json"

type AppVersion struct {
	VersionCode  int    `json:"version_code"`
	VersionName  string `json:"version_name"`
	DownloadURL  string `json:"download_url"`
	ReleaseNotes string `json:"release_notes"`
}

func loadAppVersion(path string) (AppVersion, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return AppVersion{}, err
	}
	var v AppVersion
	if err := json.Unmarshal(data, &v); err != nil {
		return AppVersion{}, err
	}
	if v.VersionCode <= 0 || v.VersionName == "" {
		return AppVersion{}, errors.New("app-version.json без version_code або version_name")
	}
	if v.DownloadURL == "" {
		v.DownloadURL = "/app.apk"
	}
	return v, nil
}

func handleAppVersion(w http.ResponseWriter, r *http.Request) {
	v, err := loadAppVersion(appVersionFile)
	if err != nil {
		apiError(w, http.StatusServiceUnavailable, errors.New("Версія APK на ПК невідома: "+err.Error()))
		return
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(v)
}
