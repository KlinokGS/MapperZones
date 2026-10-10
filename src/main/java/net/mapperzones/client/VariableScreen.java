package net.mapperzones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.mapperzones.network.ModNetwork;

public class VariableScreen extends Screen {
    private final Screen parent;
    private EditBox scope, name, value;

    public VariableScreen(Screen parent) {
        super(Component.translatable("screen.mapperzones.variables"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 150, y = 52;
        scope = new EditBox(font, x, y, 300, 20, Component.translatable("screen.mapperzones.scope"));
        scope.setValue("global");
        scope.setHint(Component.translatable("screen.mapperzones.scope_hint"));
        addRenderableWidget(scope);
        name = new EditBox(font, x, y + 32, 300, 20, Component.translatable("screen.mapperzones.name"));
        name.setHint(Component.translatable("screen.mapperzones.variable_name_hint"));
        addRenderableWidget(name);
        value = new EditBox(font, x, y + 64, 300, 20, Component.translatable("screen.mapperzones.value"));
        value.setHint(Component.translatable("screen.mapperzones.value_hint"));
        addRenderableWidget(value);
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.set_variable"), b -> {
            String s = scope.getValue().trim(), n = name.getValue().trim();
            if (!n.isEmpty() && (s.equals("global") || s.equals("player"))) {
                ModNetwork.setVariable(s, n, value.getValue());
                if (minecraft.player != null)
                    minecraft.player.displayClientMessage(Component.translatable("message.mapperzones.variable_update_sent"), false);
            }
        }).bounds(x, y + 96, 145, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.refresh"), b -> ModNetwork.requestSync()).bounds(x + 155, y + 96, 145, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back"), b -> minecraft.setScreen(parent)).bounds(x, y + 126, 300, 22).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title.getString(), width / 2, 20, 0xFFFFFF);
        int x = width / 2 - 150;
        g.drawString(font, Component.translatable("screen.mapperzones.scope_label"), x, 40, 0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.variable_name_label"), x, 72, 0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.value_label"), x, 104, 0xA0D8FF);
        int y = height / 2 + 50;
        g.drawString(font, Component.translatable("screen.mapperzones.global_variables"), width / 2 - 150, y, 0xA0D8FF);
        y += 15;
        int n = 0;
        for (var e : ClientData.GLOBALS.entrySet()) {
            if (n++ > 4) break;
            g.drawString(font, e.getKey() + " = " + e.getValue(), width / 2 - 150, y, 0xFFFFFF);
            y += 12;
        }
        g.drawString(font, Component.translatable("screen.mapperzones.personal_variables"), width / 2 + 15, height / 2 + 50, 0xA0D8FF);
        n = 0;
        for (var e : ClientData.PLAYERS.entrySet()) {
            if (n++ > 4) break;
            g.drawString(font, e.getKey() + " = " + e.getValue(), width / 2 + 15, height / 2 + 65 + (n - 1) * 12, 0xFFFFFF);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
