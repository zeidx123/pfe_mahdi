@echo off
setlocal
cd /d "%~dp0..\MOBILE" || exit /b 1

set "GESTI_API_URL=%~1"
if not defined GESTI_API_URL set "GESTI_API_URL=http://127.0.0.1:8080/api"

echo Preparing Flutter dependencies...
call flutter pub get
if errorlevel 1 exit /b %errorlevel%

echo.
echo Starting the driver application in Microsoft Edge...
echo API: %GESTI_API_URL%
call flutter run -d edge --web-port 8088 --dart-define=API_BASE_URL=%GESTI_API_URL%
exit /b %errorlevel%
