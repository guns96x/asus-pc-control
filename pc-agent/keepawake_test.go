package main

import (
	"testing"
)

func TestKeepAwakeControllerLifecycle(t *testing.T) {
	k := &KeepAwakeController{}
	if k.IsActive() {
		t.Fatal("expected inactive controller initially")
	}

	if err := k.Acquire(); err != nil {
		t.Fatalf("Acquire failed: %v", err)
	}
	if !k.IsActive() {
		t.Fatal("expected active controller after Acquire")
	}

	// Idempotent acquire
	if err := k.Acquire(); err != nil {
		t.Fatalf("Second Acquire failed: %v", err)
	}

	k.Release()
	if k.IsActive() {
		t.Fatal("expected inactive controller after Release")
	}

	// Idempotent release
	k.Release()
}
