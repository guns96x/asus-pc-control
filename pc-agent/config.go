package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
)

type Config struct {
	Port        int    `json:"port"`
	AuthToken   string `json:"auth_token"`
	EnableDDCCI bool   `json:"enable_ddc_ci"`
}

func loadConfig(path string) (*Config, error) {
	if _, err := os.Stat(path); os.IsNotExist(err) {
		tokenBytes := make([]byte, 16)
		if _, err := rand.Read(tokenBytes); err != nil {
			return nil, err
		}
		token := hex.EncodeToString(tokenBytes)

		cfg := &Config{
			Port:        8765,
			AuthToken:   token,
			EnableDDCCI: true,
		}

		data, err := json.MarshalIndent(cfg, "", "  ")
		if err != nil {
			return nil, err
		}
		if err := os.WriteFile(path, data, 0600); err != nil {
			return nil, fmt.Errorf("save agent configuration: %w", err)
		}
		return cfg, nil
	}

	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	var cfg Config
	if err := json.Unmarshal(data, &cfg); err != nil {
		return nil, err
	}

	if cfg.Port == 0 {
		cfg.Port = 8765
	}
	if cfg.Port < 1 || cfg.Port > 65535 {
		return nil, fmt.Errorf("agent port must be between 1 and 65535")
	}
	if cfg.AuthToken == "" {
		return nil, fmt.Errorf("agent authentication token must not be empty")
	}
	return &cfg, nil
}
