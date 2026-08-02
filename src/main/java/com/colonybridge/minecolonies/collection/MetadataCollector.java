package com.colonybridge.minecolonies.collection;

import com.colonybridge.api.AdapterStatus;
import com.colonybridge.model.*;
import com.minecolonies.api.colony.IColony;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

import java.util.*;

public final class MetadataCollector {
    public GameData game(AdapterStatus status) {
        return new GameData(
                SharedConstants.getCurrentVersion().getName(),
                "NeoForge",
                ModList.get().getModContainerById("neoforge").map(container -> container.getModInfo().getVersion().toString())
                        .orElse(FMLLoader.versionInfo().neoForgeVersion()),
                status.mineColoniesVersion());
    }

    public ColonyData colony(ColonyCollectionContext context) {
        context.requireServerThread();
        IColony colony = context.colony();
        UUID ownerUuid = null;
        String ownerName = null;
        try {
            ownerUuid = context.config().includeOwnerUuid() ? colony.getPermissions().getOwner() : null;
            ownerName = colony.getPermissions().getOwnerName();
        } catch (Exception exception) {
            context.errors().add(CollectionSupport.error("colony", context.colonyId(), "OWNER_READ_FAILED", exception));
        }
        Integer maxCitizens = null;
        Integer citizenCount = null;
        try {
            maxCitizens = colony.getCitizenManager().getMaxCitizens();
            citizenCount = colony.getCitizenManager().getCurrentCitizenCount();
        } catch (Exception exception) {
            context.errors().add(CollectionSupport.error("colony", context.colonyId(), "CITIZEN_COUNTS_FAILED", exception));
        }
        Boolean raided = null;
        try {
            raided = colony.getRaiderManager().isRaided();
        } catch (Exception exception) {
            context.errors().add(CollectionSupport.error("colony", context.colonyId(), "RAID_STATE_FAILED", exception));
        }
        Map<String, Object> flags = new TreeMap<>();
        flags.put("day", CollectionSupport.safe(colony::getDay, null));
        flags.put("textureStyle", CollectionSupport.safe(colony::getTextureStyleId, null));
        flags.put("structurePack", CollectionSupport.safe(colony::getStructurePack, null));
        flags.put("nameStyle", CollectionSupport.safe(colony::getNameStyle, null));
        flags.put("colonyColor", CollectionSupport.safe(() -> colony.getTeamColonyColor().getName(), null));
        flags.put("lastContactHours", CollectionSupport.safe(colony::getLastContactInHours, null));
        flags.put("potentialCitizenCapacity", CollectionSupport.safe(() -> colony.getCitizenManager().getPotentialMaxCitizens(), null));
        flags.put("researchCitizenCapacityBonus", CollectionSupport.safe(() -> colony.getCitizenManager().maxCitizensFromResearch(), null));
        flags.put("waypointCount", CollectionSupport.safe(() -> colony.getWayPoints().size(), null));
        flags.put("graveCount", CollectionSupport.safe(() -> colony.getGraveManager().getGraves().size(), null));
        flags.put("memberCount", CollectionSupport.safe(() -> colony.getPermissions().getPlayers().size(), null));
        flags.put("raidLevel", CollectionSupport.safe(() -> colony.getRaiderManager().getColonyRaidLevel(), null));
        flags.put("raidDifficultyModifier", CollectionSupport.safe(() -> colony.getRaiderManager().getRaidDifficultyModifier(), null));
        flags.put("nightsSinceLastRaid", CollectionSupport.safe(() -> colony.getRaiderManager().getNightsSinceLastRaid(), null));
        flags.put("raidExpectedTonight", CollectionSupport.safe(() -> colony.getRaiderManager().willRaidTonight(), null));
        flags.put("raidsEnabled", CollectionSupport.safe(() -> colony.getRaiderManager().canHaveRaiderEvents(), null));
        flags.put("raidCanStart", CollectionSupport.safe(() -> colony.getRaiderManager().canRaid(), null));
        flags.put("spiesEnabled", CollectionSupport.safe(() -> colony.getRaiderManager().areSpiesEnabled(), null));
        flags.put("expectedRaiderCount", CollectionSupport.safe(() -> colony.getRaiderManager().calculateRaiderAmount(
                colony.getCitizenManager().getCurrentCitizenCount()), null));
        flags.put("lostCitizensToRaids", CollectionSupport.safe(() -> colony.getRaiderManager().getLostCitizen(), null));
        Object state = CollectionSupport.safe(colony::getState, null);
        return new ColonyData(
                colony.getID(), CollectionSupport.safe(colony::getName, null), ownerUuid, ownerName,
                CollectionSupport.dimension(colony.getDimension()), CollectionSupport.pos(colony.getCenter()),
                CollectionSupport.safe(() -> colony.getTicketedChunks().size(), null), citizenCount, maxCitizens,
                CollectionSupport.safe(colony::getOverallHappiness, null), CollectionSupport.safe(colony::isActive, null),
                CollectionSupport.safe(colony::isColonyUnderAttack, null), raided,
                state == null ? null : "ABANDONED".equalsIgnoreCase(String.valueOf(state)),
                state == null ? null : String.valueOf(state), flags);
    }

    public WorldData world(ColonyCollectionContext context) {
        context.requireServerThread();
        MinecraftServer server = context.levelContext().server();
        return new WorldData(
                CollectionSupport.safe(() -> server.getWorldData().getLevelName(), null),
                CollectionSupport.safe(() -> server.getServerDirectory().toAbsolutePath().normalize().toString(), null),
                CollectionSupport.dimension(context.colony().getDimension()), server.isDedicatedServer());
    }

    public static Map<String, CapabilityData> capabilities() {
        Map<String, CapabilityData> map = new TreeMap<>();
        for (String available : List.of("colonies", "citizens", "buildings", "requests", "construction", "environment",
                "territory", "livestock", "raidForecast", "medicalCare", "research", "researchEffects", "statistics",
                "recentStatistics", "defenseStatistics", "foodSupply", "stockLedger", "constructionMetadata")) {
            map.put(available, CapabilityData.available());
        }
        map.put("constructionMaterials", CapabilityData.unsupported("Not currently exposed by the read-only bridge"));
        map.put("warehouseInventoryTotals", CapabilityData.unsupported("The stock ledger covers known colony building storage, not a warehouse-only authoritative inventory"));
        return Collections.unmodifiableMap(map);
    }
}
