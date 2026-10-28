@echo off
setlocal
rem Starts the local cleanliness model service (computer_vision\service) on 127.0.0.1:8090.
rem First run: creates computer_vision\service\.venv_service and installs the pinned requirements.
cd /d "%~dp0..\computer_vision\service" || exit /b 1

if not exist ".venv_service\Scripts\python.exe" (
    echo Creating the service virtual environment...
    py -3 -m venv .venv_service || python -m venv .venv_service
    if errorlevel 1 exit /b %errorlevel%
    call .venv_service\Scripts\python.exe -m pip install -r requirements.txt
    if errorlevel 1 exit /b %errorlevel%
)

echo Starting the local vision service (default http://127.0.0.1:8090, LOCAL_VISION_PORT to change it)...
call .venv_service\Scripts\python.exe app.py
exit /b %errorlevel%
