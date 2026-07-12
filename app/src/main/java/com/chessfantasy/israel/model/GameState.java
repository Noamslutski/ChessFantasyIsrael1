package com.chessfantasy.israel.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything that is persisted between app launches (as JSON in SharedPreferences). */
public class GameState {
    public long pawns = 3000;
    public String season = "";
    public boolean seeded = false;

    public Map<String, Card> cards = new HashMap<>();
    public List<Auction> auctions = new ArrayList<>();
    public List<SaleListing> sales = new ArrayList<>();
    public List<TradeOffer> offers = new ArrayList<>();

    /** key = season:playerId:rarity -> cards minted so far */
    public Map<String, Integer> mintCounts = new HashMap<>();

    /** Live rating overrides fetched from the FIDE API, key = playerId */
    public Map<String, Integer> ratingOverrides = new HashMap<>();
    /**
     * Rating each player had when form rewards were last claimed, key = playerId.
     * Real games move real FIDE ratings; the difference against this baseline
     * is the player's "form" and pays out Pawns.
     */
    public Map<String, Integer> ratingBaselines = new HashMap<>();
    /** FIDE ids resolved by name search, key = playerId */
    public Map<String, Long> resolvedFideIds = new HashMap<>();

    public long lastDailyClaimDay = 0;   // epoch day of the last daily reward claim
    public long lastFreePackAt = 0;      // epoch millis
    public int adsWatchedToday = 0;
    public long adsDayStamp = 0;         // epoch day the ad counter refers to
}
