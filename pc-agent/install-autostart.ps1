$ErrorActionPreference = 'Stop'
$agentPath = Join-Path $PSScriptRoot 'asus-pc-agent.exe'
if (-not (Test-Path -LiteralPath $agentPath -PathType Leaf)) {
    throw 'Build the Windows agent before installing autostart.'
}
# Remove old legacy scheduled task if present to avoid dual-launch port conflict
Unregister-ScheduledTask -TaskName 'AsusPcControlAgent' -Confirm:$false -ErrorAction SilentlyContinue

$runPath = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
$runCommand = '"' + $agentPath + '"'
if (-not (Test-Path -LiteralPath $runPath)) {
    New-Item -Path $runPath -Force | Out-Null
}
New-ItemProperty -Path $runPath -Name AsusPcControlAgent -Value $runCommand -PropertyType String -Force | Out-Null
$runningAgent = Get-CimInstance Win32_Process -Filter "name='asus-pc-agent.exe'" |
    Where-Object { $_.ExecutablePath -eq $agentPath }
if (-not $runningAgent) {
    Start-Process -FilePath $agentPath -WorkingDirectory $PSScriptRoot -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $PSScriptRoot 'agent-stdout.log') `
        -RedirectStandardError (Join-Path $PSScriptRoot 'agent-stderr.log') | Out-Null
}
Write-Output 'ASUS Control autostart registered for the current Windows user.'
