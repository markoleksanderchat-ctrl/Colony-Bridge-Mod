package com.colonybridge.minecolonies.collection;

import com.colonybridge.ColonyBridgeConstants;
import com.colonybridge.model.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class ColonySnapshotAssembler {
    public ColonySnapshot assemble(ColonyCollectionContext context, GameData game, CollectedSections sections,
                                   Map<String, CapabilityData> capabilities) {
        return assemble(context.generatedAt(), context.trigger(), game, sections, capabilities,
                context.warnings(), context.errors());
    }

    public ColonySnapshot assemble(java.time.Instant generatedAt, com.colonybridge.api.ExportTrigger trigger,
                                   GameData game, CollectedSections sections,
                                   Map<String, CapabilityData> capabilities,
                                   java.util.List<BridgeMessage> warnings,
                                   java.util.List<BridgeMessage> errors) {
        return new ColonySnapshot(
                ColonyBridgeConstants.SCHEMA_VERSION,
                ColonyBridgeConstants.VERSION,
                DateTimeFormatter.ISO_INSTANT.format(generatedAt),
                trigger.jsonName(),
                null,
                game,
                sections.world(),
                sections.colony(),
                sections.summary(),
                sections.citizens(),
                sections.buildings(),
                sections.requests(),
                sections.construction(),
                sections.environment(),
                sections.territory(),
                sections.livestock(),
                sections.research(),
                sections.statistics(),
                sections.recentStatistics(),
                sections.defenseStatistics(),
                sections.foodSupply(),
                sections.stockLedger(),
                capabilities,
                warnings,
                errors);
    }
}
