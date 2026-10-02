@echo off
setlocal
cd /d "%~dp0"

if not defined JAVA_HOME (
    for /d %%D in ("%LOCALAPPDATA%\Programs\Java\jdk-*") do (
        if exist "%%~fD\bin\javac.exe" set "JAVA_HOME=%%~fD"
    )
)

if defined JAVA_HOME (
    set "JAVAC=%JAVA_HOME%\bin\javac.exe"
    set "JAVA=%JAVA_HOME%\bin\java.exe"
)

if not defined JAVAC set "JAVAC=javac"
if not defined JAVA set "JAVA=java"

where javac >nul 2>nul
if errorlevel 1 if not exist "%JAVAC%" (
    echo Java JDK not found. Install a JDK and make sure java and javac are on PATH.
    pause
    exit /b 1
)

set "CLASSES=%TEMP%\ProductCatalogueBackend"
if not exist "%CLASSES%" mkdir "%CLASSES%"

"%JAVAC%" -encoding UTF-8 -d "%CLASSES%" backend\java\ProductCatalogueServer.java
if errorlevel 1 (
    echo Java backend compilation failed.
    pause
    exit /b 1
)

"%JAVA%" -cp "%CLASSES%" ProductCatalogueServer "%CD%\public_html"