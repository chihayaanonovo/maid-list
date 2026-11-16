package com.fairymoon3.maidlist.client;

import com.fairymoon3.maidlist.config.PanelConfig;
import com.fairymoon3.maidlist.config.ThemeMode;
import com.fairymoon3.maidlist.network.MaidAttackGlobalPayload;
import com.fairymoon3.maidlist.network.MaidAttackReplacePayload;
import com.fairymoon3.maidlist.network.MaidAttackSetPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 女仆仇恨名单界面。
 *
 * <p>列出整合包里**所有**已注册的实体类型（含其它模组添加的），可以用中文名或实体 ID 搜索，
 * 每个实体有三态切换：友好 / 中立 / 敌对。数据直接读写车万女仆的
 * {@code InitTaskData.ATTACK_LIST}，与本体「任务设置 → 攻击面板」双向同步。</p>
 */
public class MaidAttackListScreen extends Screen {

    private static final int W = 350;
    private static final int H = 252;
    private static final int HEADER_H = 24;
    private static final int TOOLBAR_H = 20;
    private static final int ROW_H = 18;
    private static final int MODE_W = 20;
    private static final int MODE_H = 12;
    private static final int CLOSE_SIZE = 11;

    private static final String FRIENDLY = "FRIENDLY";
    private static final String NEUTRAL = "NEUTRAL";
    private static final String HOSTILE = "HOSTILE";

    /** 所有实体类型（按本地化名字排序，只建一次）。 */
    private static List<EntityType<?>> ALL_TYPES;
    private static List<String> ALL_NAMES;

    private EditBox searchBox;
    private String query = "";
    private double scroll;

    private int cPanel;
    private int cHeader;
    private int cBorder;
    private int cText;
    private int cSub;
    private int cAccent;
    private int cRowHover;
    private int cBtnBg;
    private int cSearchBg;
    private int worldDim;

    public MaidAttackListScreen() {
        super(Component.translatable("fairymoon3_maid_list.attack.title"));
    }

    public void refresh() {
        clampScroll();
    }

    @Override
    protected void init() {
        applyTheme();
        ensureTypes();

        int sx = left() + 6;
        int sy = top() + HEADER_H + 2;
        this.searchBox = new EditBox(this.font, sx, sy, W - 12, 14,
                Component.translatable("fairymoon3_maid_list.attack.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(48);
        this.searchBox.setHint(Component.translatable("fairymoon3_maid_list.attack.search")
                .withStyle(style -> style.withColor(cSub & 0xFFFFFF)));
        this.searchBox.setTextColor(cText);
        this.searchBox.setValue(this.query);
        this.searchBox.setResponder(value -> {
            this.query = value;
            clampScroll();
        });
        addRenderableWidget(this.searchBox);
    }

    private static void ensureTypes() {
        if (ALL_TYPES != null) {
            return;
        }
        List<EntityType<?>> types = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            types.add(type);
        }
        types.sort(Comparator.comparing(type -> type.getDescription().getString()));
        ALL_TYPES = types;
        ALL_NAMES = new ArrayList<>(types.size());
        for (EntityType<?> type : types) {
            ALL_NAMES.add(type.getDescription().getString().toLowerCase(Locale.ROOT));
        }
    }

    private void applyTheme() {
        ThemeMode mode = PanelConfig.themeMode();
        switch (mode) {
            case DARK -> {
                worldDim = 0x77000000;
                cPanel = 0xF01A1D24;
                cHeader = 0xFF20242D;
                cBorder = 0xFF343B4A;
                cText = 0xFFEDF1F7;
                cSub = 0xFF9AA3B2;
                cAccent = 0xFFE8A33D;
                cRowHover = 0x1FFFFFFF;
                cBtnBg = 0xFF2A2F3A;
                cSearchBg = 0xFF0E1015;
            }
            case GLASS -> {
                worldDim = 0x40000000;
                cPanel = 0x661A1D24;
                cHeader = 0x5512151B;
                cBorder = 0xFFFFFFFF;
                cText = 0xFFF7F4EE;
                cSub = 0xFFD9D3C8;
                cAccent = 0xFFFFC96B;
                cRowHover = 0x2AFFFFFF;
                cBtnBg = 0x66181C24;
                cSearchBg = 0x4D101418;
            }
            default -> {
                worldDim = 0x77000000;
                cPanel = 0xF7FBF6EC;
                cHeader = 0xFFF2E8D6;
                cBorder = 0xFFD6C5A9;
                cText = 0xFF3A3229;
                cSub = 0xFF7C6F5C;
                cAccent = 0xFFD4802A;
                cRowHover = 0x18000000;
                cBtnBg = 0xFFE9DDC6;
                cSearchBg = 0xFFEAE0CB;
            }
        }
    }

    // ------------------------------------------------------------------ 布局

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return (this.height - H) / 2;
    }

