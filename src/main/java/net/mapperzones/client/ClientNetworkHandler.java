package net.mapperzones.client;

import net.minecraft.client.Minecraft;

public final class ClientNetworkHandler {
    private ClientNetworkHandler() {
    }

    public static void load(String zones, String globals, String players) {
        ClientData.load(zones, globals, players);
        // A data snapshot may arrive while the player is moving around the world.
        // Keep local wand points intact: they are the user's in-progress selection,
        // not server-owned zone data. Explicit wand actions clear them when needed.
        if (Minecraft.getInstance().screen instanceof VariableScreen variables) {
            variables.onDataSynced();
        }
    }

    public static void openGui() {
        Minecraft.getInstance().setScreen(new ZoneListScreen());
    }
}
