package com.fairymoon3.maidlist.client;

import com.github.tartaricacid.touhoulittlemaid.client.resource.models.MaidModels;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.fairymoon3.maidlist.config.PanelConfig;
import com.fairymoon3.maidlist.network.MaidEntry;
import com.fairymoon3.maidlist.network.MaidFoxdreamPayload;
import com.fairymoon3.maidlist.network.MaidListRequestPayload;
import com.fairymoon3.maidlist.network.MaidOpenGuiPayload;
import com.fairymoon3.maidlist.network.MaidSitTogglePayload;
import com.fairymoon3.maidlist.network.MaidStorePayload;
import com.fairymoon3.maidlist.network.MaidSummonAllPayload;
import com.fairymoon3.maidlist.network.MaidTeleportPayload;
import com.fairymoon3.maidlist.network.MaidTeleportToPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.ModList;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆名册面板 —— QQ 联系人列表风格：一个女仆一行，点击展开详情，滚轮浏览。
 *
 * <p>头像直接渲染女仆实体本身（跟随其模型），实体不在客户端加载范围内时退回职业图标。</p>
 */
public class MaidPanelScreen extends Screen {

    // ---------------------------------------------------------------- 布局常量
    private static final int PANEL_W = 372;
    private static final int PANEL_H = 258;
    private static final int HEADER_H = 30;
    private static final int TOOLBAR_H = 26;
    private static final int ROW_H = 32;
    private static final int DETAIL_H = 46;
    private static final int ROW_GAP = 2;
    private static final int FOOTER_H = 18;
    private static final int AVATAR = 22;
    private static final int TP_SIZE = 14;

    /** 三个按钮各自的物品图标（原版物品，缩小绘制）。 */
    private static final ItemStack ICON_FOX = stackOf("foxdream:care_journal");
    private static final ItemStack ICON_BAG = stackOf("touhou_little_maid:maid_backpack_small");
    private static final ItemStack ICON_BELL = stackOf("touhou_little_maid:servant_bell");
    /** 标题栏左侧图标：车万女仆的「神龛」。 */
    private static final ItemStack ICON_SHRINE = stackOf("touhou_little_maid:shrine");
    /** 前往女仆身边：末影之眼。 */
    private static final ItemStack ICON_EYE = stackOf("minecraft:ender_eye");
    /** 全部召唤：车万女仆的「小号」。 */
    private static final ItemStack ICON_TRUMPET = stackOf("touhou_little_maid:trumpet");

    // ---------------------------------------------------------------- 面板字体

    /**
     * 面板专用字体 ID（资源：{@code assets/fairymoon3_maid_list/font/panel.json}，
     * 字体文件为随模组分发的站酷快乐体，SIL OFL 1.1）。
     *
     * <p>只作用于本面板内我们自己画的文本，靠 {@code Style#withFont} 逐段指定，
     * 不影响游戏其它界面。</p>
     */
    private static final ResourceLocation PANEL_FONT =
            ResourceLocation.fromNamespaceAndPath("fairymoon3_maid_list", "panel");

    private static Component panelStyle(Component text) {
        MutableComponent copy = text.copy();
        if (PanelConfig.useVanillaFont()) {
            return copy;
        }
        return copy.withStyle(style -> style.withFont(PANEL_FONT));
    }

    private static Component panelStyle(String text) {
        return panelStyle(Component.literal(text));
    }

    private int panelWidth(String text) {
        return this.font.width(panelStyle(text));
    }

    private int panelWidth(Component text) {
        return this.font.width(panelStyle(text));
    }

    private void panelText(GuiGraphics graphics, Component text, int x, int y, int color, boolean shadow) {
        graphics.drawString(this.font, panelStyle(text), x, y, color, shadow);
    }

    private void panelText(GuiGraphics graphics, String text, int x, int y, int color, boolean shadow) {
        graphics.drawString(this.font, panelStyle(text), x, y, color, shadow);
    }

    private void panelCenter(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(this.font, panelStyle(text), x - panelWidth(text) / 2, y, color, false);
    }

    private void panelTooltip(GuiGraphics graphics, Component text, int mouseX, int mouseY) {
        graphics.renderTooltip(this.font, panelStyle(text), mouseX, mouseY);
    }

    private static ItemStack stackOf(String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                return ItemStack.EMPTY;
            }
            Item item = BuiltInRegistries.ITEM.get(rl);
            return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    // ---------------------------------------------------------------- 配色（白天 / 夜间可切换）
    /** 当前是否为夜间（深色）主题，来自客户端配置。 */
    private com.fairymoon3.maidlist.config.ThemeMode currentTheme = com.fairymoon3.maidlist.config.ThemeMode.GLASS;
    /** 面板外世界压暗强度，半透明主题下让世界更清楚。 */
    private int worldDim;

    private int cPanel;
    private int cBorder;
    private int cHeader;
    private int cAccent;
    private int cText;
    private int cSub;
    private int cDim;
    private int cListBg;
    private int cSearchBg;
    private int cRowHover;
    private int cRowAltA;
    private int cRowAltB;
    private int cRowSelected;
    private int cBtnBg;
    private int cBtnBorder;
    private int cAvatarBg;
    private int cHealthBg;
    private int cCoolMask;
    private int cHiLine;
    private int cHp;
    private int cFavor;
    private int cPos;
    private int cDimText;

    /** 狐梦联动的粉色。 */
    private static final int C_FOX = 0xFFF26D9C;

    /** 米白（白天）配色。 */
    private void applyLightTheme() {
        worldDim = 0x77000000;
        cPanel = 0xF7FBF6EC;
        cBorder = 0xFFD6C5A9;
        cHeader = 0xFFF2E8D6;
        cAccent = 0xFFD4802A;
        cText = 0xFF3A3229;
        cSub = 0xFF7C6F5C;
        cDim = 0xFF9C8E79;
        cListBg = 0xFFF4ECDB;
        cSearchBg = 0xFFEAE0CB;
        cRowHover = 0x18000000;
        cRowAltA = 0x0A000000;
        cRowAltB = 0x04000000;
        cRowSelected = 0x33D4802A;
        cBtnBg = 0xFFE9DDC6;
        cBtnBorder = 0xFFC9B896;
        cAvatarBg = 0xFFEDE2CC;
        cHealthBg = 0xFFDFD2BC;
        cCoolMask = 0x88000000;
        cHiLine = 0xFFD4802A;
        cHp = 0xFF2E8B2E;
        cFavor = 0xFFD03030;
        cPos = 0xFFA8791B;
        cDimText = 0xFFC0661A;
    }

    /** 深色（夜间）配色：沿用 v0.1 的黑色面板。 */
    private void applyDarkTheme() {
        worldDim = 0x77000000;
        cPanel = 0xF01A1D24;
        cBorder = 0xFF343B4A;
        cHeader = 0xFF20242D;
        cAccent = 0xFFE8A33D;
        cText = 0xFFEDF1F7;
        cSub = 0xFF9AA3B2;
        cDim = 0xFF7F8899;
        cListBg = 0xFF14171D;
        cSearchBg = 0xFF0E1015;
        cRowHover = 0x1FFFFFFF;
        cRowAltA = 0x12FFFFFF;
        cRowAltB = 0x08FFFFFF;
        cRowSelected = 0x33E8A33D;
        cBtnBg = 0xFF2A2F3A;
        cBtnBorder = 0xFF4A5265;
        cAvatarBg = 0xFF232732;
        cHealthBg = 0xFF3A3F4A;
        cCoolMask = 0xAA0E1014;
        cHiLine = 0xFFFFE8C0;
        cHp = 0xFF55FF55;
        cFavor = 0xFFFF5555;
        cPos = 0xFFFFFF55;
        cDimText = 0xFFFFAA00;
    }

    /**
     * 整体半透明（玻璃）主题。
     *
     * <p>面板与列表都带明显 alpha，能透出后面的世界；文字用近白色，
     * 边框用暖金，保证在任意背景上都能看清。</p>
     */
    private void applyGlassTheme() {
        // 更透：面板约 40%、列表约 22%，能明显看到后面的世界
        worldDim = 0x40000000;
        cPanel = 0x661A1D24;
        // 外围边框用纯白
        cBorder = 0xFFFFFFFF;
        cHeader = 0x5512151B;
        cAccent = 0xFFFFC96B;
        cText = 0xFFF7F4EE;
        cSub = 0xFFD9D3C8;
        cDim = 0xFFB3ACA0;
        cListBg = 0x3814171D;
        cSearchBg = 0x4D101418;
        cRowHover = 0x2AFFFFFF;
        cRowAltA = 0x14FFFFFF;
        cRowAltB = 0x0AFFFFFF;
        cRowSelected = 0x44FFC96B;
        cBtnBg = 0x66181C24;
        cBtnBorder = 0xCCFFFFFF;
        cAvatarBg = 0x66181B22;
        cHealthBg = 0x66FFFFFF;
        cCoolMask = 0xAA000000;
        cHiLine = 0xFFFFE9A3;
        cHp = 0xFF7BE07B;
        cFavor = 0xFFFF8B8B;
        cPos = 0xFFFFE082;
        cDimText = 0xFFFFC178;
    }

