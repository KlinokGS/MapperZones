package net.mapperzones.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.mapperzones.network.ModNetwork;

import java.util.*;

public class ZoneEditScreen extends Screen {
    private final Screen parent;
    private final String zoneName;
    private JsonObject source;
    private EditBox shape, interval, cooldown, enter, exit, stay, conditions;
    private boolean enabled = true, oneTime = false;

    public ZoneEditScreen(Screen parent, String name) {
        super(Component.translatable("screen.mapperzones.edit", name));
        this.parent = parent;
        this.zoneName = name;
        this.source = ClientData.find(name);
    }

    @Override
    protected void init() {
        int x = width / 2 - 155, y = 38, w = 310;
        if (source == null) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back"), b -> minecraft.setScreen(parent)).bounds(x, y, 90, 20).build());
            return;
        }
        shape = field(x, y, w, 20, Component.translatable("screen.mapperzones.shape_hint").getString(), source.has("shape") ? source.get("shape").getAsString() : "BOX");
        y += 34;
        enabled = !source.has("enabled") || source.get("enabled").getAsBoolean();
        oneTime = source.has("oneTime") && source.get("oneTime").getAsBoolean();
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.enabled", enabled), b -> {
            enabled = !enabled;
            b.setMessage(Component.translatable("screen.mapperzones.enabled", enabled));
        }).bounds(x, y, 145, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.one_time", oneTime), b -> {
            oneTime = !oneTime;
            b.setMessage(Component.translatable("screen.mapperzones.one_time", oneTime));
        }).bounds(x + 155, y, 155, 20).build());
        y += 28;
        interval = field(x, y, w, 20, Component.translatable("screen.mapperzones.stay_interval").getString(), source.has("stayInterval") ? source.get("stayInterval").getAsString() : "40");
        y += 32;
        cooldown = field(x, y, w, 20, Component.translatable("screen.mapperzones.cooldown").getString(), source.has("cooldownTicks") ? source.get("cooldownTicks").getAsString() : "0");
        y += 32;
        enter = field(x, y, w, 20, Component.translatable("screen.mapperzones.enter_actions").getString(), list(source, "enterActions"));
        y += 32;
        exit = field(x, y, w, 20, Component.translatable("screen.mapperzones.exit_actions").getString(), list(source, "exitActions"));
        y += 32;
        stay = field(x, y, w, 20, Component.translatable("screen.mapperzones.stay_actions").getString(), list(source, "stayActions"));
        y += 32;
        conditions = field(x, y, w, 20, Component.translatable("screen.mapperzones.conditions").getString(), list(source, "conditions"));
        y += 32;
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.save"), b -> save()).bounds(x, y, 90, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.action_syntax"), b -> minecraft.setScreen(new ActionBuilderScreen(this))).bounds(x + 100, y, 110, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back"), b -> minecraft.setScreen(parent)).bounds(x + 220, y, 90, 22).build());
    }

    private EditBox field(int x, int y, int w, int h, String hint, String value) {
        EditBox e = new EditBox(font, x, y, w, h, Component.literal(hint));
        e.setMaxLength(2048);
        e.setValue(value);
        e.setHint(Component.literal(hint));
        addRenderableWidget(e);
        return e;
    }

    private static String list(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonArray()) return "";
        StringJoiner j = new StringJoiner("; ");
        for (JsonElement e : o.getAsJsonArray(k)) j.add(e.getAsString());
        return j.toString();
    }

    private static JsonArray parseLines(String s) {
        JsonArray a = new JsonArray();
        for (String x : s.split("[;\\n]")) {
            String v = x.trim();
            if (!v.isEmpty()) a.add(v);
        }
        return a;
    }

    private void save() {
        try {
            String sh = shape.getValue().trim().toUpperCase(Locale.ROOT);
            if (!List.of("BOX", "SPHERE", "CYLINDER").contains(sh))
                throw new IllegalArgumentException("Shape must be BOX, SPHERE, or CYLINDER");
            int si = Math.max(1, Math.min(72000, Integer.parseInt(interval.getValue()))), cd = Math.max(0, Math.min(72000, Integer.parseInt(cooldown.getValue())));
            source.addProperty("shape", sh);
            source.addProperty("enabled", enabled);
            source.addProperty("oneTime", oneTime);
            source.addProperty("stayInterval", si);
            source.addProperty("cooldownTicks", cd);
            source.add("enterActions", parseLines(enter.getValue()));
            source.add("exitActions", parseLines(exit.getValue()));
            source.add("stayActions", parseLines(stay.getValue()));
            source.add("conditions", parseLines(conditions.getValue()));
            ModNetwork.saveZone(source.toString());
            minecraft.setScreen(parent);
        } catch (Exception ex) {
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(Component.literal("Invalid editor value: " + ex.getMessage()), false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title.getString(), width / 2, 15, 0xFFFFFF);
        int lx = width / 2 - 155;
        // Labels are anchored to their corresponding input fields, avoiding overlap with the toggle row.
        g.drawString(font, Component.translatable("screen.mapperzones.shape_label"), lx, 28, 0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.stay_interval"), lx, 91, 0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.cooldown"), lx, 123, 0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.enter_actions_label"), lx, 155, 0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.exit_actions_label"), lx, 187, 0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.stay_actions_label"), lx, 219, 0xA0D8FF);
        g.drawString(font, Component.translatable("screen.mapperzones.conditions"), lx, 251, 0xA0D8FF);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
