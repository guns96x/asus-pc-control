package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"os"
)

type Config struct {
	Port         int    `json:"port"`
	AuthToken    string `json:"auth_token"`
	EnableDDCCI  bool   `json:"enable_ddc_ci"`
}

func loadConfig(path string) (*Config, error) {
	if _, err := os.Stat(path); os.IsNotExist(err) {
		tokenBytes := make([]byte, 16)
		rand.Read(tokenBytes)
		token := hex.EncodeToString(tokenBytes)

		cfg := &Config{
			Port:        8765,
			AuthToken:   token,
			EnableDDCCI: true,
		}

		data, err := json.MarshalIndent(cfg, "", "  ")
		if err == nil {
			_ = os.WriteFile(path, data, 0644)
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
	return &cfg, nil
}
