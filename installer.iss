; Inno Setup 安装脚本（由 GitHub Actions 自动编译）
#define MyAppName "苹果库存监控"
#define MyAppVersion GetEnv("APP_VERSION")
#if MyAppVersion == ""
  #define MyAppVersion "1.0.0"
#endif

[Setup]
AppId={{6C1C4E2B-7F3A-4D35-9A57-3B0E6B8C2D41}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher=AppleStockMonitor
DefaultDirName={localappdata}\AppleStockMonitor
DefaultGroupName={#MyAppName}
PrivilegesRequired=lowest
OutputDir=Output
OutputBaseFilename=AppleStockMonitor-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayName={#MyAppName}

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; Flags: checkedonce

[Files]
Source: "dist\AppleStockMonitor.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\AppleStockMonitor.exe"
Name: "{group}\卸载 {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\AppleStockMonitor.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\AppleStockMonitor.exe"; Description: "立即运行 {#MyAppName}"; Flags: nowait postinstall skipifsilent
