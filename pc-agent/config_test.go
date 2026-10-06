package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestNewConfigPersistsAuthentication(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.json")
	first, err := loadConfig(path)
	if err != nil {
		t.Fatal(err)
	}
	second, err := loadConfig(path)
	if err != nil {
		t.Fatal(err)
	}
	if first.AuthToken == "" || first.AuthToken != second.AuthToken {
		t.Fatal("authentication identity was not persisted")
	}
}

func TestConfigSaveFailureDoesNotPretendAgentIsReady(t *testing.T) {
	path := filepath.Join(t.TempDir(), "missing", "config.json")
	if _, err := loadConfig(path); err == nil {
		t.Fatal("unsaved authentication accepted")
	}
}

func TestConfigRejectsUnauthenticatedOrInvalidListener(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.json")
	for _, data := range []string{`{"port":8765}`, `{"port":-1,"auth_token":"test"}`, `{"port":65536,"auth_token":"test"}`} {
		if err := os.WriteFile(path, []byte(data), 0600); err != nil {
			t.Fatal(err)
		}
		if _, err := loadConfig(path); err == nil {
			t.Fatal("unsafe configuration accepted")
		}
	}
}
