# Treefactory Light

基于 Fabric 的程序化树木建造模组，当前版本 `0.1.0-beta.1`。支持多种内置预设、
树干/树冠分组参数、17 种树冠形状（图标网格）、六角色材质调色盘、世界内实时预览
（右键放置、参数变化即更新、点「完成建造」并经服务端确认后永久保留）、JSON 预设库
与 128×128 离屏缩略图。

适用环境：Minecraft **26.3**、Java **25**、Fabric Loader **0.19.5+**、Fabric API **0.161.0+26.3**。  
客户端与服务器都需要安装。操作说明与运行边界见 [Beta 测试说明](docs/BETA_TESTING.md)。

## 构建

需要 **JDK 25**（Fabric Loom 1.18.2 要求）。若全局 `%USERPROFILE%\.gradle\gradle.properties`
里写了旧版 `org.gradle.java.home`（例如 Java 22），请改掉或在项目 `gradle.properties`
中覆盖，或在命令行指定：

```powershell
.\gradlew.bat --no-daemon --console plain build "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
```

## 代码目录约定

主要代码在 `src/main/java/com/treefactory/light/`，资源在 `src/main/resources/`，
不使用独立的 `src/client` 源集。

```text
src/main/
├── java/com/treefactory/light/
│   ├── TreefactoryLight.java
│   ├── client/                 # 客户端入口、GUI、控件与缩略图渲染
│   ├── core/algorithm/         # 空间吸引、管道模型、形态学与体素化
│   ├── core/config/            # 参数、内置预设与 JSON 预设库
│   ├── core/tree/              # 节点、修订、撤销规划与放置校验
│   ├── item/                   # 树木杖
│   ├── network/                # 预览 / 完成 / 撤销 / 提交结果包
│   └── util/                   # 世界批处理、持久化队列等
└── resources/                  # Fabric 配置、Mixin 配置与素材
```

客户端代码与公共源集共用，通过 `fabric.mod.json` 的 `client` 入口初始化；  
公共入口与服务端逻辑不得引用客户端专用类。单元测试位于 `src/test/java/`。
