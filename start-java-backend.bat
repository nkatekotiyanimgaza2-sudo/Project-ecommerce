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
set "LIB=%CLASSES%\lib"
set "DRIVER=%LIB%\mysql-connector-j-9.2.0.jar"
set "DRIVER_DOWNLOAD=%DRIVER%.download"
if not exist "%CLASSES%" mkdir "%CLASSES%"
if not exist "%LIB%" mkdir "%LIB%"

if not defined MYSQL_USER (
    echo Set MYSQL_USER and MYSQL_PASSWORD in this terminal before starting the backend.
    pause
    exit /b 
)
if not defined MYSQL_PASSWORD (
    echo Set MYSQL_USER and MYSQL_PASSWORD in this terminal before starting the backend.
    pause
    exit /b 1
)
if not defined ADMIN_TOKEN (
    set /p ADMIN_TOKEN=Enter the admin token you configured for this demo:
)
if not defined ADMIN_TOKEN (
    echo An admin token is required to start the backend.
    pause
    exit /b 1
)

if not exist "%DRIVER%" (
    where curl.exe >nul 2>nul
    if errorlevel 1 (
        echo curl.exe is required to download MySQL Connector/J.
        pause
        exit /b 1
    )
    echo Downloading MySQL Connector/J...
    curl.exe --fail --location --silent --show-error ^
        "https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar" ^
        --output "%DRIVER_DOWNLOAD%"
    if errorlevel 1 (
        del /q "%DRIVER_DOWNLOAD%" >nul 2>nul
        echo Could not download MySQL Connector/J.
        pause
        exit /b 1
    )
    move /y "%DRIVER_DOWNLOAD%" "%DRIVER%" >nul
    if errorlevel 1 (
        echo Could not save MySQL Connector/J.
        pause
        exit /b 1
    )
)

"%JAVAC%" -encoding UTF-8 -cp "%DRIVER%" -d "%CLASSES%" backend\java\ProductCatalogueServer.java
if errorlevel 1 (
    echo Java backend compilation failed.
    pause
    exit /b 1
)

"%JAVA%" -cp "%CLASSES%;%DRIVER%" ProductCatalogueServer "%CD%\public_html"