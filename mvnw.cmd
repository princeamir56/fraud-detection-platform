@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup script for Windows (only-script distribution).
@REM ----------------------------------------------------------------------------
@echo off
setlocal enabledelayedexpansion

set "MAVEN_PROJECTBASEDIR=%~dp0"
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"
set "WRAPPER_PROPS=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

if not exist "%WRAPPER_PROPS%" (
  echo Cannot find %WRAPPER_PROPS% 1>&2
  exit /b 1
)

set "DISTRIBUTION_URL="
for /f "usebackq tokens=1,* delims==" %%A in ("%WRAPPER_PROPS%") do (
  if "%%A"=="distributionUrl" set "DISTRIBUTION_URL=%%B"
)
if "%DISTRIBUTION_URL%"=="" (
  echo distributionUrl not set in %WRAPPER_PROPS% 1>&2
  exit /b 1
)

for %%F in ("%DISTRIBUTION_URL%") do set "DIST_ZIP=%%~nxF"
set "DIST_NAME=%DIST_ZIP:-bin.zip=%"
if "%MAVEN_USER_HOME%"=="" set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "MAVEN_HOME=%MAVEN_USER_HOME%\wrapper\dists\%DIST_NAME%\%DIST_NAME%"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo Downloading Apache Maven from %DISTRIBUTION_URL% ... 1>&2
  if not exist "%MAVEN_USER_HOME%\wrapper\dists\%DIST_NAME%" mkdir "%MAVEN_USER_HOME%\wrapper\dists\%DIST_NAME%"
  set "TMP_ZIP=%MAVEN_USER_HOME%\wrapper\dists\%DIST_NAME%\%DIST_ZIP%"
  powershell -NoProfile -Command "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%TMP_ZIP%'"
  powershell -NoProfile -Command "Expand-Archive -Force -Path '%TMP_ZIP%' -DestinationPath '%MAVEN_USER_HOME%\wrapper\dists\%DIST_NAME%'"
  del "%TMP_ZIP%"
)

"%MAVEN_HOME%\bin\mvn.cmd" %*
