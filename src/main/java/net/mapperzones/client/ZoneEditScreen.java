package net.mapperzones.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.mapperzones.network.ModNetwork;

import java.util.*;

/** Responsive, compact zone editor. Long action/condition lists live on their own screens. */
public class ZoneEditScreen extends Screen {
    private static final List<String> SHAPE_VALUES = List.of("BOX", "SPHERE", "CYLINDER");
    private final Screen parent;
    private final String zoneName;
    private final JsonObject source;
    private EditBox shape, interval, cooldown;
    private boolean enabled = true, oneTime = false;
    private String enterText, exitText, stayText, conditionsText;
    private int shapeIndex;

    public ZoneEditScreen(Screen parent, String name) {
        super(Component.translatable("screen.mapperzones.edit", name));
        this.parent = parent;
        this.zoneName = name;
        this.source = ClientData.find(name);
        this.enterText = list(source, "enterActions");
        this.exitText = list(source, "exitActions");
        this.stayText = list(source, "stayActions");
        this.conditionsText = list(source, "conditions");
        String initial = source.has("shape") ? source.get("shape").getAsString() : "BOX";
        int idx = SHAPE_VALUES.indexOf(initial);
        this.shapeIndex = Math.max(idx, 0);;
    }

    @Override protected void init() {
        int w = Math.min(320, width - 24), x = (width - w) / 2;
        if (source == null) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back"), b -> minecraft.setScreen(parent)).bounds(x, height - 26, w, 20).build());
            return;
        }
        // Compact layout: keep every control on-screen even at Minecraft GUI scale 4.

        addRenderableWidget(Button.builder(shapeLabel(), button -> {
            shapeIndex = (shapeIndex + 1) % SHAPE_VALUES.size();
            String newValue = SHAPE_VALUES.get(shapeIndex);
            button.setMessage(Component.translatable("screen.mapperzones.shape_selected", newValue));

            source.addProperty("shape", newValue);
        }).bounds(x, 29, w, 20).build());

        enabled = !source.has("enabled") || source.get("enabled").getAsBoolean();
        oneTime = source.has("oneTime") && source.get("oneTime").getAsBoolean();

        int half = (w - 6) / 2;
        addRenderableWidget(Button.builder(enabledLabel(), b -> { enabled = !enabled; b.setMessage(enabledLabel()); }).bounds(x, 56, half, 20).build());
        addRenderableWidget(Button.builder(oneTimeLabel(), b -> { oneTime = !oneTime; b.setMessage(oneTimeLabel()); }).bounds(x + half + 6, 56, half, 20).build());
        interval = field(x, 91, w, Component.translatable("screen.mapperzones.stay_interval").getString(), source.has("stayInterval") ? source.get("stayInterval").getAsString() : "40");
        cooldown = field(x, 127, w, Component.translatable("screen.mapperzones.cooldown").getString(), source.has("cooldownTicks") ? source.get("cooldownTicks").getAsString() : "0");

        int gap = 6, bw = (w - gap) / 2;
        addRenderableWidget(Button.builder(sectionLabel("screen.mapperzones.enter_actions_label", enterText), b -> openEditor("enter")).bounds(x, 158, bw, 22).build());
        addRenderableWidget(Button.builder(sectionLabel("screen.mapperzones.exit_actions_label", exitText), b -> openEditor("exit")).bounds(x + bw + gap, 158, bw, 22).build());
        addRenderableWidget(Button.builder(sectionLabel("screen.mapperzones.stay_actions_label", stayText), b -> openEditor("stay")).bounds(x, 185, bw, 22).build());
        addRenderableWidget(Button.builder(sectionLabel("screen.mapperzones.condition_help_button", conditionsText), b -> openEditor("conditions")).bounds(x + bw + gap, 185, bw, 22).build());

