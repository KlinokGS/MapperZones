package net.mapperzones.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.registries.ForgeRegistries;
import net.mapperzones.data.MapperData;

import java.util.*;

public final class ZoneManager {
    private static final PriorityQueue<RedstoneRestore> REDSTONE = new PriorityQueue<>(Comparator.comparingLong(RedstoneRestore::at));
    private static final Map<UUID, Long> FREEZE_UNTIL = new HashMap<>();

    private record RedstoneRestore(long at, ResourceLocation dim, BlockPos pos, BlockState old) {
    }

    private ZoneManager() {
    }

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        MapperData data = MapperData.get(overworld);
        long now = server.overworld().getGameTime();
        int processedScheduled = 0;
        for (int i = data.pendingActions.size() - 1; i >= 0 && processedScheduled < 200; i--) {
            CompoundTag pending = data.pendingActions.get(i);
            if (pending.getLong("at") > now) continue;
            UUID playerId;
            try { playerId = UUID.fromString(pending.getString("player")); }
            catch (IllegalArgumentException ex) { data.pendingActions.remove(i); data.dirty(); continue; }
            ServerPlayer scheduledPlayer = server.getPlayerList().getPlayer(playerId);
            if (scheduledPlayer == null || !scheduledPlayer.isAlive()) continue; // run when the player is online again
            data.pendingActions.remove(i);
            data.dirty();
            processedScheduled++;
            try { executeAction(scheduledPlayer, data, pending.getString("action")); }
            catch (Exception ex) { net.mapperzones.MapperZonesMod.LOGGER.warn("Scheduled action failed for {}: {}", scheduledPlayer.getGameProfile().getName(), pending.getString("action"), ex); }
        }
        RedstoneRestore r;
        int guard = 0;
        while ((r = REDSTONE.peek()) != null && r.at <= now && guard++ < 200) {
            REDSTONE.poll();
            ServerLevel l = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, r.dim));
            if (l != null && l.hasChunkAt(r.pos) && l.getBlockState(r.pos).is(Blocks.REDSTONE_BLOCK))
                l.setBlock(r.pos, r.old, 3);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Long frozen = FREEZE_UNTIL.get(player.getUUID());
            if (frozen != null) {
                if (frozen <= now) FREEZE_UNTIL.remove(player.getUUID());
                else {
                    player.setDeltaMovement(0, player.getDeltaMovement().y < 0 ? player.getDeltaMovement().y : 0, 0);
                    player.hurtMarked = true;
                }
            }
        }
        if (now % 5 != 0) return;
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) online.add(player.getUUID());
        for (TriggerZone z : new ArrayList<>(data.zones.values())) {
            // Disabled zones must not retain stale occupancy state.
            if (!z.enabled) {
                z.currentlyInside.clear();
                z.nextStayAt.clear();
                continue;
            }
            // A player who logged out must not remain marked as inside indefinitely.
            z.currentlyInside.removeIf(id -> {
                if (!online.contains(id)) { z.nextStayAt.remove(id); return true; }
                return false;
            });
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID id = player.getUUID();
                boolean was = z.currentlyInside.contains(id);
                boolean sameDimension = z.dimension.equals(player.level().dimension().location());
                boolean inside = sameDimension && z.contains(player);
                if (inside && !was) {
                    z.currentlyInside.add(id);
                    // The first stay event is scheduled relative to this player's entry time.
                    z.nextStayAt.put(id, now + Math.max(1, z.stayInterval));
                    if (canTrigger(z, player, data, now, "enter")) runActions(player, data, z.enterActions);
                } else if (!inside && was) {
                    z.currentlyInside.remove(id);
                    z.nextStayAt.remove(id);
                    // Dimension changes count as leaving the old zone.
                    if (canTrigger(z, player, data, now, "exit")) runActions(player, data, z.exitActions);
                } else if (inside && was && now >= z.nextStayAt.getOrDefault(id, now + Math.max(1, z.stayInterval))) {
                    long interval = Math.max(1, z.stayInterval);
                    // Advance from the previous deadline, avoiding drift caused by the 5-tick scan.
                    long next = z.nextStayAt.getOrDefault(id, now) + interval;
                    while (next <= now) next += interval;
                    z.nextStayAt.put(id, next);
                    if (canTrigger(z, player, data, now, "stay")) runActions(player, data, z.stayActions);
                }
            }
        }
        FREEZE_UNTIL.keySet().removeIf(id -> !online.contains(id));
        for (TriggerZone z : data.zones.values()) {
            z.nextStayAt.keySet().removeIf(id -> !online.contains(id));
            z.eventLastTriggered.keySet().removeIf(key -> {
                int split = key.indexOf(':');
                if (split < 0) return true;
                try { return !online.contains(UUID.fromString(key.substring(0, split))); }
                catch (IllegalArgumentException ex) { return true; }
            });
        }
    }

    private static boolean canTrigger(TriggerZone z, ServerPlayer p, MapperData d, long now, String event) {
        String oneTimeKey = p.getUUID() + ":" + event;
        if (z.oneTime && z.oneTimeEvents.contains(oneTimeKey)) return false;
        String cooldownKey = p.getUUID() + ":" + event + ":" + z.name.toLowerCase(Locale.ROOT);
        long last = z.eventLastTriggered.getOrDefault(cooldownKey, Long.MIN_VALUE / 2);
        if (now - last < z.cooldownTicks) return false;
        if (!conditionsPass(z, p, d, event)) return false;
        z.eventLastTriggered.put(cooldownKey, now);
        z.lastTriggered.put(p.getUUID(), now); // retained for compatibility with existing commands
        if (z.oneTime) {
            z.oneTimeEvents.add(oneTimeKey);
            if (event.equals("enter")) z.triggeredPlayers.add(p.getUUID());
            d.dirty();
        }
        return true;
    }

    /** Restore temporary blocks and clear session-only work when the integrated/dedicated server stops. */
    public static void onServerStopping(MinecraftServer server) {
        RedstoneRestore restore;
        while ((restore = REDSTONE.poll()) != null) {
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, restore.dim));
            if (level != null && level.hasChunkAt(restore.pos) && level.getBlockState(restore.pos).is(Blocks.REDSTONE_BLOCK))
                level.setBlock(restore.pos, restore.old, 3);
        }
        FREEZE_UNTIL.clear();
    }

    public static boolean conditionsPass(TriggerZone z, ServerPlayer p, MapperData d) {
        return conditionsPass(z, p, d, null);
    }

    /**
     * Conditions without a prefix apply to every event. Event-specific conditions use
     * action.enter:, action.stay: or action.exit: and are evaluated only for that event.
     */
    private static boolean conditionsPass(TriggerZone z, ServerPlayer p, MapperData d, String event) {
        // Each applicable configured line is an independent requirement; expressions within a line may use && and ||.
        for (String raw : z.conditions) {
            String c = raw.trim();
            if (c.isEmpty()) continue;
            java.util.regex.Matcher eventCondition = java.util.regex.Pattern
                    .compile("(?i)^action\\.(enter|stay|exit)\\s*:\\s*(.*)$").matcher(c);
            if (eventCondition.matches()) {
                String targetEvent = eventCondition.group(1).toLowerCase(Locale.ROOT);
                if (event == null || !targetEvent.equals(event)) continue;
                c = eventCondition.group(2).trim();
                if (c.isEmpty()) return false;
            } else if (c.regionMatches(true, 0, "action.", 0, 7)) {
                // A malformed action-scoped condition should fail closed, not silently act as a global condition.
                return false;
            }
            try {
                // Preserve the original shorthand conditions for existing zone files.
                String[] a = c.split("\\s+", 5);
                if (!c.contains("&&") && !c.contains("||")) {
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "item" -> {
                        if (a.length < 2) return false;
                        ResourceLocation id = ResourceLocation.tryParse(a[1]);
                        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
                        boolean found = p.getInventory().items.stream().anyMatch(st -> !st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).equals(id));
                        if (!found) return false;
                        continue;
                    }
                    case "team" -> {
                        if (a.length < 2 || p.getTeam() == null || !p.getTeam().getName().equals(a[1])) return false;
                        continue;
                    }
                    case "time" -> {
                        if (a.length < 3 || !compare(p.level().getDayTime() % 24000, a[1], Double.parseDouble(a[2]))) return false;
                        continue;
                    }
                    case "weather" -> {
                        if (a.length < 2) return false;
                        boolean ok = switch (a[1].toLowerCase(Locale.ROOT)) {
                            case "clear" -> !p.level().isRaining();
                            case "rain" -> p.level().isRaining() && !p.level().isThundering();
                            case "thunder" -> p.level().isThundering();
                            default -> false;
                        };
                        if (!ok) return false;
                        continue;
                    }
                    case "dim" -> {
                        if (a.length < 2 || !p.level().dimension().location().toString().equals(a[1])) return false;
                        continue;
                    }
                    case "var" -> {
                        if (a.length < 5) return false;
                        String varKey = a[1].equalsIgnoreCase("player") ? "player." + a[2] : a[2];
                        if (!d.hasVariable(p.getUUID(), varKey)) return false;
                        String val = d.variable(p.getUUID(), varKey);
                        String op = a[3], rhs = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
                        if (!compareValues(val, op, rhs)) return false;
                        continue;
                    }
                    default -> { /* New expression syntax below. */ }
                }
                }
                if (!new ConditionParser(c, p, d).parse()) return false;
            } catch (Exception ex) {
                return false;
            }
        }
        return true;
    }

    private static boolean compareValues(String left, String op, String right) {
        try { return compare(Double.parseDouble(left), op, Double.parseDouble(right)); }
        catch (NumberFormatException ignored) {
            int cmp = left.compareTo(right);
            return switch (op) {
                case "==" -> left.equals(right);
                case "!=" -> !left.equals(right);
                case ">" -> cmp > 0;
                case "<" -> cmp < 0;
                case ">=" -> cmp >= 0;
                case "<=" -> cmp <= 0;
                default -> false;
            };
        }
    }

    /** Small expression parser for player/global variables, comparisons, parentheses, && and ||. */
    private static final class ConditionParser {
        private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile(
                "\\s*(>=|<=|==|!=|&&|\\|\\||[()!<>]|\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)|[A-Za-z_][A-Za-z0-9_./:-]*)");
        private final List<String> tokens = new ArrayList<>();
        private final ServerPlayer player;
        private final MapperData data;
        private int index;

        ConditionParser(String expression, ServerPlayer player, MapperData data) {
            this.player = player;
            this.data = data;
            expression = expression.trim();
            java.util.regex.Matcher matcher = TOKEN.matcher(expression);
            int end = 0;
            while (end < expression.length()) {
                matcher.region(end, expression.length());
                if (!matcher.lookingAt()) throw new IllegalArgumentException("Unexpected token near: " + expression.substring(end));
                tokens.add(matcher.group(1));
                end = matcher.end();
            }
            if (tokens.isEmpty()) throw new IllegalArgumentException("Empty condition");
        }

        boolean parse() {
            boolean result = parseOr();
            if (index != tokens.size()) throw new IllegalArgumentException("Unexpected token: " + tokens.get(index));
            return result;
        }

        private boolean parseOr() {
            boolean value = parseAnd();
            while (take("||")) { boolean right = parseAnd(); value = value || right; }
            return value;
        }

        private boolean parseAnd() {
            boolean value = parseComparison();
            while (take("&&")) { boolean right = parseComparison(); value = value && right; }
            return value;
        }

        private boolean parseComparison() {
            Object left = parseAtom();
            if (index < tokens.size() && Set.of("==", "!=", ">", "<", ">=", "<=").contains(tokens.get(index))) {
                String op = tokens.get(index++);
                Object right = parseAtom();
                return compareObjects(left, op, right);
            }
            return truthy(left);
        }

        private Object parseAtom() {
            if (take("!")) return !truthy(parseAtom());
            if (take("(")) {
                boolean value = parseOr();
                if (!take(")")) throw new IllegalArgumentException("Missing closing parenthesis");
                return value;
            }
            if (index >= tokens.size()) throw new IllegalArgumentException("Expected value");
            String token = tokens.get(index++);
            if (token.equalsIgnoreCase("exists") && take("(")) {
                if (index >= tokens.size()) throw new IllegalArgumentException("Expected variable name in exists(...)");
                String variableName = tokens.get(index++);
                if (!take(")")) throw new IllegalArgumentException("Missing ')' after exists(...)");
                return data.hasVariable(player.getUUID(), variableName);
            }
            if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'")))
                return token.substring(1, token.length() - 1).replace("\\\"", "\"").replace("\\'", "'");
            if (token.equalsIgnoreCase("true")) return true;
            if (token.equalsIgnoreCase("false")) return false;
            try { return Double.parseDouble(token); } catch (NumberFormatException ignored) { }
            return resolve(token);
        }

        private Object resolve(String key) {
            String k = key.toLowerCase(Locale.ROOT);
            return switch (k) {
                // Backwards-compatible aliases.
                case "health", "player.health" -> (double) player.getHealth();
                case "max_health", "player.max_health" -> (double) player.getMaxHealth();
                case "absorption", "player.absorption" -> (double) player.getAbsorptionAmount();
                case "food", "player.food" -> (double) player.getFoodData().getFoodLevel();
                case "saturation", "player.saturation" -> (double) player.getFoodData().getSaturationLevel();
                case "level", "player.level" -> (double) player.experienceLevel;
                case "player.experience" -> (double) player.experienceProgress;
                case "player.name" -> player.getGameProfile().getName();
                case "player.gamemode" -> player.gameMode.getGameModeForPlayer().getName();
                case "player.uuid" -> player.getUUID().toString();
                case "player.x" -> player.getX();
                case "player.y" -> player.getY();
                case "player.z" -> player.getZ();
                case "player.dimension" -> player.level().dimension().location().toString();
                case "player.armor" -> (double) player.getArmorValue();
                case "player.air" -> (double) player.getAirSupply();
                case "player.max_air" -> (double) player.getMaxAirSupply();
                case "player.on_ground" -> player.onGround();
                case "player.sprinting" -> player.isSprinting();
                case "player.sneaking" -> player.isCrouching();
                case "player.alive" -> player.isAlive();
                case "player.in_water" -> player.isInWater();
                case "player.underwater" -> player.isUnderWater();
                case "player.invisible" -> player.isInvisible();
                case "player.sleeping" -> player.isSleeping();
                case "player.team" -> player.getTeam() == null ? "" : player.getTeam().getName();
                case "time" -> (double) (player.level().getDayTime() % 24000);
                case "dimension", "dim" -> player.level().dimension().location().toString();
                case "weather" -> player.level().isThundering() ? "thunder" : player.level().isRaining() ? "rain" : "clear";
                default -> {
                    if (k.startsWith("global.")) {
                        String name = key.substring(7);
                        if (!data.globalVars.containsKey(name)) throw new IllegalArgumentException("Unknown global variable: " + name);
                        yield data.globalVars.get(name);
                    }
                    if (k.startsWith("player.")) {
                        String name = key.substring(7);
                        if (!data.playerVars.getOrDefault(player.getUUID(), Map.of()).containsKey(name))
                            throw new IllegalArgumentException("Unknown player variable: " + name);
                        yield data.playerVars.get(player.getUUID()).get(name);
                    }
                    if (data.globalVars.containsKey(key)) yield data.globalVars.get(key);
                    if (data.playerVars.getOrDefault(player.getUUID(), Map.of()).containsKey(key))
                        yield data.playerVars.get(player.getUUID()).get(key);
                    throw new IllegalArgumentException("Unknown variable: " + key);
                }
            };
        }

        private boolean compareObjects(Object left, String op, Object right) {
            if (left instanceof Boolean || right instanceof Boolean) {
                boolean l = truthy(left), r = truthy(right);
                return switch (op) { case "==" -> l == r; case "!=" -> l != r; default -> false; };
            }
            if (left instanceof Number ln && right instanceof Number rn)
                return compare(ln.doubleValue(), op, rn.doubleValue());
            String l = String.valueOf(left), r = String.valueOf(right);
            return compareValues(l, op, r);
        }

        private boolean truthy(Object value) {
            if (value instanceof Boolean b) return b;
            if (value instanceof Number n) return n.doubleValue() != 0.0;
            return value != null && !String.valueOf(value).isEmpty() && !String.valueOf(value).equalsIgnoreCase("false");
        }

        private boolean take(String expected) {
            if (index < tokens.size() && tokens.get(index).equals(expected)) { index++; return true; }
            return false;
        }
    }

    private static boolean compare(double x, String op, double y) {
        return switch (op) {
            case "==" -> x == y;
            case "!=" -> x != y;
            case ">" -> x > y;
            case "<" -> x < y;
            case ">=" -> x >= y;
            case "<=" -> x <= y;
            default -> false;
        };
    }

    public static void runActions(ServerPlayer p, MapperData data, List<String> actions) {
        for (String a : new ArrayList<>(actions))
            try {
                executeAction(p, data, a);
            } catch (Exception ex) {
                net.mapperzones.MapperZonesMod.LOGGER.warn("Action failed for {}: {}", p.getGameProfile().getName(), a, ex);
            }
    }

    public static void executeAction(ServerPlayer p, MapperData data, String raw) {
        if (raw == null || raw.isBlank()) return;
        String input = raw.trim();
        String[] a = input.split("\\s+");
        String type = a[0].toLowerCase(Locale.ROOT);
        String tail = input.length() > a[0].length() ? input.substring(a[0].length()).trim() : "";
        // Accept both legacy whitespace syntax (sound minecraft:...) and compact
        // builder syntax (sound:minecraft:...). Only known action prefixes are split;
        // resource identifiers such as minecraft:flame remain intact.
        int colon = a[0].indexOf(':');
        if (colon > 0) {
            String prefix = a[0].substring(0, colon).toLowerCase(Locale.ROOT);
            if (Set.of("command", "sound", "particle", "effect", "teleport", "freeze", "inventory", "redstone", "zone", "delay").contains(prefix)) {
                type = prefix;
                String firstValue = a[0].substring(colon + 1);
                tail = firstValue + (tail.isEmpty() ? "" : " " + tail);
                if (type.equals("command")) {
                    runCommand(p, tail, data);
                    return;
                }
                a = (type + (tail.isEmpty() ? "" : " " + tail)).split("\\s+");
            }
        }
        switch (type) {
            case "delay" -> {
                int split = tail.indexOf(' ');
                if (split > 0) {
                    int ticks = Math.max(1, Math.min(72000, Integer.parseInt(tail.substring(0, split))));
                    String delayed = tail.substring(split + 1).trim();
                    if (!delayed.isEmpty()) {
                        if (data.pendingActions.size() >= 10000) {
                            net.mapperzones.MapperZonesMod.LOGGER.warn("MapperZones scheduled-action limit reached; dropping delayed action");
                            return;
                        }
                        CompoundTag pending = new CompoundTag();
                        pending.putLong("at", p.getServer().overworld().getGameTime() + ticks);
                        pending.putString("player", p.getUUID().toString());
                        pending.putString("action", delayed);
                        data.pendingActions.add(pending);
                        data.dirty();
                    }
                }
            }
            case "command" -> runCommand(p, tail, data);
            case "sound" -> {
                if (a.length < 2) return;
                ResourceLocation id = ResourceLocation.tryParse(a[1]);
                SoundEvent snd = id == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(id);
                if (snd != null) {
                    float vol = a.length > 2 ? Float.parseFloat(a[2]) : 1f, pitch = a.length > 3 ? Float.parseFloat(a[3]) : 1f;
                    p.level().playSound(null, p.blockPosition(), snd, net.minecraft.sounds.SoundSource.PLAYERS, vol, pitch);
                }
            }
            case "particle" -> {
                if (a.length < 2) return;
                ResourceLocation id = ResourceLocation.tryParse(a[1]);
                ParticleType<?> typeP = id == null ? null : BuiltInRegistries.PARTICLE_TYPE.get(id);
                int count = a.length > 2 ? Math.max(1, Math.min(200, Integer.parseInt(a[2]))) : 12;
                if (typeP instanceof SimpleParticleType simple)
                    p.serverLevel().sendParticles(simple, p.getX(), p.getY() + 1, p.getZ(), count, .35, .5, .35, .02);
            }
            case "effect" -> {
                if (a.length < 2) return;
                ResourceLocation id = ResourceLocation.tryParse(a[1]);
                MobEffect effect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
                if (effect != null)
                    p.addEffect(new MobEffectInstance(effect, (a.length > 2 ? Integer.parseInt(a[2]) : 5) * 20, a.length > 3 ? Integer.parseInt(a[3]) : 0));
            }
            case "teleport" -> {
                if (a.length >= 4)
                    p.teleportTo(Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]));
            }
            case "freeze" -> {
                int ticks = a.length > 1 ? Math.max(1, Math.min(12000, Integer.parseInt(a[1]))) : 20;
                FREEZE_UNTIL.merge(p.getUUID(), p.getServer().overworld().getGameTime() + ticks, Math::max);
            }
            case "inventory" -> {
                if (a.length < 2) return;
                UUID id = p.getUUID();
                if (a[1].equalsIgnoreCase("save")) {
                    CompoundTag t = new CompoundTag();
                    t.put("items", p.getInventory().save(new ListTag()));
                    data.savedInventories.put(id, t);
                    data.dirty();
                } else if (a[1].equalsIgnoreCase("restore")) {
                    CompoundTag t = data.savedInventories.get(id);
                    if (t != null && t.contains("items", 9)) {
                        p.getInventory().clearContent();
                        p.getInventory().load(t.getList("items", 10));
                        p.inventoryMenu.broadcastChanges();
                    }
                }
            }
            case "redstone" -> {
                if (a.length < 5) return;
                BlockPos pos = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
                int ticks = Math.max(1, Math.min(72000, Integer.parseInt(a[4])));
                ServerLevel l = p.serverLevel();
                BlockState old = l.getBlockState(pos);
                l.setBlock(pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                if (REDSTONE.size() >= 10000) {
                    // Avoid leaving an untracked temporary block behind when overloaded.
                    l.setBlock(pos, old, 3);
                    net.mapperzones.MapperZonesMod.LOGGER.warn("MapperZones temporary-redstone limit reached; refusing action");
                    return;
                }
                REDSTONE.add(new RedstoneRestore(p.getServer().overworld().getGameTime() + ticks, l.dimension().location(), pos, old));
            }
            case "zone" -> {
                if (a.length < 3) return;
                TriggerZone z = data.zones.get(a[2].toLowerCase(Locale.ROOT));
                if (z == null) return;
                switch (a[1].toLowerCase(Locale.ROOT)) {
                    case "enable" -> z.enabled = true;
                    case "disable" -> { z.enabled = false; z.currentlyInside.clear(); z.nextStayAt.clear(); }
                    case "reset" -> {
                        z.triggeredPlayers.clear();
                        z.oneTimeEvents.clear();
                        z.lastTriggered.clear();
                        z.eventLastTriggered.clear();
                        z.currentlyInside.clear();
                        z.nextStayAt.clear();
                    }
                    default -> {
                        return;
                    }
                }
                data.dirty();
            }
            default -> {
                if (raw.startsWith("command:")) runCommand(p, raw.substring(8), data);
            }
        }
    }

    private static void runCommand(ServerPlayer p, String cmd, MapperData d) {
        String expanded = expand(d, p, cmd).trim();
        if (expanded.startsWith("/")) expanded = expanded.substring(1);
        p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), expanded);
    }

    public static String expand(MapperData d, ServerPlayer p, String text) {
        String out = text.replace("${player.name}", p.getGameProfile().getName());
        for (var e : d.globalVars.entrySet()) out = out.replace("${" + e.getKey() + "}", e.getValue());
        for (var e : d.playerVars.getOrDefault(p.getUUID(), Map.of()).entrySet())
            out = out.replace("${player." + e.getKey() + "}", e.getValue());
        return out;
    }
}