    private int listTop() {
        return top() + HEADER_H + TOOLBAR_H + 4;
    }

    private int listBottom() {
        return top() + H - (footerRows() == 2 ? 40 : 26);
    }

    private static final int FOOTER_BTN_H = 14;
    private static final String[] FOOTER_KEYS = {
            "fairymoon3_maid_list.attack.reset",
            "fairymoon3_maid_list.attack.copy",
            "fairymoon3_maid_list.attack.paste",
            "fairymoon3_maid_list.attack.sync",
            "fairymoon3_maid_list.attack.reset.all",
    };

    /** 底栏按钮宽度（按当前语言的实际文本计算）。 */
    private int[] footerWidths() {
        int[] widths = new int[FOOTER_KEYS.length];
        for (int i = 0; i < FOOTER_KEYS.length; i++) {
            widths[i] = Math.max(44, this.font.width(Component.translatable(FOOTER_KEYS[i]).getString()) + 14);
        }
        return widths;
    }

    /** 底栏需要几行（一行放不下就两行）。 */
    private int footerRows() {
        int[] widths = footerWidths();
        int total = 0;
        for (int w : widths) {
            total += w;
        }
        total += 4 * (widths.length - 1);
        return total <= W - 16 ? 1 : 2;
    }

    /** 底栏各按钮的位置 {x, y, w, h}，渲染与点击共用，保证永远对得上。 */
    private java.util.List<int[]> footerButtons() {
        int[] widths = footerWidths();
        int gap = 4;
        int l = left();
        int b = top() + H;
        java.util.List<int[]> out = new java.util.ArrayList<>();
        if (footerRows() == 1) {
            int total = gap * (widths.length - 1);
            for (int w : widths) {
                total += w;
            }
            int x = l + (W - total) / 2;
            int y = b - 22;
            for (int w : widths) {
                out.add(new int[]{x, y, w, FOOTER_BTN_H});
                x += w + gap;
            }
        } else {
            int[][] groups = {{0, 1, 2}, {3, 4}};
            int y = b - 38;
            for (int[] group : groups) {
                int total = gap * (group.length - 1);
                for (int idx : group) {
                    total += widths[idx];
                }
                int x = l + (W - total) / 2;
                for (int idx : group) {
                    out.add(new int[]{x, y, widths[idx], FOOTER_BTN_H});
                    x += widths[idx] + gap;
                }
                y += FOOTER_BTN_H + 2;
            }
        }
        return out;
    }

    private int listHeight() {
        return listBottom() - listTop();
    }

    private int closeX() {
        return left() + W - 5 - CLOSE_SIZE;
    }

