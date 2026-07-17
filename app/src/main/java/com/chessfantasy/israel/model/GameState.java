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
    public boolean starterPacksGranted = false;

    public Map<String, Card> cards = new HashMap<>();
    public List<Auction> auctions = new ArrayList<>();
    public List<SaleListing> sales = new ArrayList<>();
    public List<TradeOffer> offers = new ArrayList<>();
    /** Players added via the online federation search, persisted with the game. */
    public List<Player> addedPlayers = new ArrayList<>();
    /** Real names pulled from the federation API, overriding bundled ones. key = playerId */
    public Map<String, String> nameOverrides = new HashMap<>();
    public Map<String, String> hebrewOverrides = new HashMap<>();

    /** Owned, unopened packs: key = PackType.name() -> count. */
    public Map<String, Integer> packInventory = new HashMap<>();
    /** Epoch millis of the last daily spin (0 = never). */
    public long lastSpinAt = 0;

    /** Essence (bottles) earned by recycling Common cards in the forge. */
    public long essence = 0;
    /** Running total + count of ratings of recycled commons (for box output). */
    public long essenceRatingSum = 0;
    public int essenceCardCount = 0;

    /** key = season:playerId:rarity -> cards minted so far */
    public Map<String, Integer> mintCounts = new HashMap<>();

    /** Live display-rating overrides (FIDE standard), key = playerId */
    public Map<String, Integer> ratingOverrides = new HashMap<>();
    /** Full multi-source ratings (FIDE, Israeli CF, chess.com), key = playerId */
    public Map<String, PlayerRatings> liveRatings = new HashMap<>();
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

    // ---- Gameweeks, scoring & leaderboard ----
    /** ISO gameweek id, e.g. "2026-W29". */
    public String currentGameweek = "";
    /** Player rating captured at the start of the current gameweek. key = playerId */
    public Map<String, Integer> gwBaseline = new HashMap<>();
    /** Cumulative manager points across all gameweeks. */
    public long managerSeasonPoints = 0;
    /** Simulated rival managers for the leaderboard (until Firebase is connected). */
    public List<RivalManager> rivals = new ArrayList<>();
    /** One-shot summary of the last closed gameweek, shown once then cleared. */
    public String lastGwSummary = "";
    /** The user's chosen 5-card team (Sorare-style lineup), by card id. */
    public List<String> teamCardIds = new ArrayList<>();

    /** Firebase anonymous user id, once signed in (empty otherwise). */
    public String firebaseUid = "";
    public String managerName = "";
}
