package main

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func mockKeyboard(t *testing.T, read func(uint32) (uint32, error), write func(uint32, uint32) error) {
	oldR, oldW := deviceRead, deviceWrite
	deviceRead, deviceWrite = read, write
	t.Cleanup(func() { deviceRead, deviceWrite = oldR, oldW })
}
func TestKeyboardSupportedAndUnknown(t *testing.T) {
	for _, raw := range []uint32{0, 0xffffffff, 0x90003, 0x10009} {
		if _, e := decodeKeyboardLevel(raw); e == nil {
			t.Errorf("unknown raw %x accepted", raw)
		}
	}
	for i := 0; i < 4; i++ {
		v, e := decodeKeyboardLevel(0x50000 | uint32(i))
		if e != nil || v != i {
			t.Fatalf("level %d: %v", i, e)
		}
	}
}
func TestKeyboardCommandUsesTufFlagAndReadback(t *testing.T) {
	raw := uint32(0x50003)
	var value uint32
	mockKeyboard(t, func(uint32) (uint32, error) { return raw, nil }, func(d, v uint32) error {
		if d != ASUS_KBD_BACKLIGHT_DEVID {
			t.Fatal("wrong device")
		}
		value = v
		raw = 0x50000 | (v & 0x7f)
		return nil
	})
	if e := SetAsusKeyboardBrightness(0); e != nil {
		t.Fatal(e)
	}
	if value != 0x80 {
		t.Fatalf("missing TUF flag: %x", value)
	}
	if GetAsusKeyboardBrightness() != 0 {
		t.Fatal("did not read real level")
	}
}
func TestFailedKeyboardWriteDoesNotInventState(t *testing.T) {
	mockKeyboard(t, func(uint32) (uint32, error) { return 0x50003, nil }, func(uint32, uint32) error { return errors.New("driver refused") })
	if e := SetAsusKeyboardBrightness(0); e == nil {
		t.Fatal("failure hidden")
	}
	if GetAsusKeyboardBrightness() != 3 {
		t.Fatal("reported requested state as actual")
	}
}
func TestKeyboardReadbackMismatchFails(t *testing.T) {
	mockKeyboard(t, func(uint32) (uint32, error) { return 0x50003, nil }, func(uint32, uint32) error { return nil })
	if e := SetAsusKeyboardBrightness(0); e == nil {
		t.Fatal("readback mismatch hidden")
	}
}
func TestUnavailableKeyboardIsUnknown(t *testing.T) {
	mockKeyboard(t, func(uint32) (uint32, error) { return 0, errors.New("not available") }, func(uint32, uint32) error { t.Fatal("must not write unknown device"); return nil })
	if GetAsusKeyboardBrightness() != -1 {
		t.Fatal("unknown must not look like brightness 3")
	}
	if e := SetAsusKeyboardBrightness(0); e == nil {
		t.Fatal("unsupported accepted")
	}
}
func TestKeyboardHTTPRejectsBadInputWithoutHardwareWrite(t *testing.T) {
	mockKeyboard(t, func(uint32) (uint32, error) { return 0x50003, nil }, func(uint32, uint32) error { t.Fatal("invalid request touched hardware"); return nil })
	for _, body := range []string{"{}", "bad json", `{"level":-1}`, `{"level":4}`} {
		w := httptest.NewRecorder()
		handleKeyboardSet(w, httptest.NewRequest(http.MethodPost, "/api/keyboard/level", strings.NewReader(body)))
		if w.Code != 400 {
			t.Errorf("%s: HTTP %d", body, w.Code)
		}
	}
	w := httptest.NewRecorder()
	handleKeyboardSet(w, httptest.NewRequest(http.MethodGet, "/api/keyboard/level?level=0", nil))
	if w.Code != 405 {
		t.Fatal(w.Code)
	}
}
func TestKeyboardHTTPReportsDriverFailure(t *testing.T) {
	mockKeyboard(t, func(uint32) (uint32, error) { return 0x50003, nil }, func(uint32, uint32) error { return errors.New("driver refused") })
	w := httptest.NewRecorder()
	handleKeyboardSet(w, httptest.NewRequest(http.MethodPost, "/api/keyboard/level", strings.NewReader(`{"level":0}`)))
	if w.Code != 503 || !strings.Contains(w.Body.String(), `"success":false`) {
		t.Fatalf("hidden hardware failure: %d %s", w.Code, w.Body.String())
	}
}

func TestMonitorOutsideConsoleFailsWithoutChangingRequestedState(t *testing.T) {
	old := activeConsoleSession
	oldState := requestedMonitorSleeping
	activeConsoleSession = func() bool { return false }
	requestedMonitorSleeping = false
	t.Cleanup(func() { activeConsoleSession = old; requestedMonitorSleeping = oldState })
	if e := SleepDisplays(true); e == nil {
		t.Fatal("RDP session must not claim physical monitor off")
	}
	if IsMonitorSleeping() {
		t.Fatal("failed command invented sleeping state")
	}
}
func TestKeyboardToggleReadsFirmwareEachTime(t *testing.T) {
	raw := uint32(0x50003)
	mockKeyboard(t, func(uint32) (uint32, error) { return raw, nil }, func(_ uint32, v uint32) error { raw = 0x50000 | (v & 0x7f); return nil })
	v, e := ToggleAsusKeyboardBacklight()
	if e != nil || v != 0 {
		t.Fatalf("first toggle: %d %v", v, e)
	}
	v, e = ToggleAsusKeyboardBacklight()
	if e != nil || v != 3 {
		t.Fatalf("second toggle: %d %v", v, e)
	}
}

func TestDarkModeDoesNotClaimAllUnsupportedIndicatorsOff(t *testing.T) {
	raw := uint32(0x50003)
	mockKeyboard(t, func(uint32) (uint32, error) { return raw, nil }, func(_ uint32, v uint32) error { raw = 0x50000 | (v & 0x7f); return nil })
	oldConsole, oldConfig := activeConsoleSession, config
	activeConsoleSession = func() bool { return false }
	config = &Config{EnableDDCCI: true}
	t.Cleanup(func() { activeConsoleSession = oldConsole; config = oldConfig })
	w := httptest.NewRecorder()
	handleDarkMode(w, httptest.NewRequest(http.MethodPost, "/api/lighting/dark", nil))
	if w.Code != 207 || !strings.Contains(w.Body.String(), `"success":false`) {
		t.Fatalf("partial action claimed full success: %d %s", w.Code, w.Body.String())
	}
}
