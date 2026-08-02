package com.colonybridge.model;

public record TerritoryData(
        Integer claimedChunks,
        Integer approximateClaimedBlocks,
        Integer minChunkX,
        Integer maxChunkX,
        Integer minChunkZ,
        Integer maxChunkZ,
        Integer widthChunks,
        Integer lengthChunks,
        Integer loadedChunks,
        Integer ticketedChunks
) {
    public static TerritoryData estimatedFromTickets(Integer loadedChunks, Integer ticketedChunks) {
        int knownTickets = ticketedChunks == null ? 0 : Math.max(0, ticketedChunks);
        return new TerritoryData(
                knownTickets,
                knownTickets * 256,
                null,
                null,
                null,
                null,
                null,
                null,
                loadedChunks,
                ticketedChunks
        );
    }
}
