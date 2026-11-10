<div align="center">

# 🔑 宿舍快开

**本人授权门锁的安卓蓝牙辅助工具**

<img src="https://img.shields.io/badge/版本-0.10.0-blue?style=flat-square" />
<img src="https://img.shields.io/badge/platform-Android-green?style=flat-square&logo=android&logoColor=white" />
<img src="https://img.shields.io/badge/language-Java-red?style=flat-square&logo=java&logoColor=white" />
<img src="https://img.shields.io/badge/license-MIT-yellow?style=flat-square" />
<img src="https://img.shields.io/badge/状态-非官方-orange?style=flat-square" />

[下载与版本说明](https://github.com/Leon-Cjl/dorm-native-open/releases) · [自助配置说明](self-service/README.md)

</div>

---

> ## ⚠️ 重要免责声明
>
> **本项目为个人开发的非官方工具，与任何学校、门锁厂商或物业管理方无关联。**
>
> - **使用前提**：用户必须已通过合法渠道获得相应门锁的有效授权。本工具不提供、不绕过任何门锁的认证机制。
> - **授权范围**：仅限本人已获授权的门锁使用。请勿上传门锁授权文件或私人签名密钥到任何公开场所。
> - **功能限制**：自动接近、锁屏后台保持和新用户流程尚未完成真实环境验证，不保证在所有设备上可用。
> - **责任归属**：使用者需确保遵守所在学校的宿舍管理规定和相关法律法规。因不当使用造成的后果由使用者自行承担。
> - **`.dormkey` 文件**：其签名仅为本项目迁移校验，不是学校发卡或门锁授权。公开工具包不含签名私钥，不接受任意房号或自造签名。

---

## 功能概览

| 功能 | 说明 |
|---|---|
| 🔵 原生蓝牙开门 | 手动按钮开门，不依赖网络 |
| 🔄 自动预连接 | 允许附近设备后主动预连接，失败按 2/4/6 秒间隔重试 |
| 📋 本地账号管理 | 管理本人宿舍信息，数据仅存本机 |
| 🏠 首页状态面板 | 集中显示开门及连接状态 |
| 🔔 前台服务 | 退到后台后由 connectedDevice 前台服务继续维持连接 |

> 预连接不写唤醒或开锁命令；实际开锁仍需当前有效授权。系统终止进程后需重新打开应用，无开机自启。

## 使用边界

需本人有效授权和兼容的门锁。以下功能尚未完成真实环境验证：

- ⏳ 长时间锁屏保持
- 👤 另一名新用户导入流程
- 🤖 自动接近功能

**2026-11-07 在小米15上的测试记录**：用户手动开门反馈"门已打开，几乎立即"。但用户反馈不是仪器计时；此前用户反馈两种自动模式未成功，不能用手动反馈证明自动模式可用。测试的早期 0.10.0 构建与最终文件摘要分开记录在 `dist/device-checks.json`。

## 首次取得文件

不是让新用户凭空寻找 `.dormkey`：公开包包含 `onboarding/给AI的指令.txt` 和 `session-export.cjs`。本人登录电脑微信小程序后，AI 读取当前学校/学号/宿舍及对应页面证据，只读查询该账号已有权限，生成 `.dormrequest`；维护者运行一条 `tools/issue-dormkey.cjs` 命令自动签出 `.dormkey`，再由本人手机选中文件直接绑定。

> 此流程不需要官方新增导出按钮，但仍需维护者的本地工具自动签发并返回文件，尚非公众一键登录。页面截图/文件摘要不自动证明操作者身份，工具不提供远程身份认证；只填学号不是登录。

细节见 `onboarding/先看这个.md`、`docs/维护者签发授权.md` 和发布包内操作教程。

## 构建与交付

```powershell
.\build.ps1 -Public    # 公众 APK，不预置任何人的授权
.\build.ps1 -Personal  # 个人 APK，含本人资料，不能公开
```

| 构建类型 | 说明 |
|---|---|
| `-Public` | 公众包，不含任何人的授权数据 |
| `-Personal` | 个人包，含本人资料，**不可公开** |

JDK/SDK 路径在脚本中。首次导出脚本只依赖已有 Node 内置模块，无 npm 安装、常驻进程或开机自启。

## 测试

```powershell
node tests/onboarding-tests.cjs     # 新流程测试
node tools/verify-port.cjs          # 校验迁移/协议及当前 APK
py -3.12 tools/package-delivery.py  # 构建源码归档并检查私人数据排除
```

真机结果见单独设备记录；模拟不能代替实体门测试。

## 参与开发

由 [Leon-Cjl](https://github.com/Leon-Cjl) 主导，AI 编程助手 [Claude](https://claude.com/claude-code)（Anthropic）、[Codex](https://openai.com/codex)（OpenAI）、[Gemini](https://gemini.google.com)（Google）和 [DeepSeek](https://www.deepseek.com) 参与了代码编写。

## 许可证

代码以 [MIT](LICENSE) 许可证开源。

用户提供的资料位于 `research/system-optimization-handoff`。GitHub 蓝牙参考及许可证在 `research/ble-github`；`research/DLUT-door-open` 使用另一学校的 SSO/门禁系统，不能直接移植成公寓智锁登录。界面参考见 `android/assets/ui/NOTICE.txt`。

## 相关项目

[Leon 的项目主页](https://github.com/Leon-Cjl) · [校园电费](https://github.com/Leon-Cjl/campus-dianfei) · [工大学伴](https://github.com/Leon-Cjl/gongda-xueban) · [奶蛙版](https://github.com/Leon-Cjl/gongda-xueban-naiwa) · [宿舍快开](https://github.com/Leon-Cjl/dorm-native-open)
