package com.fairymoon3.maidlist.client;

import com.fairymoon3.maidlist.config.PanelConfig;
import com.fairymoon3.maidlist.config.ThemeMode;
import com.fairymoon3.maidlist.network.MaidReleaseAllPayload;
import com.fairymoon3.maidlist.network.MaidReleasePayload;
import com.fairymoon3.maidlist.network.MaidStorageRequestPayload;
import com.fairymoon3.maidlist.network.StoredMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆收纳空间界面。
 *
 * <p>由左上角神龛右键打开；有关闭按钮、名字搜索、实体小头像，支持逐只 / 全部释放。</p>
 */
public class MaidStorageScreen extends Screen {

    private static final int W = 320;
    private static final int H = 236;
    private static final int HEADER_H = 26;
    private static final int TOOLBAR_H = 22;
    private static final int ROW_H = 30;
    private static final int AVATAR = 22;
    private static final int BTN_W = 44;
    private static final int BTN_H = 13;
    private static final int CLOSE_SIZE = 11;

    private static final ResourceLocation PANEL_FONT =
            ResourceLocation.fromNamespaceAndPath("fairymoon3_maid_list", "panel");

    /** 客户端为「已收纳女仆」构造的展示用实体（不进世界）。 */
    private static final Map<UUID, EntityMaid> PORTRAITS = new HashMap<>();

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

    public MaidStorageScreen() {
        super(Component.translatable("fairymoon3_maid_list.storage.title"));
    }

    public void refresh() {
        clampScroll();
    }

