@echo off
setlocal
set "APP_EXE=%~dp0app-desktop\build\compose\binaries\main\app\KNXFieldKit\KNXFieldKit.exe"
if exist "%APP_EXE%" (
    if "%~1"=="" (
        start "" "%APP_EXE%" --connect-sim
    ) else (
        start "" "%APP_EXE%" %*
    )
    exit /b 0
)

for /f "tokens=2*" %%A in ('reg query "HKCU\Environment" /v JAVA_HOME 2^>nul') do set "JAVA_HOME=%%B"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
if "%~1"=="" (
    call "%~dp0gradlew.bat" :app-desktop:run --args="--connect-sim"
) else (
    call "%~dp0gradlew.bat" :app-desktop:run --args="%*"
)
