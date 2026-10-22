package net.mapperzones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.mapperzones.network.ModNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Variable manager with global scope and selectable per-player variable sets. */
public class VariableScreen extends Screen {
    private final Screen parent;
    private EditBox name, value;
    private String selectedScope = "global", selectedName = "";
    private String sourceScope = "", sourceName = "";
    private String selectedPlayer = "";
    private int listScroll;
    private boolean changeForAllPlayers;

    public VariableScreen(Screen parent) { super(Component.translatable("screen.mapperzones.variables")); this.parent = parent; }

    @Override protected void init() {
        int w = Math.min(340, width - 20), x = (width - w) / 2;
        int top = 42, gap = 6, scopeW = 76;
        addRenderableWidget(Button.builder(scopeLabel(), b -> {
            selectedScope = selectedScope.equals("global") ? "player" : "global";
            listScroll = 0; rebuildWidgets();
        }).bounds(x, top, scopeW, 20).build());
        name = new EditBox(font, x + scopeW + gap, top, w - scopeW - gap, 20, Component.translatable("screen.mapperzones.name"));
        name.setHint(Component.translatable("screen.mapperzones.variable_name_hint")); addRenderableWidget(name);
        value = new EditBox(font, x, top + 30, w, 20, Component.translatable("screen.mapperzones.value"));
        value.setHint(Component.translatable("screen.mapperzones.value_hint")); addRenderableWidget(value);

        if (selectedScope.equals("player")) {
            int half = (w - 6) / 2;
            addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.change_for_all", changeForAllPlayers ? "true" : "false"), b -> {
                changeForAllPlayers = !changeForAllPlayers;
                if (changeForAllPlayers) selectedPlayer = "";
                selectedName = ""; sourceName = ""; sourceScope = "";
                listScroll = 0; rebuildWidgets();
            }).bounds(x, top + 54, half, 20).build());
            Button playerButton = Button.builder(playerLabel(), b -> minecraft.setScreen(new PlayerSelectScreen(this, selectedPlayer)))
                    .bounds(x + half + 6, top + 54, half, 20).build();
            playerButton.active = !changeForAllPlayers;
            addRenderableWidget(playerButton);
        }
        int actionY = top + (selectedScope.equals("player") ? 78 : 58);
        int half = (w - 6) / 2;
        if (!selectedName.isBlank()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.delete_selected"), b -> {
                if (sourceScope.equals("player") && changeForAllPlayers) {
                    // Server-side all-player removal also includes offline players not present in the client list.
                    ModNetwork.removeVariable("player", "*", sourceName);
                } else if (sourceScope.equals("player")) {
                    // In single-player mode, remove only from the explicitly selected player's UUID.
                    if (!selectedPlayer.isBlank()) ModNetwork.removeVariable("player", selectedPlayer, sourceName);
                } else {
                    ModNetwork.removeVariable(sourceScope, "", sourceName);
                }
                selectedName = ""; sourceName = ""; sourceScope = ""; name.setValue(""); value.setValue(""); ModNetwork.requestSync(); rebuildWidgets();
            }).bounds(x, actionY, half, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.refresh"), b -> ModNetwork.requestSync())
                .bounds(x + half + 6, actionY, half, 20).build());

        int listTop = actionY + 30, listStartY = listTop + 16, listBottom = height - 32, rowH = 14;
        List<Entry> entries = entries();
        int visible = Math.max(1, (listBottom - listStartY) / rowH);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, entries.size() - visible)));
        for (int i = listScroll; i < Math.min(entries.size(), listScroll + visible); i++) {
            Entry e = entries.get(i); int yy = listStartY + (i - listScroll) * rowH;
            Component label = e.scope.equals(sourceScope) && e.name.equals(sourceName)
                    ? Component.translatable("screen.mapperzones.variable_selected", e.scope, e.name, e.value)
                    : Component.literal(e.scope + ": " + e.name + " = " + e.value);
            addRenderableWidget(Button.builder(label, b -> {
                selectedScope = e.scope; selectedName = e.name; sourceScope = e.scope; sourceName = e.name;
                rebuildWidgets(); name.setValue(e.name); value.setValue(e.value);
                name.setCursorPosition(name.getValue().length()); name.setHighlightPos(name.getCursorPosition()); name.setFocused(true);
            }).bounds(x, yy, w, 13).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.set_update"), b -> {
            String n = name.getValue().trim(); if (n.isEmpty()) return;
            String target = selectedPlayer;
            if (!sourceName.isBlank() && (sourceScope.equals("global") || sourceScope.equals("player")) && !sourceScope.equals(selectedScope)) {
                ModNetwork.updateVariable(sourceScope, target, sourceName, selectedScope, n, value.getValue());
                selectedName = n;
            } else {
                if (selectedScope.equals("player") && changeForAllPlayers) {
                    for (String uuid : ClientData.PLAYER_NAMES.keySet()) ModNetwork.setVariable("player", uuid, n, value.getValue());
                } else {
                    ModNetwork.setVariable(selectedScope, target, n, value.getValue());
                }
                selectedName = n;
                sourceScope = selectedScope; sourceName = n;
            }
        }).bounds(x, height - 26, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back"), b -> minecraft.setScreen(parent))
                .bounds(x + half + 6, height - 26, half, 20).build());
    }

    public void selectPlayer(String uuid) {
        String next = uuid == null ? "" : uuid;
        if (sourceScope.equals("player") && !selectedPlayer.equals(next)) {
            selectedName = ""; sourceName = ""; sourceScope = "";
        }
        selectedPlayer = next;
        listScroll = 0;
        if (minecraft != null) minecraft.setScreen(this);
        rebuildWidgets();
    }

    private Component playerLabel() {
        if (selectedPlayer.isBlank()) return Component.translatable("screen.mapperzones.player_select_none");
        String display = ClientData.PLAYER_NAMES.getOrDefault(selectedPlayer, selectedPlayer);
        return Component.translatable("screen.mapperzones.player_select", display);
    }

    private Component scopeLabel() { return Component.translatable(selectedScope.equals("player") ? "screen.mapperzones.scope_player" : "screen.mapperzones.scope_global"); }
    private record Entry(String scope, String name, String value) {}

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        if (selectedScope.equals("global")) ClientData.GLOBALS.forEach((k,v) -> out.add(new Entry("global", k, v)));
        else if (changeForAllPlayers) {
            Map<String, String> defaults = new java.util.LinkedHashMap<>();
            for (Map<String, String> vars : ClientData.PLAYER_VARIABLES.values()) {
                vars.forEach(defaults::putIfAbsent);
            }
            defaults.forEach((k,v) -> out.add(new Entry("player", k, v)));
        } else ClientData.PLAYER_VARIABLES.getOrDefault(selectedPlayer, Map.of()).forEach((k,v) -> out.add(new Entry("player", k, v)));
        return out;
    }

    public void onDataSynced() {
        if (minecraft == null || minecraft.screen != this) return;
        if (selectedPlayer.isBlank() || !ClientData.PLAYER_NAMES.containsKey(selectedPlayer))
            selectedPlayer = ClientData.PLAYER_NAMES.keySet().stream().findFirst().orElse("");
        String n = name == null ? selectedName : name.getValue(); String v = value == null ? "" : value.getValue();
        boolean sourceExists = sourceScope.equals("global") ? ClientData.GLOBALS.containsKey(sourceName)
                : sourceScope.equals("player") && ClientData.PLAYER_VARIABLES.getOrDefault(selectedPlayer, Map.of()).containsKey(sourceName);
        boolean destinationExists = selectedScope.equals("global") ? ClientData.GLOBALS.containsKey(n)
                : ClientData.PLAYER_VARIABLES.getOrDefault(selectedPlayer, Map.of()).containsKey(n);
        if (!sourceName.isBlank() && !sourceExists && destinationExists) {
            sourceScope = selectedScope; sourceName = n; selectedName = n;
            v = selectedScope.equals("global") ? ClientData.GLOBALS.getOrDefault(n, "") : ClientData.PLAYER_VARIABLES.getOrDefault(selectedPlayer, Map.of()).getOrDefault(n, "");
        }
        rebuildWidgets(); name.setValue(n); value.setValue(v);
    }

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        int top = 42 + (selectedScope.equals("player") ? 108 : 88), bottom = height - 32;
        if (mx >= 0 && mx <= width && my >= top && my <= bottom) {
            int rowH = 14, visible = Math.max(1, (bottom - (top + 16)) / rowH);
            int max = Math.max(0, entries().size() - visible); int next = Math.max(0, Math.min(max, listScroll + (delta < 0 ? 3 : -3)));
            if (next != listScroll) { listScroll = next; rebuildWidgets(); } return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g); g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        int x = (width - Math.min(340, width - 20)) / 2, top = 42;
        g.drawString(font, Component.translatable("screen.mapperzones.scope_field_label"), x, top - 11, 0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.name_label"), x + 82, top - 11, 0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.value_label"), x, top + 19, 0xA0D8FF);
        int listTop = top + (selectedScope.equals("player") ? 108 : 88);
        g.drawString(font, Component.translatable("screen.mapperzones.variable_select_hint"), x, listTop, 0xA0D8FF);
        super.render(g, mx, my, pt);
    }
    @Override public boolean isPauseScreen() { return false; }
}