        int bottom = height - 25;
        int saveW = Math.max(70, w / 3);
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.save"), b -> save()).bounds(x, bottom, saveW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back"), b -> minecraft.setScreen(parent)).bounds(x + w - saveW, bottom, saveW, 20).build());
    }

    private Component shapeLabel() { return Component.translatable("screen.mapperzones.shape_selected", SHAPE_VALUES.get(shapeIndex)); }
    private Component enabledLabel() { return Component.translatable("screen.mapperzones.enabled", enabled); }
    private Component oneTimeLabel() { return Component.translatable("screen.mapperzones.one_time", oneTime); }
    private Component sectionLabel(String labelKey, String text) {
        int count = parseLines(text).size();
        return Component.translatable("screen.mapperzones.lines_count", Component.translatable(labelKey).getString(), count);
    }
    private EditBox field(int x, int y, int w, String hint, String value) {
        EditBox e = new EditBox(font, x, y, w, 20, Component.literal(hint)); e.setMaxLength(128); e.setValue(value); e.setHint(Component.literal(hint)); addRenderableWidget(e); return e;
    }
    private void openEditor(String target) { minecraft.setScreen(new ZoneTextEditorScreen(this, target, getText(target), value -> setText(target, value))); }
    private String getText(String target) { return switch (target) { case "enter" -> enterText; case "exit" -> exitText; case "stay" -> stayText; default -> conditionsText; }; }
    private void setText(String target, String value) {
        switch (target) {
            case "enter" -> { enterText = value; source.add("enterActions", toArray(value)); }
            case "exit" -> { exitText = value; source.add("exitActions", toArray(value)); }
            case "stay" -> { stayText = value; source.add("stayActions", toArray(value)); }
            default -> { conditionsText = value; source.add("conditions", toArray(value)); }
        }
        // Persist each action/condition editor as soon as it is closed, rather than
        // relying on the separate Save button on the zone overview screen.
        ModNetwork.saveZone(source.toString());
    }
    private static String list(JsonObject o, String k) { if (o == null || !o.has(k) || !o.get(k).isJsonArray()) return ""; StringJoiner j = new StringJoiner("\n"); for (JsonElement e:o.getAsJsonArray(k)) j.add(e.getAsString()); return j.toString(); }
    static List<String> parseLines(String s) { List<String> out = new ArrayList<>(); for (String x : s.split("[;\\n]")) { String v=x.trim(); if (!v.isEmpty()) out.add(v); } return out; }
    private static JsonArray toArray(String s) { JsonArray a=new JsonArray(); parseLines(s).forEach(a::add); return a; }

    private void save() {
        try {
            String sh = SHAPE_VALUES.get(shapeIndex);
            if (!List.of("BOX","SPHERE","CYLINDER").contains(sh)) throw new IllegalArgumentException(Component.translatable("screen.mapperzones.invalid_shape_value").getString());
            int si=Math.max(1,Math.min(72000,Integer.parseInt(interval.getValue()))), cd=Math.max(0,Math.min(72000,Integer.parseInt(cooldown.getValue())));
            source.addProperty("shape",sh); source.addProperty("enabled",enabled); source.addProperty("oneTime",oneTime); source.addProperty("stayInterval",si); source.addProperty("cooldownTicks",cd);
            source.add("enterActions",toArray(enterText)); source.add("exitActions",toArray(exitText)); source.add("stayActions",toArray(stayText)); source.add("conditions",toArray(conditionsText));
            ModNetwork.saveZone(source.toString()); minecraft.setScreen(parent);
        } catch(Exception ex) { if(minecraft.player!=null) minecraft.player.displayClientMessage(Component.translatable("screen.mapperzones.invalid_editor_value", ex.getMessage()),false); }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt) {
        renderBackground(g);
        g.drawCenteredString(font,title,width/2,6,0xFFFFFF);
        int x=(width-Math.min(320,width-24))/2;
        g.drawString(font,Component.translatable("screen.mapperzones.shape_label"),x,17,0xA0D8FF);
        g.drawString(font,Component.translatable("screen.mapperzones.stay_interval"),x,79,0xA0D8FF);
        g.drawString(font,Component.translatable("screen.mapperzones.cooldown"),x,115,0xA0D8FF);
        super.render(g,mx,my,pt);
    }
    @Override public boolean isPauseScreen(){return false;}
}
