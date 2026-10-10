package net.mapperzones.client;

import net.minecraft.client.Minecraft;

public final class ClientNetworkHandler {
    private ClientNetworkHandler() {
    }

    public static void load(String zones, String globals, String players) {
        ClientData.load(zones, globals, players);
    }

    public static void openGui() {
        Minecraft.getInstance().setScreen(new ZoneListScreen());
    }
}
