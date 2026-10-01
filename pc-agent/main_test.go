package main

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func withConfig(t *testing.T, cfg *Config) {
	old := config
	config = cfg
	t.Cleanup(func() { config = old })
}

func TestAuthAcceptsOnlyBearerHeader(t *testing.T) {
	withConfig(t, &Config{AuthToken: "secret"})
	ok := authMiddleware(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(http.StatusNoContent) })
	cases := map[string]struct {
		header, query string
		want          int
	}{
		"valid":       {"Bearer secret", "", http.StatusNoContent},
		"wrong":       {"Bearer secreT", "", http.StatusUnauthorized},
		"missing":     {"", "", http.StatusUnauthorized},
		"query token": {"", "?token=secret", http.StatusUnauthorized},
		"prefix only": {"Bearer secre", "", http.StatusUnauthorized},
	}
	for name, c := range cases {
		r := httptest.NewRequest(http.MethodGet, "/api/status"+c.query, nil)
		if c.header != "" {
			r.Header.Set("Authorization", c.header)
		}
		w := httptest.NewRecorder()
		ok(w, r)
		if w.Code != c.want {
			t.Errorf("%s: HTTP %d, want %d", name, w.Code, c.want)
		}
	}
}

func TestHibernateRequiresPost(t *testing.T) {
	called := 0
	old := hibernate
	hibernate = func() { called++ }
	t.Cleanup(func() { hibernate = old })
	w := httptest.NewRecorder()
	handleHibernate(w, httptest.NewRequest(http.MethodGet, "/api/power/hibernate", nil))
	if w.Code != http.StatusMethodNotAllowed || called != 0 {
		t.Fatalf("GET hibernated: %d, calls %d", w.Code, called)
	}
	w = httptest.NewRecorder()
	handleHibernate(w, httptest.NewRequest(http.MethodPost, "/api/power/hibernate", nil))
	if w.Code != http.StatusOK || called != 1 {
		t.Fatalf("POST: %d, calls %d", w.Code, called)
	}
}

func TestAppVersionFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "app-version.json")
	if _, err := loadAppVersion(path); err == nil {
		t.Fatal("missing file must not invent a version")
	}
	_ = os.WriteFile(path, []byte(`{"version_code":0,"version_name":"x"}`), 0600)
	if _, err := loadAppVersion(path); err == nil {
		t.Fatal("zero version accepted")
	}
	_ = os.WriteFile(path, []byte(`{"version_code":6,"version_name":"1.2.0"}`), 0600)
	v, err := loadAppVersion(path)
	if err != nil || v.VersionCode != 6 || v.DownloadURL != "/app.apk" {
		t.Fatalf("%+v %v", v, err)
	}
}
