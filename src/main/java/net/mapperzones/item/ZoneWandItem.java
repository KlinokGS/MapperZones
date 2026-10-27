package net.mapperzones.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.mapperzones.data.MapperData;
import net.mapperzones.zone.TriggerZone;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.phys.BlockHitResult;

import java.util.*;

public class ZoneWandItem extends Item {
    public static final Map<UUID, BlockPos> FIRST = new HashMap<>(), SECOND = new HashMap<>();
    public static final Map<UUID, String> MOVE_SELECTION = new HashMap<>();

    public ZoneWandItem(Properties p) {
        super(p);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (c.getLevel().isClientSide) {
            // A click confirming a move should not also become a creation point.
            if (net.mapperzones.client.ClientData.SELECTED_ZONE != null) {
                net.mapperzones.client.ClientData.SELECTED_ZONE = null;
                net.mapperzones.client.ClientData.WAND_FIRST = null;
                net.mapperzones.client.ClientData.WAND_SECOND = null;
                return InteractionResult.SUCCESS;
            }
            BlockPos clicked = c.getClickedPos().immutable();
            if (net.mapperzones.client.ClientData.WAND_FIRST == null || net.mapperzones.client.ClientData.WAND_SECOND != null) {
                net.mapperzones.client.ClientData.WAND_FIRST = clicked;
                net.mapperzones.client.ClientData.WAND_SECOND = null;
            } else {
                net.mapperzones.client.ClientData.WAND_SECOND = clicked;
            }
            return InteractionResult.SUCCESS;
        }
        if (!(c.getPlayer() instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        BlockPos pos = c.getClickedPos().immutable();
        String selected = MOVE_SELECTION.get(p.getUUID());
        if (selected != null) {
            MapperData data = MapperData.get(p.serverLevel());
            TriggerZone zone = data.zones.get(selected);
            if (zone == null) {
                MOVE_SELECTION.remove(p.getUUID());
                p.sendSystemMessage(Component.translatable("message.mapperzones.move_zone_missing").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            if (!zone.dimension.equals(p.level().dimension().location())) {
                p.sendSystemMessage(Component.translatable("message.mapperzones.move_wrong_dimension").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            int sx = zone.getSizeX(), sy = zone.getSizeY(), sz = zone.getSizeZ();
            // The clicked block is the floor; place the zone's bottom one block above it.
            int targetY = pos.getY() + 1;
            int minX = pos.getX(), minZ = pos.getZ();
            // Place the volume forward from the target, away from the player. Do not rotate or swap axes.
            switch (p.getDirection()) {
                case NORTH -> minZ = pos.getZ() - sz + 1;
                case SOUTH -> minZ = pos.getZ();
                case WEST -> minX = pos.getX() - sx + 1;
                case EAST -> minX = pos.getX();
            }
            int minY = targetY <= zone.pos2.getY() ? targetY : targetY - sy + 1;
            BlockPos old1 = zone.pos1, old2 = zone.pos2;
            int dx = minX - zone.minX(), dy = minY - zone.minY(), dz = minZ - zone.minZ();
            zone.pos1 = old1.offset(dx, dy, dz);
            zone.pos2 = old2.offset(dx, dy, dz);
            zone.currentlyInside.clear(); zone.nextStayAt.clear();
            data.dirty();
            // Moving a zone ends the move operation and invalidates any unfinished
            // creation points, otherwise the next click can be misread as point two.
            UUID playerId = p.getUUID();
            MOVE_SELECTION.remove(playerId);
            FIRST.remove(playerId);
            SECOND.remove(playerId);
            net.mapperzones.client.ClientData.WAND_FIRST = null;
            net.mapperzones.client.ClientData.WAND_SECOND = null;
            p.sendSystemMessage(Component.translatable("message.mapperzones.zone_moved", zone.name, minX, minY, minZ).withStyle(ChatFormatting.GREEN));
            net.mapperzones.network.ModNetwork.sync(p);
            return InteractionResult.SUCCESS;
        }

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

    @SubscribeEvent
    public void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // Left-clicking blocks is intentionally not used to select a zone for moving.
        // Move selection is performed with left-click in the air while standing inside a zone.
    }

    @SubscribeEvent
    public void leftClickAir(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        if (!player.getMainHandItem().is(ModItems.ZONE_WAND.get())) return;
        if (player.isShiftKeyDown()) {
            if (player.level().isClientSide) {
                net.mapperzones.client.ClientData.SELECTED_ZONE = null;
                net.mapperzones.client.ClientData.WAND_FIRST = null;
                net.mapperzones.client.ClientData.WAND_SECOND = null;
                net.mapperzones.network.ModNetwork.cancelWandSelection();
            }
        } else if (player.level().isClientSide) {
            int x = player.blockPosition().getX(), y = player.blockPosition().getY(), z = player.blockPosition().getZ();
            net.mapperzones.client.ClientData.SELECTED_ZONE = null;
            for (com.google.gson.JsonObject zone : net.mapperzones.client.ClientData.ZONES) {
                try {
                    if (!zone.has("dimension") || !zone.get("dimension").getAsString().equals(player.level().dimension().location().toString())) continue;
                    com.google.gson.JsonObject a = zone.getAsJsonObject("pos1"), b = zone.getAsJsonObject("pos2");
                    if (x >= Math.min(a.get("x").getAsInt(), b.get("x").getAsInt()) && x <= Math.max(a.get("x").getAsInt(), b.get("x").getAsInt())
                            && y >= Math.min(a.get("y").getAsInt(), b.get("y").getAsInt()) && y <= Math.max(a.get("y").getAsInt(), b.get("y").getAsInt())
                            && z >= Math.min(a.get("z").getAsInt(), b.get("z").getAsInt()) && z <= Math.max(a.get("z").getAsInt(), b.get("z").getAsInt())) {
                        net.mapperzones.client.ClientData.SELECTED_ZONE = zone.get("name").getAsString().toLowerCase(Locale.ROOT);
                        break;
                    }
                } catch (Exception ignored) { }
            }
            net.mapperzones.network.ModNetwork.selectZoneAtPlayer();
        }
    }

    private static String fmt(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }
}
