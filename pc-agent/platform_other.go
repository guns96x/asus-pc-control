//go:build !windows

package main

import (
	"errors"
	"log"
)

// Stubs keep the portable logic and HTTP handlers testable on Linux CI; the agent ships for Windows only.
var errUnsupportedPlatform = errors.New("Підтримується лише Windows")

func asusCall(method, device, value uint32) (uint32, error) { return 0, errUnsupportedPlatform }
func consoleSessionActive() bool                            { return false }
func sendDisplayPower(sleep, useDdc bool) error             { return errUnsupportedPlatform }
func probePhysicalDisplays() ([]DisplayInfo, error)         { return nil, errUnsupportedPlatform }
func readPowerStatus() (powerStatus, bool)                  { return powerStatus{}, false }
func HibernateSystem()                                      { log.Println("[Power] Hibernate is unsupported on this platform") }
