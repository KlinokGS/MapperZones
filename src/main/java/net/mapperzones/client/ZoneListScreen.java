package net.mapperzones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import net.mapperzones.network.ModNetwork;
import com.google.gson.JsonObject;

import java.util.*;

public class ZoneListScreen extends Screen {
    private static final ResourceLocation MOD_LOGO = new ResourceLocation("mapperzones", "textures/gui/mapperzones_logo.png");
    private int page = 0;
    private static final int PER_PAGE = 6;

    public ZoneListScreen() {
        super(Component.translatable("screen.mapperzones.title"));
    }

    @Override
    protected void init() {

        addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.setScreen(new SettingsScreen(this)))
                .bounds(6, 6, 20, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.mapperzones.settings.open"))).build());

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
        // Draw the logo after widgets so it appears over the blank settings button.
        // Explicitly restore the GUI texture shader and alpha blending: other renderers may
        // leave a different shader/blend state active when this screen is drawn.
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MOD_LOGO);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(MOD_LOGO, 7, 7, 18, 18, 0, 0, 128, 128, 128, 128);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
