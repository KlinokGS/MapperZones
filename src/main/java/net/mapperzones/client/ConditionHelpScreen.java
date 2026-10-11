package net.mapperzones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Responsive, scrollable syntax reference for zone condition expressions. */
public final class ConditionHelpScreen extends Screen {
    private final Screen parent;
    private int scroll;
    private int contentHeight;

    public ConditionHelpScreen(Screen parent) {
        super(Component.translatable("screen.mapperzones.condition_help_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back_editor"), b -> minecraft.setScreen(parent))
                .bounds(width / 2 - Math.min(70, (width - 20) / 2), height - 28, Math.min(140, width - 20), 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        int margin = Math.max(12, Math.min(36, width / 28));
        int x = margin;
        int maxWidth = Math.max(60, width - margin * 2);
        int top = 28;
        int bottom = height - 34;
        g.enableScissor(0, top, width, bottom);
        int y = top + 4 - scroll;

        y = heading(g, tr("player_properties"), x, y, maxWidth, 0x80D8FF);
        y = wrapped(g, tr("player_props_1"), x, y, maxWidth, 0xE0E8FF);
        y = wrapped(g, tr("player_props_2"), x, y, maxWidth, 0xE0E8FF);
        y = wrapped(g, tr("player_props_3"), x, y, maxWidth, 0xE0E8FF);
        y = separator(g, x, y, maxWidth);

        y = heading(g, tr("custom_variables"), x, y, maxWidth, 0xA0FFB0);
        y = wrapped(g, tr("global_vars"), x, y, maxWidth, 0xE0E8FF);
        y = wrapped(g, tr("player_vars"), x, y, maxWidth, 0xE0E8FF);
        y = wrapped(g, tr("variable_examples"), x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, "exists(global.score) && global.score >= 5", x, y, maxWidth, 0x78E6D0);
        y = wrapped(g, "exists(player.quest) && player.quest == \"started\"", x, y, maxWidth, 0x78E6D0);
        y = wrapped(g, tr("exists_desc"), x, y, maxWidth, 0xE0E8FF);
        y = separator(g, x, y, maxWidth);

        y = heading(g, tr("event_conditions_title"), x, y, maxWidth, 0x80D8FF);
        y = wrapped(g, tr("event_conditions_desc"), x, y, maxWidth, 0xE0E8FF);
        y = wrapped(g, "action.enter: exists(global.score)", x, y, maxWidth, 0x78E6D0);
        y = wrapped(g, "action.stay: global.score >= 5", x, y, maxWidth, 0x78E6D0);
        y = wrapped(g, "action.exit: player.quest == \"done\"", x, y, maxWidth, 0x78E6D0);
        y = separator(g, x, y, maxWidth);

        y = heading(g, tr("comparison_operators"), x, y, maxWidth, 0xA0FFB0);
        y = wrapped(g, "==  !=  >  <  >=  <=", x, y, maxWidth, 0xFFFFFF);
        y = heading(g, tr("logic_grouping"), x, y + 2, maxWidth, 0xA0FFB0);
        y = wrapped(g, tr("logic_desc"), x, y, maxWidth, 0xFFFFFF);
        y = wrapped(g, tr("quotes_desc"), x, y, maxWidth, 0xD0D0D0);
        y = separator(g, x, y, maxWidth);

        y = heading(g, tr("player_variable_examples"), x, y, maxWidth, 0xA0FFB0);
        y = wrapped(g, "player.health >= 10 && player.food > 0", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, "player.gamemode == \"creative\"", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, "player.dimension == \"minecraft:overworld\"", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, "global.score >= 5 && player.quest == \"started\"", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, "global.round >= 3 && player.rank == \"builder\"", x, y, maxWidth, 0x8BFFA0);
        y = separator(g, x, y, maxWidth);

        y = heading(g, tr("inventory_item"), x, y, maxWidth, 0x80D8FF);
        y = wrapped(g, "item minecraft:diamond", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, tr("item_desc"), x, y, maxWidth, 0xE0E8FF);
        y = wrapped(g, tr("item_details"), x, y, maxWidth, 0xBFBFBF);
        y = separator(g, x, y, maxWidth);

        y = heading(g, tr("team"), x, y, maxWidth, 0x80D8FF);
        y = wrapped(g, "team builders", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, tr("team_desc"), x, y, maxWidth, 0xE0E8FF);
        y = separator(g, x, y, maxWidth);

        y = heading(g, tr("world_time"), x, y, maxWidth, 0x80D8FF);
        y = wrapped(g, "time >= 6000", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, tr("time_desc"), x, y, maxWidth, 0xE0E8FF);
        y = wrapped(g, tr("time_details"), x, y, maxWidth, 0xBFBFBF);
        y = separator(g, x, y, maxWidth);

        y = heading(g, tr("weather"), x, y, maxWidth, 0x80D8FF);
        y = wrapped(g, "weather clear", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, "weather rain", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, "weather thunder", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, tr("weather_desc"), x, y, maxWidth, 0xE0E8FF);
        y = separator(g, x, y, maxWidth);

        y = heading(g, tr("dimension"), x, y, maxWidth, 0x80D8FF);
        y = wrapped(g, "dim minecraft:overworld", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, tr("dimension_desc"), x, y, maxWidth, 0xE0E8FF);
        y = separator(g, x, y, maxWidth);

        y = heading(g, tr("legacy_variable"), x, y, maxWidth, 0x80D8FF);
        y = wrapped(g, "var global score >= 5", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, "var player quest == \"started\"", x, y, maxWidth, 0x8BFFA0);
        y = wrapped(g, tr("legacy_desc"), x, y, maxWidth, 0xE0E8FF);
        y = wrapped(g, tr("legacy_details"), x, y, maxWidth, 0xBFBFBF);
        contentHeight = y + scroll;
        g.disableScissor();
        if (contentHeight > bottom - top) {
            g.drawString(font, tr("scroll_hint"), margin, height - 31, 0xAAAAAA);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private String tr(String key) { return Component.translatable("screen.mapperzones.help." + key).getString(); }

    private int heading(GuiGraphics g, String text, int x, int y, int width, int color) {
        g.drawString(font, text, x, y, color);
        return y + font.lineHeight + 3;
    }

    private int separator(GuiGraphics g, int x, int y, int width) {
        y += 3;
        g.fill(x, y, x + width, y + 1, 0xFF52606D);
        return y + 6;
    }

    private int wrapped(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        for (var line : font.split(Component.literal(text), maxWidth)) {
            g.drawString(font, line, x, y, color);
            y += font.lineHeight + 1;
        }
        return y;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int top = 28;
        int bottom = height - 34;
        scroll = MthClamp(scroll - (int)(delta * 18), 0, Math.max(0, contentHeight - (bottom - top)));
        return true;
    }

    private static int MthClamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override public boolean isPauseScreen() { return false; }
}
