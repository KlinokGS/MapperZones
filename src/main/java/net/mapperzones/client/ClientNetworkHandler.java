package net.mapperzones.client;

import net.minecraft.client.Minecraft;

public final class ClientNetworkHandler {
    private ClientNetworkHandler() {
    }

    public static void load(String zones, String globals, String players) {
        ClientData.load(zones, globals, players);
        if (Minecraft.getInstance().screen instanceof VariableScreen variables) {
            variables.onDataSynced();
        }
    }

    public static void openGui() {
        Minecraft.getInstance().setScreen(new ZoneListScreen());
    }
}
