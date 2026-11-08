# Rebind only an already-authorized helper after an instrumentation test stops its process.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $projectRoot '.tools/platform-tools/adb.exe'
$savedServices = (& $adbPath -d shell settings get secure enabled_accessibility_services).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot read existing accessibility authorization' }
$entries = @($savedServices.Split(':'))
$helper = @($entries | Where-Object {
    $_ -eq 'local.jcc.probe/.CaptureService' -or $_ -eq 'local.jcc.probe/local.jcc.probe.CaptureService'
})
if ($helper.Count -eq 0) { throw 'Helper is not authorized; enable it manually in Accessibility settings' }
$others = ($entries | Where-Object { $_ -notin $helper }) -join ':'
try {
    if ($others.Length -eq 0) { & $adbPath -d shell settings delete secure enabled_accessibility_services }
    else { & $adbPath -d shell settings put secure enabled_accessibility_services $others }
    if ($LASTEXITCODE -ne 0) { throw 'Cannot disconnect helper' }
    Start-Sleep -Milliseconds 700
} finally {
    & $adbPath -d shell settings put secure enabled_accessibility_services $savedServices
    if ($LASTEXITCODE -ne 0) { throw 'Restore failed; original list: ' + $savedServices }
}
Write-Output 'Existing helper authorization restored; automation remains paused.'
