package com.colonybridge.export;

import com.colonybridge.model.ColonySnapshot;

public record PreparedSnapshot(
        ColonySnapshot snapshot,
        String remoteCompactJson,
        String compactJson,
        String outputJson,
        String fingerprint,
        long serializationNanos,
        long fingerprintNanos
) {
}
