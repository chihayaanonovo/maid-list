# 车万女仆附属：女仆名单 · Maid List

车万女仆（Touhou Little Maid）的附属模组：给玩家一件工具，右键打开一份 **QQ 联系人列表风格** 的女仆名册。

- Minecraft：`1.21.1`
- 加载器：NeoForge `21.1.265`（依赖声明 `[21.1.0,)`）
- 依赖：Touhou Little Maid `1.5.3`（`[1.5.0,)`）
- 项目名：`车万女仆附属·女仆名单`（`settings.gradle` 的 `rootProject.name`）
- 构建产物：`build/libs/maid_list-<version>.jar`
- **注册 id**：`fairymoon3_maid_list`（保持不变，见下方说明）

> 命名说明：产物文件名与显示名已改为 **女仆名单 / Maid List**，但 NeoForge 的 mod id 仍是
> `fairymoon3_maid_list`。因为 mod id 一旦变化，存档里已有的 `fairymoon3_maid_list:maid_panel` 物品会全部失效。
> 所以 `base.archivesName` 与 `mod_id` 解耦（见 `gradle.properties` 的 `archive_name`），
> 只改文件名与显示名。

---

## 一、功能

| 功能 | 说明 |
|---|---|
| 女仆名单物品 | `fairymoon3_maid_list:maid_panel`，右键（对着空气）打开名册；也可用 `/give` 或合成获得 |
| 女仆名册 | 一行一位女仆，含头像（职业图标）、名字、状态、职业、好感等级、血量、距离 |
| 展开详情 | 左键点击某一行展开：生命/饱食/好感点数、坐标与维度、背包与模型 |
| 搜索 | 顶部搜索框，按名字 / 职业 / 背包名过滤 |
| 打开女仆界面 | 每行蓝色按钮，等同直接右键女仆（同一套 `openMaidGui` 入口） |
| 一键传送 | 每行橙色箭头，把女仆召唤到身边（移植仆人铃，无需绑定） |
| 刷新 | 右上角「刷新」按钮，随时向服务端重新拉取 |
| 排序 | 同维度按距离由近到远，其它维度排在后面 |
| 滚动 | 滚轮浏览，右侧有滚动条 |

状态判定优先级：`战斗中 > 休息中 > 驻守 > 自由`。

**合成配方**：纸围一圈、中间一本书、正下方一个金锭（3×3 有序合成）。

---

## 二、构建

本机网络有个特殊情况：**官方 `maven.neoforged.net` 不可达**（需要 Clash 代理，但代理当前没开）。
因此：

- `build.gradle` 用 `buildscript { classpath 'net.neoforged:moddev-gradle:2.0.148' }`
  **绕开 Gradle Plugin Marker**（marker 解析在本机不稳定）。
- 依赖全部来自 `E:\dev\gradle-home` 里已有的缓存（`neoforge:21.1.265` / `neoform` / `moddev-gradle:2.0.148` 都已缓存）。
- 所以采用 **离线构建**。

```powershell
$env:JAVA_HOME      = "E:\dev\jdk-21"
$env:GRADLE_USER_HOME = "E:\dev\gradle-home"
cd "E:\moddev\车万女仆附属·女仆名单"
gradle --offline --no-daemon build
```

> ⚠️ 注意：不要用 `Set-Content -Encoding UTF8` 去改 `build.gradle`，PowerShell 会加上 BOM，
> Gradle 会报 `Unexpected character: '﻿'`。请用 UTF-8 无 BOM 的编辑器。

### 想联网构建时

`E:\dev\gradle-home\gradle.properties` 里配置了 `127.0.0.1:7890` 代理。
代理开着时可以直接联网构建；代理没开时请保持 `--offline`。
`mirror.init.gradle` 是一个可选脚本，用于把 `neoforged.net` 仓库整体重写到 BMCLAPI 镜像
（`https://bmclapi2.bangbang93.com/maven/`），配合 `-I mirror.init.gradle` 使用。

---

## 三、安装

把 `build/libs/fairymoon3_maid_list-1.0.0.jar` 丢进整合包的 `mods/` 目录即可（需要车万女仆本体）。
本 mod **不会**打包车万女仆，只声明依赖。

---

## 四、代码结构

```
com.fairymoon3.maidlist
├── MaidList.java              @Mod 主类
├── registry/ModItems.java       物品 + 创造模式物品栏注册
├── item/ItemMaidPanel.java      工具本体：右键发请求包
├── network/
│   ├── MaidListRequestPayload   客户端 -> 服务端：请求名册
│   ├── MaidListPayload          服务端 -> 客户端：整份名册
│   ├── MaidEntry.java           单条女仆数据 + StreamCodec + 状态枚举
│   └── ModNetwork.java          PayloadRegistrar 注册与两侧处理
├── server/MaidCollector.java    遍历所有维度实体，按主人 UUID 过滤
└── client/
    ├── ClientMaidPanel.java     收到名册 -> 打开/刷新界面
    └── MaidPanelScreen.java     QQ 列表风格 GUI
```

