package com.chessfantasy.israel.api;

import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.model.PlayerRatings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Official FIDE (international) ratings via the public Lichess FIDE database
 * mirror (keyless JSON):
 *   GET https://lichess.org/api/fide/player/{fideId}
 *   GET https://lichess.org/api/fide/player?q={name}   (search)
 * This is the authoritative FIDE rating list, no API key required.
 */
public class FideProvider implements RatingProvider {

    private static final String BASE = "https://lichess.org/api/fide/player";

    @Override
    public String label() {
        return "FIDE";
    }

    @Override
    public boolean enrich(Player player, PlayerRatings out) {
        try {
            JsonObject data = null;
            if (out.fideId > 0) {
                data = fetchById(out.fideId);
                if (data != null && !surnameMatches(player.name, str(data, "name"))) {
                    data = null;
                }
            }
            if (data == null && player.fideId > 0) {
                data = fetchById(player.fideId);
                if (data != null && !surnameMatches(player.name, str(data, "name"))) {
                    data = null;
                }
            }
            if (data == null) {
                data = searchByName(player.name);
            }
            if (data == null) return false;

            boolean got = false;
            Integer std = intOrNull(data, "standard");
            Integer rapid = intOrNull(data, "rapid");
            Integer blitz = intOrNull(data, "blitz");
            if (std != null) {
                out.fideStandard = std;
                got = true;
            }
            if (rapid != null) {
                out.fideRapid = rapid;
                got = true;
            }
            if (blitz != null) {
                out.fideBlitz = blitz;
                got = true;
            }
            if (data.has("id")) {
                try {
                    out.fideId = data.get("id").getAsLong();
                } catch (Exception ignored) {
                }
            }
            if (got) out.sources.add(label());
            return got;
        } catch (Exception e) {
            return false;
        }
    }

    private JsonObject fetchById(long fideId) {
        String body = Http.get(BASE + "/" + fideId, "application/json");
        if (body == null) return null;
        try {
            JsonElement el = JsonParser.parseString(body);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonObject searchByName(String name) {
        String body;
        try {
            body = Http.get(BASE + "?q=" + URLEncoder.encode(name, "UTF-8"), "application/json");
        } catch (Exception e) {
            return null;
        }
        if (body == null) return null;
        try {
            JsonElement el = JsonParser.parseString(body);
            if (!el.isJsonArray()) return null;
            JsonArray results = el.getAsJsonArray();
            JsonObject best = null;
            int bestScore = -1;
            for (JsonElement item : results) {
                if (!item.isJsonObject()) continue;
                JsonObject obj = item.getAsJsonObject();
                if (!nameMatches(name, str(obj, "name"))) continue;
                boolean israeli = "ISR".equalsIgnoreCase(str(obj, "federation"));
                Integer std = intOrNull(obj, "standard");
                int rating = std != null ? std : 0;
                int score = rating + (israeli ? 100000 : 0);
                if (score > bestScore) {
                    bestScore = score;
                    best = obj;
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean nameMatches(String ours, String theirs) {
        if (ours == null || theirs == null) return false;
        List<String> theirTokens = tokenize(theirs);
        for (String token : tokenize(ours)) {
            if (!theirTokens.contains(token)) return false;
        }
        return true;
    }

    private boolean surnameMatches(String ours, String theirs) {
        if (ours == null || theirs == null) return false;
        List<String> ourTokens = tokenize(ours);
        if (ourTokens.isEmpty()) return false;
        return tokenize(theirs).contains(ourTokens.get(ourTokens.size() - 1));
    }

    private List<String> tokenize(String name) {
        List<String> tokens = new ArrayList<>();
        for (String t : name.toLowerCase(Locale.US).split("[^\\p{L}]+")) {
            if (!t.isEmpty()) tokens.add(t);
        }
        return tokens;
    }

    private Integer intOrNull(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try {
                int v = obj.get(key).getAsInt();
                if (v > 0) return v;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String str(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }
}
