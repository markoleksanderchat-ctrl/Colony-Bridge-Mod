package com.colonybridge.market.trader;

import com.colonybridge.ColonyBridgeConstants;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class RoyalExchangeJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            ColonyBridgeConstants.MOD_ID, "royal_exchange_screen");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(RoyalExchangeScreen.class, FullScreenProperties::new);
    }

    private record FullScreenProperties(RoyalExchangeScreen screen) implements IGuiProperties {
        @Override public Class<? extends Screen> screenClass() { return RoyalExchangeScreen.class; }
        @Override public int guiLeft() { return 0; }
        @Override public int guiTop() { return 0; }
        @Override public int guiXSize() { return screen.screenWidthPixels(); }
        @Override public int guiYSize() { return screen.screenHeightPixels(); }
        @Override public int screenWidth() { return screen.screenWidthPixels(); }
        @Override public int screenHeight() { return screen.screenHeightPixels(); }
    }
}
