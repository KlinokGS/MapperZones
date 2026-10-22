package net.mapperzones.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Compact, paged action syntax reference. */
public class ActionBuilderScreen extends Screen {
    private final Screen parent;
    private int categoryIndex = 0;
    private int exampleIndex = 0;

    private record Example(String syntax, String description, String notes) {}
    private record Category(String key, String title, List<Example> examples) {}

    private final List<Category> categories = List.of(
        new Category("commands", "Commands", List.of(
            new Example("command:say Hello ${player.name}", Component.translatable("screen.mapperzones.action_helper.command_description").getString(), Component.translatable("screen.mapperzones.action_helper.command_notes").getString()),
            new Example("command:effect give ${player.name} minecraft:speed 5 0", Component.translatable("screen.mapperzones.action_helper.command2_description").getString(), Component.translatable("screen.mapperzones.action_helper.command2_notes").getString()))),
        new Category("sound_particles", "Sound & particles", List.of(
            new Example("sound:minecraft:block.note_block.pling 1 1", Component.translatable("screen.mapperzones.action_helper.sound_description").getString(), Component.translatable("screen.mapperzones.action_helper.sound_notes").getString()),
            new Example("particle:minecraft:flame 12", Component.translatable("screen.mapperzones.action_helper.particle_description").getString(), Component.translatable("screen.mapperzones.action_helper.particle_notes").getString()))),
        new Category("effects_movement", "Effects & movement", List.of(
            new Example("effect:minecraft:speed 10 0", Component.translatable("screen.mapperzones.action_helper.effect_description").getString(), Component.translatable("screen.mapperzones.action_helper.effect_notes").getString()),
            new Example("teleport:100 70 100", Component.translatable("screen.mapperzones.action_helper.teleport_description").getString(), Component.translatable("screen.mapperzones.action_helper.teleport_notes").getString()),
            new Example("freeze 40", Component.translatable("screen.mapperzones.action_helper.freeze_description").getString(), Component.translatable("screen.mapperzones.action_helper.freeze_notes").getString()))),
        new Category("inventory", "Inventory", List.of(
            new Example("inventory:save", Component.translatable("screen.mapperzones.action_helper.inventory_description").getString(), Component.translatable("screen.mapperzones.action_helper.inventory_notes").getString()),
            new Example("inventory:restore", Component.translatable("screen.mapperzones.action_helper.restore_description").getString(), Component.translatable("screen.mapperzones.action_helper.restore_notes").getString()))),
        new Category("zones_blocks", "Zones & blocks", List.of(
            new Example("redstone:100 64 100 40", Component.translatable("screen.mapperzones.action_helper.redtone_description").getString(), Component.translatable("screen.mapperzones.action_helper.redtone_notes").getString()),
            new Example("zone:disable intro", Component.translatable("screen.mapperzones.action_helper.disable_description").getString(), Component.translatable("screen.mapperzones.action_helper.disable_notes").getString()),
            new Example("zone:enable intro", Component.translatable("screen.mapperzones.action_helper.enable_description").getString(), Component.translatable("screen.mapperzones.action_helper.enable_notes").getString()),
            new Example("zone:reset intro", Component.translatable("screen.mapperzones.action_helper.reset_description").getString(), Component.translatable("screen.mapperzones.action_helper.reset_notes").getString()))),
        new Category("block_properties", "Blocks & properties", List.of(
            new Example("blockAt(100,64,200).direction = north", Component.translatable("screen.mapperzones.action_helper.block_direction_description").getString(), Component.translatable("screen.mapperzones.action_helper.block_direction_notes").getString()),
            new Example("blockAt(100,64,200).open = true", Component.translatable("screen.mapperzones.action_helper.block_open_description").getString(), Component.translatable("screen.mapperzones.action_helper.block_open_notes").getString()),
            new Example("blockAt(100,64,200).lit = true", Component.translatable("screen.mapperzones.action_helper.block_other_description").getString(), Component.translatable("screen.mapperzones.action_helper.block_other_notes").getString()))),
        new Category("player_properties", "Player properties", List.of(
            new Example("var.player.quest.set = start", Component.translatable("screen.mapperzones.action_helper.player_vars_description").getString(), Component.translatable("screen.mapperzones.action_helper.player_vars_notes").getString()),
            new Example("var.player.quest.remove", Component.translatable("screen.mapperzones.action_helper.player_vars_description").getString(), Component.translatable("screen.mapperzones.action_helper.player_vars_notes").getString()),
            new Example("command:say ${player.name} (${player.uuid})", Component.translatable("screen.mapperzones.action_helper.player_identity_description").getString(), Component.translatable("screen.mapperzones.action_helper.player_identity_notes").getString()),
            new Example("command:effect give ${player.name} minecraft:speed 5 0", Component.translatable("screen.mapperzones.action_helper.player_status_description").getString(), Component.translatable("screen.mapperzones.action_helper.player_status_notes").getString()),
            new Example("command:say HP=${player.health}/${player.max_health} food=${player.food}", Component.translatable("screen.mapperzones.action_helper.player_stats_description").getString(), Component.translatable("screen.mapperzones.action_helper.player_stats_notes").getString()),
            new Example("command:tp ${player.name} ${player.x} ${player.y} ${player.z}", Component.translatable("screen.mapperzones.action_helper.player_position_description").getString(), Component.translatable("screen.mapperzones.action_helper.player_position_notes").getString())))
    );

