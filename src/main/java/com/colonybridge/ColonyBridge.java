package com.colonybridge;

import com.colonybridge.bootstrap.ServerLifecycleCoordinator;
import com.colonybridge.command.ColonyBridgeCommand;
import com.colonybridge.config.ColonyBridgeConfig;
import com.colonybridge.export.ColonyBridgeExporter;
import com.colonybridge.market.trader.MarketRegistries;
import com.colonybridge.minecolonies.MineColoniesAdapterFactory;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(ColonyBridgeConstants.MOD_ID)
public final class ColonyBridge {
    public static final Logger LOGGER = LogUtils.getLogger();

    private final ColonyBridgeExporter exporter;
    private final ServerLifecycleCoordinator lifecycle;

    public ColonyBridge(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ColonyBridgeConfig.SERVER_SPEC);
        MarketRegistries.register(modEventBus);
        modEventBus.addListener(MarketRegistries::addCreativeTabContents);
        exporter = new ColonyBridgeExporter(LOGGER, MineColoniesAdapterFactory.create());
        lifecycle = new ServerLifecycleCoordinator(LOGGER, exporter);
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Colony Bridge initialized with adapter {}", exporter.status().adapterName());
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        ColonyBridgeCommand.register(event.getDispatcher(), exporter);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        lifecycle.serverStarted(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        lifecycle.serverStopping(event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        lifecycle.serverTick(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        lifecycle.playerLoggedIn(event);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        lifecycle.playerLoggedOut(event);
    }
}
