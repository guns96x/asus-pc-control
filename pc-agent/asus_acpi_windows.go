package main

import (
	"encoding/binary"
	"errors"
	"fmt"
	"syscall"
	"unsafe"
)

// ATKACPI protocol: method, argument size, device id, control value (little endian).
func asusCall(method, device, value uint32) (uint32, error) {
	path, _ := syscall.UTF16PtrFromString(`\\.\ATKACPI`)
	h, err := syscall.CreateFile(path, syscall.GENERIC_READ|syscall.GENERIC_WRITE,
		syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE, nil, syscall.OPEN_EXISTING, 0, 0)
	if err != nil {
		return 0, fmt.Errorf("доступ до ASUS System Control Interface: %w", err)
	}
	defer syscall.CloseHandle(h)
	input, output := make([]byte, 16), make([]byte, 16)
	binary.LittleEndian.PutUint32(input, method)
	binary.LittleEndian.PutUint32(input[4:], 8)
	binary.LittleEndian.PutUint32(input[8:], device)
	binary.LittleEndian.PutUint32(input[12:], value)
	var returned uint32
	ok, _, callErr := procDeviceIoControl.Call(uintptr(h), 0x0022240C,
		uintptr(unsafe.Pointer(&input[0])), uintptr(len(input)), uintptr(unsafe.Pointer(&output[0])),
		uintptr(len(output)), uintptr(unsafe.Pointer(&returned)), 0)
	if ok == 0 {
		return 0, fmt.Errorf("ASUS ACPI: %w", callErr)
	}
	if returned < 4 {
		return 0, errors.New("ASUS ACPI повернув неповну відповідь")
	}
	return binary.LittleEndian.Uint32(output), nil
}
