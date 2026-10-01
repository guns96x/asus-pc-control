package main

import "syscall"

// Procedures are resolved once; NewProc on every request allocated and re-looked-up symbols.
var (
	user32   = syscall.NewLazyDLL("user32.dll")
	kernel32 = syscall.NewLazyDLL("kernel32.dll")
	dxva2    = syscall.NewLazyDLL("dxva2.dll")
	powrprof = syscall.NewLazyDLL("powrprof.dll")

	procEnumDisplayMonitors = user32.NewProc("EnumDisplayMonitors")
	procSendMessageTimeoutW = user32.NewProc("SendMessageTimeoutW")

	procGetSystemPowerStatus         = kernel32.NewProc("GetSystemPowerStatus")
	procSetThreadExecutionState      = kernel32.NewProc("SetThreadExecutionState")
	procGetCurrentProcessId          = kernel32.NewProc("GetCurrentProcessId")
	procProcessIdToSessionId         = kernel32.NewProc("ProcessIdToSessionId")
	procWTSGetActiveConsoleSessionId = kernel32.NewProc("WTSGetActiveConsoleSessionId")
	procDeviceIoControl              = kernel32.NewProc("DeviceIoControl")

	procGetNumberOfPhysicalMonitorsFromHMONITOR = dxva2.NewProc("GetNumberOfPhysicalMonitorsFromHMONITOR")
	procGetPhysicalMonitorsFromHMONITOR         = dxva2.NewProc("GetPhysicalMonitorsFromHMONITOR")
	procDestroyPhysicalMonitors                 = dxva2.NewProc("DestroyPhysicalMonitors")
	procGetVCPFeatureAndVCPFeatureReply         = dxva2.NewProc("GetVCPFeatureAndVCPFeatureReply")
	procSetVCPFeature                           = dxva2.NewProc("SetVCPFeature")
	procGetCapabilitiesStringLength             = dxva2.NewProc("GetCapabilitiesStringLength")
	procCapabilitiesRequestAndCapabilitiesReply = dxva2.NewProc("CapabilitiesRequestAndCapabilitiesReply")

	procSetSuspendState = powrprof.NewProc("SetSuspendState")
)
