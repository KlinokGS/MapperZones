package net.mapperzones;

import net.mapperzones.client.ClientBootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.mapperzones.item.ModItems;
import net.mapperzones.command.ZoneCommands;
import net.mapperzones.network.ModNetwork;
import net.mapperzones.data.MapperData;
import net.mapperzones.zone.ZoneManager;

@Mod(MapperZonesMod.MODID)
public class MapperZonesMod {
    public static final String MODID = "mapperzones";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MapperZonesMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(bus);
        bus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientBootstrap::init);
    }

    private void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(ModNetwork::register);
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent e) {
        ZoneCommands.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) {
            MinecraftServer s = e.getServer();
            if (s != null) ZoneManager.tick(s);
        }
    }

    @SubscribeEvent
    public void serverStopping(ServerStoppingEvent e) {
        ZoneManager.onServerStopping(e.getServer());
    }

    // Full snapshots are limited to operators; ordinary players do not need the editor data.
    @SubscribeEvent
    public void joined(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && p.hasPermissions(2)) ModNetwork.sync(p);
    }

    @SubscribeEvent
    public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && p.hasPermissions(2)) ModNetwork.sync(p);
    }
}