### 数据流

```
右键 -> 客户端发包(MaidListRequestPayload)
     -> 服务端 enqueueWork 收集 EntityMaid -> 发包(MaidListPayload)
     -> 客户端 setScreen(MaidPanelScreen)
```

面板完全基于服务端下发的快照渲染，客户端不会去反查实体，所以女仆走出加载范围也不会出错。

---

## 五、已知限制

1. 已加载的女仆信息最全（血量 / 职业 / 好感 / 背包）；**未加载**的女仆会通过车万女仆的
   `MaidWorldData` 持久索引补上名字、维度和上次区块，显示为「未加载」。
2. **传送**只对「同维度且区块已加载」的女仆生效（与仆人铃 `ItemServantBell` 行为一致）。
   跨维度或未加载时会给出提示，不会静默失败。2. `EntityMaid` 的大量字段由服务端读取后下发；若某个附属 mod 的女仆数据在早期 tick 不完整，
   `MaidCollector` 对每个字段都做了兜底，不会因为一只女仆异常而让整份名册失败。
3. 界面为纯代码绘制（无贴图资源），未做动画过渡。

---

## 六、可继续扩展的方向

- 点击某一行「传送」/「召唤」到玩家身边（需服务端校验权限与距离）
- 名册持久化：登录时缓存上次位置，给未加载的女仆显示「上次位置」
- 右键女仆直接打开该女仆详情页
- 支持排序切换（名字 / 等级 / 职业）

---

## 八、1.1.0 更新

### 1. 头像改为渲染女仆本体的模型

列表头像不再用职业图标，而是直接渲染 `EntityMaid` 本体，走的是原版背包界面的入口
`InventoryScreen#renderEntityInInventory`，所以**女仆换什么模型，头像就是什么模型**
（GeckoLib / Bedrock 模型、YSM 模型都跟随）。

> 为什么没用「模型图标」：车万女仆的模型图标并不是现成贴图，而是
> `CacheIconManager` + `IconCache` 在打开模型选择界面时**实时截图生成**后缓存到
> `CacheIconTexture` 里的（`MaidModelInfo#getCacheIconId` 只是个动态贴图 id）。
> 直接渲染实体既是「跟随模型」，也不依赖那些缓存是否已生成。
> 女仆不在客户端加载范围时，头像自动退回职业图标。

### 2. 移除「饱食度」

`EntityMaid#getHunger()` 读的是同步数据 `DATA_HUNGER`，但全 mod 里**只有
`EntityMaid` 自己**引用 `setHunger`，而且只在 `readAdditionalSaveData` 里读 NBT 的
`Hunger` 标签——也就是说没有任何游戏逻辑会写这个值，喂食也不会改变，永远返回 0。

因此面板不再展示饱食度，详情里改为展示 `生命 / 好感（含等级）`。

### 3. 模型名中文化

模型包 `maid_model.json` 里的 `name` 是**花括号翻译键**，例如
`"{model.touhou_little_maid.hakurei_reimu.name}"`，真正的文本在模型包自带的
`assets/touhou_little_maid/lang/zh_cn.json` 里（MC 会在运行时合并）。

现在通过 `MaidModels.getInstance().getInfo(modelId).getName()` 取原始值，
剥掉花括号后用 `I18n.get(key)` 解析，取不到才退回模型 id。

### 4. 面板内一键传送（无绑定）

移植自仆人铃 `ItemServantBell#teleportMaid`，行为逐条对齐：

```java
maid.setHomeModeEnable(false);                       // 取消驻守
if (maid.isPassenger()) maid.stopRiding();           // 先下坐骑
maid.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 1, true, false));  // 10 秒发光
maid.teleportTo(player.getX() + rand(3) - 1, player.getY(), player.getZ() + rand(3) - 1);
```

绑定步骤被「**实体 UUID + `isOwnedBy(player)` 归属校验**」取代——面板里的每一行本来就
带着 UUID，点右侧箭头即可直接召唤，不需要再拿仆人铃对女仆右键绑定。

成功后自动刷新名册并显示动作栏提示；失败（跨维度 / 未加载）会说明原因。

---

## 九、1.2.0 更新

### 1. 新增「打开女仆界面」按钮

每行多了一个蓝色 2×2 格子按钮，点击走车万女仆自己的入口
`EntityMaid#openMaidGui(Player)`，所以标签页 / 背包 / 装备 / 任务配置和右键女仆完全一致。

