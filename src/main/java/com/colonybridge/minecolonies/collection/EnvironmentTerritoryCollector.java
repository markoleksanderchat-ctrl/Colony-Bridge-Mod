package com.colonybridge.minecolonies.collection;

import com.colonybridge.model.*;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

public final class EnvironmentTerritoryCollector {
    private final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Map<Integer, List<ChunkPos>>> claimIndexes = new HashMap<>();
    public void beginCollection() { claimIndexes.clear(); }

    private Map<Integer, List<ChunkPos>> indexClaims(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        Map<Integer, List<ChunkPos>> result = new HashMap<>();
        for (var entry : IColonyManager.getInstance().getClaimData(dimension).entrySet()) {
            Set<Integer> owners = new HashSet<>(entry.getValue().getStaticClaimColonies());
            owners.add(entry.getValue().getOwningColony());
            for (int owner : owners) result.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(entry.getKey());
        }
        result.values().forEach(claims -> claims.sort(Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z)));
        return result;
    }

    public EnvironmentData environment(ColonyCollectionContext context, List<BuildingData> buildings) {
        context.requireServerThread();
        ServerLevel level = context.levelContext().server().getLevel(context.colony().getDimension());
        if (level == null) return new EnvironmentData(null, List.of(), null, null, null, null, null, null, null);
        Set<String> biomes = new TreeSet<>();
        String centerBiome = biomeId(level, context.colony().getCenter());
        if (centerBiome != null) biomes.add(centerBiome);
        buildings.stream().map(BuildingData::position).filter(Objects::nonNull).limit(31)
                .map(position -> new BlockPos(position.x(), position.y(), position.z()))
                .map(position -> biomeId(level, position)).filter(Objects::nonNull).forEach(biomes::add);
        long dayTime = level.getDayTime();
        return new EnvironmentData(centerBiome, List.copyOf(biomes), Math.floorDiv(dayTime, 24000L),
                Math.floorMod(dayTime, 24000L), level.getGameTime(), CollectionSupport.safe(level::getMoonPhase, null),
                CollectionSupport.safe(context.colony()::isDay, null), level.isRaining(), level.isThundering());
    }

    public TerritoryData territory(ColonyCollectionContext context) {
        context.requireServerThread();
        List<ChunkPos> claims;
        try {
            claims = claimIndexes.computeIfAbsent(context.colony().getDimension(), this::indexClaims)
                    .getOrDefault(context.colony().getID(), List.of());
        } catch (RuntimeException exception) {
            claims = List.of();
            context.warnings().add(CollectionSupport.error("territory", context.colonyId(), "CLAIM_MAP_READ_FAILED", exception));
        }
        if (claims.isEmpty()) {
            Integer ticketed = CollectionSupport.safe(() -> context.colony().getTicketedChunks().size(), null);
            return TerritoryData.estimatedFromTickets(CollectionSupport.safe(context.colony()::getLoadedChunkCount, null), ticketed);
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChunkPos claim : claims) {
            minX = Math.min(minX, claim.x); maxX = Math.max(maxX, claim.x);
            minZ = Math.min(minZ, claim.z); maxZ = Math.max(maxZ, claim.z);
        }
        return new TerritoryData(claims.size(), claims.size() * 256, minX, maxX, minZ, maxZ,
                maxX - minX + 1, maxZ - minZ + 1,
                CollectionSupport.safe(context.colony()::getLoadedChunkCount, null),
                CollectionSupport.safe(() -> context.colony().getTicketedChunks().size(), null));
    }

    private String biomeId(ServerLevel level, BlockPos position) {
        return position == null ? null : level.getBiome(position).unwrapKey()
                .map(key -> key.location().toString()).orElse(null);
    }
}
