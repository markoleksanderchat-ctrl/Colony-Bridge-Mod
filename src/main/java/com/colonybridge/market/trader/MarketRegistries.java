package com.colonybridge.market.trader;

import com.colonybridge.ColonyBridgeConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public final class MarketRegistries {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ColonyBridgeConstants.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ColonyBridgeConstants.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ColonyBridgeConstants.MOD_ID);

    public static final DeferredBlock<RoyalExchangeBlock> ROYAL_EXCHANGE = BLOCKS.registerBlock("royal_exchange",
            RoyalExchangeBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> ROYAL_EXCHANGE_ITEM = ITEMS.registerSimpleBlockItem(ROYAL_EXCHANGE, new Item.Properties());
    public static final DeferredHolder<MenuType<?>, MenuType<RoyalExchangeMenu>> ROYAL_EXCHANGE_MENU = MENUS.register(
            "royal_exchange", () -> new MenuType<>(RoyalExchangeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private MarketRegistries() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
    }

    public static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(ROYAL_EXCHANGE_ITEM.get());
    }
}
