# 车万女仆附属：女仆名单 · Maid List

> 版本 **v0.1.2**

车万女仆（Touhou Little Maid）的附属模组。给你一件工具，右键打开一份 **简洁列表风格** 的女仆名册，
一行一位女仆，集中查看状态并直接操作。

支持**女仆收纳空间**（可装无限只，带完整 NBT 备份与灾难恢复）与**仇恨名单管理**
（与本体攻击面板双向同步）。

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

1. 把 `maid_list-0.1.2.jar` 放进 `mods/` 目录
2. 确保同目录有车万女仆本体
3. 启动游戏

## 获取物品

- **合成**：纸围一圈 + 中间一本书 + 正下方一个金锭（3×3 有序合成）
- **创造模式**：物品栏「女仆名单」页签
- **命令**：`/give @s fairymoon3_maid_list:maid_panel`

---

## 功能

### 名册

- 一行一位女仆：头像（实时渲染女仆本体）、名字、状态、工作模式、距离、血条
- 状态：`睡觉中 / 战斗中 / 待机中 / 驻守 / 自由 / 未加载`
- 左键展开详情：生命、好感、坐标、维度、背包、模型名；狐梦女仆额外显示成长简报
- 搜索：名字 / 工作模式 / 模型
- 排序：默认按「获得女仆的先后」；**鼠标中键按住可拖动重排**，顺序自动保存

### 每行的按钮

| 按钮 | 图标 | 作用 |
|---|---|---|
| 收纳 | 神龛 | 把这只女仆收进**收纳空间** |
| 狐梦手册 | 手册 | 打开狐梦养成面板（仅对已转化女仆显示） |
| 女仆背包 | 小背包 | 打开女仆自己的界面（等同右键女仆） |
| 去她身边 | 末影之眼 | 把**玩家**传送到女仆身边（音效：末影珍珠） |
| 召唤到身边 | 仆人铃 | 把**女仆**传送到玩家身边（音效：钟声） |

行首头像左上角还有一个**语音小喇叭**：女仆说话时显示跳动音量条，点一下可**静音 / 取消静音**该女仆。

### 标题栏

- 左侧：神龛图标（**左键猫叫 + 星星粒子，右键打开收纳空间**）+ 面板名 + 版本号
- **点版本号可切换字体**：模组内置字体（站酷快乐体） ↔ 游戏默认字体（跟随材质包）
- 右侧：主题按钮 + 小号图标（一键召唤所有能传送的女仆）+ 女仆数量 + **关闭按钮 X**

### 界面主题

主题按钮三态轮换：**日间（太阳） → 夜间（月亮） → 半透明（玻璃）**，默认**半透明**。

| 主题 | 面板 | 特点 |
|---|---|---|
| 日间 | 米白 `#FBF6EC` | 柔和暖色，列表比外框略深 |
| 夜间 | 深色 `#1A1D24` | 经典深色 |
| 半透明 | 面板 40% / 列表 22% | 能看见后面的世界，外围纯白描边 |

### 女仆收纳空间

- **打开**：左上角神龛**右键**
- **收纳**：点名册里某行的「收纳」按钮；女仆实体会被收走，名册中随之隐藏
- **数据完整性**：沿用本体魂符格式（`EntityMaid.saveWithoutId` 完整实体 NBT），
  血量 / 好感 / 经验 / 背包 / 模型 / 狐梦成长数据全部保留
- **与本体魂符的差别**：一个收纳空间可以装**任意多只**
- **收纳界面**：实体实时渲染小头像、名字搜索、逐只「释放」、底部「全部释放」；
  关闭后回到女仆名册
- **灾难恢复**：客户端把每只收纳女仆的完整 NBT 备份到
  `config/maid_list_storage_backup.json`；服务端收纳数据丢失时下次上线自动回传恢复。
  服务端用**本存档独有的密钥签名**校验来源 —— 来自其它存档 / 被篡改的数据一律拒绝，
  无法在多人服务器里「导入」别的存档的女仆

### 仇恨名单管理

- **入口**：在名册里**右键**女仆 → 菜单「为女仆变更仇恨名单」
- **实体列表**：自动枚举整合包内**所有已注册实体**（含其它模组添加的），
  同时显示中文译名与实体 ID，支持按名称或 ID 搜索
- **三态切换**：友好（绿）/ 中立（金）/ 敌对（红）；再点当前状态即从名单移除
- **最近修改置顶**：刚改过的实体自动排到列表第一位，取消修改时保持原位
- **批量操作**：重置该女仆 / 复制 / 粘贴 / 同步至全体女仆 / 重置所有女仆
- **与本体双向同步**：直接读写车万女仆的 `InitTaskData.ATTACK_LIST`，
  与本体「任务设置 → 攻击面板」是同一份数据 —— 任一侧修改，另一侧立刻可见

### 传送反馈

所有传送操作（含失败）都写进左下角聊天框：

```
[女仆名单] 灵梦 已来到你身边
[女仆名单] 魔理沙 传送失败：不在加载范围内，或不在同一维度
[女仆名单] 已召唤 3 只女仆，1 只无法传送（睡觉中/未加载/在其它维度）
```

### 其它

- 面板使用独立字体（站酷快乐体），不影响游戏其它界面；可切换为游戏默认字体
- 成长阶段标签（装狐梦时）：幼年期粉底 / 青年期天蓝 / 成年期亮金；后面跟 `[性格:xx]`
- 睡觉中的女仆不能被召唤，也不能打开背包（会给聊天框提示）
- 未加载区块的女仆仍会列出，显示上次记录的数据与「多久之前」

