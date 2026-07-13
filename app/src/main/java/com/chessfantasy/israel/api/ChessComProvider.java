package com.chessfantasy.israel.api;

import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.model.PlayerRatings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * chess.com online ratings via the official public "Published-Data" API
 * (keyless JSON): https://api.chess.com/pub/player/{username}/stats
 * Returns the player's chess.com blitz/rapid ratings and the FIDE rating
 * chess.com stores. Requires the player's chess.com {@code chessComUser}.
 */
public class ChessComProvider implements RatingProvider {

    private static final String BASE = "https://api.chess.com/pub/player/";

    public static String profileUrl(String username) {
        return "https://www.chess.com/member/" + username;
    }

    @Override
    public String label() {
        return "chess.com";
    }

    @Override
    public boolean enrich(Player player, PlayerRatings out) {
        if (player.chessComUser == null || player.chessComUser.trim().isEmpty()) return false;
        String user = player.chessComUser.trim().toLowerCase();
        String body = Http.get(BASE + user + "/stats", "application/json");
        if (body == null) return false;
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            boolean got = false;
            Integer fide = intOrNull(root, "fide");
            if (fide != null) {
                out.chessComFide = fide;
                got = true;
            }
            Integer blitz = lastRating(root, "chess_blitz");
            if (blitz != null) {
                out.chessComBlitz = blitz;
                got = true;
            }
            Integer rapid = lastRating(root, "chess_rapid");
            if (rapid != null) {
                out.chessComRapid = rapid;
                got = true;
            }
            if (got) out.sources.add(label());
            return got;
        } catch (Exception e) {
            return false;
        }
    }

    private Integer lastRating(JsonObject root, String key) {
        try {
            if (!root.has(key)) return null;
            JsonObject mode = root.getAsJsonObject(key);
            if (mode.has("last")) {
                return intOrNull(mode.getAsJsonObject("last"), "rating");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Integer intOrNull(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try {
                int v = obj.get(key).getAsInt();
                if (v > 0) return v;
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
