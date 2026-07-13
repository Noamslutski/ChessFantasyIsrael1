package com.chessfantasy.israel.api;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.chessfantasy.israel.BuildConfig;
import com.chessfantasy.israel.model.Player;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Searches the Israeli Chess Federation player database through the user's
 * parse.bot scraper API:
 *   GET https://api.parse.bot/scraper/{id}/search_players?name={name}
 *   header: X-API-Key: <PARSE_API_KEY>
 *
 * The API key is injected at build time (BuildConfig.PARSE_API_KEY) from a
 * gradle property or the PARSE_API_KEY environment variable — never committed.
 *
 * The JSON shape returned by a parse.bot scraper depends on how the scraper was
 * configured, so the response is parsed defensively: it accepts a bare array or
 * an object wrapping an array, and detects each player's name / rating / FIDE id
 * from a set of candidate field names.
 */
public class IsraelPlayerSearchApi {

    private static final String BASE = "https://api.parse.bot/scraper/";
    private static final String SCRAPER_ID = "c7da0745-f5a9-4d91-86c2-e239a7a526ae";

    public interface Callback {
        void onResult(List<Player> players, String error);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static boolean isConfigured() {
        return BuildConfig.PARSE_API_KEY != null && !BuildConfig.PARSE_API_KEY.isEmpty();
    }

    public void search(String name, Callback callback) {
        executor.execute(() -> {
            List<Player> players = new ArrayList<>();
            String error = null;
            try {
                if (!isConfigured()) {
                    error = "Player-search API key not set (build with -PPARSE_API_KEY=… or the PARSE_API_KEY env var)";
                } else if (name == null || name.trim().isEmpty()) {
                    error = "Enter a name to search";
                } else {
                    String url = BASE + SCRAPER_ID + "/search_players?name="
                            + URLEncoder.encode(name.trim(), "UTF-8");
                    Map<String, String> headers = new HashMap<>();
                    headers.put("X-API-Key", BuildConfig.PARSE_API_KEY);
                    String body = Http.get(url, "application/json", headers);
                    if (body == null) {
                        error = "Couldn't reach the player-search API";
                    } else {
                        players = parse(body);
                        if (players.isEmpty()) error = "No players found for \"" + name.trim() + "\"";
                    }
                }
            } catch (Exception e) {
                error = "Search failed (" + e.getClass().getSimpleName() + ")";
            }
            final List<Player> fPlayers = players;
            final String fError = error;
            mainHandler.post(() -> callback.onResult(fPlayers, fError));
        });
    }

    // ---------------------------------------------------------------- parsing

    static List<Player> parse(String body) {
        List<Player> out = new ArrayList<>();
        JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (Exception e) {
            return out;
        }
        JsonArray array = findArray(root);
        if (array == null) return out;
        for (JsonElement el : array) {
            if (!el.isJsonObject()) continue;
            Player p = toPlayer(el.getAsJsonObject());
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Locates the players array whether the response is a bare array or wraps one. */
    private static JsonArray findArray(JsonElement root) {
        if (root.isJsonArray()) return root.getAsJsonArray();
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            for (String key : new String[]{"players", "results", "data", "items", "result"}) {
                if (obj.has(key) && obj.get(key).isJsonArray()) return obj.getAsJsonArray(key);
            }
            // Some scrapers nest one more level: { data: { players: [...] } }
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (e.getValue().isJsonArray()) return e.getValue().getAsJsonArray();
                if (e.getValue().isJsonObject()) {
                    JsonArray nested = findArray(e.getValue());
                    if (nested != null) return nested;
                }
            }
        }
        return null;
    }

    private static Player toPlayer(JsonObject obj) {
        Map<String, JsonElement> fields = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            fields.put(e.getKey().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", ""), e.getValue());
        }

        String english = str(fields, "name", "fullname", "playername", "englishname", "nameen", "enname", "namelatin");
        String hebrew = str(fields, "hebrewname", "namehe", "hename", "hebrew", "namehebrew", "שם");
        String displayName = !TextUtils.isEmpty(english) ? english : hebrew;
        if (TextUtils.isEmpty(displayName)) return null;

        long fideId = asLong(fields, "fideid", "fide", "idfide", "fidenumber");
        long ilId = asLong(fields, "ilid", "nationalid", "playerid", "israelid", "id", "memberid");
        Integer fideRating = asInt(fields, "fiderating", "fidestandard", "standardrating", "standard", "fideelo");
        Integer nationalRating = asInt(fields, "nationalrating", "rating", "elo", "ratingnational", "מדכושר");
        Integer rating = fideRating != null ? fideRating : nationalRating;

        Player p = new Player();
        p.fideId = fideId;
        p.ilId = ilId;
        if (fideId > 0) p.id = "fide_" + fideId;
        else if (ilId > 0) p.id = "il_" + ilId;
        else p.id = "search_" + Integer.toHexString(displayName.hashCode());
        p.name = displayName.trim();
        p.hebrewName = hebrew != null ? hebrew.trim() : "";
        p.title = normalizeTitle(str(fields, "title", "tit", "fidetitle"));
        p.rating = rating != null && rating > 0 ? rating : 1600;
        p.chessComUser = "";
        p.achievements = "";
        return p;
    }

    private static String str(Map<String, JsonElement> fields, String... keys) {
        for (String key : keys) {
            JsonElement el = fields.get(key);
            if (el != null && el.isJsonPrimitive()) {
                String s = el.getAsString().trim();
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    private static Integer asInt(Map<String, JsonElement> fields, String... keys) {
        for (String key : keys) {
            JsonElement el = fields.get(key);
            if (el == null || !el.isJsonPrimitive()) continue;
            try {
                String s = el.getAsString().trim().replaceAll("[^0-9]", "");
                if (!s.isEmpty()) {
                    int v = Integer.parseInt(s);
                    if (v > 0) return v;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static long asLong(Map<String, JsonElement> fields, String... keys) {
        Integer v = asInt(fields, keys);
        return v != null ? v : 0;
    }

    private static String normalizeTitle(String raw) {
        if (raw == null) return "";
        String t = raw.trim().toUpperCase(Locale.ROOT);
        switch (t) {
            case "GM": case "IM": case "FM": case "CM":
            case "WGM": case "WIM": case "WFM": case "WCM":
                return t;
            default:
                return "";
        }
    }
}
