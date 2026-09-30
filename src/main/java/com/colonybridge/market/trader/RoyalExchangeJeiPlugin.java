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
        registration.addGuiScreenHandler(RoyalExchangeScreen.class, screen ->
                FullScreenProperties.create(screen.screenWidthPixels(), screen.screenHeightPixels()));
    }

    // Snapshot dimensions: retaining the mutable screen hides resize changes from JEI's cache.
    record FullScreenProperties(int screenWidth, int screenHeight) implements IGuiProperties {
        static FullScreenProperties create(int width, int height) {
            // JEI queries during ScreenEvent.Opening, before Minecraft initializes the screen.
            if (width < 1 || height < 1 || width > 1_000_000_000 || height > 1_000_000_000) return null;
            return new FullScreenProperties(width, height);
        }
        @Override public Class<? extends Screen> screenClass() { return RoyalExchangeScreen.class; }
        @Override public int guiLeft() { return 0; }
        @Override public int guiTop() { return 0; }
        @Override public int guiXSize() { return screenWidth; }
        @Override public int guiYSize() { return screenHeight; }
    }
}
