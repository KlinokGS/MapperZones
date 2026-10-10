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
    private int listScroll;

    public VariableScreen(Screen parent) {
        super(Component.translatable("screen.mapperzones.variables"));
        this.parent = parent;
    }

    @Override protected void init() {
        int w = Math.min(300, width - 20), x = (width - w) / 2;
        int top = 42;
        scope = new EditBox(font, x, top, w, 20, Component.translatable("screen.mapperzones.scope"));
        scope.setValue("global"); scope.setHint(Component.translatable("screen.mapperzones.scope_hint")); addRenderableWidget(scope);
        name = new EditBox(font, x, top + 32, w, 20, Component.translatable("screen.mapperzones.name"));
        name.setHint(Component.translatable("screen.mapperzones.variable_name_hint")); addRenderableWidget(name);
        value = new EditBox(font, x, top + 64, w, 20, Component.translatable("screen.mapperzones.value"));
        value.setHint(Component.translatable("screen.mapperzones.value_hint")); addRenderableWidget(value);
        int half = (w - 6) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.set_update"), b -> {
            String s = scope.getValue().trim(), n = name.getValue().trim();
            if (!n.isEmpty() && (s.equals("global") || s.equals("player"))) {
                ModNetwork.setVariable(s, n, value.getValue()); selectedScope=s; selectedName=n;
            }
        }).bounds(x, top + 94, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.refresh"), b -> ModNetwork.requestSync()).bounds(x + half + 6, top + 94, half, 20).build());

        int listTop = top + 138;
        int bottomY = height - 28;
        int listBottom = bottomY - 28;
        int rowH = 13;
        List<Entry> entries = entries();
        int visible = Math.max(1, (listBottom - listTop - 16) / rowH);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, entries.size() - visible)));
        for (int i = listScroll; i < Math.min(entries.size(), listScroll + visible); i++) {
            Entry e = entries.get(i); int row = i - listScroll; int yy = listTop + 16 + row * rowH;
            addRenderableWidget(Button.builder(Component.literal((e.scope.equals(selectedScope) && e.name.equals(selectedName) ? "[SELECTED] " : "") + e.scope + ": " + e.name + " = " + e.value), b -> {
                // Rebuild first so the selected-row marker updates, then populate the NEW
                // EditBox instances created by init(). Setting them before rebuilding loses
                // the values because init() creates fresh, empty fields.
                selectedScope = e.scope;
                selectedName = e.name;
                rebuildWidgets();
                scope.setValue(e.scope);
                name.setValue(e.name);
                value.setValue(e.value);
                scope.setCursorPosition(0);
                name.setCursorPosition(0);
                value.setCursorPosition(0);
            }).bounds(x, yy, w, 12).build());
        }
        int actionsY = height - 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.delete_selected"), b -> {
            if (!selectedName.isBlank() && (selectedScope.equals("global") || selectedScope.equals("player"))) {
                String removedName = selectedName;
                selectedName = "";
                name.setValue("");
                value.setValue("");
                ModNetwork.removeVariable(selectedScope, removedName);
                // The list is rebuilt when the server's authoritative variable sync arrives.
            }
        }).bounds(x, actionsY, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back"), b -> minecraft.setScreen(parent)).bounds(x + half + 6, actionsY, half, 20).build());
    }

    /** Called after fresh variable data arrives from the server. */
    public void onDataSynced() {
        if (minecraft != null && minecraft.screen == this) {
            String scopeValue = scope == null ? selectedScope : scope.getValue();
            String nameValue = name == null ? selectedName : name.getValue();
            String variableValue = value == null ? "" : value.getValue();
            rebuildWidgets();
            scope.setValue(scopeValue);
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

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g); g.drawCenteredString(font, title, width/2, 12, 0xFFFFFF);
        int w=Math.min(300,width-20), x=(width-w)/2, top=42;
        g.drawString(font, Component.translatable("screen.mapperzones.scope_label"),x,top-11,0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.variable_name_hint"),x,top+21,0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.value_label"),x,top+53,0xA0D8FF);
        int listTop=top+138;
        g.drawString(font,Component.translatable("screen.mapperzones.variable_select_hint"),x,listTop,0xA0D8FF);
        super.render(g,mx,my,pt);
    }
    @Override public boolean isPauseScreen(){return false;}
}
