package com.colonybridge.market.trader;

import com.colonybridge.ColonyBridgeConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = ColonyBridgeConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MarketClientEvents {
    private MarketClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MarketRegistries.ROYAL_EXCHANGE_MENU.get(), RoyalExchangeScreen::new);
    }
}
