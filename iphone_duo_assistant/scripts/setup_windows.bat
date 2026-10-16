@echo off
chcp 65001 >nul
cd /d "%~dp0\.."
echo [1/3] 创建虚拟环境（需要已安装 Python 3.12，并勾选 Add to PATH）
py -3.12 -m venv .venv || (echo 未找到 Python 3.12，请先从 python.org 安装 & pause & exit /b 1)
call .venv\Scripts\activate.bat
echo [2/3] 安装依赖
python -m pip install --upgrade pip
pip install -r requirements-dev.txt || (pause & exit /b 1)
echo [3/3] 安装 Playwright 自带 Chromium（备用；默认使用 Win11 自带的 Edge）
python -m playwright install chromium
echo 完成。双击 scripts\run_gui.bat 启动。
pause
