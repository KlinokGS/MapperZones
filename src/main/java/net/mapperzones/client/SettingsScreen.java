package net.mapperzones.client;

import net.mapperzones.config.MapperZonesSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
    private final Screen parent;
    private Button distanceButton;
    private Button commandButton;
    private Button namesButton;
    private Button errorsButton;
    private int draftDistance;
    private boolean draftShortCommand;
    private boolean draftZoneNames;
    private boolean draftActionErrorsInChat;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("screen.mapperzones.settings.title"));
        this.parent = parent;
        MapperZonesSettings.load();
        draftDistance = MapperZonesSettings.highlightDistance();
        draftShortCommand = MapperZonesSettings.shortCommandEnabled();
        draftZoneNames = MapperZonesSettings.showZoneNames();
        draftActionErrorsInChat = MapperZonesSettings.actionErrorsInChat();
    }

    @Override
    protected void init() {
        int center = width / 2;
        int buttonWidth = Math.min(300, width - 40);
        int left = center - buttonWidth / 2;
        int top = Math.max(48, height / 2 - 78);

        distanceButton = addRenderableWidget(Button.builder(distanceLabel(), b -> {
            draftDistance = switch (draftDistance) { case 4 -> 8; case 8 -> 12; case 12 -> 16; default -> 4; };
            b.setMessage(distanceLabel());
        }).bounds(left, top, buttonWidth, 24).build());
        commandButton = addRenderableWidget(Button.builder(commandLabel(), b -> {
            draftShortCommand = !draftShortCommand;
            b.setMessage(commandLabel());
        }).bounds(left, top + 34, buttonWidth, 24).build());
        namesButton = addRenderableWidget(Button.builder(namesLabel(), b -> {
            draftZoneNames = !draftZoneNames;
            b.setMessage(namesLabel());
        }).bounds(left, top + 68, buttonWidth, 24).build());

        errorsButton = addRenderableWidget(Button.builder(errorsLabel(), b -> {
            draftActionErrorsInChat = !draftActionErrorsInChat;
            b.setMessage(errorsLabel());
        }).bounds(left, top + 102, buttonWidth, 24).build());

        int bottomY = height - 36;
        int gap = 8;
        int half = (buttonWidth - gap) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.settings.save"), b -> {
            try {
                MapperZonesSettings.setHighlightDistance(draftDistance);
                MapperZonesSettings.setShortCommandEnabled(draftShortCommand);
                MapperZonesSettings.setShowZoneNames(draftZoneNames);
                MapperZonesSettings.setActionErrorsInChat(draftActionErrorsInChat);
                MapperZonesSettings.save();
                minecraft.setScreen(parent);
            } catch (RuntimeException exception) {
                // Keep the screen open if the settings file could not be written.
            }
        }).bounds(left, bottomY, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.settings.back"), b -> {
            draftDistance = MapperZonesSettings.highlightDistance();
            draftShortCommand = MapperZonesSettings.shortCommandEnabled();
            draftZoneNames = MapperZonesSettings.showZoneNames();
            draftActionErrorsInChat = MapperZonesSettings.actionErrorsInChat();
            minecraft.setScreen(parent);
        }).bounds(left + half + gap, bottomY, half, 20).build());
    }

    private Component distanceLabel() {
        return Component.translatable("screen.mapperzones.settings.distance", draftDistance);
    }
    private Component commandLabel() {
        return Component.translatable("screen.mapperzones.settings.short_command", toggle(draftShortCommand));
    }
    private Component errorsLabel() {
        return Component.translatable("screen.mapperzones.settings.errors_chat", toggle(draftActionErrorsInChat));
    }
    private Component namesLabel() {
        return Component.translatable("screen.mapperzones.settings.zone_names", toggle(draftZoneNames));
    }
    private Component toggle(boolean enabled) {
        return Component.translatable(enabled ? "screen.mapperzones.settings.enabled" : "screen.mapperzones.settings.disabled");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.mapperzones.settings.subtitle"), width / 2, 42, 0xA0D8FF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