    /** 过滤后的实体类型下标列表。 */
    private List<Integer> visibleIndexes() {
        List<Integer> out = new ArrayList<>();
        String needle = this.query == null ? "" : this.query.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < ALL_TYPES.size(); i++) {
            EntityType<?> type = ALL_TYPES.get(i);
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (needle.isEmpty()
                    || ALL_NAMES.get(i).contains(needle)
                    || (id != null && id.toString().toLowerCase(Locale.ROOT).contains(needle))) {
                out.add(i);
            }
        }
        // 刚改过的实体排到最前面，方便继续操作
        out.sort(Comparator.comparingInt(index -> {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(ALL_TYPES.get(index));
            return ClientMaidAttackData.recentRank(id);
        }));
        return out;
    }

    private void clampScroll() {
        double max = Math.max(0, visibleIndexes().size() * ROW_H - listHeight());
        this.scroll = Math.max(0, Math.min(this.scroll, max));
    }

    private String modeOf(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) {
            return "";
        }
        return ClientMaidAttackData.entries().getOrDefault(id, "");
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        applyTheme();
        graphics.fill(0, 0, this.width, this.height, worldDim);

        int l = left();
        int t = top();
        int r = l + W;
        int b = t + H;

        graphics.fill(l + 3, t + 3, r + 3, b + 3, 0x55000000);
        graphics.fill(l, t, r, b, cPanel);
        graphics.fill(l + 1, t + 1, r - 1, t + HEADER_H, cHeader);
        graphics.fill(l + 1, t + HEADER_H - 1, r - 1, t + HEADER_H, cAccent);

        Component title = style(Component.translatable("fairymoon3_maid_list.attack.title"), cText);
        graphics.drawString(this.font, title, l + 8, t + 8, cText, false);
        Component sub = style(Component.literal(ClientMaidAttackData.maidName()), cSub);
        graphics.drawString(this.font, sub, l + 12 + this.font.width(title), t + 8, cSub, false);

        // 关闭
        int cx = closeX();
        int cy = t + 6;
        boolean closeHover = mouseX >= cx && mouseX <= cx + CLOSE_SIZE && mouseY >= cy && mouseY <= cy + CLOSE_SIZE;
        int closeColor = closeHover ? cAccent : cSub;
        for (int i = 0; i < CLOSE_SIZE - 3; i++) {
            graphics.fill(cx + 2 + i, cy + 2 + i, cx + 3 + i, cy + 3 + i, closeColor);
            graphics.fill(cx + CLOSE_SIZE - 3 - i, cy + 2 + i, cx + CLOSE_SIZE - 2 - i, cy + 3 + i, closeColor);
        }

        // 搜索栏底
        int sx = l + 3;
        int sy = t + HEADER_H + 1;
        graphics.fill(sx, sy, r - 3, sy + 18, cSearchBg);
        graphics.fill(sx, sy, sx + 1, sy + 18, cBorder);

        // 列表
        List<Integer> visible = visibleIndexes();
        graphics.enableScissor(l + 1, listTop(), r - 1, listBottom());
        if (visible.isEmpty()) {
            Component empty = style(Component.translatable("fairymoon3_maid_list.attack.empty"), cSub);
            graphics.drawString(this.font, empty, l + (W - this.font.width(empty)) / 2, listTop() + 30, cSub, false);
        }
        for (int row = 0; row < visible.size(); row++) {
            int rowY = listTop() + row * ROW_H - (int) this.scroll;
            if (rowY + ROW_H < listTop() || rowY > listBottom()) {
                continue;
            }
            EntityType<?> type = ALL_TYPES.get(visible.get(row));
            boolean hovered = mouseX >= l + 2 && mouseX <= r - 2
                    && mouseY >= rowY && mouseY <= rowY + ROW_H - 1
                    && mouseY >= listTop() && mouseY <= listBottom();
            if (hovered) {
                graphics.fill(l + 2, rowY, r - 2, rowY + ROW_H - 1, cRowHover);
            }
            String mode = modeOf(type);
            int nameColor = mode.isEmpty() ? cText
                    : FRIENDLY.equals(mode) ? 0xFF3FA33F
                    : HOSTILE.equals(mode) ? 0xFFD03030 : cAccent;
            Component name = style(Component.literal(type.getDescription().getString()), nameColor);
            graphics.drawString(this.font, name, l + 8, rowY + 5, nameColor, false);

            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            String idText = id == null ? "?" : id.toString();
            if (this.font.width(idText) > 190) {
                idText = this.font.plainSubstrByWidth(idText, 186) + "…";
            }
            int btnStart = r - MODE_W * 3 - 10;
            Component idComp = style(Component.literal(idText), cSub);
            graphics.drawString(this.font, idComp, Math.max(l + 8 + this.font.width(name) + 8, btnStart - this.font.width(idComp) - 8),
                    rowY + 5, cSub, false);

            // 三态按钮
            drawModeButton(graphics, btnStart, rowY + 3, "FRIENDLY", FRIENDLY.equals(mode), 0xFF3FA33F, 0xFF2E8B2E, mouseX, mouseY);
            drawModeButton(graphics, btnStart + MODE_W + 2, rowY + 3, "NEUTRAL", NEUTRAL.equals(mode), 0xFF8A93A8, 0xFF5F6675, mouseX, mouseY);
            drawModeButton(graphics, btnStart + (MODE_W + 2) * 2, rowY + 3, "HOSTILE", HOSTILE.equals(mode), 0xFFD03030, 0xFFA02020, mouseX, mouseY);
        }
        graphics.disableScissor();

        // 底部按钮（按文本宽度自适应，必要时分两行）
        java.util.List<int[]> buttons = footerButtons();
        for (int i = 0; i < buttons.size() && i < FOOTER_KEYS.length; i++) {
            int[] rect = buttons.get(i);
            boolean disabled = i == 2 && !ClientMaidAttackData.hasClipboard();
            drawFooterButton(graphics, rect[0], rect[1], rect[2], FOOTER_KEYS[i], mouseX, mouseY, disabled);
        }

        // 外框
        graphics.fill(l, t, r, t + 1, cBorder);
        graphics.fill(l, b - 1, r, b, cBorder);
        graphics.fill(l, t, l + 1, b, cBorder);
        graphics.fill(r - 1, t, r, b, cBorder);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawModeButton(GuiGraphics graphics, int x, int y, String mode, boolean active,
                                int activeColor, int idleColor, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + MODE_W && mouseY >= y && mouseY <= y + MODE_H;
        int bg = active ? activeColor : (hovered ? idleColor : cBtnBg);
        graphics.fill(x, y, x + MODE_W, y + MODE_H, bg);
        graphics.fill(x, y, x + MODE_W, y + 1, active ? 0xFFFFFFFF : cBorder);
        String label = Component.translatable(modeLabelKey(mode)).getString();
        Component text = style(Component.literal(label), active || hovered ? 0xFFFFFFFF : cText);
        graphics.drawString(this.font, text, x + (MODE_W - this.font.width(text)) / 2, y + 3,
                active || hovered ? 0xFFFFFFFF : cText, false);
    }

    private void drawFooterButton(GuiGraphics graphics, int x, int y, int w, String key,
                                  int mouseX, int mouseY, boolean disabled) {
        boolean hovered = !disabled && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 14;
        int base = disabled ? blend(cBtnBg, 0x55FFFFFF) : cBtnBg;
        graphics.fill(x, y, x + w, y + 14, hovered ? cAccent : base);
        graphics.fill(x, y, x + w, y + 1, hovered ? 0xFFFFFFFF : cBorder);
        int color = disabled ? cSub : (hovered ? 0xFFFFFFFF : cText);
        Component text = style(Component.translatable(key), color);
        graphics.drawString(this.font, text, x + Math.max(2, (w - this.font.width(text)) / 2), y + 4,
                color, false);
    }

    /** 把颜色往白里混一点，用于禁用态。 */
    private static int blend(int color, int overlay) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int oa = (overlay >>> 24) & 0xFF;
        float t = oa / 255.0F;
        int nr = (int) (r + (((overlay >> 16) & 0xFF) - r) * t);
        int ng = (int) (g + (((overlay >> 8) & 0xFF) - g) * t);
        int nb = (int) (b + ((overlay & 0xFF) - b) * t);
        return (a << 24) | (nr << 16) | (ng << 8) | nb;
    }

    private static String modeLabelKey(String mode) {
        return switch (mode) {
            case FRIENDLY -> "fairymoon3_maid_list.attack.mode.friendly";
            case HOSTILE -> "fairymoon3_maid_list.attack.mode.hostile";
            default -> "fairymoon3_maid_list.attack.mode.neutral";
        };
    }

    private static MutableComponent style(Component text, int color) {
        MutableComponent copy = text.copy();
        if (PanelConfig.useVanillaFont()) {
            return copy.withStyle(style -> style.withColor(color));
        }
        return copy.withStyle(style -> style.withColor(color)
                .withFont(ResourceLocation.fromNamespaceAndPath("fairymoon3_maid_list", "panel")));
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int l = left();
        int t = top();
        int r = l + W;
        int b = t + H;

        // 关闭
        int cx = closeX();
        int cy = t + 6;
        if (mouseX >= cx && mouseX <= cx + CLOSE_SIZE && mouseY >= cy && mouseY <= cy + CLOSE_SIZE) {
            onClose();
            return true;
        }

        // 底部按钮
        java.util.List<int[]> buttons = footerButtons();
        for (int i = 0; i < buttons.size(); i++) {
            int[] btn = buttons.get(i);
            if (mouseX >= btn[0] && mouseX <= btn[0] + btn[2] && mouseY >= btn[1] && mouseY <= btn[1] + btn[3]) {
                switch (i) {
                    case 0 -> sendReplace(Map.of());
                    case 1 -> {
                        ClientMaidAttackData.copyAll();
                        toast("fairymoon3_maid_list.attack.copy.done", ClientMaidAttackData.clipboard().size());
                    }
                    case 2 -> {
                        if (ClientMaidAttackData.hasClipboard()) {
                            sendReplace(ClientMaidAttackData.clipboard());
                            toast("fairymoon3_maid_list.attack.paste.done");
                        }
                    }
                    case 3 -> PacketDistributor.sendToServer(new MaidAttackGlobalPayload(
                            MaidAttackGlobalPayload.ACTION_SYNC_ALL,
                            ClientMaidAttackData.maidId(), ClientMaidAttackData.entries()));
                    case 4 -> PacketDistributor.sendToServer(new MaidAttackGlobalPayload(
                            MaidAttackGlobalPayload.ACTION_RESET_ALL,
                            ClientMaidAttackData.maidId(), Map.of()));
                    default -> {
                    }
                }
                return true;
            }
        }

        // 行内三态按钮
        List<Integer> visible = visibleIndexes();
        for (int row = 0; row < visible.size(); row++) {
            int rowY = listTop() + row * ROW_H - (int) this.scroll;
            if (rowY + ROW_H < listTop() || rowY > listBottom()) {
                continue;
            }
            int btnStart = r - MODE_W * 3 - 10;
            int by = rowY + 3;
            if (mouseY < by || mouseY > by + MODE_H) {
                continue;
            }
            EntityType<?> type = ALL_TYPES.get(visible.get(row));
            String[] modes = {FRIENDLY, NEUTRAL, HOSTILE};
            for (int i = 0; i < 3; i++) {
                int bx = btnStart + (MODE_W + 2) * i;
                if (mouseX >= bx && mouseX <= bx + MODE_W) {
                    String target = modes[i];
                    String current = modeOf(type);
                    // 点当前状态 = 取消（从名单移除）
                    String next = target.equals(current) ? "" : target;
                    ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                    if (id != null && ClientMaidAttackData.maidId() != null) {
                        // 只有真正设成某个状态才排到最前；取消修改（移除）保持原位
                        if (!next.isEmpty()) {
                            ClientMaidAttackData.markRecent(id);
                        }
                        PacketDistributor.sendToServer(new MaidAttackSetPayload(
                                ClientMaidAttackData.maidId(), id, next));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 轻量提示：直接在聊天栏输出一行，不依赖服务端。 */
    private void toast(String key, Object... args) {
        var player = this.minecraft == null ? null : this.minecraft.player;
        if (player != null) {
            player.displayClientMessage(Component.translatable(key, args), false);
        }
    }

    private static boolean hit(double mouseX, int x, int w) {
        return mouseX >= x && mouseX <= x + w;
    }

    private void sendReplace(Map<ResourceLocation, String> entries) {
        if (ClientMaidAttackData.maidId() == null) {
            return;
        }
        PacketDistributor.sendToServer(new MaidAttackReplacePayload(
                ClientMaidAttackData.maidId(), entries));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll -= scrollY * ROW_H * 2;
        clampScroll();
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft == null) {
            return;
        }
        var cached = ClientMaidPanel.cachedEntries();
        this.minecraft.setScreen(cached != null ? new MaidPanelScreen(cached) : null);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.fairymoon3.maidlist.network.MaidListRequestPayload());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 留空：暗化由 render 自己画
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
