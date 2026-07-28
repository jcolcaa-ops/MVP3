package com.jorge.registrollamadas;

public final class AppConfig {
    private AppConfig() {}

    public static final String API_URL =
            "https://script.google.com/macros/s/AKfycbz9VE0xm0vRzYZrHRpdSxkWey2FtY34w93yXSKxX8VigfY9_ss-G29QZNmQxG7JBCM/exec";

    public static final String PREFS = "registro_llamadas_prefs";
    public static final String KEY_ENABLED = "monitoring_enabled";
    public static final String KEY_USER_ID = "user_id";
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_MONITOR_START = "monitor_start_ms";
    public static final String KEY_LAST_PROCESSED = "last_processed_call_ms";
    public static final String KEY_CALL_ACTIVE = "call_active";
    public static final String KEY_PENDING_NUMBER = "pending_number";
    public static final String KEY_PENDING_ORDER = "pending_order";
    public static final String KEY_PENDING_TIME = "pending_time";
    public static final String KEY_LAT = "last_lat";
    public static final String KEY_LON = "last_lon";
    public static final String KEY_ACCURACY = "last_accuracy";
    public static final String KEY_LOCATION_TIME = "last_location_time";
}
