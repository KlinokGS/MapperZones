package net.mapperzones.data;

import com.google.gson.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.mapperzones.zone.TriggerZone;

import java.util.*;

public final class MapperData extends SavedData {
    private static final String KEY = "mapperzones_data";
    public final Map<String, TriggerZone> zones = new LinkedHashMap<>();
    public final Map<String, String> globalVars = new LinkedHashMap<>();
    public final Map<UUID, Map<String, String>> playerVars = new HashMap<>();
    public final Map<UUID, CompoundTag> savedInventories = new HashMap<>();
    /** Delayed actions are persisted so server restarts do not silently discard them. */
    public final List<CompoundTag> pendingActions = new ArrayList<>();

    public static MapperData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(MapperData::load, MapperData::new, KEY);
    }

    public static MapperData load(CompoundTag tag) {
        MapperData d = new MapperData();
        if (tag.contains("zones", 10)) {
            CompoundTag zt = tag.getCompound("zones");
            for (String key : zt.getAllKeys()) {
                try {
                    TriggerZone z = TriggerZone.fromJson(JsonParser.parseString(zt.getString(key)).getAsJsonObject());
                    String normalized = z.name.toLowerCase(Locale.ROOT);
                    if (d.zones.putIfAbsent(normalized, z) != null)
                        net.mapperzones.MapperZonesMod.LOGGER.warn("Skipping duplicate zone '{}' while loading saved data", z.name);
                } catch (Exception ex) {
                    net.mapperzones.MapperZonesMod.LOGGER.warn("Skipping invalid saved zone '{}': {}", key, ex.getMessage());
                }
            }
        }
        if (tag.contains("global", 10)) {
            CompoundTag v = tag.getCompound("global");
            for (String k : v.getAllKeys()) d.globalVars.put(k, v.getString(k));
        }
        if (tag.contains("players", 10)) {
            CompoundTag ps = tag.getCompound("players");
            for (String id : ps.getAllKeys())
                try {
                    UUID u = UUID.fromString(id);
                    Map<String, String> vars = new LinkedHashMap<>();
                    CompoundTag t = ps.getCompound(id);
                    for (String k : t.getAllKeys()) vars.put(k, t.getString(k));
                    d.playerVars.put(u, vars);
                } catch (Exception ignored) {
                }
        }
        if (tag.contains("pendingActions", 9)) {
            ListTag pending = tag.getList("pendingActions", 10);
            for (int i = 0; i < pending.size() && i < 10000; i++) {
                CompoundTag action = pending.getCompound(i);
                if (action.hasUUID("player") && action.contains("at", 99) && action.contains("action", 8)
                        && action.getString("action").length() <= 2048) {
                    d.pendingActions.add(action.copy());
                } else {
                    net.mapperzones.MapperZonesMod.LOGGER.warn("Skipping malformed persisted MapperZones delayed action at index {}", i);
                }
            }
        }
        if (tag.contains("inventories", 10)) {
            CompoundTag inv = tag.getCompound("inventories");
            for (String id : inv.getAllKeys())
                try {
                    d.savedInventories.put(UUID.fromString(id), inv.getCompound(id));
                } catch (Exception ignored) {
                }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag zt = new CompoundTag();

        zones.forEach((k, z) -> zt.putString(k, z.toJson().toString()));
        tag.put("zones", zt);
        CompoundTag g = new CompoundTag();

        globalVars.forEach(g::putString);
        tag.put("global", g);
        CompoundTag ps = new CompoundTag();

        playerVars.forEach((id, vars) -> {
            CompoundTag t = new CompoundTag();
            vars.forEach(t::putString);
            ps.put(id.toString(), t);
        });

        tag.put("players", ps);
        CompoundTag inv = new CompoundTag();

        savedInventories.forEach((id, t) -> inv.put(id.toString(), t));
        tag.put("inventories", inv);
        ListTag pending = new ListTag();
        for (CompoundTag action : pendingActions) pending.add(action.copy());
        tag.put("pendingActions", pending);
        return tag;
    }

    public void dirty() {
        setDirty();
    }

    public String variable(UUID player, String key) {
        if (key.startsWith("player."))
            return playerVars.getOrDefault(player, Map.of()).getOrDefault(key.substring(7), "");
        return globalVars.getOrDefault(key, "");
    }

    public String expand(UUID player, String text) {
        String out = text;
        for (var e : globalVars.entrySet()) out = out.replace("${" + e.getKey() + "}", e.getValue());
        for (var e : playerVars.getOrDefault(player, Map.of()).entrySet())
            out = out.replace("${player." + e.getKey() + "}", e.getValue());
        // The player name is contextual and is expanded by ZoneManager when a player is available.
        return out;
    }
}
