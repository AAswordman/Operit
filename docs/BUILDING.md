# **Android 项目 Operit 编译指南（Linux/Ubuntu）**

本指南详细介绍了在 Linux 环境下（推荐 Ubuntu/Debian）编译 Android 项目 **Operit** 所需的全部环境配置和步骤。

## **关于 Operit**

**Operit AI** 是移动端首个功能完备的 AI 智能助手应用，它**完全独立运行**于您的 Android 设备上，拥有强大的**工具调用能力**。本项目旨在为开发者提供一个可深度定制和扩展的 AI 助手框架。

在开始编译之前，请确保您已了解本项目的功能和目标。更多信息请参考项目主页的 [README.md](../README.md)。

## **目录**

1. 第一步：安装系统基础依赖
2. 第二步：安装 Android 命令行工具
3. 第三步：配置环境变量
4. 第四步：安装 Android SDK 和 NDK
5. 附：性能优化 - 配置编译资源
6. 第五步：克隆并编译项目
7. 常见问题排查

## **1. 第一步：安装系统基础依赖**

首先，我们需要更新包管理器并安装编译所需的关键基础软件：**Git**、**JDK 21**、**Node.js**、**npm** 和 **Python 3**。  
```bash 
# 更新软件包列表  
sudo apt update

# 安装必要的工具、JDK 21、Node.js、npm 和 Python 3
sudo apt install -y git wget unzip openjdk-21-jdk nodejs npm python3

# 安装 pnpm（sync_example_packages.py 预构建 examples 时会用到）
sudo npm install -g pnpm

# 安装完成后，请验证 Java 版本是否正确
java -version  
# 预期输出应包含 "OpenJDK Runtime Environment (build 21..." 或类似信息

# 建议同时确认 Node.js、npm、pnpm 和 Python 3 可用
node -v
npm -v
pnpm -v
python3 --version
``` 
**注意：** 项目官方要求 **JDK 21**。为确保最大兼容性，强烈建议优先安装和使用 JDK 21。

**补充说明：** 项目中的 `web-chat` 使用 React + Vite 构建；`sync_example_packages.py` 会预构建 `examples/` 下的脚本包并打包 `.toolpkg`。因此除了 Android 环境外，还需要准备好 Node.js、npm、pnpm 和 Python 3。如果后续执行前端构建时提示 Node.js 版本过低，请升级到较新的 Node.js LTS 版本后再继续。

## **2. 第二步：安装 Android 命令行工具**

为了管理 SDK，我们将使用更轻量的 Android 命令行工具（Command Line Tools），而非庞大的 Android Studio。

1. **创建 Android SDK 目录:**  
```bash
mkdir -p ~/Android/cmdline-tools
```
2. 下载命令行工具:  
访问 Android Developers 官网，复制最新的 Linux 版本链接。  
**警告：** 下方的链接仅为示例，请务必检查并替换为官方提供的最新链接！  
```bash
# 示例链接，请务必检查并替换为最新版本  
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O ~/cmdline-tools.zip
```

3. 解压并配置目录结构:  
命令行工具要求其文件位于一个名为 latest 的子目录中，否则 sdkmanager 可能无法识别。  
```bash
# 解压到目标目录  
unzip ~/cmdline-tools.zip -d ~/Android/cmdline-tools

# 将解压后的 cmdline-tools 移动到 latest 子目录  
mv ~/Android/cmdline-tools/cmdline-tools ~/Android/cmdline-tools/latest

# 清理下载的压缩包  
rm ~/cmdline-tools.zip
```
最终的工具路径应为 ~/Android/cmdline-tools/latest/bin。

## **3. 第三步：配置环境变量**

配置环境变量以便系统能找到 **Java** 和 **Android SDK** 的相关命令，如 java、git 和 sdkmanager。

1. **编辑配置文件：**  
```bash
nano ~/.bashrc
```

2. **在文件末尾添加以下内容：**  
```bash
# =============== Java JDK 21 配置 ===============  
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64  
export PATH=$JAVA_HOME/bin:$PATH

# =============== Android SDK 配置 ===============  
export ANDROID_HOME=$HOME/Android  
# 将 latest/bin 添加到 PATH  
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH  
# 将 platform-tools (ADB/Fastboot) 添加到 PATH  
export PATH=$ANDROID_HOME/platform-tools:$PATH
```

