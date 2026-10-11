package net.mapperzones.network;

import com.google.gson.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkEvent;
import net.mapperzones.MapperZonesMod;
import net.mapperzones.data.MapperData;
import net.mapperzones.zone.TriggerZone;

import java.util.*;
import java.util.function.Supplier;

public final class ModNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(MapperZonesMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int id = 0;

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(id++, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle);
        CHANNEL.registerMessage(id++, OpenGui.class, OpenGui::encode, OpenGui::decode, OpenGui::handle);
        CHANNEL.registerMessage(id++, SaveZone.class, SaveZone::encode, SaveZone::decode, SaveZone::handle);
        CHANNEL.registerMessage(id++, DeleteZone.class, DeleteZone::encode, DeleteZone::decode, DeleteZone::handle);
        CHANNEL.registerMessage(id++, SetVariable.class, SetVariable::encode, SetVariable::decode, SetVariable::handle);
        CHANNEL.registerMessage(id++, UpdateVariable.class, UpdateVariable::encode, UpdateVariable::decode, UpdateVariable::handle);
        CHANNEL.registerMessage(id++, RemoveVariable.class, RemoveVariable::encode, RemoveVariable::decode, RemoveVariable::handle);
        CHANNEL.registerMessage(id++, RequestSync.class, RequestSync::encode, RequestSync::decode, RequestSync::handle);
    }

    public static void openGui(ServerPlayer p) {
        sync(p);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new OpenGui());
    }

    public static void sync(ServerPlayer p) {
        MapperData d = MapperData.get(p.serverLevel());
        JsonArray zones = new JsonArray();

        d.zones.values().forEach(z -> zones.add(z.toJson()));
        JsonObject vars = new JsonObject();

        d.globalVars.forEach(vars::addProperty);
        JsonObject playerVars = new JsonObject();

        d.playerVars.getOrDefault(p.getUUID(), Map.of()).forEach(playerVars::addProperty);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Snapshot(zones.toString(), vars.toString(), playerVars.toString()));
    }

    public record Snapshot(String zones, String globals, String players) {
        static void encode(Snapshot m, FriendlyByteBuf b) {
            b.writeUtf(m.zones, 32767);
            b.writeUtf(m.globals, 32767);
            b.writeUtf(m.players, 32767);
        }

        static Snapshot decode(FriendlyByteBuf b) {
            return new Snapshot(b.readUtf(32767), b.readUtf(32767), b.readUtf(32767));
        }

        static void handle(Snapshot m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> net.mapperzones.client.ClientNetworkHandler.load(m.zones, m.globals, m.players));
            });
            c.get().setPacketHandled(true);
        }
    }

    public record OpenGui() {
        static void encode(OpenGui m, FriendlyByteBuf b) {
        }

        static OpenGui decode(FriendlyByteBuf b) {
            return new OpenGui();
        }

        static void handle(OpenGui m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> net.mapperzones.client.ClientNetworkHandler.openGui()));
            c.get().setPacketHandled(true);
        }
    }

    public record SaveZone(String json) {
        static void encode(SaveZone m, FriendlyByteBuf b) {
            b.writeUtf(m.json, 32767);
        }

        static SaveZone decode(FriendlyByteBuf b) {
            return new SaveZone(b.readUtf(32767));
        }

        static void handle(SaveZone m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p == null || !p.hasPermissions(2)) return;
                try {
                    TriggerZone z = TriggerZone.fromJson(JsonParser.parseString(m.json).getAsJsonObject());
                    MapperData d = MapperData.get(p.serverLevel());
                    String key = z.name.toLowerCase(Locale.ROOT);
                    if (!d.zones.containsKey(key)) {
                        p.sendSystemMessage(Component.literal("Zone no longer exists; refresh the editor."));
                        return;
                    }
                    if (z.name.length() > 64 || z.enterActions.size() > 64 || z.exitActions.size() > 64 || z.stayActions.size() > 64 || z.conditions.size() > 64)
                        throw new IllegalArgumentException("Too many entries or name too long");
                    // Runtime state is server-authoritative. The editor's JSON is based on an
                    // older client snapshot and may contain stale oneTime/reset history. Never
                    // let a save resurrect cleared one-time flags or overwrite live occupancy.
                    TriggerZone current = d.zones.get(key);
                    if (current != null) {
                        z.triggeredPlayers.addAll(current.triggeredPlayers);
                        z.oneTimeEvents.addAll(current.oneTimeEvents);
                        z.currentlyInside.addAll(current.currentlyInside);
                        z.nextStayAt.putAll(current.nextStayAt);
                        z.lastTriggered.putAll(current.lastTriggered);
                        z.eventLastTriggered.putAll(current.eventLastTriggered);
                    }
                    d.zones.put(key, z);
                    d.dirty();
                    sync(p);
                    p.sendSystemMessage(Component.literal("Saved zone " + z.name));
                } catch (Exception ex) {
                    p.sendSystemMessage(Component.literal("Could not save zone: " + ex.getMessage()));
                    MapperZonesMod.LOGGER.warn("Rejected zone edit from {}", p.getGameProfile().getName(), ex);
                }
            });
            c.get().setPacketHandled(true);
        }
    }

    public record DeleteZone(String name) {
        static void encode(DeleteZone m, FriendlyByteBuf b) {
            b.writeUtf(m.name, 128);
        }

        static DeleteZone decode(FriendlyByteBuf b) {
            return new DeleteZone(b.readUtf(128));
        }

        static void handle(DeleteZone m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p == null || !p.hasPermissions(2)) return;
                MapperData d = MapperData.get(p.serverLevel());
                d.zones.remove(m.name.toLowerCase(Locale.ROOT));
                d.dirty();
                sync(p);
            });
            c.get().setPacketHandled(true);
        }
    }

    public record SetVariable(String scope, String name, String value) {
        static void encode(SetVariable m, FriendlyByteBuf b) {
            b.writeUtf(m.scope, 16);
            b.writeUtf(m.name, 128);
            b.writeUtf(m.value, 1024);
        }

        static SetVariable decode(FriendlyByteBuf b) {
            return new SetVariable(b.readUtf(16), b.readUtf(128), b.readUtf(1024));
        }

        static void handle(SetVariable m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();

                if (p == null || !p.hasPermissions(2)) return;
                MapperData d = MapperData.get(p.serverLevel());

                if (m.scope.equals("global")) d.globalVars.put(m.name, m.value);
                else if (m.scope.equals("player"))
                    d.playerVars.computeIfAbsent(p.getUUID(), u -> new LinkedHashMap<>()).put(m.name, m.value);
                else return;
                d.dirty();
                sync(p);
            });
            c.get().setPacketHandled(true);
        }
    }

    /** Atomically updates a variable, optionally renaming it or moving it between scopes. */
    public record UpdateVariable(String oldScope, String oldName, String newScope, String newName, String value) {
        static void encode(UpdateVariable m, FriendlyByteBuf b) {
            b.writeUtf(m.oldScope, 16);
            b.writeUtf(m.oldName, 128);
            b.writeUtf(m.newScope, 16);
            b.writeUtf(m.newName, 128);
            b.writeUtf(m.value, 1024);
        }

        static UpdateVariable decode(FriendlyByteBuf b) {
            return new UpdateVariable(b.readUtf(16), b.readUtf(128), b.readUtf(16), b.readUtf(128), b.readUtf(1024));
        }

        static void handle(UpdateVariable m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p == null || !p.hasPermissions(2)) return;
                if (!(m.oldScope.equals("global") || m.oldScope.equals("player"))
                        || !(m.newScope.equals("global") || m.newScope.equals("player"))
                        || m.oldName.isBlank() || m.newName.isBlank()
                        || m.oldName.length() > 128 || m.newName.length() > 128 || m.value.length() > 1024) {
                    p.sendSystemMessage(Component.translatable("message.mapperzones.variable_update_invalid"));
                    return;
                }

                MapperData d = MapperData.get(p.serverLevel());
                Map<String, String> oldMap = variableMap(d, m.oldScope, p);
                Map<String, String> newMap = variableMap(d, m.newScope, p);
                if (oldMap == null || newMap == null || !oldMap.containsKey(m.oldName)) {
                    p.sendSystemMessage(Component.translatable("message.mapperzones.variable_update_missing", m.oldName));
                    sync(p);
                    return;
                }

                boolean sameIdentity = m.oldScope.equals(m.newScope) && m.oldName.equals(m.newName);
                if (!sameIdentity && newMap.containsKey(m.newName)) {
                    p.sendSystemMessage(Component.translatable("message.mapperzones.variable_update_conflict", m.newScope, m.newName));
                    sync(p);
                    return;
                }

                oldMap.remove(m.oldName);
                if (oldMap.isEmpty() && m.oldScope.equals("player")) d.playerVars.remove(p.getUUID());
                variableMap(d, m.newScope, p).put(m.newName, m.value);
                d.dirty();
                sync(p);
                p.sendSystemMessage(Component.translatable("message.mapperzones.variable_updated", m.newScope, m.newName));
            });
            c.get().setPacketHandled(true);
        }
    }

    private static Map<String, String> variableMap(MapperData d, String scope, ServerPlayer p) {
        if (scope.equals("global")) return d.globalVars;
        if (scope.equals("player")) return d.playerVars.computeIfAbsent(p.getUUID(), u -> new LinkedHashMap<>());
        return null;
    }

    public record RemoveVariable(String scope, String name) {
        static void encode(RemoveVariable m, FriendlyByteBuf b) { b.writeUtf(m.scope, 16); b.writeUtf(m.name, 128); }
        static RemoveVariable decode(FriendlyByteBuf b) { return new RemoveVariable(b.readUtf(16), b.readUtf(128)); }
        static void handle(RemoveVariable m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p == null || !p.hasPermissions(2)) return;
                MapperData d = MapperData.get(p.serverLevel());
                boolean removed;
                if (m.scope.equals("global")) removed = d.globalVars.remove(m.name) != null;
                else if (m.scope.equals("player")) {
                    Map<String,String> vars = d.playerVars.get(p.getUUID());
                    removed = vars != null && vars.remove(m.name) != null;
                    if (vars != null && vars.isEmpty()) d.playerVars.remove(p.getUUID());
                } else return;
                if (removed) d.dirty();
                sync(p);
                p.sendSystemMessage(Component.literal(removed ? "Removed variable " + m.name : "Variable not found: " + m.name));
            });
            c.get().setPacketHandled(true);
        }
    }

    public record RequestSync() {
        static void encode(RequestSync m, FriendlyByteBuf b) {
        }

        static RequestSync decode(FriendlyByteBuf b) {
            return new RequestSync();
        }

        static void handle(RequestSync m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p != null && p.hasPermissions(2)) sync(p);
            });
            c.get().setPacketHandled(true);
        }
    }

    public static void saveZone(String json) {
        CHANNEL.sendToServer(new SaveZone(json));
    }

    public static void deleteZone(String name) {
        CHANNEL.sendToServer(new DeleteZone(name));
    }

    public static void setVariable(String scope, String name, String value) {
        CHANNEL.sendToServer(new SetVariable(scope, name, value));
    }

    public static void updateVariable(String oldScope, String oldName, String newScope, String newName, String value) {
        CHANNEL.sendToServer(new UpdateVariable(oldScope, oldName, newScope, newName, value));
    }

    public static void removeVariable(String scope, String name) { CHANNEL.sendToServer(new RemoveVariable(scope, name)); }

    public static void requestSync() {
        CHANNEL.sendToServer(new RequestSync());
    }
}