注意原版实现在 `isSleeping()` 时直接返回 false（不肯被打扰），面板会给出
“正在睡觉，别吵醒她”的提示。

### 2. 「休息中」改为「坐下」

这条状态本来就来自 `EntityMaid#isMaidInSittingPose()`，语义是「坐下」，
之前的文案叫「休息中」不准确，已改。

### 3. 新增「睡觉中」状态

检测依据是原版 `LivingEntity#isSleeping()`，它等价于 `getSleepingPos().isPresent()`。
而 `EntityMaid` 重写了 `startSleeping(BlockPos)`，第一件事就是调
`TamableAnimal#startSleeping`（写入 `sleepingPos` + 姿态设为 `SLEEPING`），
然后才回满血并累加 `Type.SLEEP` 好感。

所以女仆被 `MaidBedTask` 领到床上躺下的一瞬间，`isSleeping()` 就为 true，
面板立刻显示「睡觉中」。状态优先级排在最前：睡觉 > 战斗 > 坐下 > 驻守 > 自由。

---

## 十一、1.4.0：狐梦伴岁软联动 + 文案调整

### 1. 按钮提示

- 蓝色按钮：`打开女仆背包`
- 女仆睡觉时悬停蓝色按钮：`正在睡觉中的女仆背包打不开哦`（原版 `openMaidGui` 在
  `isSleeping()` 时直接返回 false，这里提前在 tooltip 上告知）

### 2. 狐梦伴岁（foxdream）联动

foxdream 的包名是 `cn.blockforge.generated.winefox1801000525`，用到的都是它自己暴露的
公开 API：

| 用途 | API |
|---|---|
| 判断是否已被手册转化 | `CareData.started(Entity)` |
| 读成长阶段 | `CareData.load(Entity).stage` |
| 打开养成面板（= 手册右键） | `CareNetwork.send(maid, player, true)` |

`CareNetwork.send` 的 `open=true` 分支会先校验「是否已转化」，然后推送 `State` 包，
客户端收到后自己 `setScreen(new CareScreen(...))` —— 所以这就是手册右键的完整链路，
不是伪造 UI。

界面上表现为：

- 已转化的女仆 → 名字后面出现**粉色成长标签**（幼年期 / 青年期 / 成年期）
- 同时出现**粉色手册按钮**，点击等同手册右键
- 未转化 → 两样都不显示
- 面板每 3 秒自动刷新一次，所以女仆被手册转化后按钮会自动长出来
- 客户端没装 foxdream → `ModList.get().isLoaded("foxdream")` 为 false，一律不显示

### 3. 软依赖是怎么保证不崩的

`FoxdreamCompat` 里所有对 foxdream 类的直接引用都放在**被守卫的私有方法**里：

```java
public static int stage(EntityMaid maid) {
    if (!available()) return STAGE_NONE;   // 没装就直接短路
    try { return stage0(maid); }           // 引用 CareData 的方法，永远不会被执行
    catch (Throwable t) { return STAGE_NONE; }
}
```

JVM 的符号解析是懒执行的，短路返回时那几行字节码根本不会跑到；再加一层
`catch (Throwable)` 兜底（`NoClassDefFoundError` 是 Error 不是 Exception，所以必须 catch
Throwable）。

已实测两种场景：

- TLM + foxdream + 面板 → 三个 mod 共存，`Done (1.255s)`
- TLM + 面板（无 foxdream）→ 正常启动，`Done (1.118s)`，无 NoClassDefFound

### 4. 阶段数值的假定

`CareData.stage` 是 int，按 `_child / _youth / _adult` 的模型命名推定为
`0 = 幼年, 1 = 青年, 2 = 成年`，其余值降级显示为「阶段 N」。
如果实际顺序不对，改 `zh_cn.json` 里的三个 `fairymoon3_maid_list.foxdream.stage.*` 文案即可，不用动代码。

---

## 十、1.3.0：未加载区块支持

女仆是普通实体，区块卸载后实体数据只存在于区域文件里，运行时拿不到。
所以「未加载也能用」按能做到的程度分三档：

| 能力 | 未加载时 | 做法 |
|---|---|---|
| 显示全部信息 | ✅ | 自己的持久化快照缓存 |
| 召唤（同维度） | ✅ | 临时强加载区块 → 找到实体 → 传送 |
| 召唤（跨维度） | ✅ | 找到实体后走 `changeDimension` |
| 打开女仆界面 | ❌ | 菜单绑定活实体，区块卸载必崩 |

### 1. 快照缓存 `PanelCache`

