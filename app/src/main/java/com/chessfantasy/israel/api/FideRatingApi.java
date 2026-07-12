package com.chessfantasy.israel.api;

import android.os.Handler;
import android.os.Looper;

import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Player;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live FIDE ratings via the public Lichess FIDE database API (no key needed):
 *   GET https://lichess.org/api/fide/player/{fideId}   -> one player as JSON
 *   GET https://lichess.org/api/fide/player?q={name}   -> search results as JSON array
 *
 * Players whose fideId is known are fetched directly; the rest are resolved by
 * name search (Israeli federation, best-name match) and the resolved id is
 * cached. Any network or parse failure simply keeps the bundled offline
 * rating — the app never breaks without internet.
 */
public class FideRatingApi {

    public interface Callback {
        void onFinished(int updated, int failed);
    }

    private static final String BASE = "https://lichess.org/api/fide/player";
    private static final int TIMEOUT_MS = 8000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** One fetched result, applied to game state on the main thread. */
    private static class Result {
        final String playerId;
        final int rating;
        final long fideId;

        Result(String playerId, int rating, long fideId) {
            this.playerId = playerId;
            this.rating = rating;
            this.fideId = fideId;
        }
    }

    public void refreshAll(Callback callback) {
        executor.execute(() -> {
            GameRepository repo = GameRepository.get();
            List<Player> players = repo.getPlayers();
            List<Result> results = new ArrayList<>();
            int failed = 0;
            for (Player p : players) {
                try {
                    JsonObject data = null;
                    long fideId = repo.knownFideId(p);
                    if (fideId > 0) {
                        data = fetchById(fideId);
                        if (data != null && !nameMatches(p.name, optString(data, "name"))) {
                            data = null; // wrong id configured — fall through to search
                        }
                    }
                    if (data == null) {
                        data = searchByName(p.name);
                    }
                    if (data == null) {
                        failed++;
                        continue;
                    }
                    int rating = bestRating(data);
                    long resolvedId = data.has("id") ? data.get("id").getAsLong() : 0;
                    if (rating > 0) {
                        results.add(new Result(p.id, rating, resolvedId));
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                }
            }
            final int f = failed;
            // Game state is only ever touched from the main thread.
            mainHandler.post(() -> {
                for (Result r : results) {
                    repo.applyLiveRating(r.playerId, r.rating, r.fideId);
                }
                callback.onFinished(results.size(), f);
            });
        });
    }

    private JsonObject fetchById(long fideId) {
        String body = httpGet(BASE + "/" + fideId);
        if (body == null) return null;
        try {
            JsonElement el = JsonParser.parseString(body);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Searches the FIDE database by name; prefers Israeli federation entries. */
    private JsonObject searchByName(String name) {
        String body;
        try {
            body = httpGet(BASE + "?q=" + URLEncoder.encode(name, "UTF-8"));
        } catch (Exception e) {
            return null;
        }
        if (body == null) return null;
        try {
            JsonElement el = JsonParser.parseString(body);
            if (!el.isJsonArray()) return null;
            JsonArray results = el.getAsJsonArray();
            JsonObject best = null;
            int bestRating = -1;
            for (JsonElement item : results) {
                if (!item.isJsonObject()) continue;
                JsonObject obj = item.getAsJsonObject();
                if (!nameMatches(name, optString(obj, "name"))) continue;
                String federation = optString(obj, "federation");
                boolean israeli = "ISR".equalsIgnoreCase(federation);
                int rating = bestRating(obj);
                // Prefer Israeli entries; among them, the highest rated.
                int score = rating + (israeli ? 100000 : 0);
                if (score > bestRating) {
                    bestRating = score;
                    best = obj;
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    /** Classical rating if available, else rapid, else blitz. */
    private int bestRating(JsonObject obj) {
        for (String key : new String[]{"standard", "rapid", "blitz"}) {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                try {
                    int r = obj.get(key).getAsInt();
                    if (r > 0) return r;
                } catch (Exception ignored) {
                }
            }
        }
        return 0;
    }

    /** True when every word of `ours` appears in the API's "Lastname, Firstname" form. */
    private boolean nameMatches(String ours, String theirs) {
        if (ours == null || theirs == null) return false;
        String normalized = theirs.toLowerCase(Locale.US).replace(",", " ");
        for (String token : ours.toLowerCase(Locale.US).split("\\s+")) {
            if (token.isEmpty()) continue;
            if (!normalized.contains(token)) return false;
        }
        return true;
    }

    private String optString(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }

    private String httpGet(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ChessFantasyIsrael/1.0");
            int code = connection.getResponseCode();
            if (code != 200) return null;
            try (InputStream in = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
