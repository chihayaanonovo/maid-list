package com.fairymoon3.maidlist.client;

import com.fairymoon3.maidlist.network.MaidListPayload;
import net.minecraft.client.Minecraft;

/** 客户端入口：收到名册后打开或刷新面板。 */
public final class ClientMaidPanel {

    private ClientMaidPanel() {
    }

    public static void handle(MaidListPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MaidPanelScreen screen) {
            screen.setEntries(payload.entries());
        } else {
            minecraft.setScreen(new MaidPanelScreen(payload.entries()));
        }
    }
}
