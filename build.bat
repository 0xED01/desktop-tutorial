@echo off
chcp 65001 >nul
REM 本地打包（需要先安装 Python 3.10+，安装时勾选 Add to PATH）
python -m pip install -r requirements.txt pyinstaller || goto :err
python test_core.py || goto :err
python -m PyInstaller --noconfirm --onefile --windowed --collect-all playwright --name AppleStockMonitor apple_stock_monitor.py || goto :err
echo.
echo 打包完成：dist\AppleStockMonitor.exe
pause
exit /b 0
:err
echo 打包失败，请查看上方错误信息
pause
exit /b 1
