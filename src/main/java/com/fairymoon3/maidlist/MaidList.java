package com.fairymoon3.maidlist;

import com.fairymoon3.maidlist.config.PanelConfig;
import com.fairymoon3.maidlist.network.ModNetwork;
import com.fairymoon3.maidlist.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * 女仆名单 (Maid List)
 *
 * <p>车万女仆 (Touhou Little Maid) 的附属 mod：提供一件工具，右键打开一个
 * QQ 联系人列表风格的面板，集中展示玩家名下所有已加载女仆的信息。</p>
 *
 * <p>可选联动「车万女仆：狐梦伴岁」（foxdream）：显示成长阶段并提供手册入口。</p>
 */
@Mod(MaidList.MODID)
public class MaidList {

    public static final String MODID = "fairymoon3_maid_list";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MaidList(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.register(modEventBus);
        modEventBus.addListener(ModNetwork::register);
        modContainer.registerConfig(ModConfig.Type.CLIENT, PanelConfig.CLIENT_SPEC);
        LOGGER.info("[女仆名单] 已加载，女仆名册准备就绪。");
    }
}
