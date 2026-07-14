@echo off
set VITE_YUDAO_APP_API_BASE=http://127.0.0.1:48080/app-api
set VITE_FURNITURE_ASSISTANT_MODE=api
cd /d "%~dp0furniture web"
npm.cmd run dev -- --port 5174 --strictPort
