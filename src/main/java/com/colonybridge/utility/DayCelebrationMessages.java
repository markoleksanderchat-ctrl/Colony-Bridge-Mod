package com.colonybridge.utility;

import java.util.List;

public final class DayCelebrationMessages {
    private static final List<String> MESSAGES = List.of(
            "The kingdom perseveres.",
            "Another dawn belongs to the colony.",
            "The colony stands unbroken.",
            "Stone by stone, the kingdom rises.",
            "The people greet another dawn.",
            "Our banners still fly.",
            "The colony endures and prospers.",
            "Another chapter begins.",
            "The frontier yields to our resolve.",
            "The kingdom awakens stronger.",
            "Our walls hold. Our people thrive.",
            "Another day is ours to shape.",
            "The town carries on with purpose.",
            "From humble foundations, a kingdom grows.",
            "The colony marches ever forward.",
            "A new dawn crowns our labor.",
            "The settlement stands proud.",
            "Our work outlives the night.",
            "The kingdom welcomes the dawn.",
            "Hope rises with the sun.",
            "The colony's story continues.",
            "Another sunrise finds us standing.",
            "The realm grows beneath our banner.",
            "Together, the colony endures.",
            "The future is built today."
    );

    private DayCelebrationMessages() {
    }

    public static String forDay(long day) {
        return MESSAGES.get(Math.floorMod(day - 1, MESSAGES.size()));
    }

    public static int count() {
        return MESSAGES.size();
    }
}