一个 `SavedData`（存档内 `data/fairymoon3_maid_list_cache.dat`）。每次收集到**活着**的女仆就顺手存一份
完整快照（血量/职业/好感/模型/背包/姿态），存的是轻量字段，物品用注册名重建。

女仆离线时优先用快照，界面上显示「12min 前」表示数据新鲜度。
没有快照的才退回车万女仆 `MaidWorldData` 里的名字/维度/区块。

### 2. 召唤 `MaidSummoner`

```
当前维度已加载            → 直接传送（仆人铃逻辑）
否则查 MaidWorldData      → 拿到维度和区块坐标
addRegionTicket(FORCED)   → 把目标区块拉起来
每 2 tick 重试找实体       → 最多等 24 tick（区块加载 + 实体装载不是同步的）
找到                      → 立刻撤 ticket → 同维度 teleportTo / 跨维度 changeDimension
超时                      → 撤 ticket，给一句失败提示
```

> ⚠️ 副作用：强加载会让那个区块短暂 tick 几下（红石、机器）。ticket 找到实体后立刻撤销，
> 影响窗口只有几 tick。

### 3. 为什么「未加载时打开女仆界面」做不到

`MaidContainerMenu` 持有 `EntityMaid` 的强引用。区块一旦卸载，实体被移除，
界面还活着就会访问到失效对象。强行保活区块直到关界面也不是不行，但等于让玩家的机器
在后台持续 tick，代价不可控——所以这个按钮在未加载行上只给提示。




---

## 附：踩坑记录

### 1. 面板内容被"虚化"（1.0.1 修复）

现象：打开面板后，只有搜索框和刷新按钮（widget）清晰，面板本体、列表和文字全部糊成一片。

原因：1.21.1 的 `Screen#render` **自己**会先调用 `renderBackground`：

```java
public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    this.renderBackground(g, mouseX, mouseY, partialTick);   // 模糊 + 暗化贴图
    for (Renderable renderable : this.renderables) { ... }
}
```

而 `renderBackground` 内部是 `renderBlurredBackground()`（对整个主渲染目标做全屏模糊）
+ `renderMenuBackground()`（铺一遍暗化贴图）。

原来的代码是「自己画面板 → 调 `super.render()`」，于是 `super.render()` 里的
`renderBackground` 把**已经画好的面板**给模糊掉了，而 widget 在其后绘制所以清晰。

修复：`renderBackground` 覆写为空实现，暗化改为在 `render()` 最前面自己画
（`fill(0,0,width,height,0x77000000)`），绘制顺序变成：暗化 → 面板 → 列表 → widget。

---

## 十二、1.5.0：放宽狐梦面板距离 + 文案微调

### 1. 「超过 7~8 米就打不开」是 foxdream 自己写死的

反编译 `CareScreen#tick()` 可以看到：

```java
Entity e = minecraft.level.getEntity(entityId);
if (e == null || minecraft.player.distanceToSqr(e) > 64.0) {   // 64 = 8²
    this.onClose();
    return;
}
```

所以不是我们面板的问题，是**上游限制 8 格**（`distanceToSqr` 与 `64` 比较即 8 方块）。

### 2. 用 Mixin 把它换成可配置的值

`CareScreenDistanceMixin` 用 `@ModifyConstant` 替换掉 `tick()` 里那个 `64.0`：

```java
@Mixin(targets = "cn.blockforge.generated.winefox1801000525.client.CareScreen")
public class CareScreenDistanceMixin {
    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = 64.0D), remap = false)
    private double maidList$widenCareRange(double original) { return PanelConfig.squaredRange(); }
}
```

配置项在 `config/fairymoon3_maid_list-client.toml`：

```toml
[foxdream]
    # 0 或负数 = 不限制
    care_panel_range = 64.0
```

默认 64 格（原版 8 格）。填 0 就是完全不限制。

### 3. 软依赖保护

- 目标类用 `@Mixin(targets = "...")` 字符串写，编译期不引用 foxdream
- `MaidPanelMixinPlugin` 实现 `IMixinConfigPlugin#shouldApplyMixin`，没装 foxdream 时整条混入不应用
- 混入配置 `"required": false`，万一上游改了方法名也不会因为注入失败而崩
- `PanelConfig.squaredRange()` 自带 try/catch 兜底

> 注：混入是**客户端**逻辑（`"client"` 段），专用服务器不会加载它。
> 服务端启动已验证配置能正常解析；纯客户端效果需要进游戏实测。

### 4. 文案

- 成长标签改为粉底**白字**
- 右上角计数：`女仆 N` → `女仆数量 N 只`（英文仍为 `Maids N`）

---

## 十三、1.6.0：修掉「混入没生效」的真凶

### 现象

1.5.0 进游戏后，超过 8 格打开狐梦面板还是「闪一下就关」。