3. **使配置生效:**  
```bash
source ~/.bashrc
```

## **4. 第四步：安装 Android SDK 和 NDK**

使用刚才配置好的 sdkmanager 命令来安装项目所需的 SDK 平台、构建工具和特定版本的 NDK。

1. 接受所有 SDK 许可 (关键步骤！):  
此步骤是必须的，否则 Gradle 构建会因许可问题而失败。  
```bash
yes | sdkmanager --licenses
```

2. 安装平台工具、SDK 平台和构建工具:  
Operit 项目依赖于 android-34 平台和 34.0.0 构建工具。  
```bash
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```
3. 安装项目指定的 NDK 版本:  
本项目要求使用 NDK 25.1.8937393。  
```bash
sdkmanager "ndk;25.1.8937393"
```

## **附：性能优化 - 配置编译资源**

对于配置较高的机器（如 16GB 内存或以上），可以通过调整 Gradle 配置来显著加快编译速度。  
在项目根目录下的 **gradle.properties** 文件中，您可以调整以下参数：  

```properties
# 设置 Gradle 使用的 JVM 最大内存，例如 8GB  
org.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=1g -XX:+HeapDumpOnOutOfMemoryError

# 开启并行编译  
org.gradle.parallel=true

# (可选) 设置并行编译的 worker 数量，通常建议设置为 CPU 核心数  
# org.gradle.workers.max=8
``` 

## **5. 第五步：配置 GitHub OAuth 应用**

为了使应用的 GitHub 相关功能（如登录、MCP 包管理）能正常工作，你需要注册自己的 GitHub OAuth Application 并配置 Client ID。

