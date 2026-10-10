package net.mapperzones.item;

import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import net.mapperzones.MapperZonesMod;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister
            .create(ForgeRegistries.ITEMS, MapperZonesMod.MODID);
    public static final RegistryObject<Item> ZONE_WAND = ITEMS.register("zone_wand", ()
            -> new ZoneWandItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
