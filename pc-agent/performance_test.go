package main

import (
	"testing"
)

func TestPerformanceModeNames(t *testing.T) {
	if PerfModeName(PerfModeBalanced) != "balanced" {
		t.Errorf("expected balanced, got %s", PerfModeName(PerfModeBalanced))
	}
	if PerfModeName(PerfModeTurbo) != "turbo" {
		t.Errorf("expected turbo, got %s", PerfModeName(PerfModeTurbo))
	}
	if PerfModeName(PerfModeSilent) != "silent" {
		t.Errorf("expected silent, got %s", PerfModeName(PerfModeSilent))
	}
	if PerfModeName(99) != "unknown" {
		t.Errorf("expected unknown, got %s", PerfModeName(99))
	}

	if PerfModeTitle(PerfModeBalanced) != "Баланс" {
		t.Errorf("expected Баланс, got %s", PerfModeTitle(PerfModeBalanced))
	}
	if PerfModeTitle(PerfModeTurbo) != "Турбо" {
		t.Errorf("expected Турбо, got %s", PerfModeTitle(PerfModeTurbo))
	}
	if PerfModeTitle(PerfModeSilent) != "Тихий" {
		t.Errorf("expected Тихий, got %s", PerfModeTitle(PerfModeSilent))
	}
}

func TestSetCurrentPerformanceModeValidation(t *testing.T) {
	if err := SetCurrentPerformanceMode(-1); err == nil {
		t.Error("expected error for mode -1")
	}
	if err := SetCurrentPerformanceMode(3); err == nil {
		t.Error("expected error for mode 3")
	}
}