### 真因（本地起客户端抓日志确认）

最初的混入配置里写了个 `IMixinConfigPlugin`，用 `ModList.get().isLoaded("foxdream")`
判断要不要应用混入。但 **`shouldApplyMixin` 在启动的很早阶段就会被调用**，那时
`ModList` 还没构建好，`ModList.get()` 直接抛异常 → 我的 catch 返回 `false` →
**即使装了 foxdream，混入也被无条件跳过**。

日志原话：

```
[女仆名单] 查询 mod 列表失败，跳过狐梦混入
[女仆名单] 狐梦混入 ...CareScreenDistanceMixin -> 跳过（未装 foxdream）
```

### 修法

**删掉整个 `IMixinConfigPlugin`**。既然目标类 `CareScreen` 只在装了 foxdream 时才存在，
「类不存在 → 根本没人加载它 → 混入自然不应用」本身就是最可靠的开关，不需要额外判断。

混入本身也从 `@ModifyConstant`（依赖常量匹配）改成 `@Redirect`（直接拦截
`LocalPlayer.distanceToSqr` 那次调用），更不容易被上游的小改动打断。

配置 `"required": false` 保留，所以没装 foxdream 时只会多两条 WARN：

```
@Mixin target ...CareScreen was not found
Error loading class: ...CareScreen (ClassNotFoundException)
```

无害，游戏照常启动。

### 验证记录（本地 dev 客户端实测）

| 场景 | 结果 |
|---|---|
| 装了 foxdream | 日志出现 `已接管狐梦面板的距离检查，允许范围 64.0 格`，无注入报错 |
| 没装 foxdream | 仅两条 WARN，`未检测到狐梦伴岁，跳过狐梦混入自检`，正常启动 |

另外面板标题现在会显示版本号（如 `女仆名单 v1.6.0`），方便确认跑的是哪一版。

---

## 十四、1.6.1：蓝色按钮的远程限制（不是 1.6.0 引入的）

### 现象

蓝色「打开女仆背包」按钮在 ~10 格外打开后会「闪一下就关」。

### 真因

这是**车万女仆上游**的行为，与 1.6.0 的狐梦混入无关（那条只作用于
`CareScreen#tick`）。`AbstractMaidContainer#stillValid` 结尾是：

```java
if (maid == null) return false;
if (!maid.isOwnedBy(player)) return false;
if (!maid.isAlive() || maid.isSleeping()) return false;   // 睡觉也关
return player.canInteractWithEntity(maid, 4.0D);          // ← 距离校验
```

界面开着时客户端每 tick 都会问一次 `stillValid`，一旦返回 false 就自己关掉。
实际阈值还会叠加 `ENTITY_INTERACTION_RANGE` 属性（基础 3.0），
所以有加距离的装备/属性时体感会变成 10 格左右。

### 修法

新增 `MaidContainerDistanceMixin`，只 `@Redirect` 掉那一次
`canInteractWithEntity` 调用：

| 判断 | 处理 |
|---|---|
| `isOwnedBy` 归属 | 保留 |
| `isAlive` / `isSleeping` | 保留（睡觉依旧打不开） |
| 距离 | 接管，默认放开 |

配置项（`config/fairymoon3_maid_list-client.toml`）：

```toml
[tlm]
    # false = 恢复车万女仆原版 4 格限制
    remote_maid_container = true
```

### 验证记录（本地 dev 客户端）

```
[女仆名单] 混入自检：女仆容器距离校验（...AbstractMaidContainer）已加载
[女仆名单] 混入自检：狐梦面板距离校验（...CareScreen）已加载
[女仆名单] 已接管狐梦面板的距离检查，允许范围 64.0 格
[女仆名单] 已接管女仆容器的距离校验（允许远程打开背包）   ← 真实执行
```

无注入报错。

---

## 十五、1.7.0：狐梦 HUD 里那个「打开背包」按钮

### 真因：不是距离，是按钮被置灰了

`CareScreen#updateButtons()`：

```java
backpack.active = data.getDouble("Mood") >= 10.0 && data.getInt("Trust") > 0;
```

心情 < 10 或信任 = 0 时按钮是**灰的**，点击毫无反应 —— 这才是「点了没反应」。
服务端 `CareNetwork` 处理该动作时还有一层 `trust > 0 && !gloomy`，不满足只发一句
动作栏提示（很容易漏看）。

### 这次把整条链路上的门都过了一遍

