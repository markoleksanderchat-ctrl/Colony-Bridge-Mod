package com.colonybridge.notification;

import com.colonybridge.utility.DayCounterState;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ColonyDayTracker {
    private final Logger logger;
    private final ColonyDayCelebration celebration;
    private final int checkIntervalTicks;
    private final Map<Integer, DayCounterState> colonyDayCounters = new HashMap<>();
    private int ticksUntilCheck;
    private boolean wasEnabled;

    public ColonyDayTracker(Logger logger, ColonyDayCelebration celebration, int checkIntervalTicks) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.celebration = Objects.requireNonNull(celebration, "celebration");
        if (checkIntervalTicks < 1) throw new IllegalArgumentException("checkIntervalTicks must be positive");
        this.checkIntervalTicks = checkIntervalTicks;
        this.ticksUntilCheck = checkIntervalTicks;
    }

    public void start(boolean enabled) {
        ticksUntilCheck = checkIntervalTicks;
        wasEnabled = enabled;
        resetCounters();
    }

    public void tick(MinecraftServer server, boolean enabled) {
        if (!enabled) {
            wasEnabled = false;
            return;
        }
        if (!wasEnabled) {
            resetCounters();
            wasEnabled = true;
        }
        if (--ticksUntilCheck > 0) return;
        ticksUntilCheck = checkIntervalTicks;

        Map<Integer, Long> changedColonies = new HashMap<>();
        Set<Integer> seenColonies = new HashSet<>();
        IColonyManager manager;
        try {
            manager = IColonyManager.getInstance();
            for (var colony : manager.getAllColonies()) {
                int colonyId = colony.getID();
                seenColonies.add(colonyId);
                try {
                    DayCounterState counter = colonyDayCounters.computeIfAbsent(colonyId, ignored -> new DayCounterState());
                    if (!counter.initialized()) counter.reset(colony.getDay());
                    else counter.observe(colony.getDay()).ifPresent(day -> changedColonies.put(colonyId, day));
                } catch (RuntimeException failure) {
                    logger.debug("Colony Bridge could not inspect colony day for colony {}.", colonyId, failure);
                }
            }
        } catch (RuntimeException failure) {
            logger.debug("Colony Bridge could not inspect MineColonies day state.", failure);
            return;
        }
        colonyDayCounters.keySet().retainAll(seenColonies);
        if (changedColonies.isEmpty()) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            IColony colony;
            try {
                colony = manager.getIColony(player.serverLevel(), player.blockPosition());
            } catch (RuntimeException failure) {
                logger.debug("Colony Bridge could not resolve the colony for player {}.",
                        player.getGameProfile().getName(), failure);
                continue;
            }
            Long colonyDay = colony == null ? null : changedColonies.get(colony.getID());
            if (colonyDay != null) celebration.show(player, colonyDay);
        }
    }

    public void clear() {
        colonyDayCounters.clear();
        wasEnabled = false;
        ticksUntilCheck = checkIntervalTicks;
    }

    private void resetCounters() {
        colonyDayCounters.clear();
        try {
            for (var colony : IColonyManager.getInstance().getAllColonies()) {
                try {
                    DayCounterState counter = new DayCounterState();
                    counter.reset(colony.getDay());
                    colonyDayCounters.put(colony.getID(), counter);
                } catch (RuntimeException failure) {
                    logger.debug("Colony Bridge could not initialize a colony day counter.", failure);
                }
            }
        } catch (RuntimeException failure) {
            logger.debug("Colony Bridge could not initialize colony day counters.", failure);
        }
    }
}
