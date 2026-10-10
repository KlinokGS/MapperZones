package net.mapperzones.client;

import net.minecraftforge.common.MinecraftForge;

public final class ClientBootstrap {
    private ClientBootstrap() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ZoneRenderer());
    }
}
