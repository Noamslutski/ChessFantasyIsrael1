package com.chessfantasy.israel.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.chessfantasy.israel.BuildConfig;
import com.chessfantasy.israel.model.UpcomingGame;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Upcoming games for Israeli players via the user's parse.bot scraper:
 *   GET https://api.parse.bot/scraper/{id}/get_upcoming_games?federation=ISR&max_tournaments=25
 *   header: X-API-Key: <PARSE_API_KEY>  (BuildConfig, never committed)
 *
 * The exact JSON shape depends on how the scraper is configured, so the parser
 * is defensive: it accepts a bare array or a wrapped/nested one, flattens
 * tournaments → players → games up to two levels deep, and detects each field
 * from candidate names. Results are cached to app-private storage.
 */
public class UpcomingGamesApi {

    private static final String BASE = "https://api.parse.bot/scraper/";
    private static final String SCRAPER_ID = "6cfbc235-7768-4f73-ad21-6f656ef67685";
    private static final String CACHE_FILE = "upcoming_games.json";

    public interface Callback {
        void onResult(List<UpcomingGame> games, String error);
    }

    public static class Cache {
        public long updatedAt;
        public List<UpcomingGame> games = new ArrayList<>();
    }

    private final Context appContext;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public UpcomingGamesApi(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static boolean isConfigured() {
        return BuildConfig.PARSE_API_KEY != null && !BuildConfig.PARSE_API_KEY.isEmpty();
    }

    private File cacheFile() {
        return new File(appContext.getFilesDir(), CACHE_FILE);
    }

    public Cache loadCached() {
        File f = cacheFile();
        if (!f.exists()) return new Cache();
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Cache c = gson.fromJson(r, Cache.class);
            if (c == null) c = new Cache();
            if (c.games == null) c.games = new ArrayList<>();
            return c;
        } catch (Exception e) {
            return new Cache();
        }
    }

    public void refresh(Callback callback) {
        executor.execute(() -> {
            List<UpcomingGame> games = new ArrayList<>();
            String error = null;
            try {
                if (!isConfigured()) {
                    error = "Player-search API key not set (build with -PPARSE_API_KEY=… or the PARSE_API_KEY env var)";
                } else {
                    String url = BASE + SCRAPER_ID
                            + "/get_upcoming_games?federation=ISR&max_tournaments=25";
                    Map<String, String> headers = new HashMap<>();
                    headers.put("X-API-Key", BuildConfig.PARSE_API_KEY);
                    String body = Http.get(url, "application/json", headers);
                    if (body == null) {
                        error = "Couldn't reach the upcoming-games API";
                    } else {
                        games = parse(body);
                        Cache c = new Cache();
                        c.updatedAt = System.currentTimeMillis();
                        c.games = games;
                        writeCache(c);
                        if (games.isEmpty()) error = "No upcoming games returned";
                    }
                }
            } catch (Exception e) {
                error = "Fetch failed (" + e.getClass().getSimpleName() + ")";
            }
            final List<UpcomingGame> fGames = games;
            final String fError = error;
            mainHandler.post(() -> callback.onResult(fGames, fError));
        });
    }

    private void writeCache(Cache c) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(cacheFile()), StandardCharsets.UTF_8)) {
            gson.toJson(c, w);
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- parsing

    static List<UpcomingGame> parse(String body) {
        List<UpcomingGame> out = new ArrayList<>();
        JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (Exception e) {
            return out;
        }
        JsonArray array = findArray(root);
        if (array == null) return out;
        for (JsonElement el : array) {
            if (el.isJsonObject()) flatten(el.getAsJsonObject(), new UpcomingGame(), out, 0);
        }
        return out;
    }

    /** Walks tournament → players → games, carrying context down, emitting leaf games. */
    private static void flatten(JsonObject obj, UpcomingGame ctx, List<UpcomingGame> out, int depth) {
        Map<String, JsonElement> f = lower(obj);
        UpcomingGame g = copy(ctx);

        String tournament = str(f, "tournament", "event", "title", "name");
        // "name" at the player level is the player; only treat as tournament near the root.
        if (depth == 0 && !tournament.isEmpty()) g.tournament = tournament;
        else if (!str(f, "tournament", "event").isEmpty()) g.tournament = str(f, "tournament", "event");

        String loc = str(f, "location", "city", "place", "country", "venue");
        if (!loc.isEmpty()) g.location = loc;

        String player = str(f, "player", "playername", "fullname", "name");
        if (!player.isEmpty() && (depth > 0 || !str(f, "player", "playername").isEmpty())) g.playerName = player;
        String hebrew = str(f, "hebrewname", "playerhebrew", "namehe");
        if (!hebrew.isEmpty()) g.playerHebrew = hebrew;
        long fide = asLong(f, "fideid", "fide", "playerfideid");
        if (fide > 0) g.playerFideId = fide;

        String date = str(f, "date", "start", "startdate", "datetime", "day", "time");
        if (!date.isEmpty()) g.date = date;
        String round = str(f, "round", "rd", "board");
        if (!round.isEmpty()) g.round = round;
        String opp = str(f, "opponent", "against", "vs", "opponentname");
        if (!opp.isEmpty()) g.opponent = opp;
        String color = str(f, "color", "colour");
        if (!color.isEmpty()) g.color = color;

        // Recurse into any nested arrays (players / games / rounds / pairings).
        boolean recursed = false;
        if (depth < 3) {
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (e.getValue().isJsonArray()) {
                    for (JsonElement child : e.getValue().getAsJsonArray()) {
                        if (child.isJsonObject()) {
                            flatten(child.getAsJsonObject(), g, out, depth + 1);
                            recursed = true;
                        }
                    }
                }
            }
        }
        // A leaf (no nested arrays) that names a player is an actual game.
        if (!recursed && !g.playerName.isEmpty()) out.add(g);
    }

    private static JsonArray findArray(JsonElement root) {
        if (root.isJsonArray()) return root.getAsJsonArray();
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            for (String key : new String[]{"tournaments", "games", "players", "results", "data", "items"}) {
                if (obj.has(key) && obj.get(key).isJsonArray()) return obj.getAsJsonArray(key);
            }
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

    private static Map<String, JsonElement> lower(JsonObject obj) {
        Map<String, JsonElement> m = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            m.put(e.getKey().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", ""), e.getValue());
        }
        return m;
    }

    private static UpcomingGame copy(UpcomingGame s) {
        UpcomingGame g = new UpcomingGame();
        g.playerName = s.playerName;
        g.playerHebrew = s.playerHebrew;
        g.playerFideId = s.playerFideId;
        g.tournament = s.tournament;
        g.location = s.location;
        g.date = s.date;
        g.round = s.round;
        g.opponent = s.opponent;
        g.color = s.color;
        return g;
    }

    private static String str(Map<String, JsonElement> f, String... keys) {
        for (String k : keys) {
            JsonElement el = f.get(k);
            if (el != null && el.isJsonPrimitive()) {
                String s = el.getAsString().trim();
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    private static long asLong(Map<String, JsonElement> f, String... keys) {
        for (String k : keys) {
            JsonElement el = f.get(k);
            if (el == null || !el.isJsonPrimitive()) continue;
            try {
                String s = el.getAsString().trim().replaceAll("[^0-9]", "");
                if (!s.isEmpty()) return Long.parseLong(s);
            } catch (Exception ignored) {
            }
        }
        return 0;
    }
}
