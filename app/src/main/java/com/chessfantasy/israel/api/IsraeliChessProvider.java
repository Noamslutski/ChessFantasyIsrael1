package com.chessfantasy.israel.api;

import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.model.PlayerRatings;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Israeli Chess Federation national rating from chess.org.il.
 * The federation exposes no JSON API, so this reads the player's public profile
 * page and extracts the national rating (מד כושר). Requires the player's
 * chess.org.il id ({@code ilId}); best-effort — returns false if the page can't
 * be reached or parsed, so the app falls back to other sources.
 *   https://www.chess.org.il/players/Player.aspx?Id={ilId}
 */
public class IsraeliChessProvider implements RatingProvider {

    public static String profileUrl(long ilId) {
        return "https://www.chess.org.il/players/Player.aspx?Id=" + ilId;
    }

    // A 3-4 digit rating (chess ratings live roughly in 1000-3000).
    private static final Pattern RATING = Pattern.compile("(\\d{3,4})");
    // The national rating label on the profile page ("מד כושר לאומי" / "מד כושר").
    private static final String[] LABELS = {"מד כושר לאומי", "מד־כושר לאומי", "מד כושר"};

    @Override
    public String label() {
        return "Israeli CF";
    }

    @Override
    public boolean enrich(Player player, PlayerRatings out) {
        if (player.ilId <= 0) return false;
        String html = Http.get(profileUrl(player.ilId), "text/html");
        if (html == null) return false;
        Integer rating = extractNationalRating(html);
        if (rating == null) return false;
        out.nationalStandard = rating;
        out.sources.add(label());
        return true;
    }

    /** Finds the first plausible rating that follows a national-rating label. */
    Integer extractNationalRating(String html) {
        String text = html.replace(' ', ' ');
        for (String labelText : LABELS) {
            int idx = text.indexOf(labelText);
            while (idx >= 0) {
                int windowEnd = Math.min(text.length(), idx + labelText.length() + 240);
                String window = text.substring(idx + labelText.length(), windowEnd);
                Matcher m = RATING.matcher(window);
                while (m.find()) {
                    int value = Integer.parseInt(m.group(1));
                    if (value >= 1000 && value <= 3000) return value;
                }
                idx = text.indexOf(labelText, idx + labelText.length());
            }
        }
        return null;
    }
}
