@REM Maven Wrapper script for Windows
@REM Downloads Maven if not present and runs it

@echo off
setlocal

set "MAVEN_PROJECTBASEDIR=%~dp0"
set "WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists"

@REM Read distribution URL
for /f "usebackq tokens=1,* delims==" %%a in ("%WRAPPER_PROPERTIES%") do (
    if "%%a"=="distributionUrl" set "DIST_URL=%%b"
)

if "%DIST_URL%"=="" (
    echo Error: Could not read distributionUrl from %WRAPPER_PROPERTIES%
    exit /b 1
)

@REM Extract version
for /f "tokens=4 delims=/" %%v in ("%DIST_URL%") do set "MAVEN_VERSION=%%v"

set "MAVEN_DIST=%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%"

@REM Download if not present
if not exist "%MAVEN_DIST%" (
    echo Downloading Maven %MAVEN_VERSION%...
    if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"
    set "MAVEN_ZIP=%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%-bin.zip"

    powershell -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%-bin.zip'"

    echo Extracting Maven...
    powershell -Command "Expand-Archive -Path '%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%-bin.zip' -DestinationPath '%MAVEN_HOME%' -Force"
    del "%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%-bin.zip"
    echo Maven %MAVEN_VERSION% installed successfully.
)

@REM Find Java
if defined JAVA_HOME (
    set "JAVACMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVACMD=java"
)

@REM Run Maven
set "MAVEN_HOME=%MAVEN_DIST%"
"%MAVEN_DIST%\bin\mvn.cmd" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" %*

endlocal
