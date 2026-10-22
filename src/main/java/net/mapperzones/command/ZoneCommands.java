package net.mapperzones.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.mapperzones.data.MapperData;
import net.mapperzones.config.MapperZonesSettings;
import net.mapperzones.item.ModItems;
import net.mapperzones.item.ZoneWandItem;
import net.mapperzones.network.ModNetwork;
import net.mapperzones.zone.TriggerZone;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Registers the /mapperzones command tree.
 */
public final class ZoneCommands {
    private ZoneCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        MapperZonesSettings.load();
        dispatcher.register(buildRoot("mapperzones"));
        if (MapperZonesSettings.shortCommandEnabled()) dispatcher.register(buildRoot("mz"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String literal) {
        return Commands.literal(literal)
                .requires(source -> source.hasPermission(2))
                .executes(context -> openGui(context.getSource()))
                .then(Commands.literal("gui").executes(context -> openGui(context.getSource())))
                .then(Commands.literal("wand").executes(context -> giveWand(context.getSource())))
                .then(Commands.literal("list").executes(context -> listZones(context.getSource())))
                .then(createCommand())
                .then(removeCommand())
                .then(enableCommand())
                .then(resetCommand())
                .then(shapeCommand())
                .then(actionCommand())
                .then(conditionCommand())
                .then(exportCommand())
                .then(importCommand())
                .then(variableCommand());
    }

    private static int openGui(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ModNetwork.openGui(player);
        return 1;
    }

    private static int giveWand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.ZONE_WAND.get()));
        player.sendSystemMessage(Component.translatable("message.mapperzones.wand_given").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int listZones(CommandSourceStack source) {
        MapperData data = MapperData.get(source.getLevel());
        if (data.zones.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.mapperzones.no_zones"), false);
            return 0;
        }
        for (TriggerZone zone : data.zones.values()) {
            source.sendSuccess(() -> Component.literal(zone.name + " [" + zone.shape + "] " + (zone.enabled ? "enabled" : "disabled") + " @ " + zone.dimension), false);
        }
        return data.zones.size();
    }

    private static void syncIfPlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player && player.hasPermissions(2)) ModNetwork.sync(player);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("create").then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            UUID playerId = player.getUUID();
            BlockPos first = ZoneWandItem.FIRST.get(playerId);
            BlockPos second = ZoneWandItem.SECOND.get(playerId);

            if (first == null || second == null) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.select_points"));
                return 0;
            }

            String name = StringArgumentType.getString(context, "name");
            String key = normalizeName(name);
            MapperData data = MapperData.get(player.serverLevel());

            if (data.zones.containsKey(key)) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.zone_exists", name));
                return 0;
            }

            TriggerZone zone = new TriggerZone(name, first, second, player.level().dimension().location());
            data.zones.put(key, zone);
            data.dirty();
            ZoneWandItem.FIRST.remove(playerId);
            ZoneWandItem.SECOND.remove(playerId);
            context.getSource().sendSuccess(() -> Component.translatable("message.mapperzones.zone_created", name, zone.getSizeX(), zone.getSizeY(), zone.getSizeZ()), true);
            ModNetwork.sync(player);
            return 1;
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> removeCommand() {
        return Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(MapperData.get(context.getSource().getLevel()).zones.keySet(), builder)).executes(context -> {
            MapperData data = MapperData.get(context.getSource().getLevel());
            String name = StringArgumentType.getString(context, "name");
            TriggerZone removed = data.zones.remove(normalizeName(name));

            if (removed == null) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.unknown_zone", name));
                return 0;
            }
            data.dirty();
            syncIfPlayer(context.getSource());
            context.getSource().sendSuccess(() -> Component.translatable("message.mapperzones.zone_removed", removed.name), true);
            return 1;
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> enableCommand() {
        return Commands.literal("enable").then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> {
            MapperData data = MapperData.get(context.getSource().getLevel());
            String name = StringArgumentType.getString(context, "name");
            TriggerZone zone = findZone(data, name);

            if (zone == null) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.unknown_zone", name));
                return 0;
            }
            zone.enabled = BoolArgumentType.getBool(context, "enabled");
            if (!zone.enabled) { zone.currentlyInside.clear(); zone.nextStayAt.clear(); }
            data.dirty();
            syncIfPlayer(context.getSource());
            context.getSource().sendSuccess(() -> Component.translatable("message.mapperzones.zone_enabled", zone.name, zone.enabled), true);
            return 1;
        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resetCommand() {
        return Commands.literal("reset").then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
            MapperData data = MapperData.get(context.getSource().getLevel());
            String name = StringArgumentType.getString(context, "name");
            TriggerZone zone = findZone(data, name);

            if (zone == null) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.unknown_zone", name));
                return 0;
            }
            zone.triggeredPlayers.clear();
            zone.oneTimeEvents.clear();
            zone.lastTriggered.clear();
            zone.eventLastTriggered.clear();
            zone.currentlyInside.clear();
            zone.nextStayAt.clear();
            data.dirty();
            syncIfPlayer(context.getSource());
            context.getSource().sendSuccess(() -> Component.translatable("message.mapperzones.zone_reset", zone.name), true);
            return 1;
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shapeCommand() {
        return Commands.literal("shape").then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("shape", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("box", "sphere", "cylinder"), builder)).executes(context -> {
            MapperData data = MapperData.get(context.getSource().getLevel());
            String name = StringArgumentType.getString(context, "name");
            TriggerZone zone = findZone(data, name);

            if (zone == null) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.unknown_zone", name));
                return 0;
            }
            try {
                zone.shape = TriggerZone.Shape.valueOf(StringArgumentType.getString(context, "shape").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.invalid_shape"));
                return 0;
            }
            data.dirty();
            syncIfPlayer(context.getSource());
            return 1;
        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> actionCommand() {
        return Commands.literal("action").then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("event", StringArgumentType.word()).then(Commands.argument("action", StringArgumentType.greedyString()).executes(context -> {
            MapperData data = MapperData.get(context.getSource().getLevel());
            String name = StringArgumentType.getString(context, "name");
            TriggerZone zone = findZone(data, name);

            if (zone == null) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.unknown_zone", name));
                return 0;
            }
            String event = StringArgumentType.getString(context, "event").toLowerCase(Locale.ROOT);
            List<String> actions = switch (event) {
                case "enter" -> zone.enterActions;
                case "exit" -> zone.exitActions;
                case "stay" -> zone.stayActions;
                default -> null;
            };
            if (actions == null) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.invalid_event"));
                return 0;
            }
            String action = StringArgumentType.getString(context, "action");
            if (action.length() > 2048) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.action_too_long"));
                return 0;
            }
            actions.add(action);
            data.dirty();
            syncIfPlayer(context.getSource());
            context.getSource().sendSuccess(() -> Component.translatable("message.mapperzones.action_added", zone.name, event), true);
            return 1;
        }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> conditionCommand() {
        return Commands.literal("condition").then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("condition", StringArgumentType.greedyString()).executes(context -> {
            MapperData data = MapperData.get(context.getSource().getLevel());
            String name = StringArgumentType.getString(context, "name");
            TriggerZone zone = findZone(data, name);

            if (zone == null) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.unknown_zone", name));
                return 0;
            }
            String condition = StringArgumentType.getString(context, "condition");
            if (condition.length() > 512) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.condition_too_long"));
                return 0;
            }
            zone.conditions.add(condition);
            data.dirty();
            syncIfPlayer(context.getSource());
            context.getSource().sendSuccess(() -> Component.translatable("message.mapperzones.condition_added", zone.name), true);
            return 1;
        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> exportCommand() {
        return Commands.literal("export").then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
            MapperData data = MapperData.get(context.getSource().getLevel());
            String name = StringArgumentType.getString(context, "name");
            TriggerZone zone = findZone(data, name);

            if (zone == null) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.unknown_zone", name));
                return 0;
            }
            try {
                Path directory = FMLPaths.CONFIGDIR.get().resolve("mapperzones/exports");
                Files.createDirectories(directory);
                Path file = directory.resolve(safeName(zone.name) + ".json");
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(zone.toJson()));
                context.getSource().sendSuccess(() -> Component.translatable("message.mapperzones.exported", file.toAbsolutePath().toString()), false);
                return 1;
            } catch (Exception exception) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.export_failed", exception.getMessage()));
                return 0;
            }
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> importCommand() {
        return Commands.literal("import").then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
            String name = StringArgumentType.getString(context, "name");
            try {
                Path file = FMLPaths.CONFIGDIR.get().resolve("mapperzones/exports").resolve(safeName(name) + ".json");
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                TriggerZone zone = TriggerZone.fromJson(json);
                if (!zone.name.equalsIgnoreCase(name)) {
                    throw new IllegalArgumentException("File zone name does not match requested name.");
                }
                MapperData data = MapperData.get(context.getSource().getLevel());
                String key = normalizeName(name);
                if (data.zones.containsKey(key)) {
                    context.getSource().sendFailure(Component.literal("A zone named '" + name + "' already exists. Remove or rename it before importing."));
                    return 0;
                }
                data.zones.put(key, zone);
                data.dirty();
                context.getSource().sendSuccess(() -> Component.translatable("message.mapperzones.imported", zone.name), true);
                return 1;
            } catch (Exception exception) {
                context.getSource().sendFailure(Component.translatable("message.mapperzones.import_failed", exception.getMessage()));
                return 0;
            }
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> variableCommand() {
        var globalSetValue = Commands.argument("value", StringArgumentType.greedyString())
                .executes(ctx -> setVariable(ctx.getSource(), "global", null,
                        StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "value")));
        var globalSetName = Commands.argument("name", StringArgumentType.word()).then(globalSetValue);
        var playerSetValue = Commands.argument("value", StringArgumentType.greedyString())
                .executes(ctx -> setForTargets(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                        StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "value")));
        var playerSetName = Commands.argument("name", StringArgumentType.word()).then(playerSetValue);
        var playerSetTargets = Commands.argument("targets", EntityArgument.players()).then(playerSetName);
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set")
                .then(Commands.literal("global").then(globalSetName))
                .then(Commands.literal("player").then(playerSetTargets));

        var globalGetName = Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> getVariable(ctx.getSource(), "global", null, StringArgumentType.getString(ctx, "name")));
        var playerGetName = Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> getForTargets(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "name")));
        var playerGetTargets = Commands.argument("targets", EntityArgument.players()).then(playerGetName);
        LiteralArgumentBuilder<CommandSourceStack> get = Commands.literal("get")
                .then(Commands.literal("global").then(globalGetName))
                .then(Commands.literal("player").then(playerGetTargets));

        var globalAddAmount = Commands.argument("amount", DoubleArgumentType.doubleArg())
                .executes(ctx -> addVariable(ctx.getSource(), "global", null, StringArgumentType.getString(ctx, "name"), DoubleArgumentType.getDouble(ctx, "amount")));
        var globalAddName = Commands.argument("name", StringArgumentType.word()).then(globalAddAmount);
        var playerAddAmount = Commands.argument("amount", DoubleArgumentType.doubleArg())
                .executes(ctx -> addForTargets(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "name"), DoubleArgumentType.getDouble(ctx, "amount")));
        var playerAddName = Commands.argument("name", StringArgumentType.word()).then(playerAddAmount);
        var playerAddTargets = Commands.argument("targets", EntityArgument.players()).then(playerAddName);
        LiteralArgumentBuilder<CommandSourceStack> add = Commands.literal("add")
                .then(Commands.literal("global").then(globalAddName))
                .then(Commands.literal("player").then(playerAddTargets));

        var globalRemoveName = Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> removeVariable(ctx.getSource(), "global", null, StringArgumentType.getString(ctx, "name")));
        var playerRemoveName = Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> removeForTargets(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "name")));
        var playerRemoveTargets = Commands.argument("targets", EntityArgument.players()).then(playerRemoveName);
        LiteralArgumentBuilder<CommandSourceStack> remove = Commands.literal("remove")
                .then(Commands.literal("global").then(globalRemoveName))
                .then(Commands.literal("player").then(playerRemoveTargets));
        return Commands.literal("var").then(set).then(get).then(add).then(remove);
    }

    private static int setForTargets(CommandSourceStack source, Collection<ServerPlayer> targets, String name, String value) {
        if (name.length() > 128 || value.length() > 1024) { source.sendFailure(Component.translatable("message.mapperzones.variable_too_long")); return 0; }
        MapperData data = MapperData.get(source.getLevel());
        for (ServerPlayer target : targets) data.playerVars.computeIfAbsent(target.getUUID(), ignored -> new java.util.LinkedHashMap<>()).put(name, value);
        data.dirty();
        source.sendSuccess(() -> Component.literal("Set player variable " + name + " for " + targets.size() + " player(s)."), false);
        if (source.getEntity() instanceof ServerPlayer p) ModNetwork.sync(p);
        return targets.size();
    }

    private static int setVariable(CommandSourceStack source, String scope, ServerPlayer target, String name, String value) {
        MapperData data = MapperData.get(source.getLevel());
        if (name.length() > 128 || value.length() > 1024) { source.sendFailure(Component.translatable("message.mapperzones.variable_too_long")); return 0; }
        if (scope.equals("global")) data.globalVars.put(name, value);
        else data.playerVars.computeIfAbsent(target.getUUID(), ignored -> new java.util.LinkedHashMap<>()).put(name, value);
        data.dirty();
        source.sendSuccess(() -> Component.literal("Set " + scope + " variable " + name + (target == null ? "" : " for " + target.getGameProfile().getName())), false);
        if (source.getEntity() instanceof ServerPlayer p) ModNetwork.sync(p);
        return 1;
    }

    private static int getVariable(CommandSourceStack source, String scope, ServerPlayer target, String name) {
        MapperData data = MapperData.get(source.getLevel());
        String value = scope.equals("global") ? data.globalVars.get(name) : data.playerVars.getOrDefault(target.getUUID(), Map.of()).get(name);
        if (value == null) { source.sendFailure(Component.translatable("message.mapperzones.variable_not_found", name)); return 0; }
        source.sendSuccess(() -> Component.literal(name + " = " + value), false);
        return 1;
    }

    private static int getForTargets(CommandSourceStack source, Collection<ServerPlayer> targets, String name) {
        MapperData data = MapperData.get(source.getLevel()); int found = 0;
        for (ServerPlayer target : targets) {
            String value = data.playerVars.getOrDefault(target.getUUID(), Map.of()).get(name);
            if (value == null) source.sendFailure(Component.literal("Variable not found for " + target.getGameProfile().getName() + ": " + name));
            else { source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + ": " + name + " = " + value), false); found++; }
        }
        return found;
    }

    private static int removeVariable(CommandSourceStack source, String scope, ServerPlayer target, String name) {
        MapperData data = MapperData.get(source.getLevel());
        boolean removed;
        if (scope.equals("global")) removed = data.globalVars.remove(name) != null;
        else { Map<String,String> vars = data.playerVars.get(target.getUUID()); removed = vars != null && vars.remove(name) != null; if (vars != null && vars.isEmpty()) data.playerVars.remove(target.getUUID()); }
        if (!removed) { source.sendFailure(Component.translatable("message.mapperzones.variable_not_found", name)); return 0; }
        data.dirty(); source.sendSuccess(() -> Component.literal("Removed " + scope + " variable " + name), false);
        if (source.getEntity() instanceof ServerPlayer p) ModNetwork.sync(p);
        return 1;
    }

    private static int removeForTargets(CommandSourceStack source, Collection<ServerPlayer> targets, String name) {
        MapperData data = MapperData.get(source.getLevel()); int removed = 0;
        for (ServerPlayer target : targets) {
            Map<String,String> vars = data.playerVars.get(target.getUUID());
            if (vars != null && vars.remove(name) != null) { removed++; if (vars.isEmpty()) data.playerVars.remove(target.getUUID()); }
        }
        if (removed == 0) { source.sendFailure(Component.translatable("message.mapperzones.variable_not_found", name)); return 0; }
        data.dirty(); final int removedCount = removed;
        source.sendSuccess(() -> Component.literal("Removed player variable " + name + " for " + removedCount + " player(s)."), false);
        if (source.getEntity() instanceof ServerPlayer p) ModNetwork.sync(p);
        return removedCount;
    }

    private static int addVariable(CommandSourceStack source, String scope, ServerPlayer target, String name, double amount) {
        MapperData data = MapperData.get(source.getLevel());
        Map<String,String> vars = scope.equals("global") ? data.globalVars : data.playerVars.computeIfAbsent(target.getUUID(), ignored -> new java.util.LinkedHashMap<>());
        try { vars.put(name, Double.toString(Double.parseDouble(vars.getOrDefault(name, "0")) + amount)); }
        catch (NumberFormatException ex) { source.sendFailure(Component.translatable("message.mapperzones.variable_add_failed", ex.getMessage())); return 0; }
        data.dirty(); source.sendSuccess(() -> Component.literal("Updated " + scope + " variable " + name), false);
        if (source.getEntity() instanceof ServerPlayer p) ModNetwork.sync(p);
        return 1;
    }

    private static int addForTargets(CommandSourceStack source, Collection<ServerPlayer> targets, String name, double amount) {
        MapperData data = MapperData.get(source.getLevel());
        for (ServerPlayer target : targets) {
            Map<String,String> vars = data.playerVars.computeIfAbsent(target.getUUID(), ignored -> new java.util.LinkedHashMap<>());
            try { vars.put(name, Double.toString(Double.parseDouble(vars.getOrDefault(name, "0")) + amount)); }
            catch (NumberFormatException ex) { source.sendFailure(Component.literal("Cannot add to " + name + " for " + target.getGameProfile().getName() + ": not a number.")); return 0; }
        }
        data.dirty(); source.sendSuccess(() -> Component.literal("Updated player variable " + name + " for " + targets.size() + " player(s)."), false);
        if (source.getEntity() instanceof ServerPlayer p) ModNetwork.sync(p);
        return targets.size();
    }

    private static TriggerZone findZone(MapperData data, String name) {
        return data.zones.get(normalizeName(name));
    }

    private static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String safeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
