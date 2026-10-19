@echo off
setlocal
cd /d "%~dp0"

where dotnet >nul 2>nul
if errorlevel 1 (
    echo .NET SDK not found. Install the .NET 8 SDK from https://dotnet.microsoft.com/download
    pause
    exit /b 1
)

dotnet run --project backend\csharp\AllFitCheckoutApi\AllFitCheckoutApi.csproj
