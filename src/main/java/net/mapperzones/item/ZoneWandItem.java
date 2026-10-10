package net.mapperzones.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class ZoneWandItem extends Item {
    public static final Map<UUID, BlockPos> FIRST = new HashMap<>(), SECOND = new HashMap<>();

    public ZoneWandItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (!(c.getPlayer() instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        BlockPos pos = c.getClickedPos().immutable();

        if (!FIRST.containsKey(p.getUUID()) || SECOND.containsKey(p.getUUID())) {
            FIRST.put(p.getUUID(), pos);
            SECOND.remove(p.getUUID());
            p.sendSystemMessage(Component.translatable("message.mapperzones.first_point", fmt(pos)).withStyle(ChatFormatting.GREEN));
        } else {
            SECOND.put(p.getUUID(), pos);
            p.sendSystemMessage(Component.translatable("message.mapperzones.second_point", fmt(pos)).withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    private static String fmt(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }
}
