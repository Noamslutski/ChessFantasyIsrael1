package com.chessfantasy.israel.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Live ratings for one player, gathered from multiple real chess data sources
 * (FIDE, the Israeli Chess Federation, and chess.com). Any field may be null
 * when a source doesn't cover that player or is unreachable.
 */
public class PlayerRatings {
    // Official FIDE (international) ratings
    public Integer fideStandard;
    public Integer fideRapid;
    public Integer fideBlitz;
    public long fideId;

    // Israeli Chess Federation (chess.org.il) national rating
    public Integer nationalStandard;

    // chess.com online ratings + the FIDE rating chess.com stores
    public Integer chessComBlitz;
    public Integer chessComRapid;
    public Integer chessComFide;

    /** Human-readable list of sources that contributed, e.g. "FIDE, Israeli CF". */
    public Set<String> sources = new LinkedHashSet<>();
    public long updatedAt;

    /** The rating used across the game — prefer official FIDE standard. */
    public Integer displayRating() {
        if (fideStandard != null) return fideStandard;
        if (nationalStandard != null) return nationalStandard;
        if (chessComFide != null) return chessComFide;
        if (fideRapid != null) return fideRapid;
        if (fideBlitz != null) return fideBlitz;
        return null;
    }

    public boolean hasAny() {
        return displayRating() != null || chessComBlitz != null || chessComRapid != null;
    }
}
