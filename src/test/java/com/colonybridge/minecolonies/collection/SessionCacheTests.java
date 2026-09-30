package com.colonybridge.minecolonies.collection;

import com.colonybridge.model.LivestockData;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class SessionCacheTests {
    @Test void newSessionCannotReuseSameColonyId() {
        var cache=new LivestockObservationCache();
        cache.put(1,new LivestockData(5,5,0,Map.of(),List.of()));
        assertTrue(cache.get(1).isPresent()); cache.clear(); assertTrue(cache.get(1).isEmpty());
    }
}
