package net.mapperzones.client;

import com.google.gson.*;

import java.util.*;

public final class ClientData {
    public static final List<JsonObject> ZONES = new ArrayList<>();
    public static final Map<String, String> GLOBALS = new LinkedHashMap<>();
    public static final Map<String, String> PLAYER_NAMES = new LinkedHashMap<>();
    public static final Map<String, Map<String, String>> PLAYER_VARIABLES = new LinkedHashMap<>();
    public static final Map<String, String> PLAYERS = new LinkedHashMap<>();

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
            PLAYERS.clear(); PLAYER_NAMES.clear(); PLAYER_VARIABLES.clear();
            JsonObject p = JsonParser.parseString(players).getAsJsonObject();
            for (var x : p.entrySet()) {
                String id = x.getKey();
                if (x.getValue().isJsonObject() && x.getValue().getAsJsonObject().has("vars")) {
                    JsonObject entry = x.getValue().getAsJsonObject();
                    PLAYER_NAMES.put(id, entry.has("name") ? entry.get("name").getAsString() : id);
                    Map<String,String> values = new LinkedHashMap<>();
                    entry.getAsJsonObject("vars").entrySet().forEach(v -> values.put(v.getKey(), v.getValue().getAsString()));
                    PLAYER_VARIABLES.put(id, values);
                } else {
                    PLAYERS.put(id, x.getValue().getAsString());
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static JsonObject find(String name) {
        return ZONES.stream().filter(z -> z.has("name") && z.get("name").getAsString().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
