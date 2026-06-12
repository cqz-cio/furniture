@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0test-furniture-agent.ps1" %*
exit /b %ERRORLEVEL%
