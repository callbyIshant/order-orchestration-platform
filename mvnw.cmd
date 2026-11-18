@echo off
setlocal
set "MAVEN_VERSION=3.10.1"
if not defined MAVEN_USER_HOME set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "WRAPPER_HOME=%MAVEN_USER_HOME%\wrapper\dists\apache-maven-%MAVEN_VERSION%"
set "MAVEN_HOME=%WRAPPER_HOME%\apache-maven-%MAVEN_VERSION%"

if exist "%MAVEN_HOME%\bin\mvn.cmd" goto runMaven

if not exist "%WRAPPER_HOME%" mkdir "%WRAPPER_HOME%"
set "MAVEN_ARCHIVE=%WRAPPER_HOME%\apache-maven-%MAVEN_VERSION%-bin.zip"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference = 'Stop'; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%MAVEN_ARCHIVE%'; Expand-Archive -LiteralPath '%MAVEN_ARCHIVE%' -DestinationPath '%WRAPPER_HOME%' -Force; Remove-Item -LiteralPath '%MAVEN_ARCHIVE%' -Force"
if errorlevel 1 exit /b %errorlevel%

:runMaven
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %errorlevel%
