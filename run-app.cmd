@echo off
setlocal
for /f "tokens=2*" %%A in ('reg query "HKCU\Environment" /v JAVA_HOME 2^>nul') do set "JAVA_HOME=%%B"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
call "%~dp0gradlew.bat" :app-desktop:run
