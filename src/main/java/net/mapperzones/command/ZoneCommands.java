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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.mapperzones.data.MapperData;
import net.mapperzones.item.ModItems;
import net.mapperzones.item.ZoneWandItem;
import net.mapperzones.network.ModNetwork;
import net.mapperzones.zone.TriggerZone;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mapperzones").requires(source -> source.hasPermission(2)).then(Commands.literal("gui").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ModNetwork.openGui(player);
            return 1;
        })).then(Commands.literal("wand").executes(context -> giveWand(context.getSource()))).then(Commands.literal("list").executes(context -> listZones(context.getSource()))).then(createCommand()).then(removeCommand()).then(enableCommand()).then(resetCommand()).then(shapeCommand()).then(actionCommand()).then(conditionCommand()).then(exportCommand()).then(importCommand()).then(variableCommand());

        dispatcher.register(root);
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
            data.dirty();
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
            zone.lastTriggered.clear();
            data.dirty();
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
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set").then(Commands.argument("scope", StringArgumentType.word()).then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("value", StringArgumentType.greedyString()).executes(context -> setVariable(context.getSource(), StringArgumentType.getString(context, "scope"), StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "value"))))));

        LiteralArgumentBuilder<CommandSourceStack> get = Commands.literal("get").then(Commands.argument("scope", StringArgumentType.word()).then(Commands.argument("name", StringArgumentType.word()).executes(context -> getVariable(context.getSource(), StringArgumentType.getString(context, "scope"), StringArgumentType.getString(context, "name")))));

        LiteralArgumentBuilder<CommandSourceStack> add = Commands.literal("add").then(Commands.argument("scope", StringArgumentType.word()).then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("amount", DoubleArgumentType.doubleArg()).executes(context -> addVariable(context.getSource(), StringArgumentType.getString(context, "scope"), StringArgumentType.getString(context, "name"), DoubleArgumentType.getDouble(context, "amount"))))));

        LiteralArgumentBuilder<CommandSourceStack> remove = Commands.literal("remove").then(Commands.argument("scope", StringArgumentType.word()).then(Commands.argument("name", StringArgumentType.word()).executes(context -> removeVariable(context.getSource(), StringArgumentType.getString(context, "scope"), StringArgumentType.getString(context, "name")))));
        return Commands.literal("var").then(set).then(get).then(add).then(remove);
    }

    private static int setVariable(CommandSourceStack source, String scope, String name, String value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MapperData data = MapperData.get(source.getLevel());

        if (name.length() > 128 || value.length() > 1024) {
            source.sendFailure(Component.translatable("message.mapperzones.variable_too_long"));
            return 0;
        }
        if (scope.equals("global")) {
            data.globalVars.put(name, value);
        } else if (scope.equals("player")) {
            ServerPlayer player = source.getPlayerOrException();
            data.playerVars.computeIfAbsent(player.getUUID(), ignored -> new java.util.LinkedHashMap<>()).put(name, value);
        } else {
            source.sendFailure(Component.translatable("message.mapperzones.invalid_scope"));
            return 0;
        }
        data.dirty();
        source.sendSuccess(() -> Component.translatable("message.mapperzones.variable_set", scope, name), false);
        return 1;
    }

    private static int getVariable(CommandSourceStack source, String scope, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MapperData data = MapperData.get(source.getLevel());
        String value;

        if (scope.equals("global")) {
            value = data.globalVars.get(name);
        } else if (scope.equals("player")) {
            ServerPlayer player = source.getPlayerOrException();
            value = data.playerVars.getOrDefault(player.getUUID(), Map.of()).get(name);
        } else {
            value = null;
        }
        if (value == null) {
            source.sendFailure(Component.translatable("message.mapperzones.variable_not_found", name));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.mapperzones.variable_value", name, value), false);
        return 1;
    }

    private static int removeVariable(CommandSourceStack source, String scope, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MapperData data = MapperData.get(source.getLevel());
        boolean removed;
        if (scope.equals("global")) removed = data.globalVars.remove(name) != null;
        else if (scope.equals("player")) {
            ServerPlayer player = source.getPlayerOrException();
            Map<String, String> vars = data.playerVars.get(player.getUUID());
            removed = vars != null && vars.remove(name) != null;
            if (vars != null && vars.isEmpty()) data.playerVars.remove(player.getUUID());
        } else {
            source.sendFailure(Component.translatable("message.mapperzones.invalid_scope")); return 0;
        }
        if (!removed) { source.sendFailure(Component.translatable("message.mapperzones.variable_not_found", name)); return 0; }
        data.dirty();
        source.sendSuccess(() -> Component.literal("Removed variable " + name + " (" + scope + ")"), false);
        return 1;
    }

    private static int addVariable(CommandSourceStack source, String scope, String name, double amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MapperData data = MapperData.get(source.getLevel());
        try {
            if (scope.equals("global")) {
                double updated = Double.parseDouble(data.globalVars.getOrDefault(name, "0")) + amount;
                data.globalVars.put(name, Double.toString(updated));
            } else if (scope.equals("player")) {
                ServerPlayer player = source.getPlayerOrException();
                Map<String, String> variables = data.playerVars.computeIfAbsent(player.getUUID(), ignored -> new java.util.LinkedHashMap<>());
                double updated = Double.parseDouble(variables.getOrDefault(name, "0")) + amount;
                variables.put(name, Double.toString(updated));
            } else {
                source.sendFailure(Component.translatable("message.mapperzones.invalid_scope"));
                return 0;
            }
            data.dirty();
            source.sendSuccess(() -> Component.translatable("message.mapperzones.variable_added", name), false);
            return 1;
        } catch (NumberFormatException exception) {
            source.sendFailure(Component.translatable("message.mapperzones.variable_add_failed", exception.getMessage()));
            return 0;
        }
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
