package net.mapperzones.zone;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.*;

public final class TriggerZone {
    public enum Shape {
        BOX, SPHERE, CYLINDER
    }

    public final String name;
    public BlockPos pos1, pos2;
    public ResourceLocation dimension;
    public Shape shape = Shape.BOX;
    public boolean enabled = true, oneTime = false;
    public int stayInterval = 40, cooldownTicks = 0;
    public final List<String> enterActions = new ArrayList<>(), exitActions = new ArrayList<>(), stayActions = new ArrayList<>(), conditions = new ArrayList<>();
    /** Legacy list retained for compatibility; now records players whose enter event fired. */
    public final Set<UUID> triggeredPlayers = new HashSet<>();
    /** oneTime is tracked per player and event so enter, stay and exit can each fire once. */
    public final Set<String> oneTimeEvents = new HashSet<>();
    /** Runtime schedule: next stay event time, measured from entry and then repeated by interval. */
    public final Map<UUID, Long> nextStayAt = new HashMap<>();
    public final Map<UUID, Long> lastTriggered = new HashMap<>();
    /** Event-specific cooldowns prevent an entry cooldown from suppressing exit/stay events. */
    public final Map<String, Long> eventLastTriggered = new HashMap<>();
    public final Set<UUID> currentlyInside = new HashSet<>();

    public TriggerZone(String name, BlockPos a, BlockPos b, ResourceLocation dimension) {
        this.name = name;
        this.pos1 = a.immutable();
        this.pos2 = b.immutable();
        this.dimension = dimension;
    }

    public int minX() {
        return Math.min(pos1.getX(), pos2.getX());
    }

    public int minY() {
        return Math.min(pos1.getY(), pos2.getY());
    }

    public int minZ() {
        return Math.min(pos1.getZ(), pos2.getZ());
    }

    public int maxX() {
        return Math.max(pos1.getX(), pos2.getX());
    }

    public int maxY() {
        return Math.max(pos1.getY(), pos2.getY());
    }

    public int maxZ() {
        return Math.max(pos1.getZ(), pos2.getZ());
    }

    public int getSizeX() {
        return maxX() - minX() + 1;
    }

    public int getSizeY() {
        return maxY() - minY() + 1;
    }

    public int getSizeZ() {
        return maxZ() - minZ() + 1;
    }

    public AABB bounds() {
        return new AABB(minX(), minY(), minZ(), maxX() + 1, maxY() + 1, maxZ() + 1);
    }

    public boolean contains(ServerPlayer p) {
        double x = p.getX(), y = p.getY(), z = p.getZ();

        if (shape == Shape.BOX) return bounds().contains(x, y, z);
        double cx = (minX() + maxX() + 1) / 2.0, cz = (minZ() + maxZ() + 1) / 2.0;

        if (shape == Shape.SPHERE) {
            double cy = (minY() + maxY() + 1) / 2.0;
            // Treat the selected cuboid as the sphere's diameter bounds. This avoids
            // silently expanding a narrow selection to the largest dimension.
            double rx = Math.max(0.5, (maxX() - minX() + 1) / 2.0);
            double ry = Math.max(0.5, (maxY() - minY() + 1) / 2.0);
            double rz = Math.max(0.5, (maxZ() - minZ() + 1) / 2.0);
            return sq((x - cx) / rx) + sq((y - cy) / ry) + sq((z - cz) / rz) <= 1.0;
        }
        double rx = Math.max(0.5, (maxX() - minX() + 1) / 2.0);
        double rz = Math.max(0.5, (maxZ() - minZ() + 1) / 2.0);
        return sq((x - cx) / rx) + sq((z - cz) / rz) <= 1.0 && y >= minY() && y < maxY() + 1;
    }

    private static double sq(double v) {
        return v * v;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("schemaVersion", 1);
        o.addProperty("name", name);
        o.addProperty("dimension", dimension.toString());
        addPos(o, "pos1", pos1);
        addPos(o, "pos2", pos2);
        o.addProperty("shape", shape.name());
        o.addProperty("enabled", enabled);
        o.addProperty("oneTime", oneTime);
        o.addProperty("stayInterval", stayInterval);
        o.addProperty("cooldownTicks", cooldownTicks);
        addList(o, "enterActions", enterActions);
        addList(o, "exitActions", exitActions);
        addList(o, "stayActions", stayActions);
        addList(o, "conditions", conditions);
        JsonArray done = new JsonArray();
        triggeredPlayers.forEach(u -> done.add(u.toString()));
        o.add("triggeredPlayers", done);
        JsonArray oneTimeDone = new JsonArray();
        oneTimeEvents.forEach(oneTimeDone::add);
        o.add("oneTimeEvents", oneTimeDone);
        return o;
    }

