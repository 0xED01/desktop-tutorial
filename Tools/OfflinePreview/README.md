# 离线预览工具（仅用于开发校验，不参与游戏构建）

在没有 Unity 授权的环境里验证场景生成代码：用 `UnityStub.cs`（极简的 UnityEngine 替身）
直接编译并运行 `Assets/Scripts` 里的 `OfficeBuilder`、`CharacterRig`、`PoseLibrary` 等真实代码，
再用 `Program.cs` 里的软件光栅器把生成的网格渲染成图片（即 `Docs/preview/*.jpg`）。

它能验证：网格绕序 / 法线朝向、家具布局与比例、UV 方向、人物骨架与姿态。
它不能验证：URP 的真实光影、阴影、后期、触控手感与帧率——这些必须在 Unity / 真机上确认。

编译方式（任意 .NET 6+ SDK）：把 `UnityStub.cs`、`Program.cs` 与
`Assets/Scripts/Core/*.cs`、`Assets/Scripts/World/OfficeBuilder.cs`、
`Assets/Scripts/Player/CharacterRig.cs`、`Assets/Scripts/Player/PoseLibrary.cs` 一起编译为控制台程序运行，
输出 PPM 图片。
