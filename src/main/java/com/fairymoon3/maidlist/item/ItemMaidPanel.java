package com.fairymoon3.maidlist.item;

import com.fairymoon3.maidlist.network.MaidListRequestPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 女仆名单 —— 右键（对着空气）打开女仆名册。
 *
 * <p>客户端只负责发一个"给我名册"的请求包，真正的数据由服务端收集后回传，
 * 这样单人、局域网和专用服务器行为一致。</p>
 */
public class ItemMaidPanel extends Item {

    public ItemMaidPanel(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            PacketDistributor.sendToServer(new MaidListRequestPayload());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.fairymoon3_maid_list.maid_panel.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.fairymoon3_maid_list.maid_panel.desc2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
