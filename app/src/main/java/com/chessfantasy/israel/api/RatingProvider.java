package com.chessfantasy.israel.api;

import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.model.PlayerRatings;

/** A real chess data source that can enrich a player's ratings. */
public interface RatingProvider {

    /** Short label shown in the UI, e.g. "FIDE" or "Israeli CF". */
    String label();

    /**
     * Fetches from this source and fills whatever fields it can into {@code out}.
     * Must never throw; returns true if it contributed any data.
     */
    boolean enrich(Player player, PlayerRatings out);
}
