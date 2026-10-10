package net.mapperzones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ActionBuilderScreen extends Screen {
    private final Screen parent;
    private final List<String> examples = List.of("command:say Hello ${player.name}", "sound:minecraft:block.note_block.pling 1 1", "particle:minecraft:flame 12", "effect:minecraft:speed 10 0", "teleport:100 70 100", "freeze 40", "inventory:save", "inventory:restore", "redstone:100 64 100 40", "zone:disable intro", "zone:enable intro", "zone:reset intro");

    public ActionBuilderScreen(Screen parent) {
        super(Component.translatable("screen.mapperzones.actions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back_editor"), b -> minecraft.setScreen(parent)).bounds(width / 2 - 70, height - 35, 140, 22).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title.getString(), width / 2, 15, 0xFFFFFF);
        int y = 38;

        for (String s : examples) {
            g.drawString(font, s, width / 2 - 150, y, 0xD8E8FF);
            y += 18;
        }
        g.drawString(font, Component.translatable("screen.mapperzones.condition_examples"), width / 2 - 150, y + 8, 0xA0FFB0);
        g.drawString(font, Component.translatable("screen.mapperzones.condition_examples_more"), width / 2 - 150, y + 25, 0xA0FFB0);
        g.drawString(font, Component.translatable("screen.mapperzones.variable_examples"), width / 2 - 150, y + 42, 0xA0FFB0);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
