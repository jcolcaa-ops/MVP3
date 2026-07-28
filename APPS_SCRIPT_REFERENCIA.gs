package com.jorge.registrollamadas;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    private ApiClient() {}

    public static ApiResult ping() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(AppConfig.API_URL + "?accion=ping");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            String body = readBody(connection, code);
            boolean success = code >= 200 && code < 300 && new JSONObject(body).optBoolean("success", false);
            return new ApiResult(success, "HTTP " + code + ": " + body);
        } catch (Exception e) {
            return new ApiResult(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static ApiResult post(String json) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(AppConfig.API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int code = connection.getResponseCode();
            String body = readBody(connection, code);
            boolean success = false;
            try {
                success = code >= 200 && code < 300 && new JSONObject(body).optBoolean("success", false);
            } catch (Exception ignored) {
                // Se devuelve el cuerpo para diagnóstico si el endpoint no responde JSON.
            }
            return new ApiResult(success, "HTTP " + code + ": " + body);
        } catch (Exception e) {
            return new ApiResult(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBody(HttpURLConnection connection, int code) throws Exception {
        InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    public static final class ApiResult {
        public final boolean success;
        public final String message;
        public ApiResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
