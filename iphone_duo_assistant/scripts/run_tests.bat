@echo off
chcp 65001 >nul
cd /d "%~dp0\.."
call .venv\Scripts\activate.bat
set DUO_TEST_CHANNEL=msedge
python -m pytest -v
echo.
echo 显示浏览器运行自检：
python run_app.py --self-test --headed --channel msedge
pause
