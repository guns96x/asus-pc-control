[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ApkPath,
    [string]$ReleaseNotes = 'Оновлення ASUS Control',
    [string]$BuildToolsPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools\35.0.0')
)
$ErrorActionPreference = 'Stop'
$candidatePath = (Resolve-Path -LiteralPath $ApkPath).Path
$currentApk = Join-Path $PSScriptRoot 'AsusControl.apk'
$agentApk = Join-Path $PSScriptRoot 'pc-agent\AsusControl.apk'
$manifestPath = Join-Path $PSScriptRoot 'pc-agent\app-release.json'
$signer = Join-Path $BuildToolsPath 'apksigner.bat'
$aapt = Join-Path $BuildToolsPath 'aapt2.exe'

function Get-ApkCertificate([string]$Path) {
    $certOutput = & $signer verify --print-certs $Path 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $certificates = @($certOutput | Select-String -Pattern 'certificate SHA-256 digest:' |
        ForEach-Object { ($_.Line -split 'digest:')[1].Trim() } | Sort-Object)
    if ($certificates.Count -eq 0) { throw 'APK has no signing certificate.' }
    return $certificates -join ','
}

function Get-ApkPackage([string]$Path) {
    $badging = & $aapt dump badging $Path 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the APK package.' }
    $packageLine = $badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
    if ($packageLine -notmatch "name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'" ) {
        throw 'APK package metadata is invalid.'
    }
    return [pscustomobject]@{Name=$Matches[1]; Code=[int]$Matches[2]; Version=$Matches[3]}
}

$candidate = Get-ApkPackage $candidatePath
if ($candidate.Name -ne 'com.hermes.pccontrol') { throw 'Unexpected Android package identity.' }
$candidateCertificate = Get-ApkCertificate $candidatePath
if (Test-Path -LiteralPath $currentApk) {
    if ($candidateCertificate -ne (Get-ApkCertificate $currentApk)) {
        throw 'Signing identity changed; the existing installation cannot be updated in place.'
    }
    $current = Get-ApkPackage $currentApk
    if ($candidate.Code -lt $current.Code) { throw 'Refusing to publish a downgrade.' }
}

# Validate first, then back up and publish both download copies before advertising the version.
$backupPath = Join-Path $PSScriptRoot ('.release-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backupPath -Force | Out-Null
foreach ($entry in @(@{Source=$currentApk; Name='AsusControl.apk'},
    @{Source=$agentApk; Name='agent-AsusControl.apk'},
    @{Source=$manifestPath; Name='app-release.json'})) {
    if (Test-Path -LiteralPath $entry.Source) {
        Copy-Item -LiteralPath $entry.Source -Destination (Join-Path $backupPath $entry.Name)
    }
}
foreach ($destination in @($currentApk, $agentApk)) {
    Copy-Item -LiteralPath $candidatePath -Destination ($destination + '.new') -Force
    Move-Item -LiteralPath ($destination + '.new') -Destination $destination -Force
}
$release = [ordered]@{
    version_code=$candidate.Code
    version_name=$candidate.Version
    download_url='/app.apk'
    release_notes=$ReleaseNotes
}
[System.IO.File]::WriteAllText(($manifestPath + '.new'), ($release | ConvertTo-Json), [System.Text.UTF8Encoding]::new($false))
Move-Item -LiteralPath ($manifestPath + '.new') -Destination $manifestPath -Force
Write-Output ('Published ASUS Control ' + $candidate.Version + ' (code ' + $candidate.Code + ').')
