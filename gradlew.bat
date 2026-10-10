@echo off
setlocal
set ROOT=%~dp0
set DIST=%USERPROFILE%\.gradle\mapperzones\gradle-8.8
if exist "%DIST%\bin\gradle.bat" goto run
if not exist "%USERPROFILE%\.gradle\mapperzones" mkdir "%USERPROFILE%\.gradle\mapperzones"
echo Gradle 8.8 is not installed locally. Downloading it now...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $u='https://services.gradle.org/distributions/gradle-8.8-bin.zip'; $z=Join-Path $env:TEMP 'mapperzones-gradle-8.8.zip'; Invoke-WebRequest $u -OutFile $z; Expand-Archive -Path $z -DestinationPath (Join-Path $env:USERPROFILE '.gradle\mapperzones') -Force; Remove-Item $z"
if errorlevel 1 (echo Could not download Gradle. Check internet access and try again.& exit /b 1)
:run
call "%DIST%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
