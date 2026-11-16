package com.fairymoon3.maidlist.client;

import com.fairymoon3.maidlist.network.MaidListPayload;
import net.minecraft.client.Minecraft;

/** 客户端入口：收到名册后打开或刷新面板。 */
public final class ClientMaidPanel {

    private ClientMaidPanel() {
    }

    /** 最近一次收到的名册，用于从收纳空间返回时立即恢复主界面。 */
    private static java.util.List<com.fairymoon3.maidlist.network.MaidEntry> lastEntries;

    public static java.util.List<com.fairymoon3.maidlist.network.MaidEntry> cachedEntries() {
        return lastEntries;
    }

    public static void handle(MaidListPayload payload) {
        lastEntries = payload.entries();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MaidPanelScreen screen) {
            screen.setEntries(payload.entries());
            return;
        }
        // 收纳空间开着的时候，释放 / 收纳都会让服务端推名册，
        // 这里只是更新缓存，绝不抢焦点把收纳界面顶掉。
        if (minecraft.screen instanceof MaidStorageScreen) {
            return;
        }
        minecraft.setScreen(new MaidPanelScreen(payload.entries()));
    }
}
