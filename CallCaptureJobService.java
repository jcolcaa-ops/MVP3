package com.jorge.registrollamadas;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.CallLog;
import android.provider.Settings;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class CallLogProcessor {
    private CallLogProcessor() {}

    public static int processNewCalls(Context context) {
        if (Build.VERSION.SDK_INT >= 23 &&
                context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return 0;
        }

        SharedPreferences prefs = context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(AppConfig.KEY_ENABLED, false)) return 0;

        long monitorStart = prefs.getLong(AppConfig.KEY_MONITOR_START, System.currentTimeMillis());
        long lastProcessed = prefs.getLong(AppConfig.KEY_LAST_PROCESSED, monitorStart);
        long queryFrom = Math.max(monitorStart, lastProcessed - 1000L);

        String[] projection = {
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE
        };

        List<RawCall> calls = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                projection,
                CallLog.Calls.DATE + ">?",
                new String[]{String.valueOf(queryFrom)},
                CallLog.Calls.DATE + " ASC")) {
            if (cursor == null) return 0;
            while (cursor.moveToNext()) {
                calls.add(new RawCall(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getLong(2),
                        cursor.getLong(3),
                        cursor.getInt(4)));
            }
        } catch (SecurityException e) {
            return 0;
        }

        CallDatabase db = new CallDatabase(context);
        int inserted = 0;
        long maxDate = lastProcessed;
        for (RawCall call : calls) {
            maxDate = Math.max(maxDate, call.dateMs);
            String id = "CALL-" + call.id + "-" + call.dateMs;
            if (db.exists(id)) continue;

            String phone = normalizePhone(call.number);
            String day = format(call.dateMs, "yyyy-MM-dd");
            int attempt = db.countAttempts(phone, day) + 1;
            String orderId = resolvePendingOrder(prefs, phone, call.dateMs);
            JSONObject payload = buildPayload(context, prefs, call, id, phone, day, attempt, orderId);
            if (db.insert(id, phone, day, call.dateMs, payload.toString())) inserted++;
        }
        if (maxDate > lastProcessed) {
            prefs.edit().putLong(AppConfig.KEY_LAST_PROCESSED, maxDate).apply();
        }
        return inserted;
    }

    private static JSONObject buildPayload(Context context, SharedPreferences prefs, RawCall call,
                                           String id, String phone, String day, int attempt, String orderId) {
        JSONObject json = new JSONObject();
        try {
            long endMs = call.dateMs + (call.durationSec * 1000L);
            String gpsState = "NO_DISPONIBLE";
            Double lat = null;
            Double lon = null;
            Float accuracy = null;
            long locationTime = prefs.getLong(AppConfig.KEY_LOCATION_TIME, 0L);
            boolean recentLocation = locationTime > 0 &&
                    Math.abs(call.dateMs - locationTime) <= 30L * 60L * 1000L;
            if (recentLocation) {
                lat = Double.longBitsToDouble(prefs.getLong(AppConfig.KEY_LAT, 0L));
                lon = Double.longBitsToDouble(prefs.getLong(AppConfig.KEY_LON, 0L));
                accuracy = prefs.getFloat(AppConfig.KEY_ACCURACY, 0f);
                gpsState = "DISPONIBLE";
            }

            json.put("accion", "registrarLlamada");
            json.put("ID_REGISTRO", id);
            json.put("ID_USUARIO", prefs.getString(AppConfig.KEY_USER_ID, ""));
            json.put("NOMBRE_USUARIO", prefs.getString(AppConfig.KEY_USER_NAME, ""));
            json.put("NUMERO_CELULAR", phone);
            json.put("ID_PEDIDO", orderId);
            json.put("FECHA_LLAMADA", day);
            json.put("HORA_INICIO", format(call.dateMs, "HH:mm:ss"));
            json.put("HORA_FIN", format(endMs, "HH:mm:ss"));
            json.put("FECHA_HORA_INICIO", format(call.dateMs, "yyyy-MM-dd'T'HH:mm:ss"));
            json.put("TIPO_LLAMADA", mapType(call.type));
            json.put("DURACION_SEGUNDOS", call.durationSec);
            json.put("DURACION_FORMATO", formatDuration(call.durationSec));
            json.put("NUMERO_INTENTO_DIA", attempt);
            json.put("TOTAL_INTENTOS_DIA", attempt);
            json.put("ESTADO_INTENTO", call.durationSec > 0 ? "CON_DURACION" : "CERO_SEGUNDOS");
            if (lat == null) json.put("LATITUD", JSONObject.NULL); else json.put("LATITUD", lat);
            if (lon == null) json.put("LONGITUD", JSONObject.NULL); else json.put("LONGITUD", lon);
            if (accuracy == null) json.put("PRECISION_METROS", JSONObject.NULL); else json.put("PRECISION_METROS", accuracy);
            json.put("FECHA_HORA_COORDENADA", locationTime > 0 ? format(locationTime, "yyyy-MM-dd'T'HH:mm:ss") : "");
            json.put("ESTADO_GPS", gpsState);
            json.put("ID_DISPOSITIVO", Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID));
            json.put("MODELO_DISPOSITIVO", Build.MANUFACTURER + " " + Build.MODEL);
            json.put("VERSION_ANDROID", "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            json.put("ESTADO_SINCRONIZACION", "PENDIENTE");
            json.put("FECHA_SINCRONIZACION", "");
            json.put("MENSAJE_ERROR", "");
            json.put("FECHA_CREACION", format(System.currentTimeMillis(), "yyyy-MM-dd'T'HH:mm:ss"));
            json.put("ORIGEN_REGISTRO", "APP_ANDROID");
            json.put("OBSERVACION", "Registro automático activado por el usuario; servicio visible");
        } catch (Exception ignored) {
        }
        return json;
    }

    private static String resolvePendingOrder(SharedPreferences prefs, String callPhone, long callTime) {
        String pendingNumber = normalizePhone(prefs.getString(AppConfig.KEY_PENDING_NUMBER, ""));
        String pendingOrder = prefs.getString(AppConfig.KEY_PENDING_ORDER, "");
        long pendingTime = prefs.getLong(AppConfig.KEY_PENDING_TIME, 0L);
        boolean recent = pendingTime > 0 && Math.abs(callTime - pendingTime) <= 6L * 60L * 60L * 1000L;
        if (recent && samePhone(pendingNumber, callPhone)) return pendingOrder == null ? "" : pendingOrder;
        return "";
    }

    private static boolean samePhone(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        String aa = a.length() > 7 ? a.substring(a.length() - 7) : a;
        String bb = b.length() > 7 ? b.substring(b.length() - 7) : b;
        return aa.equals(bb);
    }

    public static String normalizePhone(String value) {
        if (value == null) return "DESCONOCIDO";
        String cleaned = value.replaceAll("[^0-9+]", "");
        return cleaned.isEmpty() ? "DESCONOCIDO" : cleaned;
    }

    private static String mapType(int type) {
        switch (type) {
            case CallLog.Calls.OUTGOING_TYPE: return "SALIENTE";
            case CallLog.Calls.INCOMING_TYPE: return "ENTRANTE";
            case CallLog.Calls.MISSED_TYPE: return "PERDIDA";
            case CallLog.Calls.REJECTED_TYPE: return "RECHAZADA";
            case CallLog.Calls.BLOCKED_TYPE: return "BLOQUEADA";
            default: return "DESCONOCIDA";
        }
    }

    private static String format(long time, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(new Date(time));
    }

    private static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    private static final class RawCall {
        final long id;
        final String number;
        final long dateMs;
        final long durationSec;
        final int type;
        RawCall(long id, String number, long dateMs, long durationSec, int type) {
            this.id = id;
            this.number = number;
            this.dateMs = dateMs;
            this.durationSec = durationSec;
            this.type = type;
        }
    }
}
