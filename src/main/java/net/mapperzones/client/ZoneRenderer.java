package net.mapperzones.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mapperzones.item.ModItems;
import net.mapperzones.config.MapperZonesSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Renders zone boundaries only while the selection wand is held.
 */
public final class ZoneRenderer {
    private record ZoneLabel(double x, double y, double z, String text, int color) { }
    @SubscribeEvent
    public void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null) return;
        boolean holdingWand = minecraft.player.getMainHandItem().is(ModItems.ZONE_WAND.get()) || minecraft.player.getOffhandItem().is(ModItems.ZONE_WAND.get());

        if (!holdingWand) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();

        var buffers = minecraft.renderBuffers().bufferSource();

        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        java.util.List<ZoneLabel> labels = new java.util.ArrayList<>();

        for (JsonObject zone : ClientData.ZONES) {
            try {
                if (!zone.has("dimension") || !zone.get("dimension").getAsString().equals(minecraft.level.dimension().location().toString()))
                    continue;
                JsonObject first = zone.getAsJsonObject("pos1");
                JsonObject second = zone.getAsJsonObject("pos2");
                double minX = Math.min(first.get("x").getAsInt(), second.get("x").getAsInt());
                double minY = Math.min(first.get("y").getAsInt(), second.get("y").getAsInt());
                double minZ = Math.min(first.get("z").getAsInt(), second.get("z").getAsInt());
                double maxX = Math.max(first.get("x").getAsInt(), second.get("x").getAsInt()) + 1;
                double maxY = Math.max(first.get("y").getAsInt(), second.get("y").getAsInt()) + 1;
                double maxZ = Math.max(first.get("z").getAsInt(), second.get("z").getAsInt()) + 1;
                // Chunk-space culling keeps huge maps from rendering distant wireframes.
                int minChunkX = Math.floorDiv((int)Math.floor(minX), 16);
                int maxChunkX = Math.floorDiv((int)Math.floor(maxX - 1), 16);
                int minChunkZ = Math.floorDiv((int)Math.floor(minZ), 16);
                int maxChunkZ = Math.floorDiv((int)Math.floor(maxZ - 1), 16);
                int playerChunkX = minecraft.player.chunkPosition().x;
                int playerChunkZ = minecraft.player.chunkPosition().z;
                int dx = playerChunkX < minChunkX ? minChunkX - playerChunkX : playerChunkX > maxChunkX ? playerChunkX - maxChunkX : 0;
                int dz = playerChunkZ < minChunkZ ? minChunkZ - playerChunkZ : playerChunkZ > maxChunkZ ? playerChunkZ - maxChunkZ : 0;
                int maxChunkDistance = MapperZonesSettings.highlightDistance();
                if ((long) dx * dx + (long) dz * dz > (long) maxChunkDistance * maxChunkDistance) continue;
                boolean enabled = !zone.has("enabled") || zone.get("enabled").getAsBoolean();
                boolean selected = zone.has("name") && ClientData.SELECTED_ZONE != null
                        && zone.get("name").getAsString().equalsIgnoreCase(ClientData.SELECTED_ZONE);
                float red = selected ? 1.0F : (enabled ? 0.1F : 1.0F);
                float green = selected ? 0.65F : (enabled ? 1.0F : 0.15F);
                float blue = selected ? 0.05F : 0.2F;
                String zoneName = zone.has("name") ? zone.get("name").getAsString() : "Unnamed zone";
                if (MapperZonesSettings.showZoneNames() && camera.distanceToSqr((minX + maxX) / 2.0, maxY + 0.25, (minZ + maxZ) / 2.0) <= 48.0 * 48.0) {
                    String labelText = selected ? zoneName + " [" + Component.translatable("message.mapperzones.move_label").getString() + "]" : (enabled ? zoneName : zoneName + " [OFF]");
                    labels.add(new ZoneLabel((minX + maxX) / 2.0, maxY + 0.25, (minZ + maxZ) / 2.0, labelText, enabled ? 0xFFB8FFB8 : 0xFFFF7777));
                }
                String shape = zone.has("shape") ? zone.get("shape").getAsString() : "BOX";
                if ("SPHERE".equalsIgnoreCase(shape) || "CYLINDER".equalsIgnoreCase(shape)) {
                    double cx = (minX + maxX) / 2.0, cy = (minY + maxY) / 2.0, cz = (minZ + maxZ) / 2.0;
                    double rx = (maxX - minX) / 2.0, ry = (maxY - minY) / 2.0, rz = (maxZ - minZ) / 2.0;
                    if ("CYLINDER".equalsIgnoreCase(shape)) {
                        drawRing(pose, lines, cx, minY, cz, rx, rz, red, green, blue);
                        drawRing(pose, lines, cx, maxY, cz, rx, rz, red, green, blue);
                        for (int i = 0; i < 4; i++) {
                            double angle = Math.PI * i / 2.0;
                            drawSegment(pose, lines, cx + Math.cos(angle) * rx, minY, cz + Math.sin(angle) * rz,
                                    cx + Math.cos(angle) * rx, maxY, cz + Math.sin(angle) * rz, red, green, blue);
                        }
                    } else {
                        // Ellipsoid wireframe: latitude rings and vertical meridians.
                        for (int lat = -3; lat <= 3; lat++) {
                            double angle = lat * Math.PI / 8.0;
                            double ringScale = Math.cos(angle);
                            drawRing(pose, lines, cx, cy + Math.sin(angle) * ry, cz, rx * ringScale, rz * ringScale, red, green, blue);
                        }
                        for (int meridian = 0; meridian < 8; meridian++) {
                            double angle = Math.PI * meridian / 4.0;
                            double prevX = cx + Math.cos(angle) * rx, prevZ = cz + Math.sin(angle) * rz;
                            for (int step = 1; step <= 16; step++) {
                                double t = -Math.PI / 2.0 + Math.PI * step / 16.0;
                                double nextX = cx + Math.cos(angle) * rx * Math.cos(t);
                                double nextY = cy + Math.sin(t) * ry;
                                double nextZ = cz + Math.sin(angle) * rz * Math.cos(t);
                                double prevY = cy + Math.sin(-Math.PI / 2.0 + Math.PI * (step - 1) / 16.0) * ry;
                                drawSegment(pose, lines, prevX, prevY, prevZ, nextX, nextY, nextZ, red, green, blue);
                                prevX = nextX; prevZ = nextZ;
                            }
                        }
                    }
                } else {
                    LevelRenderer.renderLineBox(pose, lines, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, 0.85F);
                }
            } catch (Exception ignored) {
                // Malformed synced data should not interrupt world rendering.
            }
        }
        // Preview the zone volume after both creation points have been selected.
        if (ClientData.WAND_FIRST != null && ClientData.WAND_SECOND != null) {
            var firstPoint = ClientData.WAND_FIRST;
            var secondPoint = ClientData.WAND_SECOND;
            double createMinX = Math.min(firstPoint.getX(), secondPoint.getX());
            double createMinY = Math.min(firstPoint.getY(), secondPoint.getY());
            double createMinZ = Math.min(firstPoint.getZ(), secondPoint.getZ());
            double createMaxX = Math.max(firstPoint.getX(), secondPoint.getX()) + 1.0;
            double createMaxY = Math.max(firstPoint.getY(), secondPoint.getY()) + 1.0;
            double createMaxZ = Math.max(firstPoint.getZ(), secondPoint.getZ()) + 1.0;
            LevelRenderer.renderLineBox(pose, lines, createMinX, createMinY, createMinZ,
                    createMaxX, createMaxY, createMaxZ, 0.15F, 0.85F, 1.0F, 1.0F);
            if (MapperZonesSettings.showZoneNames()) {
                String previewLabel = Component.translatable("message.mapperzones.create_preview").getString();
                labels.add(new ZoneLabel((createMinX + createMaxX) / 2.0, createMaxY + 0.25,
                        (createMinZ + createMaxZ) / 2.0, previewLabel, 0xFF66DDFF));
            }
        }

        // Preview the selected zone at the current target block before the player confirms with RMB.
        if (ClientData.SELECTED_ZONE != null) {
            JsonObject selectedZone = ClientData.find(ClientData.SELECTED_ZONE);
            if (selectedZone != null) {
                try {
                    var hit = minecraft.player.pick(5.0D, 0.0F, false);
                    if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit
                            && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                            && (!selectedZone.has("dimension") || selectedZone.get("dimension").getAsString().equals(minecraft.level.dimension().location().toString()))) {
                        JsonObject a = selectedZone.getAsJsonObject("pos1");
                        JsonObject b = selectedZone.getAsJsonObject("pos2");
                        int sx = Math.abs(a.get("x").getAsInt() - b.get("x").getAsInt()) + 1;
                        int sy = Math.abs(a.get("y").getAsInt() - b.get("y").getAsInt()) + 1;
                        int sz = Math.abs(a.get("z").getAsInt() - b.get("z").getAsInt()) + 1;
                        var target = blockHit.getBlockPos();
                        // Treat the targeted block as the floor: preview begins one block above it.
                        int targetY = target.getY() + 1;
                        int minX = target.getX(), minY = targetY, minZ = target.getZ();
                        // Match server placement: extend the zone forward, away from the player, without rotation.
                        switch (minecraft.player.getDirection()) {
                            case NORTH -> minZ = target.getZ() - sz + 1;
                            case SOUTH -> minZ = target.getZ();
                            case WEST -> minX = target.getX() - sx + 1;
                            case EAST -> minX = target.getX();
                        }
                        int oldPos2Y = b.get("y").getAsInt();
                        if (targetY > oldPos2Y) minY = targetY - sy + 1;
                        double x1 = minX, y1 = minY, z1 = minZ;
                        double x2 = minX + sx, y2 = minY + sy, z2 = minZ + sz;
                        LevelRenderer.renderLineBox(pose, lines, x1, y1, z1, x2, y2, z2, 1.0F, 0.55F, 0.05F, 1.0F);
                        if (MapperZonesSettings.showZoneNames()) {
                            String previewLabel = Component.translatable("message.mapperzones.move_preview").getString();
                            labels.add(new ZoneLabel((x1 + x2) / 2.0, y2 + 0.25, (z1 + z2) / 2.0, previewLabel, 0xFFFFAA33));
                        }
                    }
                } catch (Exception ignored) { }
            }
        }
        buffers.endBatch(RenderType.lines());
        // Billboard labels are drawn after the wireframes so their text does not interfere with line rendering.
        Font font = minecraft.font;
        for (ZoneLabel label : labels) {
            pose.pushPose();
            pose.translate(label.x, label.y, label.z);
            pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-0.025F, -0.025F, 0.025F);
            Component text = Component.literal(label.text);
            float textX = -font.width(text) / 2.0F;
            font.drawInBatch(text, textX, 0, label.color, false, pose.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, 0x50000000, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
        pose.popPose();
    }


    private static void drawRing(PoseStack pose, VertexConsumer lines, double cx, double y, double cz,
                                 double rx, double rz, float red, float green, float blue) {
        final int segments = 32;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.PI * 2.0 * i / segments;
            double a2 = Math.PI * 2.0 * (i + 1) / segments;
            drawSegment(pose, lines, cx + Math.cos(a1) * rx, y, cz + Math.sin(a1) * rz,
                    cx + Math.cos(a2) * rx, y, cz + Math.sin(a2) * rz, red, green, blue);
        }
    }

    private static void drawSegment(PoseStack pose, VertexConsumer lines,
                                    double x1, double y1, double z1, double x2, double y2, double z2,
                                    float red, float green, float blue) {
        var last = pose.last();
        lines.vertex(last.pose(), (float) x1, (float) y1, (float) z1).color(red, green, blue, 0.85F)
                .normal(last.normal(), 0.0F, 1.0F, 0.0F).endVertex();
        lines.vertex(last.pose(), (float) x2, (float) y2, (float) z2).color(red, green, blue, 0.85F)
                .normal(last.normal(), 0.0F, 1.0F, 0.0F).endVertex();
    }
}