| 环节 | 校验 | 我们的处理 |
|---|---|---|
| 客户端按钮启用 | `Mood ≥ 10 && Trust > 0` | `CareScreenBackpackButtonMixin` 强制可点（**只解 UI**） |
| 客户端→服务端动作 | 5 tick 频率限制 | 保留（防刷） |
| 服务端动作分支 | `trust > 0 && !gloomy` | **保留**（foxdream 的真实规则） |
| `EntityMaid#openMaidGui` | 睡觉时 false | 保留 |
| 容器 `stillValid` | `canInteractWithEntity(maid, 4.0)` | 1.6.1 已接管 |
| `OpenMaidGuiPackage#stillValid` | `distanceTo < 5.0F` | **1.7.0 新增接管** |

所以现在的行为是：**按钮永远可点**，距离不再拦；但如果信任/心情不满足，
服务端会明确提示出来（以前按钮是灰的，连提示都看不到）。

配置（`config/fairymoon3_maid_list-client.toml`）：

```toml
[foxdream_ui]
    force_backpack_button = true   # false = 恢复 foxdream 原版置灰逻辑

[tlm]
    remote_maid_container = true   # false = 恢复 4 格限制
```

### 验证记录（本地 dev 客户端）

四个混入全部加载且无注入错误，其中两条在真实运行中被触发：

```
[女仆名单] 已接管女仆容器的距离校验（允许远程打开背包）
[女仆名单] 已接管狐梦面板的距离检查，允许范围 64.0 格
```

---

## 十六、1.7.1：HUD 里所有按钮在远处失效的真因

### 现象

远程打开狐梦 HUD 后，**打开背包**、**早点睡觉** 等所有按钮在 8 格外点击都没有任何反馈，
8 格内正常。

### 真因：动作包被服务端静默丢弃

HUD 里所有按钮都只是发同一个动作包 `CareNetwork$Action`，服务端处理时第一件事是：

```java
if (!(maid.isAlive()
      && maid.isOwnedBy(player)
      && player.distanceToSqr(maid) <= 64.0        // 64 = 8²
      && player.hasLineOfSight(maid))) {
    return;                                        // ← 静默 return，没有任何提示
}
```

所以不是某个按钮的问题，而是**整个动作通道**被 8 格距离 + 视线挡掉了。
1.7.0 修的「按钮置灰」和「容器距离」都在这个判断的下游，所以当时没解决。

### 修法

`CareNetworkActionRangeMixin` 接管这两处调用：

| 判断 | 处理 | 配置项 |
|---|---|---|
| `distanceToSqr(maid) <= 64.0` | 接管，恒返回 0 | `remote_maid_container` |
| `hasLineOfSight(maid)` | 接管，恒返回 true | `ignore_line_of_sight` |

归属（`isOwnedBy`）、存活（`isAlive`）等其它条件原样保留。

### 全量排查：还有哪些距离门

扫了 foxdream 全部 633 个 class，**网络处理**层只有 3 处距离判断：

| 类 | 判断什么 | 是否影响我们的面板 |
|---|---|---|
| `CareNetwork`（HUD 动作包） | 玩家 ↔ 女仆距离 8 格 + 视线 | ✅ 已接管 |
| `BedBindingNetwork` | 玩家 ↔ 床方块距离 | ❌ 我们的面板不打开它 |
| `VaultFilterNetwork.allowed` | 玩家 ↔ 宝库方块距离 | ❌ 同上 |

其余 80+ 个引用 `distanceToSqr` 的类都是 AI / 玩法逻辑，与面板无关。

连同之前几版，现在被接管的距离门一共 5 处：

1. `CareScreen#tick` 面板自身 8 格（1.6.0）
2. `AbstractMaidContainer#stillValid` 容器 4 格（1.6.1）
3. `OpenMaidGuiPackage#stillValid` 开界面包 5 格（1.7.0）
4. `CareNetwork` 动作包 8 格 + 视线（1.7.1）
5. `CareScreen#updateButtons` 的按钮置灰（1.7.0，UI 层）

### 验证记录

```
[女仆名单] 已接管狐梦 HUD 动作包的距离校验（允许远程操作）   ← 真实执行
```

---

## 十七、1.7.2：睡觉按钮 + 睡觉中禁止传送

### 1. 「早点睡觉」为什么单独还有限制

`CareOrderedSleep#begin` 里**独立写了一份**同样的校验，和动作包里那份不共享：

```java
if (!CareData.started(maid) || !maid.isAlive() || !maid.isOwnedBy(player)
    || player.isSpectator()
    || player.level() != maid.level()
    || player.distanceToSqr(maid) > 64.0        // 8 格
    || !player.hasLineOfSight(maid)) {
    return false;
}
```

所以 1.7.1 放开动作包后，「打开背包」通了但「早点睡觉」还是静默失败。
`CareOrderedSleepRangeMixin` 只接管距离与视线，其余条件（已转化 / 存活 / 归属 /
旁观 / 同维度）全部保留。

