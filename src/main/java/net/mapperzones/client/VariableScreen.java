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

/** Responsive variable manager with selectable entries and explicit deletion. */
public class VariableScreen extends Screen {
    private final Screen parent;
    private EditBox scope, name, value;
    private String selectedScope = "global", selectedName = "";
    // Original identity of the selected entry; selectedScope is the destination scope toggle.
    private String sourceScope = "", sourceName = "";
    private int listScroll;

    public VariableScreen(Screen parent) {
        super(Component.translatable("screen.mapperzones.variables"));
        this.parent = parent;
    }

    @Override protected void init() {
        int w = Math.min(300, width - 20), x = (width - w) / 2;
        int top = 42;
        int scopeW = 76;
        int gap = 6;
        scope = null; // The scope is selected with a compact button, not a text field.
        addRenderableWidget(Button.builder(scopeLabel(), b -> {
            selectedScope = selectedScope.equals("global") ? "player" : "global";
            b.setMessage(scopeLabel());
        }).bounds(x, top, scopeW, 20).build());
        name = new EditBox(font, x + scopeW + gap, top, w - scopeW - gap, 20,
                Component.translatable("screen.mapperzones.name"));
        name.setHint(Component.translatable("screen.mapperzones.variable_name_hint"));
        addRenderableWidget(name);
        value = new EditBox(font, x, top + 32, w, 20, Component.translatable("screen.mapperzones.value"));
        value.setHint(Component.translatable("screen.mapperzones.value_hint"));
        addRenderableWidget(value);

        int half = (w - 6) / 2;
        int actionsY = height - 24;
        if (!selectedName.isBlank() && (selectedScope.equals("global") || selectedScope.equals("player"))) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.delete_selected"), b -> {
                String removedName = sourceName;
                String removedScope = sourceScope;
                selectedName = "";
                sourceName = "";
                sourceScope = "";
                name.setValue("");
                value.setValue("");
                ModNetwork.removeVariable(removedScope, removedName);
                rebuildWidgets();
            }).bounds(x, top + 64, half, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.refresh"), b -> ModNetwork.requestSync())
                .bounds(x + half + 6, top + 64, half, 20).build());

        int listTop = top + 100;
        int rowH = 14;
        List<Entry> entries = entries();
        int listStartY = listTop + 16;
        int listEndY = actionsY - 8;
        int visible = Math.max(1, (listEndY - listStartY) / rowH);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, entries.size() - visible)));
        for (int i = listScroll; i < Math.min(entries.size(), listScroll + visible); i++) {
            Entry e = entries.get(i); int row = i - listScroll; int yy = listStartY + row * rowH;
            Component label = (e.scope.equals(sourceScope) && e.name.equals(sourceName))
                    ? Component.translatable("screen.mapperzones.variable_selected", e.scope, e.name, e.value)
                    : Component.literal(e.scope + ": " + e.name + " = " + e.value);
            addRenderableWidget(Button.builder(label, b -> {
                selectedScope = e.scope;
                selectedName = e.name;
                sourceScope = e.scope;
                sourceName = e.name;
                rebuildWidgets();
                name.setValue(e.name);
                value.setValue(e.value);
                name.setCursorPosition(name.getValue().length());
                name.setHighlightPos(name.getCursorPosition());
                value.setCursorPosition(value.getValue().length());
                value.setHighlightPos(value.getCursorPosition());
                name.setFocused(true);
            }).bounds(x, yy, w, 13).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.set_update"), b -> {
            String n = name.getValue().trim();
            if (!n.isEmpty() && (selectedScope.equals("global") || selectedScope.equals("player"))) {
                if (!sourceName.isBlank() && (sourceScope.equals("global") || sourceScope.equals("player"))
                        && !sourceScope.equals(selectedScope)) {
                    // Changing scope means moving the selected variable. The server checks for conflicts.
                    ModNetwork.updateVariable(sourceScope, sourceName, selectedScope, n, value.getValue());
                    selectedName = n;
                    // Keep the original identity until the server snapshot confirms the move.
                } else {
                    // In the same scope, Set/Update retains the familiar behavior: update by name
                    // or create a separate variable if the name was changed.
                    ModNetwork.setVariable(selectedScope, n, value.getValue());
                    selectedName = n;
                    if (!sourceName.equals(n) || !sourceScope.equals(selectedScope)) {
                        sourceScope = "";
                        sourceName = "";
                    }
                }
            }
        }).bounds(x, actionsY, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back"), b -> minecraft.setScreen(parent))
                .bounds(x + half + 6, actionsY, half, 20).build());
    }

    private Component scopeLabel() {
        return Component.translatable(selectedScope.equals("player")
                ? "screen.mapperzones.scope_player" : "screen.mapperzones.scope_global");
    }

    /** Called after fresh variable data arrives from the server. */
    public void onDataSynced() {
        if (minecraft != null && minecraft.screen == this) {
            String nameValue = name == null ? selectedName : name.getValue();
            String variableValue = value == null ? "" : value.getValue();
            // If the source disappeared and the destination now exists, the server accepted a move.
            boolean sourceStillExists = sourceScope.equals("global")
                    ? ClientData.GLOBALS.containsKey(sourceName)
                    : sourceScope.equals("player") && ClientData.PLAYERS.containsKey(sourceName);
            boolean destinationExists = selectedScope.equals("global")
                    ? ClientData.GLOBALS.containsKey(nameValue)
                    : selectedScope.equals("player") && ClientData.PLAYERS.containsKey(nameValue);
            if (!sourceName.isBlank() && !sourceStillExists && destinationExists) {
                sourceScope = selectedScope;
                sourceName = nameValue;
                selectedName = nameValue;
                String serverValue = selectedScope.equals("global")
                        ? ClientData.GLOBALS.get(nameValue) : ClientData.PLAYERS.get(nameValue);
                if (serverValue != null) variableValue = serverValue;
            }
            rebuildWidgets();
            name.setValue(nameValue);
            value.setValue(variableValue);
        }
    }

    private record Entry(String scope, String name, String value) {}
    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String,String> e : ClientData.GLOBALS.entrySet()) out.add(new Entry("global",e.getKey(),e.getValue()));
        for (Map.Entry<String,String> e : ClientData.PLAYERS.entrySet()) out.add(new Entry("player",e.getKey(),e.getValue()));
        return out;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listTop = 42 + 100;
        int listBottom = height - 24 - 8;
        if (mouseX >= 0 && mouseX <= width && mouseY >= listTop && mouseY <= listBottom) {
            List<Entry> entries = entries();
            int visible = Math.max(1, (listBottom - (listTop + 16)) / 14);
            int maxScroll = Math.max(0, entries.size() - visible);
            int direction = delta < 0 ? 1 : -1;
            int next = Math.max(0, Math.min(maxScroll, listScroll + direction * 3));
            if (next != listScroll) {
                listScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        int x = (width - Math.min(300, width - 20)) / 2;
        int top = 42;
        g.drawString(font, Component.translatable("screen.mapperzones.scope_field_label"), x, top - 11, 0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.name_label"), x + 82, top - 11, 0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.value_label"), x, top + 21, 0xA0D8FF);
        int listTop = top + 100;
        g.drawString(font, Component.translatable("screen.mapperzones.variable_select_hint"), x, listTop, 0xA0D8FF);
        super.render(g, mx, my, pt);
    }
    @Override public boolean isPauseScreen(){return false;}
}
