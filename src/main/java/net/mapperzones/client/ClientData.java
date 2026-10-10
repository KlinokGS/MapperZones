package net.mapperzones.client;

import com.google.gson.*;

import java.util.*;

public final class ClientData {
    public static final List<JsonObject> ZONES = new ArrayList<>();
    public static final Map<String, String> GLOBALS = new LinkedHashMap<>(), PLAYERS = new LinkedHashMap<>();

    private ClientData() {
    }

    public static void load(String zones, String globals, String players) {
        try {
            ZONES.clear();
            JsonElement e = JsonParser.parseString(zones);
            if (e.isJsonArray())
                for (JsonElement z : e.getAsJsonArray()) if (z.isJsonObject()) ZONES.add(z.getAsJsonObject());
            GLOBALS.clear();
            JsonObject g = JsonParser.parseString(globals).getAsJsonObject();
            g.entrySet().forEach(x -> GLOBALS.put(x.getKey(), x.getValue().getAsString()));
            PLAYERS.clear();
            JsonObject p = JsonParser.parseString(players).getAsJsonObject();
            p.entrySet().forEach(x -> PLAYERS.put(x.getKey(), x.getValue().getAsString()));
        } catch (Exception ignored) {
        }
    }

    public static JsonObject find(String name) {
        return ZONES.stream().filter(z -> z.has("name") && z.get("name").getAsString().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
