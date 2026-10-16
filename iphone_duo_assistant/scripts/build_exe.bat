@echo off
chcp 65001 >nul
cd /d "%~dp0\.."
call .venv\Scripts\activate.bat
pyinstaller --noconfirm --clean --onedir --windowed ^
  --name iPhoneDuoAssistant ^
  --collect-submodules duo_assistant ^
  run_app.py || (pause & exit /b 1)
echo.
echo 打包完成：dist\iPhoneDuoAssistant\iPhoneDuoAssistant.exe
echo 自检：dist\iPhoneDuoAssistant\iPhoneDuoAssistant.exe --self-test --self-test-out selftest_result.json
pause
