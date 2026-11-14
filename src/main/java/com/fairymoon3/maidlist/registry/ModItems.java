package com.fairymoon3.maidlist.registry;

import com.fairymoon3.maidlist.MaidList;
import com.fairymoon3.maidlist.item.ItemMaidPanel;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 物品与创造模式物品栏注册。 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MaidList.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MaidList.MODID);

    /** 女仆名单本体：右键打开女仆名册。 */
    public static final DeferredItem<Item> MAID_PANEL = ITEMS.register("maid_panel",
            () -> new ItemMaidPanel(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MaidList.MODID))
            .icon(() -> new ItemStack(MAID_PANEL.get()))
            .displayItems((parameters, output) -> output.accept(MAID_PANEL.get()))
            .build());

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
