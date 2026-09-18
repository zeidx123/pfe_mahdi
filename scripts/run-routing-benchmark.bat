@echo off
setlocal
where py >nul 2>nul
if %errorlevel% equ 0 (
  py -3 "%~dp0run-routing-benchmark.py" %*
) else (
  python "%~dp0run-routing-benchmark.py" %*
)
exit /b %errorlevel%
