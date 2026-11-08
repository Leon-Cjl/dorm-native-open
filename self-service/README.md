# 自助签发：发布者不再参与

**先前发布的0.10.0公共APK不能导入其他人自签的文件。** 本路线给每个使用者在其电脑生成一对匹配的文件：自己的 `.dormkey` 和固定自己公钥的独立“宿舍快开·自助版.apk”。安装自助版后导入同一目录的文件，不需要发布者签名或私钥。

普通使用者直接把整个工具包和 `self-service/给AI的完整指令.txt` 交给能操作本机的AI。只会聊天、不能读取实际本人页面或运行命令的AI不能完成全部步骤。首次需要Windows电脑；本流程没有承诺纯手机或其他系统的一键构建。

## 操作顺序

1. 自己登录电脑微信“公寓智锁”，准备本人“我的”“我的房间”页面。
2. AI读取学校全称、本人学号、完整房间号，保存对应页面证据并生成观测记录。
3. 从工具包根目录执行：

```powershell
node onboarding/session-export.cjs "本人输入/observed-session.json" "本人输入/本人宿舍.dormrequest" --self
pwsh -NoProfile -File self-service/build-self.ps1 -Request "本人输入/本人宿舍.dormrequest" -EvidenceDir "本人输入" -WorkDir "本机新的自助目录"
```

JDK或SDK未自动找到，第二条命令增加 `-JdkPath "实际JDK或jbr目录" -SdkPath "实际SDK目录"`。模板构建脚本检测环境变量、常见Android Studio位置及用户SDK位置，选已有API35以上平台和稳定Build Tools；不自动下载或安装工具。

4. 取自助目录 `dist/宿舍快开-自助版.apk` 和 `dist/本人宿舍授权.dormkey`，放到手机。安装应用名称带“自助版”的APK，选文件即绑定；原公共版不能使用这一份自签文件。应用使用由本机公钥指纹派生的独立包名，可和原版并存。
5. 本人到门旁手动验证。新文件、编译成功或校验通过，不等于实体门已打开。

## 需要哪些工具

Node.js、PowerShell7、带javac的JDK、Android SDK Platform API35或以上与稳定SDK Build Tools。Android Studio的内置jbr可用。无npm依赖，不用Gradle、模拟器、NDK或CMake。缺环境时由AI先说明安装盘符、磁盘占用、常驻/自启及影响，让使用者决定；本包不自行安装、不改系统策略。

Android工具安装入口：[Android Studio及系统要求](https://developer.android.com/studio/install)、[SDK工具管理说明](https://developer.android.com/tools/sdkmanager)。首次下载开发环境可能明显大于本工具包，应按官方要求和实际组件占用预留空间。构建结束后Java等构建进程退出，不需要常驻签名服务器。

## 修复失败、换宿舍与更新

- 构建中断：保留已创建的自助目录，修复缺失环境后从工具包根目录执行 `pwsh -NoProfile -File self-service/build-self.ps1 -WorkDir "原自助目录" -Resume`，按需补JDK/SDK路径。它会保留原密钥继续构建并整理输出，不能因为失败就删除密钥重做。
- 换宿舍：从自己当前官方页面重新采集，生成新的 `.dormrequest`。在原自助目录执行 `node tools/issue-dormkey.cjs "新申请.dormrequest" "对应证据目录" "dist/新宿舍.dormkey"`。用原密钥自动签发，无需发布者；在同一个自助版内导入。
- 换手机：安装保留的同一自助版APK，再导入自己保留的文件。
- 更新程序：保留 `private/local-migration-signing.pem`、`android/signing/local-test.keystore`、当前包名和公钥。新版本必须继续匹配这对签名配置。重新生成密钥会形成另一独立应用，不能当作原安装的直接覆盖更新。

## 验证边界

独立包名不会改变学校的人员/房间权限；程序保留签名检查和现有后台权限查询。此工具只处理实际本人会话观测，不提供远程身份认证，不允许填写他人学号或房号取得权限。共享的是干净源码模板，发布者的私钥和本人资料不会复制到他人的工作目录。

2026-11-07已在本机生成独立密钥和自助APK，完成构建签名及离线生产验签回归；基于本人已观测会话的申请，重新只读查询现有权限。当前未将这次自助APK安装到手机，也未验证另一名使用者的首次导入和实体门开锁。不要把此前公共/个人版的手动开门反馈直接等同于本次独立版本的实门验证。
