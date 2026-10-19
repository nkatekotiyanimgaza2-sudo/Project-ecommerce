$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$securePassword = $null
$passwordPointer = [IntPtr]::Zero

Push-Location $projectRoot
try {
    $mysqlUser = Read-Host 'MySQL username (default: root)'
    if ([string]::IsNullOrWhiteSpace($mysqlUser)) {
        $mysqlUser = 'root'
    }
    $env:MYSQL_USER = $mysqlUser
    $securePassword = Read-Host 'MySQL password (input is hidden)' -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $env:MYSQL_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)

    & (Join-Path $projectRoot 'start-java-backend.bat')
    if ($LASTEXITCODE -ne 0) {
        throw 'The Java backend launcher failed. Review the launcher output above.'
    }
}
finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    if ($securePassword) {
        $securePassword.Dispose()
    }
    Remove-Item Env:MYSQL_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MYSQL_USER -ErrorAction SilentlyContinue
    Pop-Location
}
