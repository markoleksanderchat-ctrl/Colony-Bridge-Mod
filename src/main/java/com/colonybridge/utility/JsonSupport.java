package com.colonybridge.utility;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class JsonSupport {
    private static final Gson PRETTY = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private static final Gson COMPACT = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private JsonSupport() {
    }

    public static Gson gson(boolean pretty) {
        return pretty ? PRETTY : COMPACT;
    }

    public static String toJson(Object value, boolean pretty) {
        return gson(pretty).toJson(value);
    }
}
