@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-furniture-agent-backend.ps1"
exit /b %ERRORLEVEL%
