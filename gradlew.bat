@echo off
setlocal
set "APP_HOME=%~dp0"
set "GRADLE_VERSION=8.9"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "DIST_DIR=%GRADLE_USER_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin\nerdfm-bootstrap"
set "GRADLE_BIN=%DIST_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat"
if not exist "%GRADLE_BIN%" (
  if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip=Join-Path '%DIST_DIR%' 'gradle-%GRADLE_VERSION%-bin.zip'; $part=$zip+'.part'; if (!(Test-Path $zip)) { Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile $part; Move-Item -LiteralPath $part -Destination $zip }; Expand-Archive -LiteralPath $zip -DestinationPath '%DIST_DIR%' -Force"
  if errorlevel 1 exit /b %errorlevel%
)
call "%GRADLE_BIN%" -p "%APP_HOME%" %*
exit /b %errorlevel%
