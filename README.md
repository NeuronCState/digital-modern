# Digital 中文现代化 Fork

这是 [hneemann/Digital](https://github.com/hneemann/Digital) 的个人 fork，
用于持续维护 Digital 数字逻辑设计与电路仿真器，并在原项目基础上加入现代化
macOS 用户界面、独立应用打包以及面向 AI 的 MCP 工具支持。

> 本项目是非官方 fork。Digital 原作者和原项目的版权、许可证及技术成果归原项目所有。

## 项目简介

Digital 是一款面向教学和工程实践的数字逻辑设计与电路仿真软件，可以用来搭建、
运行和测试组合逻辑电路、时序逻辑电路、有限状态机以及处理器示例。

原项目主页：<https://github.com/hneemann/Digital>

## 本 Fork 的主要改进

- 面向现代 macOS 的界面风格和深色模式适配。
- 更现代的工具栏、侧边栏、画布和交互反馈。
- 侧边栏整行点击、展开和收起动画。
- 电路画布缩放、拖拽、多选和更清晰的深色主题配色。
- 原生 macOS 标题栏和 Vibrancy 效果支持。
- 独立的 `Digital.app`、运行时和 DMG 打包流程。
- 保留 Digital 原有的电路仿真、测试、SVG 导出、FSM、74xx 器件库和示例工程。
- 独立的 Digital MCP Server，可让 AI 根据文字或图片理解结果自动生成电路、运行测试、
  导出预览并打开 Digital。

## 下载和运行 macOS 版本

当前构建产物位于：

- [Digital.app](modern/dist/Digital.app)
- [Digital-1.31.0.dmg](modern/dist/Digital-1.31.0.dmg)
- [现代化构建脚本](modern/build.sh)

在 macOS 上可以直接双击 `modern/dist/Digital.app`，或在终端执行：

```bash
open modern/dist/Digital.app
```

如果只需要运行 JAR：

```bash
java -jar modern/dist/Digital.jar
```

## 从源码构建

本 Fork 的现代化构建使用 JDK 21。构建脚本会负责检查 JDK、Maven、macOS 原生组件和
打包资源：

```bash
./modern/build.sh info
./modern/build.sh jar
./modern/build.sh app
./modern/build.sh dmg
```

完整构建可以使用：

```bash
./modern/build.sh all
```

构建结果默认位于 `modern/dist/`。在发布或验证时，应以该目录中的最新产物为准，
而不是只检查源代码或中间构建目录。

## 测试

运行现代化界面和打包相关的快速检查：

```bash
./modern/test.sh
```

Digital 原有的 Java 测试位于 `source/src/test/`。如果已经准备好 Maven 和 JDK 21，
可以运行：

```bash
cd source
mvn test
```

## AI / MCP 支持

独立 MCP Server 位于单独仓库：

[NeuronCState/digital-mcp-server](https://github.com/NeuronCState/digital-mcp-server)

它支持以下工作流：

```text
文字需求或电路图片
        ↓
AI 识别元件、标签和连线
        ↓
MCP 生成 .dig 电路文件
        ↓
Digital 无头 CLI 运行测试
        ↓
导出 SVG / 仿真图片 / 真值表
        ↓
在 Digital.app 中打开
```

MCP Server 提供的核心能力包括：

- 根据结构化设计生成 `.dig` 文件。
- 检查元件、属性、位置和连线。
- 运行 Digital 内置测试用例。
- 导出 SVG 和仿真预览。
- 生成测试结果和真值表。
- 在 macOS 上加载生成的电路。

MCP Server 的配置示例：

```json
{
  "mcpServers": {
    "digital": {
      "command": "python3",
      "args": [
        "/绝对路径/digital-mcp-server/digital_mcp_server.py"
      ],
      "env": {
        "DIGITAL_JAR": "/绝对路径/Digital/source/target/Digital.jar",
        "DIGITAL_JAVA": "/绝对路径/Digital/.runtime/desktop-runtime/bin/java",
        "DIGITAL_APP": "/绝对路径/Digital/modern/dist/Digital.app"
      }
    }
  }
}
```

图片的视觉理解由支持视觉的 AI 客户端完成，MCP Server 负责确定性地生成、验证和运行
Digital 电路。

## 目录结构

```text
Digital/
├── modern/                 # macOS 现代化界面、资源和打包脚本
│   ├── build.sh
│   ├── test.sh
│   └── dist/               # Digital.app、JAR、DMG 和运行时
├── mcp/                    # MCP 集成镜像和本地开发入口
├── source/                 # Digital Java 源码
├── examples/               # 电路、FSM、处理器和 HDL 示例
├── lib/                    # 74xx、RAM 等器件库
└── docu/                   # 文档和项目资料
```

## 许可证

Digital 原项目使用 GPL-3.0 许可证。本 Fork 的代码和衍生修改继续遵循原项目适用的
许可证要求。详细内容请查看仓库中的许可证文件和上游项目说明。

## 致谢

感谢 [hneemann/Digital](https://github.com/hneemann/Digital) 原作者及所有贡献者。
本项目的现代化改造建立在 Digital 原有的电路模型、仿真器、测试框架和示例资源之上。
