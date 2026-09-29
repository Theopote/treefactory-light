# Treefactory Light

## 代码目录约定

设计文档第 6 节的目录结构以现有工程为基础调整：主要代码统一放在
`src/main/java/com/treefactory/light/` 下，资源放在 `src/main/resources/` 下，
不使用独立的 `src/client` 源集。保留现有包名 `com.treefactory.light`。

后续功能按以下包划分：

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
公共入口和服务端逻辑不得引用客户端专用类。以上功能包随实现添加。

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
