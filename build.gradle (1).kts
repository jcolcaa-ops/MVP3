package com.jorge.registrollamadas;

import org.json.JSONObject;

public final class JsonUtil {
    private JsonUtil() {}

    public static String optString(JSONObject object, String key) {
        String value = object.optString(key, "");
        return "null".equalsIgnoreCase(value) ? "" : value;
    }
}