    /** 按配置刷新调色板；搜索框颜色也跟着主题走。 */
    private void applyTheme() {
        this.currentTheme = PanelConfig.themeMode();
        switch (this.currentTheme) {
            case DARK -> applyDarkTheme();
            case GLASS -> applyGlassTheme();
            default -> applyLightTheme();
        }
        if (this.searchBox != null) {
            this.searchBox.setTextColor(cText);
            this.searchBox.setHint(Component.translatable("fairymoon3_maid_list.search.hint")
                    .withStyle(style -> style.withColor(cDim & 0xFFFFFF)));
        }
    }

    private List<MaidEntry> entries = new ArrayList<>();
    private UUID selected;
    private double scroll;
    private String query = "";

    private EditBox searchBox;
    private MaidEntry hoveredTp;
    private MaidEntry hoveredGui;
    private MaidEntry hoveredFox;
    private MaidEntry hoveredGo;
    private boolean hoveredSummonAll;
    private boolean hoveredTheme;
    private MaidEntry hoveredMute;
    private MaidEntry hoveredStore;
    private boolean hoveredShrine;

    /** 右上角关闭按钮的边长。 */
    private static final int CLOSE_SIZE = 11;
    /** 版本号文字的可点击区域 {x, y, w, h}，用于切换字体。 */
    private int[] versionRect;

    /** 右键列表弹出的操作菜单（宽度按文本自适应）。 */
    private static final int MENU_H = 18;
    private MaidEntry contextMenuEntry;
    private int contextMenuX;
    private int contextMenuY;

    /** 主题切换按钮的冷却（0.5 秒）。 */
    private static final long THEME_COOLDOWN_MS = 500L;
    /** 坐下 / 起身按钮的冷却（1 秒）。 */
    private static final long SIT_COOLDOWN_MS = 1000L;
    private long cdThemeUntil;
    /** 坐下 / 起身：每个女仆各自一条冷却。 */
    private final java.util.Map<UUID, Long> cdSit = new java.util.HashMap<>();

    /** 面板开着时每隔若干 tick 自动刷新一次，保证狐梦转化等状态能实时出现。 */
    private int refreshTimer;
    /** 面板自动刷新间隔（tick）。1 秒一次，狐梦那边改数值能较快反映出来。 */
    private static final int REFRESH_INTERVAL = 20;

    /** 手动刷新时的列表扫光动画（毫秒）。 */
    private static final long REFRESH_ANIM_MS = 600L;
    private long refreshAnimStart;
    private long refreshAnimUntil;

    /** 三个传送按钮各自独立的冷却（毫秒）。 */
    private static final long TP_COOLDOWN_MS = 2000L;
    /** 召唤 / 前往：传送类按钮共用一条冷却。 */
    private long cdSummonUntil;
    private long cdGoUntil;
    /** 一键全体召唤是全局按钮，也是共用冷却。 */
    private long cdSummonAllUntil;

    private static long cdOf(java.util.Map<UUID, Long> map, MaidEntry entry) {
        Long until = map.get(entry.uuid());
        return until == null ? 0L : until;
    }

    private static boolean onCooldown(long until) {
        return System.currentTimeMillis() < until;
    }

    /** 中键拖动重排：正在拖的女仆、鼠标下方的目标行、上次鼠标 Y。 */
    private UUID dragging;
    private MaidEntry dragTarget;
    private double dragMouseY;

    /** 模型 id -> 显示名，避免每帧重复解析翻译键。 */
    private final Map<String, String> modelNameCache = new HashMap<>();

    /** UUID -> 客户端女仆实体，每帧刷新一次，用于渲染头像。 */
    private final Map<UUID, EntityMaid> clientMaids = new HashMap<>();

    public MaidPanelScreen(List<MaidEntry> entries) {
        super(Component.translatable("fairymoon3_maid_list.title"));
        if (entries != null) {
            this.entries = new ArrayList<>(entries);
        }
    }

    public void setEntries(List<MaidEntry> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        if (this.selected != null && this.entries.stream().noneMatch(e -> e.uuid().equals(this.selected))) {
            this.selected = null;
        }
        // 首次见到的女仆按「获得时间」补进排列顺序表尾部，之后顺序就固定下来
        List<MaidEntry> byAcquire = new ArrayList<>(this.entries);
        byAcquire.sort(Comparator.comparingLong(MaidEntry::acquiredAt).thenComparing(MaidEntry::name));
        List<UUID> ids = new ArrayList<>(byAcquire.size());
        for (MaidEntry entry : byAcquire) {
            ids.add(entry.uuid());
        }
        MaidOrderStore.seed(ids);
        clampScroll();
    }

    // ---------------------------------------------------------------- 几何辅助

    private int panelLeft() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelTop() {
        return (this.height - PANEL_H) / 2;
    }

    private int listLeft() {
        return panelLeft() + 8;
    }

    private int listRight() {
        return panelLeft() + PANEL_W - 8;
    }

    private int listTop() {
        return panelTop() + HEADER_H + TOOLBAR_H;
    }

    private int listBottom() {
        return panelTop() + PANEL_H - FOOTER_H;
    }

    private int tpButtonX() {
        return listRight() - TP_SIZE - 4;
    }

    private int goButtonX() {
        return tpButtonX() - TP_SIZE - 3;
    }

    private int guiButtonX() {
        return goButtonX() - TP_SIZE - 3;
    }

    private int foxButtonX() {
        return guiButtonX() - TP_SIZE - 3;
    }

    /** 收纳按钮（最左侧，用神龛图标）。 */
    private int storeButtonX() {
        return foxButtonX() - TP_SIZE - 3;
    }

    /** 一行里文字可用的右边界（跟着按钮布局走）。 */
    private int rowContentRight(MaidEntry entry) {
        return storeButtonX() - 6;
    }

    private int listHeight() {
        return listBottom() - listTop();
    }

    private boolean isExpanded(MaidEntry entry) {
        return this.selected != null && this.selected.equals(entry.uuid());
    }

    /** 狐梦数值区额外占用的高度：空行 + 分割线 + 标题 + 3 行数据 = 6 行。 */
    private static final int FOX_EXTRA_H = 78;

    /** 展开后的详情高度：只有已转化的女仆才多出狐梦那几行。 */
    private int detailHeight(MaidEntry entry) {
        return DETAIL_H + (entry.foxStats() != null ? FOX_EXTRA_H : 0);
    }

    private int rowHeight(MaidEntry entry) {
        return ROW_H + (isExpanded(entry) ? detailHeight(entry) : 0);
    }

    private int contentHeight() {
        int total = 0;
        for (MaidEntry entry : visibleEntries()) {
            total += rowHeight(entry) + ROW_GAP;
        }
        return total;
    }

    private void clampScroll() {
        double max = Math.max(0, contentHeight() - listHeight());
        this.scroll = Math.max(0, Math.min(this.scroll, max));
    }

    // ---------------------------------------------------------------- 数据整理

    private List<MaidEntry> visibleEntries() {
        List<MaidEntry> list = orderedAll();
        String q = this.query == null ? "" : this.query.trim().toLowerCase();
        if (!q.isEmpty()) {
            list.removeIf(entry -> !matches(entry, q));
        }
        return list;
    }

    /** 全部女仆的固定顺序：先看手动排列表，其次「获得时间」，最后名字。 */
    private List<MaidEntry> orderedAll() {
        List<MaidEntry> list = new ArrayList<>(this.entries);
        list.sort(Comparator.<MaidEntry>comparingInt(entry -> MaidOrderStore.indexOf(entry.uuid()))
                .thenComparingLong(MaidEntry::acquiredAt)
                .thenComparing(MaidEntry::name));
        return list;
    }

    private boolean matches(MaidEntry entry, String q) {
        return entry.name().toLowerCase().contains(q)
                || entry.taskId().getPath().toLowerCase().contains(q)
                || taskName(entry).toLowerCase().contains(q)
                || entry.backpackName().toLowerCase().contains(q)
                || modelName(entry).toLowerCase().contains(q);
    }

