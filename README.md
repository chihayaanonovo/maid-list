# 车万女仆附属：女仆名单 · Maid List

> 版本 **v0.1**（首个公开版本）

车万女仆（Touhou Little Maid）的附属模组。给你一件工具，右键打开一份 **简洁列表风格** 的女仆名册，
一行一位女仆，集中查看状态并直接操作。

Bilibili首发 发布地址：

https://www.bilibili.com/video/BV1xuWC6bEiw/?vd_source=d3deb14eadb422019a4acf2ecd56700a


---

## 环境要求

| 项目 | 要求 |
|---|---|
| Minecraft | `1.21.1` |
| 加载器 | NeoForge `21.1.x`（构建于 `21.1.265`） |
| 必需依赖 | Touhou Little Maid `1.5.x` |
| 可选联动 | 狐梦伴岁 / foxdream `0.9.x`（未安装时相关按钮与成长标签自动隐藏） |

## 安装

1. 把 `maid_list-0.1.jar` 放进 `mods/` 目录
2. 确保同目录有车万女仆本体
3. 启动游戏

## 获取物品

- **合成**：纸围一圈 + 中间一本书 + 正下方一个金锭（3×3 有序合成）
- **创造模式**：物品栏「女仆名单」页签
- **命令**：`/give @s fairymoon3_maid_list:maid_panel`

---

## 功能

### 名册

- 一行一位女仆：头像（直接渲染女仆本体）、名字、状态、工作模式、距离、血条
- 状态：`睡觉中 / 战斗中 / 待机中 / 驻守 / 自由 / 未加载`
- 展开详情（左键）：生命、好感、坐标、维度、背包、模型名
- 搜索：名字 / 工作模式 / 模型
- 排序：默认按「获得女仆的先后」；**鼠标中键按住可拖动重排**，顺序自动保存到本地

### 每行的三个按钮

| 按钮 | 图标 | 作用 |
|---|---|---|
| 女仆背包 | 小背包 | 打开女仆自己的界面（等同右键女仆） |
| 去她身边 | 末影之眼 | 把**玩家**传送到女仆身边（音效：末影珍珠） |
| 召唤到身边 | 仆人铃 | 把**女仆**传送到玩家身边（音效：钟声） |

狐梦联动按钮（手册图标）只在女仆已被狐梦手册转化时出现，点击打开狐梦养成面板。

### 标题栏

- 左侧：神龛图标 + 面板名 + 版本号
- 右侧：**小号**图标（一键召唤所有能传送的女仆）+ 女仆数量

三个传送按钮各自 **2 秒独立冷却**，冷却时图标底部有遮罩动画。

### 传送反馈

所有传送操作（含失败）都写进左下角聊天框：

```
[女仆名单] 灵梦 已来到你身边
[女仆名单] 魔理沙 传送失败：不在加载范围内，或不在同一维度
[女仆名单] 已召唤 3 只女仆，1 只无法传送（睡觉中/未加载/在其它维度）
```

### 其它

- 面板内使用独立字体（站酷快乐体），不影响游戏其它界面
- 成长阶段标签（装狐梦时）：幼年期粉底 / 青年期天蓝 / 成年期亮金
- 睡觉中的女仆不能被召唤，也不能打开背包（会给提示）
- 未加载区块的女仆仍会列出，显示上次记录的数据与「多久之前」

---

## 配置

文件：`config/fairymoon3_maid_list-client.toml`

```toml
[tlm]
    # 远程管理总开关：容器 / 狐梦 HUD / HUD 动作是否无视距离限制
    remote_maid_container = true

[foxdream_ui]
    # 狐梦 HUD 的动作包是否忽略视线要求
    ignore_line_of_sight = true
```

> 默认开启，因为这是本模组的设计目的。想要原版体验把 `remote_maid_container` 关掉即可。

---

## 本模组做了什么"跨界"的事（请留意）

为了让面板能远程操作，本模组用 Mixin 接管了**其它两个模组**的距离校验：

| 目标 | 原本的限制 |
|---|---|
| 车万女仆 `AbstractMaidContainer#stillValid` | 4 格内才能保持容器界面 |
| 车万女仆 `OpenMaidGuiPackage#stillValid` | 5 格内才能请求打开界面 |
| 狐梦 `CareScreen#tick` | 8 格外自动关闭养成面板 |
| 狐梦 `CareNetwork` 动作包 | 8 格 + 视线，否则静默丢弃 |
| 狐梦 `CareOrderedSleep#begin` | 8 格 + 视线 |

全部可用配置关闭。若与你其它模组冲突，优先检查这几处。

---

## 已知限制

1. 未加载区块的女仆只能显示**上次记录**的数据（打开面板时会自动收集并缓存）
2. 「打开背包」需要女仆在客户端可见（超出实体追踪范围会失败并提示）
3. 跨维度/未加载的传送会临时强加载目标区块（找到实体后立即释放）
4. 只在服务端做归属校验：别人的女仆无法被操作

---

## 构建

```powershell
$env:JAVA_HOME      = "E:\dev\jdk-21"
$env:GRADLE_USER_HOME = "E:\dev\gradle-home"
cd "E:\moddev\车万女仆附属·女仆名单"
gradle --offline --no-daemon build
```

产物：`build/libs/maid_list-0.1.jar`

> 本工程用 ModDevGradle（`net.neoforged:moddev-gradle`）构建。
> `libs/` 下的两个 jar 只是编译期引用（TLM 与狐梦），不会被打包；
> 从仓库克隆后需要自行把对应版本的 jar 放进 `libs/`：
>
> - `libs/touhoulittlemaid-1.5.3-neoforge+mc1.21.1.jar`
> - `libs/foxdream-0.9.0-r234.jar`
>
> 没有狐梦的 jar 时可以删掉 `build.gradle` 里对应的 `compileOnly` 与
> `FoxdreamCompat`（或直接留空目录，编译会报找不到符号）。

## 目录结构

```
src/main/java/com/fairymoon3/maidlist/
├── MaidList.java            @Mod 主类
├── config/PanelConfig.java  客户端配置
├── registry/ModItems.java   物品与创造页签
├── item/ItemMaidPanel.java  工具本体
├── network/                 数据包（名册 / 传送 / 狐梦）
├── server/                  收集器、传送、快照缓存、狐梦兼容
├── client/                  界面、字体、排序存储、启动自检
└── mixin/                   五处距离校验接管
```

## 许可证与致谢

- 本模组代码：**MIT**（见 `LICENSE-MIT.txt`）
- 内置字体：**站酷快乐体**，SIL Open Font License 1.1（见 `LICENSE-ZCOOL-KuaiLe-OFL.txt`）
- 参考与感谢：车万女仆（Touhou Little Maid）、狐梦伴岁（foxdream）

---

开发过程记录见 [docs/DEVLOG.md](docs/DEVLOG.md)，版本变更见 [CHANGELOG.md](CHANGELOG.md)。
