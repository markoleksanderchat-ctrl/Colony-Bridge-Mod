package com.colonybridge.minecolonies;

import com.colonybridge.api.MineColoniesAdapter;
import net.neoforged.fml.ModList;

public final class MineColoniesAdapterFactory {
    private MineColoniesAdapterFactory() {
    }

    public static MineColoniesAdapter create() {
        var mod = ModList.get().getModContainerById("minecolonies");
        if (mod.isEmpty()) {
            return new UnavailableMineColoniesAdapter("MineColonies mod is not loaded.");
        }
        return new MineColonies121Adapter(mod.get().getModInfo().getVersion().toString());
    }
}
