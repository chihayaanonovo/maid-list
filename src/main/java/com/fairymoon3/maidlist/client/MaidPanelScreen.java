package com.fairymoon3.maidlist.client;

import com.github.tartaricacid.touhoulittlemaid.client.resource.models.MaidModels;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.fairymoon3.maidlist.network.MaidEntry;
import com.fairymoon3.maidlist.network.MaidFoxdreamPayload;
import com.fairymoon3.maidlist.network.MaidListRequestPayload;
import com.fairymoon3.maidlist.network.MaidOpenGuiPayload;
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
        return text.copy().withStyle(style -> style.withFont(PANEL_FONT));
    }

    private static Component panelStyle(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(PANEL_FONT));
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

    // ---------------------------------------------------------------- 配色
    private static final int C_PANEL = 0xF01A1D24;
    private static final int C_BORDER = 0xFF343B4A;
    private static final int C_HEADER = 0xFF20242D;
    private static final int C_ACCENT = 0xFFE8A33D;
    private static final int C_TEXT = 0xFFEDF1F7;
    private static final int C_SUB = 0xFF9AA3B2;
    private static final int C_DIM = 0xFF7F8899;
    /** 狐梦联动的粉色。 */
    private static final int C_FOX = 0xFFF26D9C;

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

    /** 面板开着时每隔若干 tick 自动刷新一次，保证狐梦转化等状态能实时出现。 */
    private int refreshTimer;
    private static final int REFRESH_INTERVAL = 60;

    /** 三个传送按钮各自独立的冷却（毫秒）。 */
    private static final long TP_COOLDOWN_MS = 2000L;
    private long cdSummonUntil;
    private long cdGoUntil;
    private long cdSummonAllUntil;

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

    private int listHeight() {
        return listBottom() - listTop();
    }

    private boolean isExpanded(MaidEntry entry) {
        return this.selected != null && this.selected.equals(entry.uuid());
    }

    private int rowHeight(MaidEntry entry) {
        return ROW_H + (isExpanded(entry) ? DETAIL_H : 0);
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
        int sx = listLeft() + 4;
        int sy = panelTop() + HEADER_H + 5;
        int sw = PANEL_W - 16 - 54;

        this.searchBox = new EditBox(this.font, sx, sy, sw, 14,
                Component.translatable("fairymoon3_maid_list.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(32);
        this.searchBox.setHint(Component.translatable("fairymoon3_maid_list.search.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        this.searchBox.setTextColor(C_TEXT);
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
        graphics.fill(0, 0, this.width, this.height, 0x77000000);

        refreshClientMaids();
        this.hoveredSummonAll = false;
        int left = panelLeft();
        int top = panelTop();
        int right = left + PANEL_W;
        int bottom = top + PANEL_H;

        // 外框与背景
        graphics.fill(left + 3, top + 3, right + 3, bottom + 3, 0x55000000);
        graphics.fill(left, top, right, bottom, C_PANEL);
        graphics.fill(left, top, right, top + 1, C_BORDER);
        graphics.fill(left, bottom - 1, right, bottom, C_BORDER);
        graphics.fill(left, top, left + 1, bottom, C_BORDER);
        graphics.fill(right - 1, top, right, bottom, C_BORDER);

        // 标题栏
        graphics.fill(left + 1, top + 1, right - 1, top + HEADER_H, C_HEADER);
        graphics.fill(left + 1, top + HEADER_H - 1, right - 1, top + HEADER_H, C_ACCENT);

        // 左上角：神龛图标 + 标题（文字与右上的计数统一垂直居中于图标中线）
        int iconSize = 16;
        int iconX = left + 6;
        int iconY = top + (HEADER_H - iconSize) / 2;
        int centerY = iconY + iconSize / 2;
        int textY = centerY - this.font.lineHeight / 2;

        drawScaledItem(graphics, ICON_SHRINE, iconX, iconY, iconSize);
        int titleX = ICON_SHRINE.isEmpty() ? left + 20 : iconX + iconSize + 4;
        Component title = Component.translatable("fairymoon3_maid_list.title");
        panelText(graphics, title, titleX, textY, C_TEXT, false);
        String version = modVersion();
        if (!version.isEmpty()) {
            panelText(graphics, "v" + version, titleX + panelWidth(title) + 6, textY, C_DIM, false);
        }
        String count = Component.translatable("fairymoon3_maid_list.count", this.entries.size()).getString();
        int countX = right - 12 - panelWidth(count);

        // 计数左侧：一键召唤所有能传送的女仆
        int allBtn = 16;
        int allX = countX - 6 - allBtn;
        int allY = iconY;
        boolean allHovered = inRect(mouseX, mouseY, allX, allY, allBtn);
        if (allHovered) {
            this.hoveredSummonAll = true;
        }
        drawIconButton(graphics, allX, allY, allBtn, allHovered, C_ACCENT, ICON_TRUMPET);
        drawCooldownOverlay(graphics, allX, allY, allBtn, this.cdSummonAllUntil);

        panelText(graphics, count, countX, textY, C_ACCENT, false);

        // 搜索栏底（与 init() 里的 EditBox 严格对齐）
        int sx = listLeft() + 4;
        int sy = top + HEADER_H + 5;
        int sw = PANEL_W - 16 - 54;
        graphics.fill(sx - 3, sy - 3, sx + sw + 3, sy + 15, 0xFF0E1015);
        graphics.fill(sx - 3, sy - 3, sx - 2, sy + 15, C_BORDER);

        // 列表
        this.hoveredTp = null;
        this.hoveredGui = null;
        this.hoveredFox = null;
        this.hoveredGo = null;
        drawList(graphics, mouseX, mouseY);

        // 底部提示（居中）
        Component hint = Component.translatable("fairymoon3_maid_list.hint");
        panelCenter(graphics, hint, (left + right) / 2, bottom - 13, C_DIM);

        super.render(graphics, mouseX, mouseY, partialTick);

        // 按钮的浮层提示放在最后画，避免被列表裁剪
        if (this.hoveredSummonAll) {
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
    }

    private void drawList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<MaidEntry> list = visibleEntries();
        int lx = listLeft();
        int rx = listRight();

        graphics.fill(lx, listTop(), rx, listBottom(), 0xFF14171D);

        if (list.isEmpty()) {
            Component empty = this.entries.isEmpty()
                    ? Component.translatable("fairymoon3_maid_list.empty")
                    : Component.translatable("fairymoon3_maid_list.empty.search");
            panelCenter(graphics, empty, (lx + rx) / 2, listTop() + 40, C_DIM);
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
                graphics.fill(lx, y, rx, y + 1, 0xFFFFE8C0);
                graphics.fill(lx, y + h - 1, rx, y + h, 0xFFFFE8C0);
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
        graphics.fill(trackX, trackTop, trackX + 2, trackTop + view, 0x22000000);
        graphics.fill(trackX, barY, trackX + 2, barY + barH, 0x88E8A33D);
    }

    private void drawRow(GuiGraphics graphics, MaidEntry entry, int index, int x, int y, int w, int h,
                         int mouseX, int mouseY) {
        boolean expanded = isExpanded(entry);
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        int bg = expanded ? 0x33E8A33D : hovered ? 0x1FFFFFFF : (index % 2 == 0 ? 0x12FFFFFF : 0x08FFFFFF);
        graphics.fill(x, y, x + w, y + h, bg);
        if (expanded) {
            graphics.fill(x, y, x + 2, y + h, C_ACCENT);
        }

        // 头像：直接渲染女仆实体（跟随模型）
        int ax = x + 5;
        int ay = y + 5;
        drawAvatar(graphics, entry, ax, ay, AVATAR);

        int textX = ax + AVATAR + 6;
        int btnY = y + 9;
        boolean foxAvailable = hasFoxdream(entry);
        int tpX = x + w - TP_SIZE - 4;
        int goX = tpX - TP_SIZE - 3;
        int guiX = goX - TP_SIZE - 3;
        int foxX = guiX - TP_SIZE - 3;
        boolean tpHovered = inRect(mouseX, mouseY, tpX, btnY, TP_SIZE);
        boolean goHovered = inRect(mouseX, mouseY, goX, btnY, TP_SIZE);
        boolean guiHovered = inRect(mouseX, mouseY, guiX, btnY, TP_SIZE);
        boolean foxHovered = foxAvailable && inRect(mouseX, mouseY, foxX, btnY, TP_SIZE);
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

        int contentRight = (foxAvailable ? foxX : guiX) - 6;
        int healthW = 34;
        int healthX = contentRight - healthW;

        // 第一行：名字 +（狐梦成长阶段标签）+ 距离
        String dist = distanceText(entry);
        int distW = panelWidth(dist);
        int nameMax = contentRight - distW - 8 - textX;
        String name = trimToWidth(entry.name(), nameMax);
        panelText(graphics, name, textX, y + 5, C_TEXT, false);

        int stage = foxdreamStage(entry);
        if (stage >= 0) {
            String label = foxStageLabel(stage);
            int chipX = textX + panelWidth(name) + 5;
            int chipW = panelWidth(label) + 6;
            if (chipX + chipW <= contentRight - distW - 4) {
                graphics.fill(chipX, y + 4, chipX + chipW, y + 14, foxStageColor(stage));
                panelText(graphics, label, chipX + 3, y + 5, 0xFFFFFFFF, false);
            }
        }
        panelText(graphics, dist, contentRight - distW, y + 5, C_SUB, false);

        // 第二行：状态点 + 状态 + 职业 + 等级
        MaidEntry.Status status = entry.status();
        graphics.fill(textX, y + 19, textX + 4, y + 23, status.color);
        String statusText = tr(status.translationKey);
        panelText(graphics, statusText, textX + 7, y + 18, status.color, false);

        int used = textX + 7 + panelWidth(statusText) + 6;
        String detail = entry.loaded()
                ? taskName(entry)
                : tr("fairymoon3_maid_list.detail.chunk", entry.chunkX(), entry.chunkZ());
        String trimmed = trimToWidth(detail, healthX - 8 - used);
        panelText(graphics, trimmed, used, y + 18, C_SUB, false);

        // 血条
        if (entry.loaded()) {
            drawHealthBar(graphics, entry, healthX, y + 19, healthW, 3);
        } else {
            graphics.fill(healthX, y + 19, healthX + healthW, y + 22, 0xFF2A2F3A);
        }

        // 按钮：狐梦手册 / 女仆背包 / 前往她身边 / 召唤到身边
        if (foxAvailable) {
            drawIconButton(graphics, foxX, btnY, TP_SIZE, foxHovered, C_FOX, ICON_FOX);
        }
        drawIconButton(graphics, guiX, btnY, TP_SIZE, guiHovered, 0xFF8FD1E8, ICON_BAG);
        drawIconButton(graphics, goX, btnY, TP_SIZE, goHovered, 0xFFB39DFF, ICON_EYE);
        drawIconButton(graphics, tpX, btnY, TP_SIZE, tpHovered, C_ACCENT, ICON_BELL);
        drawCooldownOverlay(graphics, goX, btnY, TP_SIZE, this.cdGoUntil);
        drawCooldownOverlay(graphics, tpX, btnY, TP_SIZE, this.cdSummonUntil);

        if (expanded) {
            drawDetails(graphics, entry, textX, y + ROW_H - 2);
        }
    }

    /** 头像：优先渲染真实女仆模型，失败/未加载时退回职业图标。 */
    private void drawAvatar(GuiGraphics graphics, MaidEntry entry, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, 0xFF232732);
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
    private void drawCooldownOverlay(GuiGraphics graphics, int x, int y, int size, long until) {
        long now = System.currentTimeMillis();
        if (now >= until) {
            return;
        }
        float remain = (until - now) / (float) TP_COOLDOWN_MS;
        remain = Math.max(0.0F, Math.min(1.0F, remain));
        int h = Math.max(1, (int) (size * remain));
        graphics.fill(x, y + size - h, x + size, y + size, 0xAA0E1014);
        graphics.fill(x, y + size - h, x + size, y + size - h + 1, 0xFFFFE8C0);
    }

    /** 统一的小按钮：底色 + 顶部描边 + 缩小后的物品图标。 */
    private void drawIconButton(GuiGraphics graphics, int bx, int by, int size,
                                boolean hovered, int accent, ItemStack icon) {
        graphics.fill(bx, by, bx + size, by + size, hovered ? accent : 0xFF2A2F3A);
        graphics.fill(bx, by, bx + size, by + 1, hovered ? 0xFFFFE8C0 : 0xFF4A5265);

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

    private void drawHealthBar(GuiGraphics graphics, MaidEntry entry, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF3A3F4A);
        int fill = (int) (w * entry.healthRatio());
        if (fill > 0) {
            graphics.fill(x, y, x + fill, y + h, healthColor(entry.healthRatio()));
        }
    }

    private void drawDetails(GuiGraphics graphics, MaidEntry entry, int x, int y) {
        int lineY = y + 4;
        int gap = 13;

        Component line1 = Component.translatable("fairymoon3_maid_list.detail.vitals",
                entry.health(), entry.maxHealth(), entry.favorability(), entry.favorLevel());
        panelText(graphics, line1, x, lineY, C_SUB, false);

        lineY += gap;
        Component line2 = entry.loaded()
                ? Component.translatable("fairymoon3_maid_list.detail.pos",
                Math.round(entry.x()), Math.round(entry.y()), Math.round(entry.z()),
                dimensionName(entry.dimension()))
                : Component.translatable("fairymoon3_maid_list.detail.pos.chunk",
                entry.chunkX(), entry.chunkZ(), dimensionName(entry.dimension()));
        panelText(graphics, line2, x, lineY, C_DIM, false);

        lineY += gap;
        Component gear = entry.hasBackpack()
                ? Component.translatable("fairymoon3_maid_list.detail.gear",
                entry.backpackName().isEmpty() ? tr("fairymoon3_maid_list.unknown") : entry.backpackName(),
                modelName(entry))
                : Component.translatable("fairymoon3_maid_list.detail.gear_none", modelName(entry));
        panelText(graphics, gear, x, lineY, C_DIM, false);
    }

    private int favorColor(int level) {
        return switch (Math.max(0, Math.min(5, level))) {
            case 0 -> 0xFF6C7484;
            case 1 -> 0xFF7ED07E;
            case 2 -> 0xFF8FD1E8;
            case 3 -> 0xFF9C8CF0;
            case 4 -> 0xFFE8A33D;
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
        return inRect(mouseX, mouseY, listRight() - TP_SIZE - 4, top + 9, TP_SIZE);
    }

    private boolean isOverGuiButton(MaidEntry entry, double mouseX, double mouseY) {
        int top = rowTopOf(entry);
        if (top == Integer.MIN_VALUE) {
            return false;
        }
        return inRect(mouseX, mouseY, listRight() - TP_SIZE * 3 - 10, top + 9, TP_SIZE);
    }

    private boolean isOverGoButton(MaidEntry entry, double mouseX, double mouseY) {
        int top = rowTopOf(entry);
        if (top == Integer.MIN_VALUE) {
            return false;
        }
        return inRect(mouseX, mouseY, listRight() - TP_SIZE * 2 - 7, top + 9, TP_SIZE);
    }

    private boolean isOverFoxButton(MaidEntry entry, double mouseX, double mouseY) {
        if (!hasFoxdream(entry)) {
            return false;
        }
        int top = rowTopOf(entry);
        if (top == Integer.MIN_VALUE) {
            return false;
        }
        return inRect(mouseX, mouseY, listRight() - TP_SIZE * 4 - 13, top + 9, TP_SIZE);
    }

    /** 标题栏「全部召唤」按钮的判定区域。 */
    private boolean isOverSummonAllButton(double mouseX, double mouseY) {
        int right = panelLeft() + PANEL_W;
        String count = Component.translatable("fairymoon3_maid_list.count", this.entries.size()).getString();
        int countX = right - 12 - panelWidth(count);
        int allBtn = 16;
        int allX = countX - 6 - allBtn;
        int allY = panelTop() + (HEADER_H - 16) / 2;
        return inRect(mouseX, mouseY, allX, allY, allBtn);
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
        if (button == 0 && isOverSummonAllButton(mouseX, mouseY)) {
            if (!onCooldown(this.cdSummonAllUntil)) {
                this.cdSummonAllUntil = System.currentTimeMillis() + TP_COOLDOWN_MS;
                PacketDistributor.sendToServer(new MaidSummonAllPayload());
            }
            return true;
        }
        if (button == 0) {
            MaidEntry hit = rowAt(mouseX, mouseY);
            if (hit != null) {
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
