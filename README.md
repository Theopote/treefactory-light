# Treefactory Light

基于 Fabric 的程序化树木建造模组，当前版本 `0.1.0-beta.1`。支持四种树形、
三个基因滑块、全息预览、JSON 预设库、128×128 离屏缩略图和每玩家五步撤销。

适用环境：Minecraft 26.3、Java 25、Fabric Loader 0.19.5+、Fabric API 0.161.0+26.3。
客户端与服务器都需要安装。使用方法与运行边界见 [Beta 测试说明](docs/BETA_TESTING.md)。

```powershell
.\gradlew.bat --no-daemon --console plain build
```

生成的模组位于 `build/libs/treefactory-light-0.1.0-beta.1.jar`。

## 代码目录约定

设计文档第 6 节的目录结构以现有工程为基础调整：主要代码统一放在
`src/main/java/com/treefactory/light/` 下，资源放在 `src/main/resources/` 下，
不使用独立的 `src/client` 源集。保留现有包名 `com.treefactory.light`。

功能按以下包划分：

```text
src/main/
├── java/com/treefactory/light/
│   ├── TreefactoryLight.java
│   ├── client/                 # 客户端入口、GUI、控件与预览渲染
│   ├── core/algorithm/         # 空间吸引、管道模型与体素化
│   ├── core/config/            # 参数与树种预设
│   ├── core/tree/              # 节点结构与构建任务
│   ├── item/                  # 树木杖
│   ├── network/               # 构建与撤销数据包
│   └── util/                  # 方块批处理等工具
└── resources/                 # Fabric 配置、Mixin 配置与素材
```

客户端代码虽然属于同一源集，仍通过 `fabric.mod.json` 的 `client` 入口初始化；
公共入口和服务端逻辑不得引用客户端专用类。单元测试位于 `src/test/java/`。

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