    private int dimensionRank(MaidEntry entry) {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null) {
            return 1;
        }
        return entry.dimension().equals(player.level().dimension().location()) ? 0 : 1;
    }

    private int distanceSq(MaidEntry entry) {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null || !entry.dimension().equals(player.level().dimension().location())) {
            return Integer.MAX_VALUE;
        }
        double dx = entry.x() - player.getX();
        double dy = entry.y() - player.getY();
        double dz = entry.z() - player.getZ();
        return (int) Math.min(Integer.MAX_VALUE, dx * dx + dy * dy + dz * dz);
    }

    private String distanceText(MaidEntry entry) {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null) {
            return "";
        }
        if (!entry.loaded()) {
            // 未加载：有快照就显示"多久之前记录的"，否则只能说未加载
            if (entry.ageSeconds() > 0) {
                return tr("fairymoon3_maid_list.record_age", humanAge(entry.ageSeconds()));
            }
            return tr("fairymoon3_maid_list.status.unloaded");
        }
        if (!entry.dimension().equals(player.level().dimension().location())) {
            return tr("fairymoon3_maid_list.other_dimension");
        }
        return (int) Math.sqrt(distanceSq(entry)) + "m";
    }

    private static String humanAge(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "min";
        }
        return (seconds / 3600) + "h";
    }

    private String taskName(MaidEntry entry) {
        ResourceLocation id = entry.taskId();
        return tr("task." + id.getNamespace() + "." + id.getPath());
    }

    private String dimensionName(ResourceLocation id) {
        return tr(id.toLanguageKey("dimension"));
    }

    /** 模型显示名：解析车万女仆模型包里的 {翻译键}，取不到就退回模型 id。 */
    private String modelName(MaidEntry entry) {
        if (entry.ysmModel()) {
            return tr("fairymoon3_maid_list.model.ysm");
        }
        String id = entry.modelId();
        if (id == null || id.isBlank()) {
            return tr("fairymoon3_maid_list.default_model");
        }
        return this.modelNameCache.computeIfAbsent(id, key -> {
            try {
                var info = MaidModels.getInstance().getInfo(key);
                if (info.isPresent()) {
                    String raw = info.get().getName();
                    if (raw != null && !raw.isEmpty()) {
                        if (raw.startsWith("{") && raw.endsWith("}")) {
                            String translationKey = raw.substring(1, raw.length() - 1);
                            if (I18n.exists(translationKey)) {
                                return I18n.get(translationKey);
                            }
                            return key;
                        }
                        return raw;
                    }
                }
            } catch (Throwable ignored) {
                // 模型包没加载好之类的情况，退回 id
            }
            return key;
        });
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // ---------------------------------------------------------------- 初始化

    @Override
    protected void init() {
        applyTheme();
        int sx = listLeft() + 4;
        int sy = panelTop() + HEADER_H + 5;
        int sw = PANEL_W - 16 - 54;

        this.searchBox = new EditBox(this.font, sx, sy, sw, 14,
                Component.translatable("fairymoon3_maid_list.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(32);
        this.searchBox.setHint(Component.translatable("fairymoon3_maid_list.search.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        this.searchBox.setTextColor(cText);
        this.searchBox.setValue(this.query);
        this.searchBox.setResponder(value -> {
            this.query = value;
            this.scroll = 0;
            clampScroll();
        });
        addRenderableWidget(this.searchBox);

        addRenderableWidget(Button.builder(Component.translatable("fairymoon3_maid_list.refresh"), b -> requestRefresh())
                .bounds(panelLeft() + PANEL_W - 8 - 48, panelTop() + HEADER_H + 4, 48, 16)
                .build());
    }

    private void requestRefresh() {
        this.refreshAnimStart = System.currentTimeMillis();
        this.refreshAnimUntil = this.refreshAnimStart + REFRESH_ANIM_MS;
        PacketDistributor.sendToServer(new MaidListRequestPayload());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 完全接管背景。
     *
     * <p>1.21.1 的 {@code Screen#render} 会先调用 {@code renderBackground}，而原版实现是
     * 「模糊(processBlurEffect) + 暗化贴图」铺满整屏。如果不接管，它就会被画在本屏幕内容之上，
     * 把面板糊成一片。这里改成空实现，暗化由 {@link #render} 自己画在最底层。</p>
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 故意留空
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 只压暗世界，不做模糊（模糊会把面板一起糊掉）
        graphics.fill(0, 0, this.width, this.height, this.worldDim);

        applyTheme();
        refreshClientMaids();
        this.hoveredSummonAll = false;
        this.hoveredTheme = false;
        this.hoveredMute = null;
        this.hoveredStore = null;
        this.hoveredShrine = false;
        int left = panelLeft();
        int top = panelTop();
        int right = left + PANEL_W;
        int bottom = top + PANEL_H;

        // 外框与背景
        graphics.fill(left + 3, top + 3, right + 3, bottom + 3, 0x55000000);
        graphics.fill(left, top, right, bottom, cPanel);
        graphics.fill(left, top, right, top + 1, cBorder);
        graphics.fill(left, bottom - 1, right, bottom, cBorder);
        graphics.fill(left, top, left + 1, bottom, cBorder);
        graphics.fill(right - 1, top, right, bottom, cBorder);

        // 标题栏
        graphics.fill(left + 1, top + 1, right - 1, top + HEADER_H, cHeader);
        graphics.fill(left + 1, top + HEADER_H - 1, right - 1, top + HEADER_H, cAccent);

        // 左上角：神龛图标 + 标题（文字与右上的计数统一垂直居中于图标中线）
        int iconSize = 16;
        int iconX = left + 6;
        int iconY = top + (HEADER_H - iconSize) / 2;
        int centerY = iconY + iconSize / 2;
        int textY = centerY - this.font.lineHeight / 2;

        drawScaledItem(graphics, ICON_SHRINE, iconX, iconY, iconSize);
        int titleX = ICON_SHRINE.isEmpty() ? left + 20 : iconX + iconSize + 4;
        Component title = Component.translatable("fairymoon3_maid_list.title");
        panelText(graphics, title, titleX, textY, cText, false);
        String version = modVersion();
        if (!version.isEmpty()) {
            String versionText = "v" + version;
            int versionX = titleX + panelWidth(title) + 6;
            int versionW = panelWidth(versionText);
            this.versionRect = new int[]{versionX, textY, versionW, 9};
            panelText(graphics, versionText, versionX, textY, cDim, false);
        } else {
            this.versionRect = null;
        }
        String count = Component.translatable("fairymoon3_maid_list.count", this.entries.size()).getString();
        int countX = closeButtonX() - 8 - panelWidth(count);

        // 计数左侧：一键召唤所有能传送的女仆
        int allBtn = 16;
        int allX = countX - 6 - allBtn;
        int allY = iconY;
        boolean allHovered = inRect(mouseX, mouseY, allX, allY, allBtn);
        if (allHovered) {
            this.hoveredSummonAll = true;
        }
        drawIconButton(graphics, allX, allY, allBtn, allHovered, cAccent, ICON_TRUMPET);
        drawCooldownOverlay(graphics, allX, allY, allBtn, this.cdSummonAllUntil);

        // 主题切换（太阳 / 月亮）
        int themeBtn = 16;
        int themeX = themeButtonX();
        boolean themeHovered = inRect(mouseX, mouseY, themeX, allY, themeBtn);
        if (themeHovered) {
            this.hoveredTheme = true;
        }
        drawThemeButton(graphics, themeX, allY, themeBtn, themeHovered);
        if (isOverShrine(mouseX, mouseY)) {
            this.hoveredShrine = true;
        }
        drawCooldownOverlay(graphics, themeX, allY, themeBtn, themeBtn, this.cdThemeUntil, THEME_COOLDOWN_MS);

        // 右上角关闭按钮
        int closeX = closeButtonX();
        int closeY = closeButtonY();
        boolean closeHovered = inRect(mouseX, mouseY, closeX, closeY, CLOSE_SIZE);
        int closeColor = closeHovered ? cAccent : cSub;
        for (int i = 0; i < CLOSE_SIZE - 3; i++) {
            graphics.fill(closeX + 2 + i, closeY + 2 + i, closeX + 3 + i, closeY + 3 + i, closeColor);
            graphics.fill(closeX + CLOSE_SIZE - 3 - i, closeY + 2 + i,
                    closeX + CLOSE_SIZE - 2 - i, closeY + 3 + i, closeColor);
        }

        panelText(graphics, count, countX, textY, cAccent, false);

        // 搜索栏底（与 init() 里的 EditBox 严格对齐）
        int sx = listLeft() + 4;
        int sy = top + HEADER_H + 5;
        int sw = PANEL_W - 16 - 54;
        graphics.fill(sx - 3, sy - 3, sx + sw + 3, sy + 15, cSearchBg);
        graphics.fill(sx - 3, sy - 3, sx - 2, sy + 15, cBorder);

        // 列表
        this.hoveredTp = null;
        this.hoveredGui = null;
        this.hoveredFox = null;
        this.hoveredGo = null;
        drawList(graphics, mouseX, mouseY);
        drawRefreshAnimation(graphics);

        // 底部提示（居中）
        Component hint = Component.translatable("fairymoon3_maid_list.hint");
        panelCenter(graphics, hint, (left + right) / 2, bottom - 13, cDim);

        super.render(graphics, mouseX, mouseY, partialTick);


        // 按钮的浮层提示放在最后画，避免被列表裁剪
        if (this.hoveredShrine) {
            panelTooltip(graphics, Component.translatable("fairymoon3_maid_list.storage.tooltip.shrine"),
                    mouseX, mouseY);
        } else if (this.hoveredStore != null) {
            panelTooltip(graphics, Component.translatable("fairymoon3_maid_list.storage.store.tooltip"),
                    mouseX, mouseY);
        } else if (this.hoveredMute != null) {
            panelTooltip(graphics, Component.translatable(MaidMuteStore.isMuted(this.hoveredMute.uuid())
                    ? "fairymoon3_maid_list.voice.unmute"
                    : "fairymoon3_maid_list.voice.mute"), mouseX, mouseY);
        } else if (this.hoveredTheme) {
            panelTooltip(graphics, Component.translatable("fairymoon3_maid_list.theme.tooltip"),
                    mouseX, mouseY);
        } else if (this.hoveredSummonAll) {
            panelTooltip(graphics, Component.translatable("fairymoon3_maid_list.summon_all.tooltip"),
                    mouseX, mouseY);
        } else if (this.hoveredFox != null) {
            panelTooltip(graphics, Component.translatable("fairymoon3_maid_list.foxdream.tooltip"),
                    mouseX, mouseY);
        } else if (this.hoveredGui != null) {
            Component tip = this.hoveredGui.sleeping()
                    ? Component.translatable("fairymoon3_maid_list.gui.sleeping.tooltip")
                    : Component.translatable("fairymoon3_maid_list.gui.tooltip");
            panelTooltip(graphics, tip, mouseX, mouseY);
        } else if (this.hoveredGo != null) {
            panelTooltip(graphics, Component.translatable("fairymoon3_maid_list.go.tooltip"), mouseX, mouseY);
        } else if (this.hoveredTp != null) {
            Component tip = this.hoveredTp.sleeping()
                    ? Component.translatable("fairymoon3_maid_list.tp.sleeping.tooltip")
                    : Component.translatable("fairymoon3_maid_list.tp.tooltip");
            panelTooltip(graphics, tip, mouseX, mouseY);
        }
    
        drawStars(graphics);

        // 右键菜单最后画：盖住列表按钮、搜索框、刷新按钮等一切内容
        drawContextMenu(graphics, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ 左上角神龛互动

    private record UiStar(double x, double y, double vx, double vy, long born, int color) {
    }

    private static final long STAR_LIFE_MS = 900L;
    private final java.util.List<UiStar> uiStars = new java.util.ArrayList<>();

    private boolean isOverShrine(double mouseX, double mouseY) {
        int iconSize = 16;
        int iconX = panelLeft() + 6;
        int iconY = panelTop() + (HEADER_H - iconSize) / 2;
        return inRect(mouseX, mouseY, iconX, iconY, iconSize);
    }

    /** 点神龛：叫一声（原版猫叫），没有冷却。 */
    private void playMeow() {
        try {
            if (this.minecraft == null) {
                return;
            }
            this.minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.CAT_AMBIENT, 1.0F, 1.0F));
        } catch (Throwable ignored) {
            // 声音失败不影响界面
        }
    }

    /** 点神龛：从点击处散出一圈星星。 */
    private void spawnStars(double cx, double cy) {
        var rnd = java.util.concurrent.ThreadLocalRandom.current();
        long now = System.currentTimeMillis();
        int count = 10;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 * i / count) + rnd.nextDouble() * 0.6;
            double speed = 16.0 + rnd.nextDouble() * 26.0;
            int color = switch (i % 3) {
                case 0 -> 0xFFFFE9A3;
                case 1 -> 0xFFFFC46B;
                default -> 0xFFFFF7DC;
            };
            this.uiStars.add(new UiStar(cx, cy,
                    Math.cos(angle) * speed,
                    Math.sin(angle) * speed - 12.0,
                    now, color));
        }
    }

    private void drawStars(GuiGraphics graphics) {
        if (this.uiStars.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        this.uiStars.removeIf(star -> now - star.born() > STAR_LIFE_MS);
        for (UiStar star : this.uiStars) {
            float t = (now - star.born()) / (float) STAR_LIFE_MS;
            int alpha = (int) (255.0F * (1.0F - t));
            if (alpha <= 6) {
                continue;
            }
            double px = star.x() + star.vx() * t;
            double py = star.y() + star.vy() * t + 70.0 * t * t;
            int color = (alpha << 24) | (star.color() & 0xFFFFFF);
            int ix = (int) Math.round(px);
            int iy = (int) Math.round(py);
            graphics.fill(ix, iy - 2, ix + 1, iy + 3, color);
            graphics.fill(ix - 2, iy, ix + 3, iy + 1, color);
        }
    }

    /** 菜单宽度：跟随当前语言的实际文本，避免右侧一大片空白。 */
    private int contextMenuWidth() {
        return panelWidth(Component.translatable("fairymoon3_maid_list.attack.menu.entry")) + 16;
    }

    /** 右键菜单：目前只有一个入口 —— 打开这只女仆的仇恨名单。 */
    /** 右键菜单的位置：跟随鼠标，超出屏幕时自动夹回来。 */
    private int[] contextMenuRect() {
        if (this.contextMenuEntry == null) {
            return null;
        }
        int w = contextMenuWidth();
        int x = Math.max(4, Math.min(this.contextMenuX, this.width - w - 4));
        int y = Math.max(4, Math.min(this.contextMenuY, this.height - MENU_H - 4));
        this.contextMenuX = x;
        this.contextMenuY = y;
        return new int[]{x, y, w, MENU_H};
    }

    private void drawContextMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        int[] rect = contextMenuRect();
        if (rect == null) {
            return;
        }
        int x = rect[0];
        int y = rect[1];
        int w = rect[2];

        // GUI 的 z 越大越靠近镜头；物品 / 实体渲染会把 z 抬到 100~200，
        // 所以这里把菜单抬到 400，才能真正压在 3D 内容（头像、物品图标）之上。
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 400.0F);

        boolean hovered = inRect(mouseX, mouseY, x, y, w, MENU_H);
        boolean glass = this.currentTheme == com.fairymoon3.maidlist.config.ThemeMode.GLASS;

        // 底：玻璃主题用 60% 的深色（真半透明），其它主题用各自的面板底色
        int menuBg = glass ? ((cPanel & 0x00FFFFFF) | 0x99000000) : cPanel;
        // 边框：玻璃主题用 60% 白，不要用纯白整块，否则会透过底色把菜单顶成白灰
        int menuBorder = glass ? 0x99FFFFFF : cBorder;

        graphics.fill(x + 2, y + 2, x + w + 3, y + MENU_H + 3, 0x55000000);
        // 先铺底
        graphics.fill(x, y, x + w, y + MENU_H, menuBg);
        if (hovered) {
            graphics.fill(x, y, x + w, y + MENU_H, cRowHover);
        }
        // 再画四条 1px 边框线（只在边缘，不会垫在底色下面）
        graphics.fill(x, y, x + w, y + 1, menuBorder);
        graphics.fill(x, y + MENU_H - 1, x + w, y + MENU_H, menuBorder);
        graphics.fill(x, y, x + 1, y + MENU_H, menuBorder);
        graphics.fill(x + w - 1, y, x + w, y + MENU_H, menuBorder);
        // 顶部高光
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, glass ? 0x55FFFFFF : 0x33FFFFFF);
        panelText(graphics, Component.translatable("fairymoon3_maid_list.attack.menu.entry"),
                x + 7, y + 5, hovered ? cAccent : cText, false);

        pose.popPose();
    }

    private void drawList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<MaidEntry> list = visibleEntries();
        int lx = listLeft();
        int rx = listRight();

        graphics.fill(lx, listTop(), rx, listBottom(), cListBg);

        if (list.isEmpty()) {
            Component empty = this.entries.isEmpty()
                    ? Component.translatable("fairymoon3_maid_list.empty")
                    : Component.translatable("fairymoon3_maid_list.empty.search");
            panelCenter(graphics, empty, (lx + rx) / 2, listTop() + 40, cDim);
            return;
        }

        graphics.enableScissor(lx, listTop(), rx, listBottom());
        int y = listTop() - (int) this.scroll;
        int index = 0;
        for (MaidEntry entry : list) {
            int h = rowHeight(entry);
            if (y + h >= listTop() && y <= listBottom()) {
                drawRow(graphics, entry, index, lx, y, rx - lx, h, mouseX, mouseY);
            }
            y += h + ROW_GAP;
            index++;
        }
        if (this.dragging != null) {
            drawDragIndicator(graphics);
        }
        graphics.disableScissor();

        drawScrollbar(graphics);
    }

    /** 中键拖动时的视觉反馈：被拖动行的描边 + 插入位置的一条粉线。 */
    private void drawDragIndicator(GuiGraphics graphics) {
        int lx = listLeft();
        int rx = listRight();

        int y = listTop() - (int) this.scroll;
        for (MaidEntry entry : visibleEntries()) {
            int h = rowHeight(entry);
            if (entry.uuid().equals(this.dragging)) {
                graphics.fill(lx, y, rx, y + 1, cHiLine);
                graphics.fill(lx, y + h - 1, rx, y + h, cHiLine);
            }
            y += h + ROW_GAP;
        }

        MaidEntry target = this.dragTarget;
        if (target != null) {
            y = listTop() - (int) this.scroll;
            for (MaidEntry entry : visibleEntries()) {
                if (entry.uuid().equals(target.uuid())) {
                    graphics.fill(lx, y - 1, rx, y + 1, C_FOX);
                    break;
                }
                y += rowHeight(entry) + ROW_GAP;
            }
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int total = contentHeight();
        int view = listHeight();
        if (total <= view) {
            return;
        }
        int trackX = listRight() - 3;
        int trackTop = listTop();
        int barH = Math.max(16, (int) (view * (view / (double) total)));
        double max = total - view;
        int barY = trackTop + (int) ((view - barH) * (this.scroll / max));
        graphics.fill(trackX, trackTop, trackX + 2, trackTop + view, 0x14000000);
        graphics.fill(trackX, barY, trackX + 2, barY + barH, 0x99D4802A);
    }

    private void drawRow(GuiGraphics graphics, MaidEntry entry, int index, int x, int y, int w, int h,
                         int mouseX, int mouseY) {
        boolean expanded = isExpanded(entry);
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        int bg = expanded ? cRowSelected : hovered ? cRowHover : (index % 2 == 0 ? cRowAltA : cRowAltB);
        graphics.fill(x, y, x + w, y + h, bg);
        if (expanded) {
            graphics.fill(x, y, x + 2, y + h, cAccent);
        }

        // 头像：直接渲染女仆实体（跟随模型）
        int ax = x + 5;
        int ay = y + 5;
        drawAvatar(graphics, entry, ax, ay, AVATAR);
        boolean muted = MaidMuteStore.isMuted(entry.uuid());
        boolean speaking = MaidSpeechTracker.isSpeaking(entry.uuid());
        if (muted || speaking) {
            drawSpeakingBadge(graphics, ax, ay, muted);
            if (inRect(mouseX, mouseY, ax - 2, ay - 2, 9)) {
                this.hoveredMute = entry;
            }
        }

        int textX = ax + AVATAR + 6;
        int btnY = y + 9;
        boolean foxAvailable = hasFoxdream(entry);
        int tpX = tpButtonX();
        int goX = goButtonX();
        int guiX = guiButtonX();
        int foxX = foxButtonX();
        int storeX = storeButtonX();
        boolean tpHovered = inRect(mouseX, mouseY, tpX, btnY, TP_SIZE);
        boolean goHovered = inRect(mouseX, mouseY, goX, btnY, TP_SIZE);
        boolean guiHovered = inRect(mouseX, mouseY, guiX, btnY, TP_SIZE);
        boolean foxHovered = foxAvailable && inRect(mouseX, mouseY, foxX, btnY, TP_SIZE);
        boolean storeHovered = inRect(mouseX, mouseY, storeX, btnY, TP_SIZE);
        if (storeHovered) {
            this.hoveredStore = entry;
        }
        if (tpHovered) {
            this.hoveredTp = entry;
        }
        if (goHovered) {
            this.hoveredGo = entry;
        }
        if (guiHovered) {
            this.hoveredGui = entry;
        }
        if (foxHovered) {
            this.hoveredFox = entry;
        }

        int contentRight = rowContentRight(entry);
        int healthW = 34;
        int healthX = contentRight - healthW;

        // 第一行：名字 +（狐梦成长阶段标签）+ 距离
        String dist = distanceText(entry);
        int distW = panelWidth(dist);
        int nameMax = contentRight - distW - 8 - textX;
        String name = trimToWidth(entry.name(), nameMax);
        panelText(graphics, name, textX, y + 5, cText, false);

        int stage = foxdreamStage(entry);
        int cursorX = textX + panelWidth(name) + 5;
        if (stage >= 0) {
            // 成长阶段：保留自己的彩色底 + 白字
            String stageLabel = foxStageLabel(stage);
            int chipW = panelWidth(stageLabel) + 6;
            if (cursorX + chipW <= contentRight - distW - 4) {
                graphics.fill(cursorX, y + 4, cursorX + chipW, y + 14, foxStageColor(stage));
                panelText(graphics, stageLabel, cursorX + 3, y + 5, 0xFFFFFFFF, false);
                cursorX += chipW + 4;
            }
            // 初始性格：独立文本，不共用底色，颜色跟随日间 / 夜间主题
            var fox = entry.foxStats();
            if (fox != null && fox.personality() != null && !fox.personality().isBlank()) {
                String tag = tr("fairymoon3_maid_list.fox.personality", fox.personality());
                if (cursorX + panelWidth(tag) <= contentRight - distW - 4) {
                    panelText(graphics, tag, cursorX, y + 5, cSub, false);
                }
            }
        }
        panelText(graphics, dist, contentRight - distW, y + 5, cSub, false);

        // 第二行：状态点 + 状态 + 职业 + 等级
        MaidEntry.Status status = entry.status();
        graphics.fill(textX, y + 19, textX + 4, y + 23, status.color);
        String statusText = tr(status.translationKey);
        if (entry.sittingWithAggro()) {
            statusText = statusText + tr("fairymoon3_maid_list.status.sitting.aggro");
        }
        panelText(graphics, statusText, textX + 7, y + 18, status.color, false);

        int used = textX + 7 + panelWidth(statusText) + 6;
        String detail = entry.loaded()
                ? taskName(entry)
                : tr("fairymoon3_maid_list.detail.chunk", entry.chunkX(), entry.chunkZ());
        String trimmed = trimToWidth(detail, healthX - 8 - used);
        panelText(graphics, trimmed, used, y + 18, cSub, false);

        // 血条
        if (entry.loaded()) {
            drawHealthBar(graphics, entry, healthX, y + 19, healthW, 3);
        } else {
            graphics.fill(healthX, y + 19, healthX + healthW, y + 22, cBtnBg);
        }

        // 按钮：收纳 / 狐梦手册 / 女仆背包 / 前往她身边 / 召唤到身边
        drawIconButton(graphics, storeX, btnY, TP_SIZE, storeHovered, 0xFFC9A227, ICON_SHRINE);
        if (foxAvailable) {
            drawIconButton(graphics, foxX, btnY, TP_SIZE, foxHovered, C_FOX, ICON_FOX);
        }
        drawIconButton(graphics, guiX, btnY, TP_SIZE, guiHovered, 0xFF8FD1E8, ICON_BAG);
        drawIconButton(graphics, goX, btnY, TP_SIZE, goHovered, 0xFFB39DFF, ICON_EYE);
        drawIconButton(graphics, tpX, btnY, TP_SIZE, tpHovered, cAccent, ICON_BELL);
        drawCooldownOverlay(graphics, goX, btnY, TP_SIZE, this.cdGoUntil);
        drawCooldownOverlay(graphics, tpX, btnY, TP_SIZE, this.cdSummonUntil);

        if (expanded) {
            drawDetails(graphics, entry, textX, y + ROW_H - 2, contentRight, mouseX, mouseY);
        }
    }

    /** 头像：优先渲染真实女仆模型，失败/未加载时退回职业图标。 */
    /**
     * 正在说话的小标记：头像左上角一个 9×9 的小底板，里面三根随音量跳动的竖条。
     *
     * @param ax 头像左上角 X
     * @param ay 头像左上角 Y
     */
    private void drawSpeakingBadge(GuiGraphics graphics, int ax, int ay, boolean muted) {
        int size = 9;
        int bx = ax - 2;
        int by = ay - 2;
        graphics.fill(bx, by, bx + size, by + size, 0xE0201C16);
        graphics.fill(bx, by, bx + size, by + 1, muted ? 0xFF9AA0AA : 0xFFFFD98A);
        if (muted) {
            // 静音：灰色音量条 + 红色斜杠
            for (int i = 0; i < 3; i++) {
                int col = bx + 2 + i * 2;
                graphics.fill(col, by + 4 + (i % 2), col + 1, by + size - 2, 0xFF7E838C);
            }
            for (int i = 0; i < 5; i++) {
                graphics.fill(bx + 1 + i, by + size - 2 - i, bx + 2 + i, by + size - 1 - i, 0xFFFF6B6B);
            }
        } else {
            long t = System.currentTimeMillis();
            for (int i = 0; i < 3; i++) {
                double phase = t / 130.0 + i * 1.3;
                int barH = 2 + (int) Math.round(4.0 * Math.abs(Math.sin(phase)));
                int col = bx + 2 + i * 2;
                int bottom = by + size - 2;
                graphics.fill(col, bottom - barH, col + 1, bottom, 0xFFFFE9A3);
            }
        }
    }

    private void drawAvatar(GuiGraphics graphics, MaidEntry entry, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, cAvatarBg);
        boolean painted = false;

        EntityMaid maid = clientMaid(entry.uuid());
        if (maid != null) {
            painted = renderMaidPortrait(graphics, maid, x, y, size);
        }
        if (!painted && !entry.taskIcon().isEmpty()) {
            graphics.renderItem(entry.taskIcon(), x + (size - 16) / 2, y + (size - 16) / 2 - 1);
        }

        // 好感等级色条
        graphics.fill(x, y + size - 2, x + size, y + size, favorColor(entry.favorLevel()));
    }

    private EntityMaid clientMaid(UUID id) {
        return this.clientMaids.get(id);
    }

    /**
     * 每帧刷一次「UUID -> 客户端女仆实体」的索引。
     *
     * <p>{@code ClientLevel#getEntities()} 是 protected 的，拿不到按 UUID 的查询接口，
     * 所以自己走一遍渲染实体列表建 map。客户端实体数量有限，一次遍历可以接受。</p>
     */
    private void refreshClientMaids() {
        this.clientMaids.clear();
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity instanceof EntityMaid maid && maid.isAlive()) {
                this.clientMaids.put(maid.getUUID(), maid);
            }
        }
    }

    /**
     * 在 GUI 里渲染一个女仆立绘。
     *
     * <p>用的是原版背包界面的那套入口（{@code InventoryScreen#renderEntityInInventory}），
     * 它会自己处理光照与投影，只需要给出缩放和旋转。</p>
     */
    private boolean renderMaidPortrait(GuiGraphics graphics, EntityMaid maid, int x, int y, int size) {
        float bodyRot = maid.yBodyRot;
        float yRot = maid.getYRot();
        float xRot = maid.getXRot();
        float headRotO = maid.yHeadRotO;
        float headRot = maid.yHeadRot;
        try {
            float yaw = 200.0F;
            maid.yBodyRot = yaw;
            maid.setYRot(yaw);
            maid.setXRot(0.0F);
            maid.yHeadRot = yaw;
            maid.yHeadRotO = yaw;

            float centerX = x + size / 2.0F;
            float centerY = y + size / 2.0F + size * 0.08F;
            float scale = size * 0.5F;
            Vector3f offset = new Vector3f(0.0F, maid.getBbHeight() * 0.5F, 0.0F);
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);

            InventoryScreen.renderEntityInInventory(graphics, centerX, centerY, scale, offset, rotation, null, maid);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            maid.yBodyRot = bodyRot;
            maid.setYRot(yRot);
            maid.setXRot(xRot);
            maid.yHeadRotO = headRotO;
            maid.yHeadRot = headRot;
        }
    }

    /** 冷却遮罩：从底部向上覆盖，顶部一条高亮线。 */
    /** 手动刷新时，在列表区域扫过一道光带，给玩家"列表已刷新"的反馈。 */
    private void drawRefreshAnimation(GuiGraphics graphics) {
        long now = System.currentTimeMillis();
        if (this.refreshAnimStart <= 0L || now >= this.refreshAnimUntil) {
            return;
        }
        float progress = (now - this.refreshAnimStart) / (float) REFRESH_ANIM_MS;
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        int lx = listLeft();
        int rx = listRight();
        int top = listTop();
        int bottom = listBottom();
        if (bottom <= top) {
            return;
        }
        int band = 18;
        int center = top + (int) ((bottom - top + band) * progress) - band / 2;
        int from = Math.max(top, center - band / 2);
        int to = Math.min(bottom, center + band / 2);
        if (to > from) {
            graphics.fill(lx, from, rx, to, 0x26D4802A);
            int edge = Math.max(from, Math.min(to - 1, center));
            graphics.fill(lx, edge, rx, Math.min(to, edge + 2), 0x66D4802A);
        }
    }

    private void drawCooldownOverlay(GuiGraphics graphics, int x, int y, int size, long until) {
        drawCooldownOverlay(graphics, x, y, size, size, until, TP_COOLDOWN_MS);
    }

    private void drawCooldownOverlay(GuiGraphics graphics, int x, int y, int w, int h,
                                     long until, long totalMs) {
        long now = System.currentTimeMillis();
        if (now >= until) {
            return;
        }
        float remain = (until - now) / (float) totalMs;
        remain = Math.max(0.0F, Math.min(1.0F, remain));
        int barH = Math.max(1, (int) (h * remain));
        graphics.fill(x, y + h - barH, x + w, y + h, cCoolMask);
        graphics.fill(x, y + h - barH, x + w, y + h - barH + 1, cHiLine);
    }

    /** 实心圆（逐行填充），用于画太阳 / 月亮图标。 */
    private static void fillDisc(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /**
     * 主题切换按钮：直接画太阳 / 月亮图标，不再加底色块。
     *
     * <p>白天用深一档的琥珀色太阳 + 灰蓝月亮，夜间用亮色，两种主题下都能看清。</p>
     */
    private void drawThemeButton(GuiGraphics graphics, int x, int y, int size, boolean hovered) {
        if (hovered) {
            int c = cAccent;
            graphics.fill(x, y, x + size, y + 1, c);
            graphics.fill(x, y + size - 1, x + size, y + size, c);
            graphics.fill(x, y, x + 1, y + size, c);
            graphics.fill(x + size - 1, y, x + size, y + size, c);
        }
        int cx = x + size / 2;
        int cy = y + size / 2;
        switch (this.currentTheme) {
            case DARK -> {
                // 月亮：圆盘 + 用表头底色挖出月牙
                int moon = 0xFFE8ECF7;
                fillDisc(graphics, cx, cy, 5, moon);
                // 挖空必须不透明，否则玻璃主题下挖不干净
                fillDisc(graphics, cx + 3, cy - 2, 5, cHeader | 0xFF000000);
            }
            case GLASS -> {
                // 玻璃：半透明方块 + 斜向高光
                int frame = 0xFF9FD8E8;
                graphics.fill(cx - 5, cy - 5, cx + 5, cy - 4, frame);
                graphics.fill(cx - 5, cy + 4, cx + 5, cy + 5, frame);
                graphics.fill(cx - 5, cy - 5, cx - 4, cy + 5, frame);
                graphics.fill(cx + 4, cy - 5, cx + 5, cy + 5, frame);
                graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0x66FFFFFF);
                graphics.fill(cx - 3, cy + 1, cx + 1, cy + 2, 0xCCFFFFFF);
                graphics.fill(cx - 2, cy, cx - 1, cy + 1, 0xCCFFFFFF);
            }
            default -> {
                // 太阳：圆盘 + 四向光芒
                int sun = 0xFFE0952B;
                fillDisc(graphics, cx, cy, 4, sun);
                graphics.fill(cx, cy - 8, cx + 1, cy - 6, sun);
                graphics.fill(cx, cy + 6, cx + 1, cy + 8, sun);
                graphics.fill(cx - 8, cy, cx - 6, cy + 1, sun);
                graphics.fill(cx + 6, cy, cx + 8, cy + 1, sun);
            }
        }
    }

    /** 统一的小按钮：底色 + 顶部描边 + 缩小后的物品图标。 */
    private void drawIconButton(GuiGraphics graphics, int bx, int by, int size,
                                boolean hovered, int accent, ItemStack icon) {
        graphics.fill(bx, by, bx + size, by + size, hovered ? accent : cBtnBg);
        graphics.fill(bx, by, bx + size, by + 1, hovered ? cHiLine : cBtnBorder);

        if (icon.isEmpty()) {
            return;
        }
        drawScaledItem(graphics, icon, bx + 2, by + 2, size - 4);
    }

    /** 把 16×16 的物品图标缩放到指定尺寸绘制。 */
    private void drawScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) {
            return;
        }
        float scale = size / 16.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    /** 客户端是否装了狐梦伴岁。 */
    private static boolean foxdreamLoaded() {
        try {
            return ModList.get().isLoaded("foxdream");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 面板标题旁显示的版本号，方便确认玩家跑的是哪一版。 */
    private static String modVersion() {
        try {
            return ModList.get().getModContainerById("fairymoon3_maid_list")
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("");
        } catch (Throwable t) {
            return "";
        }
    }

    /** 这只女仆是否是狐梦成长体系（且客户端装了对应 mod）。 */
    private boolean hasFoxdream(MaidEntry entry) {        return entry.foxdreamStage() >= 0 && foxdreamLoaded();
    }

    private int foxdreamStage(MaidEntry entry) {
        return hasFoxdream(entry) ? entry.foxdreamStage() : -1;
    }

    /** 成长阶段的底色：幼年粉、青年天蓝、成年亮金。 */
    private int foxStageColor(int stage) {
        return switch (stage) {
            case 0 -> 0xFFF26D9C;
            case 1 -> 0xFF5FA8E0;
            case 2 -> 0xFFE8B93D;
            default -> 0xFF8A8F99;
        };
    }

    private String foxStageLabel(int stage) {
        String key = "fairymoon3_maid_list.foxdream.stage." + stage;
        if (I18n.exists(key)) {
            return I18n.get(key);
        }
        return tr("fairymoon3_maid_list.foxdream.stage.other", stage);
    }

    private static boolean inRect(double mouseX, double mouseY, int x, int y, int size) {
        return mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
    }

    private static boolean inRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void drawHealthBar(GuiGraphics graphics, MaidEntry entry, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, cHealthBg);
        int fill = (int) (w * entry.healthRatio());
        if (fill > 0) {
            graphics.fill(x, y, x + fill, y + h, healthColor(entry.healthRatio()));
        }
    }

    private static final int SIT_BTN_W = 46;
    private static final int SIT_BTN_H = 13;
    private int[] sitButtonRect(int rightEdge, int detailTop) {
        return new int[]{rightEdge - SIT_BTN_W, detailTop + 2, SIT_BTN_W, SIT_BTN_H};
    }

    private void drawTextButton(GuiGraphics graphics, int bx, int by, int bw, int bh,
                                String label, boolean hovered) {
        graphics.fill(bx, by, bx + bw, by + bh, hovered ? cAccent : cBtnBg);
        graphics.fill(bx, by, bx + bw, by + 1, hovered ? 0xFFFFFFFF : cBtnBorder);
        int color = hovered ? 0xFFFFFFFF : cText;
        panelText(graphics, label, bx + (bw - panelWidth(label)) / 2, by + 3, color, false);
    }

    private static MutableComponent dimensionComponent(ResourceLocation id) {
        return Component.translatable(id.toLanguageKey("dimension"));
    }

    private void drawDetails(GuiGraphics graphics, MaidEntry entry, int x, int y,
                             int rightEdge, int mouseX, int mouseY) {
        int lineY = y + 4;
        int gap = 13;

        // 生命（绿）+ 好感♥（红）
        Component vitals = Component.translatable("fairymoon3_maid_list.detail.hp",
                        entry.health(), entry.maxHealth())
                .withStyle(style -> style.withColor(cHp))
                .append(Component.literal("    "))
                .append(Component.translatable("fairymoon3_maid_list.detail.favor",
                                entry.favorability(), entry.favorLevel())
                        .withStyle(style -> style.withColor(cFavor)));
        panelText(graphics, vitals, x, lineY, 0xFFFFFFFF, false);

        // 坐标（黄）+ 维度（金橙）
        lineY += gap;
        MutableComponent posPart = entry.loaded()
                ? Component.translatable("fairymoon3_maid_list.detail.pos",
                        Math.round(entry.x()), Math.round(entry.y()), Math.round(entry.z()))
                : Component.translatable("fairymoon3_maid_list.detail.pos.chunk",
                        entry.chunkX(), entry.chunkZ());
        posPart = posPart.withStyle(style -> style.withColor(cPos));
        Component line2 = posPart
                .append(Component.literal(" · "))
                .append(dimensionComponent(entry.dimension())
                        .withStyle(style -> style.withColor(cDimText)));
        panelText(graphics, line2, x, lineY, 0xFFFFFFFF, false);

        // 背包 / 模型（保持灰色）
        lineY += gap;
        Component gear = entry.hasBackpack()
                ? Component.translatable("fairymoon3_maid_list.detail.gear",
                entry.backpackName().isEmpty() ? tr("fairymoon3_maid_list.unknown") : entry.backpackName(),
                modelName(entry))
                : Component.translatable("fairymoon3_maid_list.detail.gear_none", modelName(entry));
        panelText(graphics, gear, x, lineY, cDim, false);

        // 狐梦成长数值（只对已转化的女仆）
        foxdreamStage(entry);
        var fox = entry.foxStats();
        if (fox != null) {
            // 先空一行，再画一条分割线，然后是简报标题
            lineY += gap;
            graphics.fill(x, lineY + 4, Math.max(x + 20, rightEdge - SIT_BTN_W - 6), lineY + 5, cBorder);
            lineY += gap;
            panelText(graphics, tr("fairymoon3_maid_list.fox.brief"), x, lineY, cAccent, false);

            lineY += gap;
            MutableComponent l4 = Component.translatable("fairymoon3_maid_list.fox.sleep", fox.sleep())
                    .withStyle(style -> style.withColor(0xFF3F9FD6))
                    .append(Component.literal("   "))
                    .append(Component.translatable("fairymoon3_maid_list.fox.satisfaction", fox.satisfaction())
                            .withStyle(style -> style.withColor(0xFFC97A16)))
                    .append(Component.literal("   "))
                    .append(Component.translatable("fairymoon3_maid_list.fox.mood", fox.mood())
                            .withStyle(style -> style.withColor(0xFFB255C8)));
            panelText(graphics, l4, x, lineY, 0xFFFFFFFF, false);

            lineY += gap;
            MutableComponent l5 = Component.translatable("fairymoon3_maid_list.fox.trust",
                            fox.trustLevel(), fox.trustPercent())
                    .withStyle(style -> style.withColor(cText))
                    .append(Component.literal("    "))
                    .append(Component.translatable("fairymoon3_maid_list.fox.favor",
                                    fox.favorLevel(), fox.favorPoints(), fox.favorCap())
                            .withStyle(style -> style.withColor(cSub)))
                    .append(Component.literal("    "))
                    .append(Component.translatable("fairymoon3_maid_list.fox.days", fox.daysText())
                            .withStyle(style -> style.withColor(cSub)));
            panelText(graphics, l5, x, lineY, 0xFFFFFFFF, false);

            lineY += gap;
            MutableComponent l6 = Component.translatable("fairymoon3_maid_list.fox.meals")
                    .withStyle(style -> style.withColor(cSub));
            for (int i = 0; i < 3; i++) {
                boolean eaten = fox.mealEaten(i);
                l6.append(Component.literal("   "))
                        .append(Component.translatable("fairymoon3_maid_list.fox.meal." + i)
                                .withStyle(style -> style.withColor(cText)))
                        .append(Component.literal(" "))
                        .append(Component.literal(tr(eaten
                                        ? "fairymoon3_maid_list.fox.check"
                                        : "fairymoon3_maid_list.fox.cross"))
                                .withStyle(style -> style.withColor(eaten ? 0xFF2E8B2E : 0xFFD03030)));
            }
            panelText(graphics, l6, x, lineY, 0xFFFFFFFF, false);
        }

        // 坐下 / 起身 按钮
        int[] r = sitButtonRect(rightEdge, y);
        boolean hovered = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        drawTextButton(graphics, r[0], r[1], r[2], r[3],
                tr(entry.sitting() ? "fairymoon3_maid_list.sit.standup" : "fairymoon3_maid_list.sit.sitdown"),
                hovered);
        drawCooldownOverlay(graphics, r[0], r[1], r[2], r[3], cdOf(this.cdSit, entry), SIT_COOLDOWN_MS);
    }

    private int favorColor(int level) {
        return switch (Math.max(0, Math.min(5, level))) {
            case 0 -> 0xFF6C7484;
            case 1 -> 0xFF7ED07E;
            case 2 -> 0xFF8FD1E8;
            case 3 -> 0xFF9C8CF0;
            case 4 -> cHiLine;
            default -> 0xFFF26D9C;
        };
    }

    private int healthColor(float ratio) {
        if (ratio > 0.6F) {
            return 0xFF6FD66F;
        }
        if (ratio > 0.3F) {
            return 0xFFE8C23D;
        }
        return 0xFFE5544B;
    }

    private String trimToWidth(String text, int maxWidth) {
        if (maxWidth <= 0 || text == null || text.isEmpty()) {
            return "";
        }
        if (panelWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int tail = panelWidth(ellipsis);
        StringBuilder out = new StringBuilder();
        int acc = 0;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int cw = panelWidth(ch);
            if (acc + cw > maxWidth - tail) {
                break;
            }
            out.append(ch);
            acc += cw;
        }
        return out.append(ellipsis).toString();
    }

    // ---------------------------------------------------------------- 交互

    private MaidEntry rowAt(double mouseX, double mouseY) {
        if (mouseX < listLeft() || mouseX > listRight() || mouseY < listTop() || mouseY > listBottom()) {
            return null;
        }
        int y = listTop() - (int) this.scroll;
        for (MaidEntry entry : visibleEntries()) {
            int h = rowHeight(entry);
            if (mouseY >= y && mouseY <= y + h) {
                return entry;
            }
            y += h + ROW_GAP;
        }
        return null;
    }

    private int rowTopOf(MaidEntry target) {
        int y = listTop() - (int) this.scroll;
        for (MaidEntry entry : visibleEntries()) {
            if (entry.uuid().equals(target.uuid())) {
                return y;
            }
            y += rowHeight(entry) + ROW_GAP;
        }
        return Integer.MIN_VALUE;
    }

    private boolean isOverTeleportButton(MaidEntry entry, double mouseX, double mouseY) {
        int top = rowTopOf(entry);
        if (top == Integer.MIN_VALUE) {
            return false;
        }
        return inRect(mouseX, mouseY, tpButtonX(), top + 9, TP_SIZE);
    }

    private boolean isOverGuiButton(MaidEntry entry, double mouseX, double mouseY) {
        int top = rowTopOf(entry);
        if (top == Integer.MIN_VALUE) {
            return false;
        }
        return inRect(mouseX, mouseY, guiButtonX(), top + 9, TP_SIZE);
    }

    private boolean isOverGoButton(MaidEntry entry, double mouseX, double mouseY) {
        int top = rowTopOf(entry);
        if (top == Integer.MIN_VALUE) {
            return false;
        }
        return inRect(mouseX, mouseY, goButtonX(), top + 9, TP_SIZE);
    }

    private boolean isOverStoreButton(MaidEntry entry, double mouseX, double mouseY) {
        int top = rowTopOf(entry);
        if (top == Integer.MIN_VALUE) {
            return false;
        }
        return inRect(mouseX, mouseY, storeButtonX(), top + 9, TP_SIZE);
    }

    private boolean isOverFoxButton(MaidEntry entry, double mouseX, double mouseY) {
        if (!hasFoxdream(entry)) {
            return false;
        }
        int top = rowTopOf(entry);
        if (top == Integer.MIN_VALUE) {
            return false;
        }
        return inRect(mouseX, mouseY, foxButtonX(), top + 9, TP_SIZE);
    }

    /** 右上角关闭按钮的 X。 */
    private int closeButtonX() {
        return panelLeft() + PANEL_W - 4 - CLOSE_SIZE;
    }

    private int closeButtonY() {
        return panelTop() + 6;
    }

    /** 标题栏「全部召唤」按钮的 X。 */
    private int summonAllButtonX() {
        String count = Component.translatable("fairymoon3_maid_list.count", this.entries.size()).getString();
        return closeButtonX() - 8 - panelWidth(count) - 6 - 16;
    }

    /** 标题栏「主题切换」按钮的 X。 */
    private int themeButtonX() {
        return summonAllButtonX() - 4 - 16;
    }

    /** 标题栏「全部召唤」按钮的判定区域。 */
    private boolean isOverSummonAllButton(double mouseX, double mouseY) {
        int allY = panelTop() + (HEADER_H - 16) / 2;
        return inRect(mouseX, mouseY, summonAllButtonX(), allY, 16);
    }

    /** 标题栏「主题切换」按钮的判定区域。 */
    private boolean isOverThemeButton(double mouseX, double mouseY) {
        int allY = panelTop() + (HEADER_H - 16) / 2;
        return inRect(mouseX, mouseY, themeButtonX(), allY, 16);
    }

    @Override
    public void tick() {
        super.tick();
        // 定时刷新：狐梦转化这类状态要能"实时"出现
        if (++this.refreshTimer >= REFRESH_INTERVAL) {
            this.refreshTimer = 0;
            PacketDistributor.sendToServer(new MaidListRequestPayload());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && inRect(mouseX, mouseY, closeButtonX(), closeButtonY(), CLOSE_SIZE)) {
            this.onClose();
            return true;
        }
        if (button == 0 && this.versionRect != null
                && inRect(mouseX, mouseY, versionRect[0], versionRect[1], versionRect[2], versionRect[3])) {
            PanelConfig.toggleVanillaFont();
            return true;
        }
        if (button == 1 && isOverShrine(mouseX, mouseY)) {
            ClientMaidStorage.requestOpen();
            return true;
        }
        if (button == 0 && isOverShrine(mouseX, mouseY)) {
            playMeow();
            spawnStars(mouseX, mouseY);
            return true;
        }
        if (button == 0 && isOverThemeButton(mouseX, mouseY)) {
            if (!onCooldown(this.cdThemeUntil)) {
                this.cdThemeUntil = System.currentTimeMillis() + THEME_COOLDOWN_MS;
                this.currentTheme = PanelConfig.cycleTheme();
                applyTheme();
            }
            return true;
        }
        if (button == 0 && isOverSummonAllButton(mouseX, mouseY)) {
            if (!onCooldown(this.cdSummonAllUntil)) {
                this.cdSummonAllUntil = System.currentTimeMillis() + TP_COOLDOWN_MS;
                PacketDistributor.sendToServer(new MaidSummonAllPayload());
            }
            return true;
        }
        // 右键列表中的女仆：弹出操作菜单
        if (button == 1) {
            MaidEntry hit = rowAt(mouseX, mouseY);
            if (hit != null) {
                this.contextMenuEntry = hit;
                this.contextMenuX = (int) mouseX;
                this.contextMenuY = (int) mouseY;
                return true;
            }
        }
        // 左键：处理弹出的菜单
        if (button == 0 && this.contextMenuEntry != null) {
            int[] rect = contextMenuRect();
            boolean inMenu = rect != null
                    && inRect(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]);
            MaidEntry target = this.contextMenuEntry;
            this.contextMenuEntry = null;
            if (inMenu) {
                ClientMaidAttackData.requestOpen(target.uuid());
            }
            return true;
        }
        if (button == 0) {
            MaidEntry hit = rowAt(mouseX, mouseY);
            if (hit != null) {
                int rowTop = rowTopOf(hit);
                if (rowTop != Integer.MIN_VALUE
                        && inRect(mouseX, mouseY, listLeft() + 5 - 2, rowTop + 5 - 2, 9)) {
                    MaidMuteStore.toggle(hit.uuid());
                    return true;
                }
                if (isOverStoreButton(hit, mouseX, mouseY)) {
                    PacketDistributor.sendToServer(new MaidStorePayload(hit.uuid()));
                    return true;
                }
                if (isOverFoxButton(hit, mouseX, mouseY)) {
                    PacketDistributor.sendToServer(new MaidFoxdreamPayload(hit.uuid()));
                    return true;
                }
                if (isOverGuiButton(hit, mouseX, mouseY)) {
                    PacketDistributor.sendToServer(new MaidOpenGuiPayload(hit.uuid()));
                    return true;
                }
                if (isOverGoButton(hit, mouseX, mouseY)) {
                    if (!onCooldown(this.cdGoUntil)) {
                        this.cdGoUntil = System.currentTimeMillis() + TP_COOLDOWN_MS;
                        PacketDistributor.sendToServer(new MaidTeleportToPayload(hit.uuid()));
                    }
                    return true;
                }
                if (isOverTeleportButton(hit, mouseX, mouseY)) {
                    if (!onCooldown(this.cdSummonUntil)) {
                        this.cdSummonUntil = System.currentTimeMillis() + TP_COOLDOWN_MS;
                        PacketDistributor.sendToServer(new MaidTeleportPayload(hit.uuid()));
                    }
                    return true;
                }
                if (isExpanded(hit)) {
                    int top = rowTopOf(hit);
                    if (top != Integer.MIN_VALUE) {
                        int[] r = sitButtonRect(rowContentRight(hit), top + ROW_H - 2);
                        if (inRect(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                            if (!onCooldown(cdOf(this.cdSit, hit))) {
                                this.cdSit.put(hit.uuid(), System.currentTimeMillis() + SIT_COOLDOWN_MS);
                                PacketDistributor.sendToServer(new MaidSitTogglePayload(hit.uuid()));
                            }
                            return true;
                        }
                    }
                }
                this.selected = isExpanded(hit) ? null : hit.uuid();
                clampScroll();
                return true;
            }
        }
        // 中键：抓起一行开始拖动重排
        if (button == 2) {
            MaidEntry hit = rowAt(mouseX, mouseY);
            if (hit != null) {
                this.dragging = hit.uuid();
                this.dragTarget = hit;
                this.dragMouseY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging != null && button == 2) {
            this.dragMouseY = mouseY;
            MaidEntry over = rowAt(mouseX, mouseY);
            if (over != null) {
                this.dragTarget = over;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 2 && this.dragging != null) {
            UUID moved = this.dragging;
            MaidEntry target = this.dragTarget;
            this.dragging = null;
            this.dragTarget = null;

            if (target != null && !target.uuid().equals(moved)) {
                List<MaidEntry> all = orderedAll();
                List<UUID> ids = new ArrayList<>(all.size());
                for (MaidEntry entry : all) {
                    ids.add(entry.uuid());
                }
                int targetIndex = ids.indexOf(target.uuid());
                if (targetIndex >= 0) {
                    MaidOrderStore.applyMove(ids, moved, targetIndex);
                    clampScroll();
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listLeft() && mouseX <= listRight() && mouseY >= listTop() && mouseY <= listBottom()) {
            this.scroll -= scrollY * 20.0D;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
