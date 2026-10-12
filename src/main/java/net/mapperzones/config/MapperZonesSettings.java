package net.mapperzones.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-facing MapperZones settings persisted in config/mapperzones/settings.json. */
public final class MapperZonesSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("mapperzones").resolve("settings.json");
    private static int highlightDistance = 8;
    private static boolean shortCommandEnabled = true;
    private static boolean showZoneNames = true;
    private static boolean loaded;

    private MapperZonesSettings() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                JsonObject json = com.google.gson.JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
                if (json.has("highlightDistance")) {
                    int value = json.get("highlightDistance").getAsInt();
                    if (value == 4 || value == 8 || value == 12 || value == 16) highlightDistance = value;
                }
                if (json.has("shortCommandEnabled")) shortCommandEnabled = json.get("shortCommandEnabled").getAsBoolean();
                if (json.has("showZoneNames")) showZoneNames = json.get("showZoneNames").getAsBoolean();
            } else {
                save();
            }
        } catch (Exception ignored) {
            // Keep safe defaults if the settings file is malformed or unreadable.
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("highlightDistance", highlightDistance);
            json.addProperty("shortCommandEnabled", shortCommandEnabled);
            json.addProperty("showZoneNames", showZoneNames);
            Files.writeString(FILE, GSON.toJson(json), StandardCharsets.UTF_8);
            loaded = true;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save MapperZones settings to " + FILE, exception);
        }
    }

    public static int highlightDistance() { load(); return highlightDistance; }
    public static boolean shortCommandEnabled() { load(); return shortCommandEnabled; }
    public static boolean showZoneNames() { load(); return showZoneNames; }
    public static void setHighlightDistance(int value) {
        if (value == 4 || value == 8 || value == 12 || value == 16) highlightDistance = value;
    }
    public static void cycleHighlightDistance() {
        highlightDistance = switch (highlightDistance) { case 4 -> 8; case 8 -> 12; case 12 -> 16; default -> 4; };
    }
    public static void setShortCommandEnabled(boolean value) { shortCommandEnabled = value; }
    public static void setShowZoneNames(boolean value) { showZoneNames = value; }
}