顺带排查了同一次分派里的其它动作：

| 动作目标 | 是否有距离门 |
|---|---|
| `CareLife#action` | 无 |
| `CareWishes#claim` | 无 |
| `CareDebug#act` | 无（调试用） |
| `CareOrderedSleep#begin` | **有（已接管）** |

### 2. 睡觉中的女仆不可传送

- 橙色按钮悬停时：女仆在睡觉 → 提示 **「不可以打扰女孩子睡觉哦」**
- 真去点时服务端也拦：`MaidSummoner` 在传送前检查 `maid.isSleeping()`，
  命中就发同一句动作栏提示并直接返回（不会强行把她拖起床）

### 验证记录

```
[女仆名单] 已接管狐梦『早点睡觉』的距离校验（允许远程下达）   ← 真实执行
```

---

## 十八、1.7.3：那个「60 格」是我自己设的默认上限

### 现象

蓝按钮（直接开背包）无限制，但「通过面板打开狐梦手册」「手册里的打开背包 / 早点睡觉」
都在大约 60 格外失效。

### 真因：`care_panel_range` 默认 64

1.6.0 起 `CareScreenDistanceMixin` 是这么写的：

```java
double limitSq = PanelConfig.squaredRange();   // 默认 care_panel_range = 64.0
if (real <= limitSq) return 0.0D;              // 64 格内放行
return 1.0E12D;                                 // 超出 → 交还原版 onClose()
```

也就是**我在放开狐梦面板时顺手留了个 64 格上限**。HUD 一超出 64 格就被关掉，
里面所有按钮自然都点不到 —— 三个症状同一个原因。

### 修法

- 距离判断不再有上限：只要 `tlm.remote_maid_container` 打开，恒返回 0
- 删掉 `care_panel_range` 配置项（你配置文件里残留的 `[foxdream] care_panel_range = 64.0`
  会被当成未知键忽略，可以手动删掉）
- 额外补一处：`tick()` 里还有 `getEntity(entityId) == null → onClose()`，
  客户端因追踪范围 / 换维度拿不到实体时也会关。现在取不到就返回玩家自己，
  让守卫通过（面板内容全部来自服务端下发的 NBT，不依赖实体）

### 验证记录

5 个混入自检全部通过，无注入错误。

---

## 十九、1.8.0：按钮图标 / 排序 / 中键拖动

### 1. 移除 `force_backpack_button`

已按原版恢复：狐梦面板的「打开背包」按钮重新按 `心情 ≥ 10 && 信任 > 0` 置灰
（`CareScreenBackpackButtonMixin` 整个删除，配置项也去掉）。残留的旧键会被当成未知项忽略。

### 2. 按钮改用原版物品图标（缩小绘制）

| 按钮 | 图标来源 |
|---|---|
| 粉色（狐梦） | `foxdream:care_journal` 狐梦陪伴手册 |
| 蓝色（背包） | `touhou_little_maid:maid_backpack_small` 小背包 |
| 橙色（传送） | `touhou_little_maid:servant_bell` 仆人铃 |

按钮 14×14，图标区 10×10（按 `10/16` 缩放 `renderItem`），悬停时底色换成对应强调色。
物品取不到（比如没装 foxdream）时退回纯色按钮。

### 3. 排序改为「获得顺序」

不再按距离排序，改为：

1. 手动拖动后的顺序（`config/fairymoon3_maid_list_order.json`）
2. `MaidWorldData` 索引里记录的获得时间戳（`acquiredAt`）
3. 名字

距离仍显示在行右侧，只是不再参与排序。

### 4. 中键拖动重排

- 按住**鼠标中键**抓起一行 → 该行出现描边
- 拖到目标位置 → 目标处显示一条粉色插入线
- 松开 → 落位并立刻写入 `config/fairymoon3_maid_list_order.json`（客户端本地文件，不影响服务端）

第一次打开面板时会按获得时间自动把现有女仆写入顺序表，所以初始顺序就是「获得先后」。

---

## 二十、1.8.1：标题栏图标 + 重画物品贴图

### 1. 标题栏左侧加了神龛图标

用 `touhou_little_maid:shrine`（方块 `BlockShrine` 的物品形态，资源里确实有
`models/item/shrine.json` + `textures/item/shrine.png`），16×16 原尺寸绘制在标题左边，
标题和版本号顺延。取不到物品时自动退回原来的纯文字布局。

### 2. 重画了女仆名单自己的物品图标

改成一个「魔改原版书本」的 16×16 像素图标：

