package net.mapperzones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Selects the player whose personal variables are being edited. */
public final class PlayerSelectScreen extends Screen {
    private final VariableScreen parent;
    private final String selected;
    private int scroll;

    public PlayerSelectScreen(VariableScreen parent, String selected) {
        super(Component.translatable("screen.mapperzones.players_title"));
        this.parent = parent;
        this.selected = selected;
    }

    @Override protected void init() {
        int w = Math.min(300, width - 20), x = (width - w) / 2;
        List<Map.Entry<String, String>> players = new ArrayList<>(ClientData.PLAYER_NAMES.entrySet());
        int rowH = 22, top = 42, bottom = height - 32;
        int visible = Math.max(1, (bottom - top) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, players.size() - visible)));
        for (int i = scroll; i < Math.min(players.size(), scroll + visible); i++) {
            var entry = players.get(i);
            String id = entry.getKey();
            String playerName = entry.getValue();
            int count = ClientData.PLAYER_VARIABLES.getOrDefault(id, Map.of()).size();
            String label = (id.equals(selected) ? "Выбран: " : "") + playerName + " (" + count + ")";
            addRenderableWidget(Button.builder(Component.literal(label), b -> parent.selectPlayer(id))
                    .bounds(x, top + (i - scroll) * rowH, w, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back"), b -> minecraft.setScreen(parent))
                .bounds(x, height - 26, w, 20).build());
    }

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        int top = 42, bottom = height - 32, rowH = 22;
        if (mx >= 0 && mx <= width && my >= top && my <= bottom) {
            int visible = Math.max(1, (bottom - top) / rowH);
            int max = Math.max(0, ClientData.PLAYER_NAMES.size() - visible);
            int next = Math.max(0, Math.min(max, scroll + (delta < 0 ? 3 : -3)));
            if (next != scroll) { scroll = next; rebuildWidgets(); }
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.mapperzones.players_hint"), width / 2, 28, 0xA0D8FF);
        if (ClientData.PLAYER_NAMES.isEmpty()) g.drawCenteredString(font, Component.translatable("screen.mapperzones.player_select_none"), width / 2, height / 2, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