    public ActionBuilderScreen(Screen parent) {
        super(Component.translatable("screen.mapperzones.actions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonY = height - 48;
        int gap = 6;
        int totalWidth = Math.min(width - 24, 420);
        int arrowWidth = 34;
        int exampleWidth = Math.min(96, Math.max(72, totalWidth / 4));
        int backWidth = Math.max(90, totalWidth - arrowWidth * 2 - exampleWidth - gap * 3);
        int actualWidth = arrowWidth * 2 + exampleWidth + backWidth + gap * 3;
        int left = (width - actualWidth) / 2;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            categoryIndex = (categoryIndex + categories.size() - 1) % categories.size(); exampleIndex = 0; rebuildWidgets();
        }).bounds(left, buttonY, arrowWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.example"), b -> {
            exampleIndex = (exampleIndex + 1) % categories.get(categoryIndex).examples().size(); rebuildWidgets();
        }).bounds(left + arrowWidth + gap, buttonY, exampleWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            categoryIndex = (categoryIndex + 1) % categories.size(); exampleIndex = 0; rebuildWidgets();
        }).bounds(left + arrowWidth + gap + exampleWidth + gap, buttonY, arrowWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back_editor"), b -> minecraft.setScreen(parent))
                .bounds(left + arrowWidth + gap + exampleWidth + gap + arrowWidth + gap, buttonY, backWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        Category category = categories.get(categoryIndex);
        Example example = category.examples().get(exampleIndex);
        int margin = Math.max(12, Math.min(32, width / 24));
        int left = margin;
        int contentWidth = width - margin * 2;
        int bottom = height - 58;
        int y = 32;
        g.drawString(font, Component.translatable("screen.mapperzones.action_category." + category.key()).getString(), left, y, 0x80D8FF); y += 15;
        g.drawString(font, Component.translatable("screen.mapperzones.action_example_count", exampleIndex + 1, category.examples().size()), left, y, 0xAAAAAA); y += 12;
        g.fill(left, y, left + contentWidth, y + 1, 0xFF52606D); y += 8;
        g.drawString(font, Component.translatable("screen.mapperzones.syntax"), left, y, 0xA0FFB0); y += 13;
        y = drawWrapped(g, example.syntax(), left, y, contentWidth, 0xE0E8FF);
        y += 6;
        g.drawString(font, Component.translatable("screen.mapperzones.what_it_does"), left, y, 0xA0FFB0); y += 13;
        y = drawWrapped(g, example.description(), left, y, contentWidth, 0xFFFFFF);
        y += 6;
        g.drawString(font, Component.translatable("screen.mapperzones.details"), left, y, 0xA0FFB0); y += 13;
        drawWrapped(g, example.notes(), left, y, contentWidth, 0xD0D0D0);
        if (height >= 180) {
            g.drawString(font, Component.translatable("screen.mapperzones.legacy_action_note"), left, bottom - 11, 0xA8A8A8);
        }
        super.render(g, mx, my, pt);
    }

    private int drawWrapped(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        for (var line : font.split(Component.literal(text), Math.max(40, maxWidth))) {
            g.drawString(font, line, x, y, color);
            y += font.lineHeight + 2;
        }
        return y;
    }

    @Override public boolean isPauseScreen() { return false; }
}