    @Override
    protected void init() {
        applyTheme();
        int sx = left() + 6;
        int sy = top() + HEADER_H + 4;
        int sw = W - 12;
        this.searchBox = new EditBox(this.font, sx, sy, sw, 14,
                Component.translatable("fairymoon3_maid_list.storage.search.hint"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(32);
        this.searchBox.setHint(Component.translatable("fairymoon3_maid_list.storage.search.hint")
                .withStyle(style -> style.withColor(cSub & 0xFFFFFF)));
        this.searchBox.setTextColor(cText);
        this.searchBox.setValue(this.query);
        this.searchBox.setResponder(value -> {
            this.query = value;
            clampScroll();
        });
        addRenderableWidget(this.searchBox);

        PacketDistributor.sendToServer(new MaidStorageRequestPayload());
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
        return top() + H - 26;
    }

    private int listHeight() {
        return listBottom() - listTop();
    }

    private int closeX() {
        return left() + W - 5 - CLOSE_SIZE;
    }

    private int closeY() {
        return top() + 6;
    }

    /** 过滤后的列表（按名字搜索）。 */
    private List<StoredMaid> visible() {
        List<StoredMaid> all = ClientMaidStorage.entries();
        if (this.query == null || this.query.isBlank()) {
            return all;
        }
        String needle = this.query.toLowerCase();
        List<StoredMaid> out = new ArrayList<>();
        for (StoredMaid stored : all) {
            if (stored.name() != null && stored.name().toLowerCase().contains(needle)) {
                out.add(stored);
            }
        }
        return out;
    }

    private void clampScroll() {
        double max = Math.max(0, visible().size() * ROW_H - listHeight());
        this.scroll = Math.max(0, Math.min(this.scroll, max));
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

        // 标题栏
        graphics.fill(l + 1, t + 1, r - 1, t + HEADER_H, cHeader);
        graphics.fill(l + 1, t + HEADER_H - 1, r - 1, t + HEADER_H, cAccent);

        drawText(graphics, Component.translatable("fairymoon3_maid_list.storage.title"), l + 10, t + 9, cText);
        String countText = Component.translatable("fairymoon3_maid_list.storage.count",
                ClientMaidStorage.entries().size()).getString();
        Component count = style(Component.literal(countText), cAccent);
        graphics.drawString(this.font, count, closeX() - 8 - this.font.width(count), t + 9, cAccent, false);

        // 关闭按钮
        drawCloseButton(graphics, mouseX, mouseY);

        // 搜索栏
        int sx = l + 3;
        int sy = t + HEADER_H + 2;
        graphics.fill(sx, sy, r - 3, sy + 18, cSearchBg);
        graphics.fill(sx, sy, sx + 1, sy + 18, cBorder);

        // 列表
        List<StoredMaid> list = visible();
        graphics.enableScissor(l + 1, listTop(), r - 1, listBottom());
        if (list.isEmpty()) {
            Component empty = style(Component.translatable(ClientMaidStorage.entries().isEmpty()
                    ? "fairymoon3_maid_list.storage.empty"
                    : "fairymoon3_maid_list.empty.search"), cSub);
            graphics.drawString(this.font, empty, l + (W - this.font.width(empty)) / 2, listTop() + 24, cSub, false);
        }
        for (int i = 0; i < list.size(); i++) {
            int rowY = listTop() + i * ROW_H - (int) this.scroll;
            if (rowY + ROW_H < listTop() || rowY > listBottom()) {
                continue;
            }
            StoredMaid stored = list.get(i);
            boolean hovered = mouseX >= l + 2 && mouseX <= r - 2
                    && mouseY >= rowY && mouseY <= rowY + ROW_H - 1
                    && mouseY >= listTop() && mouseY <= listBottom();
            if (hovered) {
                graphics.fill(l + 2, rowY, r - 2, rowY + ROW_H - 1, cRowHover);
            }

            int ax = l + 6;
            int ay = rowY + (ROW_H - AVATAR) / 2;
            graphics.fill(ax, ay, ax + AVATAR, ay + AVATAR, cBtnBg);
            drawPortrait(graphics, stored, ax, ay, AVATAR);

            int textX = ax + AVATAR + 6;
            int btnX = r - BTN_W - 8;
            drawText(graphics, Component.literal(stored.name()), textX, rowY + 5, cText);
            String hp = "HP " + stored.hp() + "/" + stored.maxHp();
            graphics.drawString(this.font, style(Component.literal(hp), cSub),
                    textX, rowY + 17, cSub, false);

            int bx = btnX;
            int by = rowY + (ROW_H - 1 - BTN_H) / 2;
            boolean bh = mouseX >= bx && mouseX <= bx + BTN_W && mouseY >= by && mouseY <= by + BTN_H;
            graphics.fill(bx, by, bx + BTN_W, by + BTN_H, bh ? cAccent : cBtnBg);
            graphics.fill(bx, by, bx + BTN_W, by + 1, bh ? 0xFFFFFFFF : cBorder);
            Component rel = style(Component.translatable("fairymoon3_maid_list.storage.release"),
                    bh ? 0xFFFFFFFF : cText);
            graphics.drawString(this.font, rel, bx + (BTN_W - this.font.width(rel)) / 2, by + 3,
                    bh ? 0xFFFFFFFF : cText, false);
        }
        graphics.disableScissor();

        // 底部：全部释放
        int footerY = b - 22;
        int allW = 76;
        int allX = r - allW - 10;
        boolean allHover = mouseX >= allX && mouseX <= allX + allW
                && mouseY >= footerY && mouseY <= footerY + 14;
        graphics.fill(allX, footerY, allX + allW, footerY + 14, allHover ? cAccent : cBtnBg);
        graphics.fill(allX, footerY, allX + allW, footerY + 1, allHover ? 0xFFFFFFFF : cBorder);
        Component all = style(Component.translatable("fairymoon3_maid_list.storage.release.all"),
                allHover ? 0xFFFFFFFF : cText);
        graphics.drawString(this.font, all, allX + (allW - this.font.width(all)) / 2, footerY + 4,
                allHover ? 0xFFFFFFFF : cText, false);

        // 外框
        graphics.fill(l, t, r, t + 1, cBorder);
        graphics.fill(l, b - 1, r, b, cBorder);
        graphics.fill(l, t, l + 1, b, cBorder);
        graphics.fill(r - 1, t, r, b, cBorder);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCloseButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = closeX();
        int y = closeY();
        boolean hovered = mouseX >= x && mouseX <= x + CLOSE_SIZE && mouseY >= y && mouseY <= y + CLOSE_SIZE;
        int color = hovered ? cAccent : cSub;
        // 两条对角线画成 X
        for (int i = 0; i < CLOSE_SIZE - 3; i++) {
            graphics.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, color);
            graphics.fill(x + CLOSE_SIZE - 3 - i, y + 2 + i, x + CLOSE_SIZE - 2 - i, y + 3 + i, color);
        }
    }

    private void drawText(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(this.font, style(text, color), x, y, color, false);
    }

    /** 按当前字体设置给文本加样式。 */
    private static MutableComponent style(Component text, int color) {
        MutableComponent copy = text.copy();
        if (PanelConfig.useVanillaFont()) {
            return copy.withStyle(style -> style.withColor(color));
        }
        return copy.withStyle(style -> style.withFont(PANEL_FONT).withColor(color));
    }

    /** 用收纳时录下的实体数据画一个小头像；失败就留空底色。 */
    private void drawPortrait(GuiGraphics graphics, StoredMaid stored, int x, int y, int size) {
        try {
            EntityMaid maid = PORTRAITS.get(stored.uuid());
            if (maid == null) {
                if (this.minecraft == null || this.minecraft.level == null) {
                    return;
                }
                maid = new EntityMaid(this.minecraft.level);
                maid.load(stored.tag().copy());
                PORTRAITS.put(stored.uuid(), maid);
            }
            float yaw = 200.0F;
            float bodyRot = maid.yBodyRot;
            float yRot = maid.getYRot();
            float xRot = maid.getXRot();
            float headRot = maid.yHeadRot;
            float headRotO = maid.yHeadRotO;
            try {
                maid.yBodyRot = yaw;
                maid.setYRot(yaw);
                maid.setXRot(0.0F);
                maid.yHeadRot = yaw;
                maid.yHeadRotO = yaw;
                Vector3f offset = new Vector3f(0.0F, maid.getBbHeight() * 0.5F, 0.0F);
                Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
                InventoryScreen.renderEntityInInventory(graphics,
                        x + size / 2.0F, y + size / 2.0F + size * 0.08F, size * 0.5F,
                        offset, rotation, null, maid);
            } finally {
                maid.yBodyRot = bodyRot;
                maid.setYRot(yRot);
                maid.setXRot(xRot);
                maid.yHeadRot = headRot;
                maid.yHeadRotO = headRotO;
            }
        } catch (Throwable ignored) {
            // 渲染失败就保留底色块
        }
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // 关闭
        int cx = closeX();
        int cy = closeY();
        if (mouseX >= cx && mouseX <= cx + CLOSE_SIZE && mouseY >= cy && mouseY <= cy + CLOSE_SIZE) {
            onClose();
            return true;
        }

        int l = left();
        int r = l + W;
        int b = top() + H;

        // 全部释放
        int footerY = b - 22;
        int allW = 76;
        int allX = r - allW - 10;
        if (mouseX >= allX && mouseX <= allX + allW && mouseY >= footerY && mouseY <= footerY + 14) {
            PacketDistributor.sendToServer(new MaidReleaseAllPayload());
            return true;
        }

        // 单只释放
        List<StoredMaid> list = visible();
        for (int i = 0; i < list.size(); i++) {
            int rowY = listTop() + i * ROW_H - (int) this.scroll;
            if (rowY + ROW_H < listTop() || rowY > listBottom()) {
                continue;
            }
            int bx = r - BTN_W - 8;
            int by = rowY + (ROW_H - 1 - BTN_H) / 2;
            if (mouseX >= bx && mouseX <= bx + BTN_W && mouseY >= by && mouseY <= by + BTN_H) {
                PacketDistributor.sendToServer(new MaidReleasePayload(list.get(i).uuid()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll -= scrollY * ROW_H;
        clampScroll();
        return true;
    }

    /** 关掉收纳空间后回到女仆名单主界面（只有玩家主动关闭才会离开）。 */
    @Override
    public void onClose() {
        if (this.minecraft == null) {
            return;
        }
        var cached = ClientMaidPanel.cachedEntries();
        if (cached != null) {
            this.minecraft.setScreen(new MaidPanelScreen(cached));
        } else {
            this.minecraft.setScreen(null);
        }
        // 顺便拉一次最新名册（回包会进入上面的 MaidPanelScreen）
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.fairymoon3.maidlist.network.MaidListRequestPayload());
    }

    /**
     * 1.21.1 的 {@code Screen#render} 会先调用 {@code renderBackground}，
     * 原版实现是「模糊 + 暗化贴图」铺满整屏，会把我们的界面糊掉。
     * 这里改成空实现，暗化由 {@link #render} 自己画在最底层。
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 故意留空
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
