package net.mapperzones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.mapperzones.network.ModNetwork;
import com.google.gson.JsonObject;

import java.util.*;

public class ZoneListScreen extends Screen {
    private int page = 0;
    private static final int PER_PAGE = 6;

    public ZoneListScreen() {
        super(Component.translatable("screen.mapperzones.title"));
    }

    @Override
    protected void init() {

        int left = width / 2 - 150, top = 45;

        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.refresh"), b -> ModNetwork.requestSync()).bounds(left, top, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.variables_button"), b -> minecraft.setScreen(new VariableScreen(this))).bounds(left + 100, top, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.close"), b -> onClose()).bounds(left + 210, top, 90, 20).build());

        int start = page * PER_PAGE;

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= ClientData.ZONES.size()) break;
            JsonObject z = ClientData.ZONES.get(idx);
            String name = z.get("name").getAsString();
            String shape = z.has("shape") ? z.get("shape").getAsString() : "BOX";
            boolean enabled = !z.has("enabled") || z.get("enabled").getAsBoolean();
            addRenderableWidget(Button.builder(Component.literal(name + "  |  " + shape + "  |  " + Component.translatable(enabled ? "screen.mapperzones.on" : "screen.mapperzones.off").getString()), b -> minecraft.setScreen(new ZoneEditScreen(this, name))).bounds(left, top + 30 + i * 27, 300, 22).build());
        }

        if (page > 0) addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.previous"), b -> {
            page--;
            rebuildWidgets();
        }).bounds(left, top + 200, 90, 20).build());

        if ((page + 1) * PER_PAGE < ClientData.ZONES.size())
            addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.next"), b -> {
                page++;
                rebuildWidgets();
            }).bounds(left + 210, top + 200, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title.getString(), width / 2, 18, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.mapperzones.zone_count", ClientData.ZONES.size()).getString(), width / 2, 31, 0xA0D8FF);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
