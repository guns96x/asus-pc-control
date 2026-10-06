package main

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestHibernateRejectsReadRequests(t *testing.T) {
	for _, method := range []string{http.MethodGet, http.MethodHead, http.MethodPut} {
		w := httptest.NewRecorder()
		handleHibernate(w, httptest.NewRequest(method, "/api/power/hibernate", nil))
		if w.Code != http.StatusMethodNotAllowed || w.Header().Get("Allow") != "POST" {
			t.Fatalf("%s: hibernation must reject non-POST requests: %d", method, w.Code)
		}
	}
}

func TestReleaseRequiresValidMetadataAndAvailableAPK(t *testing.T) {
	dir := t.TempDir()
	manifest, apk := filepath.Join(dir, "release.json"), filepath.Join(dir, "app.apk")
	valid := `{"version_code":6,"version_name":"1.1.4","download_url":"/app.apk"}`
	if err := os.WriteFile(manifest, []byte(valid), 0600); err != nil {
		t.Fatal(err)
	}
	if _, err := loadAppRelease(manifest, apk); err == nil {
		t.Fatal("missing APK advertised")
	}
	if err := os.WriteFile(apk, nil, 0600); err != nil {
		t.Fatal(err)
	}
	if _, err := loadAppRelease(manifest, apk); err == nil {
		t.Fatal("empty APK advertised")
	}
	if err := os.WriteFile(apk, []byte("apk test fixture"), 0600); err != nil {
		t.Fatal(err)
	}
	release, err := loadAppRelease(manifest, apk)
	if err != nil || release.VersionCode != 6 {
		t.Fatalf("valid release: %v", err)
	}
	for _, invalid := range []string{`{}`, `broken`, `{"version_code":6,"version_name":"1.1.4","download_url":"https://other/app.apk"}`} {
		if err := os.WriteFile(manifest, []byte(invalid), 0600); err != nil {
			t.Fatal(err)
		}
		if _, err := loadAppRelease(manifest, apk); err == nil {
			t.Fatalf("invalid manifest advertised: %s", invalid)
		}
	}
}
