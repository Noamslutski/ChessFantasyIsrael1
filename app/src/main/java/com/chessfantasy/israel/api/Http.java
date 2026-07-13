package com.chessfantasy.israel.api;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Tiny blocking HTTP GET helper shared by the rating providers. */
final class Http {

    private static final int TIMEOUT_MS = 8000;

    private Http() {
    }

    /** Returns the response body, or null on any non-200 or error. */
    static String get(String urlString, String accept) {
        return get(urlString, accept, null);
    }

    /** As {@link #get(String, String)} but with extra request headers. */
    static String get(String urlString, String accept, Map<String, String> headers) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            if (accept != null) connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty("User-Agent",
                    "ChessFantasyIsrael/1.0 (Android; fantasy chess app)");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    connection.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            int code = connection.getResponseCode();
            if (code != 200) return null;
            try (InputStream in = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) sb.append(buffer, 0, read);
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