---

## 配置

### 客户端 `config/fairymoon3_maid_list-client.toml`

```toml
[tlm]
    # 远程管理总开关：容器 / 狐梦 HUD / HUD 动作是否无视距离限制
    remote_maid_container = true

[foxdream_ui]
    # 狐梦 HUD 的动作包是否忽略视线要求
    ignore_line_of_sight = true

[ui]
    # 界面主题：light（日间）/ dark（夜间）/ glass（半透明）
    theme = "GLASS"
    # 是否使用游戏默认字体（跟随材质包）；false = 使用内置字体
    use_vanilla_font = false
```

### 服务端 `config/fairymoon3_maid_list-server.toml`

专用服务器**不会加载客户端配置**，所以服主要用这份来关闭远程管理：

```toml
[tlm]
    remote_maid_container = true

[foxdream_ui]
    ignore_line_of_sight = true
```

> 读取优先级：**客户端配置 → 服务端配置 → 内置默认值**。

---

## 数据文件

| 文件 | 位置 | 用途 |
|---|---|---|
| `maid_list_order.json` | `config/` | 玩家拖动排序后的名册顺序 |
| `maid_list_muted.json` | `config/` | 被静音的女仆 UUID |
| `maid_list_storage_backup.json` | `config/` | 收纳女仆的完整 NBT 备份（用于灾难恢复） |
| `maid_list_attack_backup.json` | `config/` | 仇恨名单留档（按女仆 UUID 归档） |
| `maid_list_storage.dat` | 存档 `data/` | 收纳空间本体数据 |
| `maid_list_world_key.dat` | 存档 `data/` | 存档密钥（用于收纳数据来源签名） |
| `maid_list_cache.dat` | 存档 `data/` | 女仆信息快照（离线女仆的最近数据） |

---

## 本模组做了什么"跨界"的事（请留意）

### 距离校验（可配置关闭）

| 目标 | 原本的限制 |
|---|---|
| 车万女仆 `AbstractMaidContainer#stillValid` | 4 格内才能保持容器界面 |
| 车万女仆 `OpenMaidGuiPackage#stillValid` | 5 格内才能请求打开界面 |
| 狐梦 `CareScreen#tick` | 8 格外自动关闭养成面板 |
| 狐梦 `CareNetwork` 动作包 | 8 格 + 视线，否则静默丢弃 |
| 狐梦 `CareOrderedSleep#begin` | 8 格 + 视线 |

### 语音检测（只读，不改变行为）

| 目标 | 用途 |
|---|---|
| `SoundManager#play` | 识别哪只女仆正在播语音（用于头像上的小喇叭） |
| `PlayMaidSoundPackage` / `PlayMaidSoundAtPosPackage` | 补给上面这条通道，覆盖狐梦语音 |

静音功能会在播放前拦截对应女仆的语音（仅客户端本地生效）。

若与你其它模组冲突，优先检查这几处。

---

## 已知限制

1. 未加载区块的女仆只能显示**上次记录**的数据（打开面板时会自动收集并缓存）
2. 「打开背包」需要女仆在客户端可见（超出实体追踪范围会失败并提示）
3. 跨维度/未加载的传送会临时强加载目标区块（找到实体后立即释放）
4. 只在服务端做归属校验：别人的女仆无法被操作
5. 收纳恢复依赖存档密钥文件；整个 `data/` 目录丢失时无法校验来源
6. 仇恨名单的批量同步只作用于**已加载**的女仆
7. 个别模型的小头像可能需要额外客户端状态，可能显示为空

---

## 构建

```powershell
$env:JAVA_HOME      = "E:\dev\jdk-21"
$env:GRADLE_USER_HOME = "E:\dev\gradle-home"
cd "E:\moddev\车万女仆附属·女仆名单"
gradle --offline --no-daemon build
```

产物：`build/libs/maid_list-0.1.2.jar`

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
├── MaidList.java                 @Mod 主类
├── config/                       客户端 / 服务端配置、主题枚举
├── registry/ModItems.java        物品与创造页签
├── item/ItemMaidPanel.java       工具本体
├── network/                      数据包（名册 / 传送 / 狐梦 / 收纳 / 仇恨名单）
├── server/                       收集器、传送、快照缓存、收纳与恢复、仇恨名单、狐梦兼容
├── client/                       名册界面、收纳界面、仇恨名单界面、语音追踪、静音、排序/备份
└── mixin/                        距离校验接管 + 语音检测
```

## 许可证与致谢

- 本模组代码：**MIT**（见 `LICENSE-MIT.txt`）
- 内置字体：**站酷快乐体**，SIL Open Font License 1.1（见 `LICENSE-ZCOOL-KuaiLe-OFL.txt`）
- 参考与感谢：车万女仆（Touhou Little Maid）、狐梦伴岁（foxdream）
- 特别感谢 **[@REDSTONE440](https://github.com/REDSTONE440)** 提交的专用服务器崩溃报告（[Issue #1](https://github.com/chihayaanonovo/touhoulittlemaid_maid_list/issues/1)）

---

开发过程记录见 [docs/DEVLOG.md](docs/DEVLOG.md)，版本变更见 [CHANGELOG.md](CHANGELOG.md)，
v0.1.2 功能汇总见 [docs/REPORT-v0.1.2.md](docs/REPORT-v0.1.2.md)。