| 部位 | 配色 |
|---|---|
| 书皮主体 | 白色 `#F7F7FC`（原来原版书本是棕色） |
| 外描边 | 深棕 `#786860`，模拟书脊投影 |
| 金边 | `#D6AC3E` / 高光 `#F7DC84` |
| 书脊 | 粉色 `#F08AB2`，与白书皮之间用金线分隔 |
| 中央纹章 | 粉色菱形 + 高光点 |
| 书页 | 右侧留一列米白 + 两道页纹 |

生成脚本在 `maid_panel_preview.png` 旁边（预览图 160×160 便于查看）。

---

## 二十一、1.9.0：面板专用字体

### 为什么不能打包方正喵呜体

方正喵呜体是**商业字体**，随模组分发属于再分发，需要授权。所以换成开源授权字体。

### 可合法打包分发的可爱中文字体

| 字体 | 授权 | 特点 |
|---|---|---|
| **站酷快乐体 ZCOOL KuaiLe** | SIL OFL 1.1 | 圆润活泼、笔画粗，最接近喵呜体的 Q 感（**本模组采用**） |
| 站酷庆科黄油体 ZCOOL QingKe HuangYou | SIL OFL 1.1 | 更粗更方，招牌感强，小字号偏糊 |
| 霞鹜文楷 LXGW WenKai | SIL OFL 1.1 | 手写楷体，可读性最好，中文覆盖极全 |
| 得意黑 Smiley Sans | SIL OFL 1.1 | 倾斜压缩潮牌字形，小字号偏挤 |
| Cubic 11 | SIL OFL 1.1 | 像素字体，专为 12px 网格设计，MC 气质最搭 |
| 思源黑体 / Noto Sans SC | SIL OFL 1.1 | 最稳，但不够可爱 |

> OFL 允许嵌入作品分发，但**必须随附许可证文本**（已打包含 `LICENSE-ZCOOL-KuaiLe-OFL.txt`）；
> 若改造字体本身且字体带 Reserved Font Name，需要改名。

### 实现方式

- 字体文件：`assets/fairymoon3_maid_list/font/panel.ttf`（站酷快乐体）
- 字体定义：`assets/fairymoon3_maid_list/font/panel.json`
  ```json
  { "providers": [
      { "type": "ttf", "file": "fairymoon3_maid_list:panel.ttf", "size": 11.0, "oversample": 2.0,
        "shift": [0.0, 0.0], "skip": "" },
      { "type": "reference", "id": "minecraft:default" } ] }
  ```
  第二个 provider 是**逐字形回退**：字体缺字时自动用原版字体补，不会出现豆腐块。
- 只作用于面板：所有文本都包一层 `Component.copy().withStyle(s -> s.withFont(fairymoon3_maid_list:panel))`，
  `Font#width(Component)` 会自动按该字体测量宽度，布局不受影响。

### 想换字体

替换 `panel.ttf` 后重新构建即可（或告诉我换哪款，我直接换）。

---

## 二十二、2.0.0：传送体系与视觉微调

### 1. 图标白边

边缘半透明像素是"图案 + 棋盘格背景"混合出来的浅色，直接切 alpha 会留一圈白晕。这次三处处理：

- 把 alpha 低阈值从 `0.18` 提到 `0.42`，弱 alpha 直接清零
- 对边缘像素按棋盘格平均色 `232` 做反混合：`c = (obs - 232×(1-a)) / a`
- 缩放到 32×32 后再收一次边（`< 0.35` 清零，`> 0.92` 变不透明）

半透明像素占比从 11% 降到 **6%**。

### 2. 传送音效

| 操作 | 音效 |
|---|---|
| 召唤女仆到身边 | `SoundEvents.BELL_BLOCK`（车万女仆仆人铃用的就是原版钟声） |
| 玩家前往女仆身边 | `SoundEvents.PLAYER_TELEPORT`（末影珍珠传送音效） |

### 3. 新增「去她身边」按钮

行内 TP 按钮旁新增**末影之眼**按钮，把玩家传送过去，悬停显示「去她身边」。

### 4. 新增「一键召唤」

标题栏计数左侧新增**小号**（`touhou_little_maid:trumpet`）按钮，一键把**所有能传送的**
女仆叫过来（同维度 + 已加载 + 非睡眠）。

### 5. 聊天框记录

三种传送操作的成功/失败都会写入左下角聊天框：

```
[女仆名单] 灵梦 已来到你身边
[女仆名单] 魔理沙 传送失败：不在加载范围内，或不在同一维度
[女仆名单] 已召唤 3 只女仆，1 只无法传送（未加载或在其它维度）
```

### 6. 成长阶段配色

| 阶段 | 底色 |
|---|---|
| 幼年期 | 粉 `#F26D9C` |
| 青年期 | 天蓝 `#5FA8E0` |
| 成年期 | 亮金 `#E8B93D` |

统一白字。











