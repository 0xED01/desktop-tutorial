@echo off
chcp 65001 >nul
cd /d "%~dp0\.."
call .venv\Scripts\activate.bat
python run_app.py --mock-store --port 8765 --preset normal