    private static void addPos(JsonObject o, String key, BlockPos p) {
        JsonObject v = new JsonObject();
        v.addProperty("x", p.getX());
        v.addProperty("y", p.getY());
        v.addProperty("z", p.getZ());
        o.add(key, v);
    }

    private static void addList(JsonObject o, String k, List<String> l) {
        JsonArray a = new JsonArray();
        l.forEach(a::add);
        o.add(k, a);
    }

    public static TriggerZone fromJson(JsonObject o) {
        int schemaVersion = o.has("schemaVersion") ? o.get("schemaVersion").getAsInt() : 0; // 0 = legacy files
        if (schemaVersion < 0 || schemaVersion > 1)
            throw new IllegalArgumentException("Unsupported zone schema version: " + schemaVersion);
        String name = reqString(o, "name").trim();
        if (name.isEmpty() || name.length() > 64 || !name.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Zone name must be 1-64 characters: letters, digits, '_' or '-'.");
        ResourceLocation dim = ResourceLocation.tryParse(reqString(o, "dimension"));
        if (dim == null) throw new IllegalArgumentException("Invalid dimension");
        BlockPos a = readPos(o.getAsJsonObject("pos1")), b = readPos(o.getAsJsonObject("pos2"));
        TriggerZone z = new TriggerZone(name, a, b, dim);
        try {
            z.shape = Shape.valueOf(o.has("shape") ? o.get("shape").getAsString() : "BOX");
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid shape");
        }
        z.enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
        z.oneTime = o.has("oneTime") && o.get("oneTime").getAsBoolean();
        z.stayInterval = Math.max(1, Math.min(72000, o.has("stayInterval") ? o.get("stayInterval").getAsInt() : 40));
        z.cooldownTicks = Math.max(0, Math.min(72000, o.has("cooldownTicks") ? o.get("cooldownTicks").getAsInt() : 0));

        readList(o, "enterActions", z.enterActions);
        readList(o, "exitActions", z.exitActions);
        readList(o, "stayActions", z.stayActions);
        readList(o, "conditions", z.conditions);
        validateList("enterActions", z.enterActions);
        validateList("exitActions", z.exitActions);
        validateList("stayActions", z.stayActions);
        validateList("conditions", z.conditions);

        if (o.has("triggeredPlayers") && o.get("triggeredPlayers").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("triggeredPlayers")) {
                try {
                    z.triggeredPlayers.add(UUID.fromString(e.getAsString()));
                } catch (Exception ignored) {
                }
            }
        if (o.has("oneTimeEvents") && o.get("oneTimeEvents").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("oneTimeEvents")) {
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                    String key = e.getAsString();
                    if (key.length() <= 64) z.oneTimeEvents.add(key);
                }
            }
        // Migrate legacy oneTime saves: previously triggeredPlayers meant enter had fired.
        for (UUID id : z.triggeredPlayers) z.oneTimeEvents.add(id + ":enter");
        return z;
    }

    private static String reqString(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonPrimitive()) throw new IllegalArgumentException("Missing " + k);
        return o.get(k).getAsString();
    }

    private static BlockPos readPos(JsonObject o) {
        if (o == null) throw new IllegalArgumentException("Missing position");
        return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
    }

    private static void readList(JsonObject o, String k, List<String> target) {
        if (!o.has(k)) return;
        if (!o.get(k).isJsonArray()) throw new IllegalArgumentException("'" + k + "' must be a JSON array.");
        JsonArray values = o.getAsJsonArray(k);
        if (values.size() > 512) throw new IllegalArgumentException("'" + k + "' contains too many entries (max 512).");
        for (JsonElement e : values) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("'" + k + "' entries must be strings.");
            target.add(e.getAsString());
        }
    }

    private static void validateList(String key, List<String> values) {
        for (String value : values) {
            if (value.length() > 2048) throw new IllegalArgumentException("Entry in '" + key + "' exceeds 2048 characters.");
        }
    }
}
