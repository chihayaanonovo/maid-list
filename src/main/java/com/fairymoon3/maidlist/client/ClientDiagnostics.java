package com.fairymoon3.maidlist.client;

import com.fairymoon3.maidlist.MaidList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 客户端启动自检。
 *
 * <p>主动把 foxdream 的 {@code CareScreen} 类加载一次，这样指向它的混入会在**启动阶段**
 * 就被应用/报错，而不是等到玩家第一次打开面板才暴露问题（那时候错误只会安静地躺在日志里）。</p>
 */
@EventBusSubscriber(modid = MaidList.MODID, value = Dist.CLIENT)
public final class ClientDiagnostics {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CARE_SCREEN =
            "cn.blockforge.generated.winefox1801000525.client.CareScreen";
    private static final String MAID_CONTAINER =
            "com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer";
    private static final String OPEN_GUI_PACKET =
            "com.github.tartaricacid.touhoulittlemaid.network.message.OpenMaidGuiPackage";
    private static final String FOX_CARE_NETWORK =
            "cn.blockforge.generated.winefox1801000525.CareNetwork";
    private static final String FOX_ORDERED_SLEEP =
            "cn.blockforge.generated.winefox1801000525.CareOrderedSleep";

    private ClientDiagnostics() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 1) 女仆容器混入（无狐梦也必须生效，所以第一个检查）
        preload(MAID_CONTAINER, "女仆容器距离校验");
        preload(OPEN_GUI_PACKET, "女仆开界面包校验");

        // 2) 狐梦面板混入（只在装了 foxdream 时才有目标类）
        boolean present;
        try {
            present = ModList.get().isLoaded("foxdream");
        } catch (Throwable t) {
            present = false;
        }
        if (!present) {
            LOGGER.info("[女仆名单] 未检测到狐梦伴岁，跳过狐梦混入自检。");
            return;
        }
        preload(CARE_SCREEN, "狐梦面板距离校验");
        preload(FOX_CARE_NETWORK, "狐梦 HUD 动作包校验");
        preload(FOX_ORDERED_SLEEP, "狐梦早点睡觉校验");
    }

    /** 主动加载目标类，让混入在启动阶段就应用一次；失败只记日志，不影响游戏。 */
    private static void preload(String className, String what) {
        try {
            Class.forName(className, false, ClientDiagnostics.class.getClassLoader());
            LOGGER.info("[女仆名单] 混入自检：{}（{}）已加载，若注入失败上方会有 mixin 报错。", what, className);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("[女仆名单] 混入自检：找不到 {}，目标 mod 版本可能变了。", className);
        } catch (Throwable t) {
            LOGGER.warn("[女仆名单] 混入自检：预加载 {} 失败（不影响游戏）", className, t);
        }
    }
}
