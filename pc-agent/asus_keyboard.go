package main

import (
	"errors"
	"fmt"
	"sync"
)

const ASUS_KBD_BACKLIGHT_DEVID uint32 = 0x00050021
const asusStatusLED uint32 = 0x000600C2

var keyboardMu sync.Mutex
var deviceRead = readAsusDevice
var deviceWrite = writeAsusDevice

func readAsusDevice(device uint32) (uint32, error) { return asusCall(0x53545344, device, 0) }
func writeAsusDevice(device, value uint32) error {
	result, err := asusCall(0x53564544, device, value)
	if err != nil {
		return err
	}
	if result != 1 {
		return fmt.Errorf("ASUS відхилив команду (результат %d)", result)
	}
	return nil
}
func devicePresent(raw uint32) bool { return int32(raw) >= 0 && raw&0x10000 != 0 && raw&0x80000 == 0 }
func decodeKeyboardLevel(raw uint32) (int, error) {
	if !devicePresent(raw) {
		return -1, errors.New("Драйвер ASUS не підтримує керування підсвіткою клавіатури")
	}
	level := int(raw & 0x7F)
	if level > 3 {
		return -1, fmt.Errorf("Невідома яскравість клавіатури: %d", level)
	}
	return level, nil
}
func readKeyboardLevel() (int, error) {
	raw, err := deviceRead(ASUS_KBD_BACKLIGHT_DEVID)
	if err != nil {
		return -1, err
	}
	return decodeKeyboardLevel(raw)
}
func ReadAsusKeyboardBrightness() (int, error) {
	keyboardMu.Lock()
	defer keyboardMu.Unlock()
	return readKeyboardLevel()
}
func GetAsusKeyboardBrightness() int {
	level, err := ReadAsusKeyboardBrightness()
	if err != nil {
		return -1
	}
	return level
}
func setKeyboardLevel(level int) error {
	if level < 0 || level > 3 {
		return errors.New("Яскравість клавіатури має бути від 0 до 3")
	}
	if _, err := readKeyboardLevel(); err != nil {
		return err
	}
	// TUF firmware requires bit 7 when setting brightness, including level zero.
	if err := deviceWrite(ASUS_KBD_BACKLIGHT_DEVID, 0x80|uint32(level)); err != nil {
		return err
	}
	actual, err := readKeyboardLevel()
	if err != nil {
		return fmt.Errorf("Не вдалося перевірити підсвітку після команди: %w", err)
	}
	if actual != level {
		return fmt.Errorf("ASUS повернув яскравість %d замість %d", actual, level)
	}
	return nil
}
func SetAsusKeyboardBrightness(level int) error {
	keyboardMu.Lock()
	defer keyboardMu.Unlock()
	return setKeyboardLevel(level)
}
func ToggleAsusKeyboardBacklight() (int, error) {
	keyboardMu.Lock()
	defer keyboardMu.Unlock()
	current, err := readKeyboardLevel()
	if err != nil {
		return -1, err
	}
	next := 0
	if current == 0 {
		next = 3
	}
	if err := setKeyboardLevel(next); err != nil {
		return -1, err
	}
	return next, nil
}