1. **创建 GitHub OAuth App:**  
   - 访问你的 GitHub 开发者设置页面：[**GitHub Developer Settings**](https://github.com/settings/developers)
   - 点击 **"New OAuth App"**。
   - 填写以下信息：
     - **Application name**: `Operit Dev` (或任何你喜欢的名字)
     - **Homepage URL**: `https://github.com/<你的 GitHub 用户名>/Operit` (使用你 Fork 后的仓库地址)
     - **Authorization callback URL**: `operit://github-oauth-callback` (**必须完全匹配！**)

2. **获取 Client ID:**  
   创建成功后，页面会显示生成的 **Client ID**。复制这个 ID。

3. **配置项目:**  
   - 在项目根目录，找到 `local.properties.example` 文件。
   - 复制该文件并重命名为 `local.properties`。
   - 打开 `local.properties` 文件，将 `"YOUR_OWN_GITHUB_CLIENT_ID_HERE"` 替换为你刚刚复制的 Client ID。

   ```properties
   # 示例:
   GITHUB_CLIENT_ID=iv1.1234567890abcdef
   ```
   **注意：** `local.properties` 文件已被 Git 忽略，因此你的个人 ID 不会被提交到仓库中，确保了安全。

## **6. 第六步：克隆并编译项目**

环境准备就绪，现在开始编译项目。

1. 克隆项目仓库并进入目录:  
请根据需要选择以下两种克隆方式（项目包含 Git 子模块）：

**推荐：先 Fork 后克隆你的仓库**  
在 GitHub 打开上游仓库并点击 Fork： [AAswordman/Operit](https://github.com/AAswordman/Operit)  
克隆你的 Fork（注意使用 --recurse-submodules）：
```bash
git clone --recurse-submodules https://github.com/<你的 GitHub 用户名>/Operit.git
cd Operit
```  
（可选）添加上游仓库以便同步更新：  
```bash
git remote add upstream https://github.com/AAswordman/Operit.git
```  

**备选：不 Fork，直接克隆上游仓库（只读）**  
```bash
git clone --recurse-submodules https://github.com/AAswordman/Operit.git
cd Operit
```  

如果你已克隆但忘记带 --recurse-submodules，可在仓库目录中执行：  
```bash
git submodule update --init --recursive
```  
如果你只想单独初始化 FBX 运行时依赖，也可以执行：  
```bash
git submodule update --init fbx/third_party/ufbx
```  
2. **下载并放置依赖库 (关键步骤！):**  
`README.md` 中提到，项目依赖一些需要手动下载的库。请从 [这个 Google Drive 链接](https://drive.google.com/drive/folders/1g-Q_i7cf6Ua4KX9ZM6V282EEZvTVVfF7?usp=sharing) 下载所有文件，并将它们解压或放置到项目根目录下对应的 `libs` 或有 `.keep` 文件的文件夹中。  **警告：** 如果跳过此步骤，编译将因缺少依赖而失败。当前需要下载并解压这四个压缩包：`models.zip`、`subpack.zip`、`jniLibs.zip`、`libs.zip`。  
```bash
./app/src/main/assets/models/.keep  
./app/src/main/assets/subpack/.keep  
./app/src/main/jniLibs/.keep
./app/libs
```

3. **切换到你的工作分支 (如果需要):**
```bash
git checkout docs/add-building-guide
# 将上面的示例分支名替换为你自己创建的分支名
```

4. **安装项目根目录的脚本依赖:**
```bash
npm install
```
这一步会安装 `sync_example_packages.py` 预构建示例脚本包时需要用到的 `typescript`、`esbuild` 等依赖。

5. **安装 web-chat 的前端依赖:**
```bash
npm --prefix web-chat install
```

6. **先构建 web-chat 并同步到 Android assets (关键步骤！):**
```bash
npm run build:webchat
```
该命令会先执行 `web-chat` 的 React/Vite 构建，再把生成的静态文件同步到 `app/src/main/assets/web-chat`。如果你修改了 `web-chat/src` 下的代码，重新编译 APK 前也需要重新执行一次这一步。

7. **打包 ToolPkg 并同步示例包到应用 assets (关键步骤！):**
```bash
python3 ./sync_example_packages.py
```
该命令会按 `packages_whitelist.txt` 预构建 `examples/` 下的脚本包，并将包含 `manifest.json` 或 `manifest.hjson` 的目录打包成 `.toolpkg`，最终输出到 `app/src/main/assets/packages/`。如果你修改了 `examples/` 下的脚本包代码，重新编译 APK 前也需要重新执行一次这一步。

8. **为 Gradle 包装器添加可执行权限:**
```bash
chmod +x ./gradlew
```

9. 运行 assembleDebug 命令进行编译:  
首次编译会下载大量依赖，请耐心等待。  
```bash
./gradlew assembleDebug
```

或者运行 assembleDebugClone 编译共存版:
```
./gradlew assembleDebugClone
```

10. 查找 APK 文件:  
编译成功后，生成的 APK 文件位于项目目录下的以下路径：  
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/clone/app-clone.apk

## **7. 常见问题排查**

| 错误信息 | 解决方案 |
| :---- | :---- |
| sdkmanager: command not found | 环境变量未正确设置或生效。请检查 **~/.bashrc** 文件内容，并执行 source ~/.bashrc。 |
| Could not determine Java version... | **JAVA_HOME** 环境变量不正确，或安装了错误的 JDK 版本。请确保已安装 **JDK 21** 并指向正确的路径。 |
| NDK not found. | 确保已在 **第四步** 中使用 sdkmanager 安装了项目所需的 **ndk;25.1.8937393** 版本。 |
| pnpm: command not found | 尚未安装 `pnpm`。请先执行 `sudo npm install -g pnpm`，再重新运行 `python3 ./sync_example_packages.py`。 |
| Missing web-chat/dist. Run `npm --prefix web-chat run build` first. | 尚未构建 `web-chat` 或构建失败。请先执行 `npm --prefix web-chat install`，再在项目根目录执行 `npm run build:webchat`。 |
| ERROR: prebuild step failed | `sync_example_packages.py` 在预构建 `examples/` 时失败。请先确认已在项目根目录执行 `npm install`，并检查 `pnpm -v`、`python3 --version` 是否可用。 |
| You have not accepted the license agreements... | 你跳过了或未成功执行接受许可的步骤。请返回 **第四步** 执行 `yes |

