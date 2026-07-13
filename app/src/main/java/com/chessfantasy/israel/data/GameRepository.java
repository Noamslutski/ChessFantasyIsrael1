package com.chessfantasy.israel.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.chessfantasy.israel.api.FideFullListLoader;
import com.chessfantasy.israel.api.UpcomingGamesApi;
import com.chessfantasy.israel.model.Auction;
import com.chessfantasy.israel.model.Card;
import com.chessfantasy.israel.model.GameState;
import com.chessfantasy.israel.model.LeaderboardEntry;
import com.chessfantasy.israel.model.PackType;
import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.model.PlayerRatings;
import com.chessfantasy.israel.model.Rarity;
import com.chessfantasy.israel.model.RivalManager;
import com.chessfantasy.israel.model.SaleListing;
import com.chessfantasy.israel.model.TradeOffer;
import com.chessfantasy.israel.model.UpcomingGame;
import com.google.gson.Gson;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Single source of truth for the whole game: the user's collection and pawns,
 * card minting with per-season scarcity caps, the simulated market (auctions,
 * direct sales, counter-offers with bot collectors) and daily/ad rewards.
 * State is persisted as JSON in SharedPreferences — no login, no backend.
 */
public class GameRepository {

    public static final String USER_ID = "me";
    public static final String[] BOTS = {
            "PawnStorm", "KingHunter", "Zugzwang77", "CaroKanner", "TalmidTal", "RookieRoy"
    };

    public static final long FREE_PACK_COOLDOWN_MS = TimeUnit.HOURS.toMillis(4);
    public static final long SPIN_COOLDOWN_MS = TimeUnit.HOURS.toMillis(24);
    public static final int ADS_PER_DAY = 10;
    public static final long AD_BONUS_PAWNS = 25;
    /** Chance that a rewarded ad upgrades the prize to a Limited ("Pro") card. */
    public static final double AD_LIMITED_CHANCE = 0.15;

    private static GameRepository instance;

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final Random random = new Random();

    private GameState state;
    private Context appContext;
    /** The bundled, hand-curated notable players (from players.json). */
    private List<Player> roster;
    /** Optionally-downloaded full FIDE Israel pool (thousands of players). */
    private List<Player> pool = new ArrayList<>();
    /** roster + pool, deduped by fideId; the full playable set. */
    private List<Player> combined;
    /** Fast id -> player lookup over {@link #combined}. */
    private Map<String, Player> playerIndex = new HashMap<>();
    private long poolUpdatedAt;

    /** Upcoming games (fixtures) loaded from the federation API cache. */
    private List<UpcomingGame> fixtures = new ArrayList<>();
    private Map<Long, Integer> fixtureCountByFide = new HashMap<>();
    private Map<String, Integer> fixtureCountByName = new HashMap<>();
    private long fixturesUpdatedAt;

    public static final int LINEUP_SIZE = 5;
    public static final int RIVAL_COUNT = 9;
    private static final int BASE_GAME_SCORE = 50;
    private String clubName = "Israel National Chess Pool";
    private String clubNameHebrew = "";

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new GameRepository(context.getApplicationContext());
        }
    }

    public static GameRepository get() {
        return instance;
    }

    private GameRepository(Context context) {
        appContext = context.getApplicationContext();
        prefs = context.getSharedPreferences("chess_fantasy", Context.MODE_PRIVATE);
        PlayerCatalog.CatalogFile catalog = PlayerCatalog.load(context);
        roster = catalog.players;
        if (catalog.club != null && !catalog.club.isEmpty()) clubName = catalog.club;
        if (catalog.clubHebrew != null) clubNameHebrew = catalog.clubHebrew;
        load();
        reloadPoolInternal();
        reloadFixtures();
        ensureSeeded();
        ensureStarterPacks();
        ensureBaselines();
        ensureLeaderboard();
        tick();
    }

    // ------------------------------------------------------------------ state

    private void load() {
        String json = prefs.getString("game_state", null);
        if (json != null) {
            try {
                state = gson.fromJson(json, GameState.class);
            } catch (Exception ignored) {
                state = null;
            }
        }
        if (state == null) state = new GameState();
        // Gson does NOT run field initializers for keys missing from an older
        // saved JSON, so newly-added collections come back null. Guard them all
        // or the first grantPack/addPlayers/applyRatings will NPE on upgrade.
        if (state.cards == null) state.cards = new java.util.HashMap<>();
        if (state.auctions == null) state.auctions = new ArrayList<>();
        if (state.sales == null) state.sales = new ArrayList<>();
        if (state.offers == null) state.offers = new ArrayList<>();
        if (state.addedPlayers == null) state.addedPlayers = new ArrayList<>();
        if (state.nameOverrides == null) state.nameOverrides = new java.util.HashMap<>();
        if (state.hebrewOverrides == null) state.hebrewOverrides = new java.util.HashMap<>();
        if (state.packInventory == null) state.packInventory = new java.util.HashMap<>();
        if (state.mintCounts == null) state.mintCounts = new java.util.HashMap<>();
        if (state.ratingOverrides == null) state.ratingOverrides = new java.util.HashMap<>();
        if (state.liveRatings == null) state.liveRatings = new java.util.HashMap<>();
        if (state.ratingBaselines == null) state.ratingBaselines = new java.util.HashMap<>();
        if (state.resolvedFideIds == null) state.resolvedFideIds = new java.util.HashMap<>();
        if (state.gwBaseline == null) state.gwBaseline = new java.util.HashMap<>();
        if (state.rivals == null) state.rivals = new ArrayList<>();
        if (state.currentGameweek == null) state.currentGameweek = "";
        if (state.lastGwSummary == null) state.lastGwSummary = "";
        if (state.managerName == null) state.managerName = "";
        if (state.firebaseUid == null) state.firebaseUid = "";
        if (state.season == null || state.season.isEmpty()) {
            state.season = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        }
    }

    public void save() {
        prefs.edit().putString("game_state", gson.toJson(state)).apply();
    }

    private void ensureSeeded() {
        if (state.seeded) return;
        state.seeded = true;
        // A couple of starter cards so the market feels alive on first launch.
        for (int i = 0; i < 2; i++) mintRandom(Rarity.COMMON, USER_ID);
        replenishBotListings();
        save();
    }

    /**
     * Grants 3 welcome packs to a new account (and once to existing accounts
     * that predate this feature): a Free, a Limited and a Rare pack to open.
     */
    private void ensureStarterPacks() {
        if (state.starterPacksGranted) return;
        state.starterPacksGranted = true;
        grantPack(PackType.FREE, 1);
        grantPack(PackType.LIMITED_PACK, 1);
        grantPack(PackType.RARE_PACK, 1);
        save();
    }

    // ----------------------------------------------------------------- roster

    public String getClubName() {
        return clubName;
    }

    public String getClubNameHebrew() {
        return clubNameHebrew;
    }

    /** The full playable set: curated roster + any downloaded FIDE Israel pool. */
    public List<Player> getPlayers() {
        return combined;
    }

    /** Only the curated players — used for the per-player live-rating refresh. */
    public List<Player> getCoreRoster() {
        return roster;
    }

    public Player getPlayer(String playerId) {
        Player p = playerIndex.get(playerId);
        if (p != null) return p;
        // Fallback (index built lazily/robustly)
        for (Player q : combined) if (q.id.equals(playerId)) return q;
        return null;
    }

    // ------------------------------------------------------- downloaded pool

    public int poolCount() {
        return pool.size();
    }

    public long poolUpdatedAt() {
        return poolUpdatedAt;
    }

    public boolean hasPool() {
        return !pool.isEmpty();
    }

    /** Re-reads the cached FIDE Israel pool from disk and rebuilds the player set. */
    public void reloadPool() {
        reloadPoolInternal();
        ensureBaselines();
    }

    private void reloadPoolInternal() {
        try {
            FideFullListLoader.Pool cached = new FideFullListLoader(appContext).loadCached();
            pool = cached.players != null ? cached.players : new ArrayList<>();
            poolUpdatedAt = cached.updatedAt;
        } catch (Exception e) {
            pool = new ArrayList<>();
            poolUpdatedAt = 0;
        }
        buildCombined();
    }

    /** Merges roster + search-added players + pool, deduped by fideId/id. */
    private void buildCombined() {
        List<Player> merged = new ArrayList<>(roster);
        java.util.Set<Long> fideIds = new java.util.HashSet<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Player p : roster) {
            if (p.fideId > 0) fideIds.add(p.fideId);
            ids.add(p.id);
        }
        // Players the user added via online search rank alongside the curated set.
        List<Player> extras = new ArrayList<>();
        if (state != null && state.addedPlayers != null) extras.addAll(state.addedPlayers);
        extras.addAll(pool);
        for (Player p : extras) {
            if (p == null || p.id == null) continue;
            if (p.fideId > 0 && fideIds.contains(p.fideId)) continue;
            if (ids.contains(p.id)) continue;
            if (p.fideId > 0) fideIds.add(p.fideId);
            ids.add(p.id);
            merged.add(p);
        }
        // Apply real names fetched from the federation API over bundled ones.
        for (Player p : merged) {
            String realName = state.nameOverrides.get(p.id);
            if (realName != null && !realName.isEmpty()) p.name = realName;
            String realHebrew = state.hebrewOverrides.get(p.id);
            if (realHebrew != null && !realHebrew.isEmpty()) p.hebrewName = realHebrew;
        }
        merged.sort((a, b) -> Integer.compare(ratingOf(b), ratingOf(a)));
        combined = merged;
        playerIndex = new java.util.HashMap<>();
        for (Player p : combined) playerIndex.put(p.id, p);
    }

    /**
     * Merges players returned by the online federation search: corrects the
     * real name (and Hebrew name) of players already in the game (matched by
     * FIDE id, federation id, or name), and adds any that are new. Returns
     * {added, corrected}.
     */
    public int[] mergeSearched(List<Player> searched) {
        if (searched == null || searched.isEmpty()) return new int[]{0, 0};
        int corrected = 0;
        List<Player> toAdd = new ArrayList<>();
        for (Player s : searched) {
            if (s == null) continue;
            Player existing = findExisting(s);
            if (existing != null) {
                boolean changed = false;
                if (s.name != null && !s.name.isEmpty() && !s.name.equals(existing.name)) {
                    state.nameOverrides.put(existing.id, s.name);
                    changed = true;
                }
                if (s.hebrewName != null && !s.hebrewName.isEmpty()
                        && !s.hebrewName.equals(existing.hebrewName)) {
                    state.hebrewOverrides.put(existing.id, s.hebrewName);
                    changed = true;
                }
                if (changed) corrected++;
            } else {
                toAdd.add(s);
            }
        }
        int added = toAdd.isEmpty() ? 0 : addPlayers(toAdd); // addPlayers saves + rebuilds
        if (corrected > 0) {
            buildCombined();
            save();
        }
        return new int[]{added, corrected};
    }

    /** Finds a player already in the game matching a searched result. */
    private Player findExisting(Player s) {
        if (s.fideId > 0) {
            for (Player p : combined) if (p.fideId == s.fideId) return p;
        }
        if (s.ilId > 0) {
            for (Player p : combined) if (p.ilId > 0 && p.ilId == s.ilId) return p;
        }
        if (s.name != null && !s.name.isEmpty()) {
            for (Player p : combined) {
                if (p.name != null && p.name.equalsIgnoreCase(s.name)) return p;
            }
        }
        return null;
    }

    /**
     * Adds players found via the online federation search to the playable pool.
     * Returns how many were newly added (duplicates are ignored).
     */
    public int addPlayers(List<Player> players) {
        if (players == null || players.isEmpty()) return 0;
        java.util.Set<Long> knownFide = new java.util.HashSet<>();
        java.util.Set<String> knownIds = new java.util.HashSet<>();
        for (Player p : combined) {
            if (p.fideId > 0) knownFide.add(p.fideId);
            knownIds.add(p.id);
        }
        int added = 0;
        for (Player p : players) {
            if (p == null || p.id == null) continue;
            if (p.fideId > 0 && knownFide.contains(p.fideId)) continue;
            if (knownIds.contains(p.id)) continue;
            state.addedPlayers.add(p);
            knownIds.add(p.id);
            if (p.fideId > 0) knownFide.add(p.fideId);
            added++;
        }
        if (added > 0) {
            buildCombined();
            ensureBaselines();
            save();
        }
        return added;
    }

    /** Never returns null — cards whose player was removed from players.json still render. */
    public Player playerOrUnknown(String playerId) {
        Player p = getPlayer(playerId);
        if (p != null) return p;
        Player unknown = new Player();
        unknown.id = playerId;
        unknown.name = "Unknown Player";
        unknown.hebrewName = "";
        unknown.title = "";
        unknown.rating = 2000;
        return unknown;
    }

    public int ratingOf(Player p) {
        Integer override = state.ratingOverrides.get(p.id);
        return override != null ? override : p.rating;
    }

    /** Applies merged multi-source live ratings for one player. */
    public void applyRatings(String playerId, PlayerRatings ratings) {
        if (ratings == null) return;
        state.liveRatings.put(playerId, ratings);
        Integer display = ratings.displayRating();
        if (display != null && display > 0) state.ratingOverrides.put(playerId, display);
        if (ratings.fideId > 0) state.resolvedFideIds.put(playerId, ratings.fideId);
        save();
    }

    public PlayerRatings liveRatings(String playerId) {
        return state.liveRatings.get(playerId);
    }

    public long knownIlId(Player p) {
        return p != null ? p.ilId : 0;
    }

    public long knownFideId(Player p) {
        Long resolved = state.resolvedFideIds.get(p.id);
        if (resolved != null && resolved > 0) return resolved;
        return p.fideId;
    }

    // -------------------------------------------------- form from real games

    private void ensureBaselines() {
        for (Player p : roster) {
            if (!state.ratingBaselines.containsKey(p.id)) {
                state.ratingBaselines.put(p.id, ratingOf(p));
            }
        }
    }

    /**
     * Rating change since the last form claim. FIDE ratings only move when the
     * player plays real rated games, so this reflects real-world results.
     */
    public int formDelta(String playerId) {
        Player p = getPlayer(playerId);
        if (p == null) return 0;
        Integer baseline = state.ratingBaselines.get(playerId);
        if (baseline == null) return 0;
        return ratingOf(p) - baseline;
    }

    /** Pawns currently claimable from my cards' real-world form. */
    public long claimableFormPawns() {
        long total = 0;
        for (Card c : myCards()) {
            int delta = formDelta(c.playerId);
            if (delta > 0) {
                total += Math.round(delta * c.rarity.valueMultiplier);
            }
        }
        return total;
    }

    /**
     * Pays out the form bonus and resets all baselines to current ratings.
     * Returns the amount, or 0 when nothing is claimable.
     */
    public long claimFormRewards() {
        long amount = claimableFormPawns();
        if (amount <= 0) return 0;
        state.pawns += amount;
        for (Player p : roster) {
            state.ratingBaselines.put(p.id, ratingOf(p));
        }
        save();
        return amount;
    }

    // ---------------------------------------------------------------- minting

    public String getSeason() {
        return state.season;
    }

    private String mintKey(String playerId, Rarity rarity) {
        return state.season + ":" + playerId + ":" + rarity.name();
    }

    public int mintedCount(String playerId, Rarity rarity) {
        Integer n = state.mintCounts.get(mintKey(playerId, rarity));
        return n != null ? n : 0;
    }

    public int remainingSupply(String playerId, Rarity rarity) {
        if (!rarity.isLimitedSupply()) return Integer.MAX_VALUE;
        return Math.max(0, rarity.mintCapPerSeason - mintedCount(playerId, rarity));
    }

    public boolean canMintAny(Rarity rarity) {
        for (Player p : combined) if (remainingSupply(p.id, rarity) > 0) return true;
        return false;
    }

    /** Mints a new serial-numbered card; returns null when the season cap is reached. */
    public Card mintCard(String playerId, Rarity rarity, String owner) {
        int minted = mintedCount(playerId, rarity);
        if (rarity.isLimitedSupply() && minted >= rarity.mintCapPerSeason) return null;
        int serial = minted + 1;
        state.mintCounts.put(mintKey(playerId, rarity), serial);
        Card card = new Card(UUID.randomUUID().toString(), playerId, rarity, serial, state.season, owner);
        state.cards.put(card.id, card);
        return card;
    }

    public Card mintRandom(Rarity rarity, String owner) {
        if (combined.isEmpty()) return null;
        List<Player> shuffled = new ArrayList<>(combined);
        Collections.shuffle(shuffled, random);
        for (Player p : shuffled) {
            Card card = mintCard(p.id, rarity, owner);
            if (card != null) return card;
        }
        return null;
    }

    // ------------------------------------------------------------------ cards

    public Card getCard(String cardId) {
        return state.cards.get(cardId);
    }

    public List<Card> cardsOwnedBy(String owner) {
        List<Card> result = new ArrayList<>();
        for (Card c : state.cards.values()) if (owner.equals(c.owner)) result.add(c);
        result.sort((a, b) -> Long.compare(cardValue(b), cardValue(a)));
        return result;
    }

    public List<Card> myCards() {
        return cardsOwnedBy(USER_ID);
    }

    /** Cards I own that are not locked in an auction or sale listing. */
    public List<Card> myTradableCards() {
        List<Card> result = new ArrayList<>();
        for (Card c : myCards()) if (!isCardListed(c.id)) result.add(c);
        return result;
    }

    public boolean isCardListed(String cardId) {
        for (Auction a : state.auctions) if (a.cardId.equals(cardId)) return true;
        for (SaleListing s : state.sales) if (s.cardId.equals(cardId)) return true;
        return false;
    }

    /** Estimated market value in pawns: FIDE rating x rarity multiplier / 10. */
    public long cardValue(Card card) {
        Player p = playerOrUnknown(card.playerId);
        double mult = card.rarity != null ? card.rarity.valueMultiplier : 1.0;
        return Math.round(ratingOf(p) * mult / 10.0);
    }

    // ------------------------------------------------------------------ pawns

    public long getPawns() {
        return state.pawns;
    }

    // ------------------------------------------------------------------ packs

    /** Buys and opens a paid pack. Returns the cards, or null if it can't be bought. */
    public List<Card> buyPack(PackType type) {
        if (type == PackType.FREE) return null;
        if (state.pawns < type.price) return null;
        for (Rarity r : type.contents) {
            if (r.isLimitedSupply() && !canMintAny(r)) return null; // sold out this season
        }
        state.pawns -= type.price;
        List<Card> cards = new ArrayList<>();
        for (Rarity r : type.contents) {
            Card c = mintRandom(r, USER_ID);
            if (c != null) cards.add(c);
        }
        save();
        return cards;
    }

    public long freePackRemainingMs() {
        long elapsed = System.currentTimeMillis() - state.lastFreePackAt;
        return Math.max(0, FREE_PACK_COOLDOWN_MS - elapsed);
    }

    public List<Card> openFreePack() {
        if (freePackRemainingMs() > 0) return null;
        state.lastFreePackAt = System.currentTimeMillis();
        List<Card> cards = new ArrayList<>();
        for (Rarity r : PackType.FREE.contents) {
            Card c = mintRandom(r, USER_ID);
            if (c != null) cards.add(c);
        }
        save();
        return cards;
    }

    // -------------------------------------------------------- pack inventory

    /** Adds owned, unopened packs of a tier (from spins, rewards, gifts). */
    public void grantPack(PackType type, int n) {
        if (type == null || n <= 0) return;
        String key = type.name();
        Integer have = state.packInventory.get(key);
        state.packInventory.put(key, (have != null ? have : 0) + n);
        save();
    }

    public int packCount(PackType type) {
        Integer n = state.packInventory.get(type.name());
        return n != null ? n : 0;
    }

    public int totalPackCount() {
        int total = 0;
        for (Integer n : state.packInventory.values()) if (n != null) total += n;
        return total;
    }

    /** Opens one owned pack of {@code type} from the inventory. Null if none/sold out. */
    public List<Card> openInventoryPack(PackType type) {
        if (packCount(type) <= 0) return null;
        for (Rarity r : type.contents) {
            if (r.isLimitedSupply() && !canMintAny(r)) return null;
        }
        state.packInventory.put(type.name(), packCount(type) - 1);
        List<Card> cards = new ArrayList<>();
        for (Rarity r : type.contents) {
            Card c = mintRandom(r, USER_ID);
            if (c != null) cards.add(c);
        }
        save();
        return cards;
    }

    // ---------------------------------------------------------- daily spin

    public boolean spinAvailable() {
        return spinRemainingMs() <= 0;
    }

    public long spinRemainingMs() {
        long elapsed = System.currentTimeMillis() - state.lastSpinAt;
        return Math.max(0, SPIN_COOLDOWN_MS - elapsed);
    }

    /** Order of the wheel segments (also drives the wheel drawing). */
    public static final PackType[] SPIN_SEGMENTS = {
            PackType.FREE, PackType.LIMITED_PACK, PackType.RARE_PACK,
            PackType.SUPER_RARE_PACK, PackType.UNIQUE_PACK
    };

    /**
     * Performs the daily spin: picks a weighted-random pack tier, grants it to
     * the inventory and starts the 24h cooldown. Returns the tier won, or null
     * if the spin isn't available yet.
     */
    public PackType spin() {
        if (!spinAvailable()) return null;
        state.lastSpinAt = System.currentTimeMillis();
        double r = random.nextDouble();
        PackType won;
        if (r < 0.45) won = PackType.FREE;
        else if (r < 0.80) won = PackType.LIMITED_PACK;
        else if (r < 0.95) won = PackType.RARE_PACK;
        else if (r < 0.99) won = PackType.SUPER_RARE_PACK;
        else won = PackType.UNIQUE_PACK;
        grantPack(won, 1); // grantPack saves
        return won;
    }

    // --------------------------------------------------------- fixtures (games)

    /** Re-reads the cached upcoming games and rebuilds per-player counts. */
    public void reloadFixtures() {
        try {
            UpcomingGamesApi.Cache cache = new UpcomingGamesApi(appContext).loadCached();
            fixtures = cache.games != null ? cache.games : new ArrayList<>();
            fixturesUpdatedAt = cache.updatedAt;
        } catch (Exception e) {
            fixtures = new ArrayList<>();
            fixturesUpdatedAt = 0;
        }
        fixtureCountByFide = new HashMap<>();
        fixtureCountByName = new HashMap<>();
        for (UpcomingGame g : fixtures) {
            if (g.playerFideId > 0) {
                fixtureCountByFide.merge(g.playerFideId, 1, Integer::sum);
            }
            String key = normName(g.playerName);
            if (!key.isEmpty()) fixtureCountByName.merge(key, 1, Integer::sum);
        }
    }

    public long fixturesUpdatedAt() {
        return fixturesUpdatedAt;
    }

    public List<UpcomingGame> allFixtures() {
        return fixtures;
    }

    private String normName(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]", "");
    }

    /** How many upcoming games the player has in the current slate. */
    public int gamesForPlayer(Player p) {
        if (p == null) return 0;
        long fide = knownFideId(p);
        Integer byFide = fide > 0 ? fixtureCountByFide.get(fide) : null;
        if (byFide != null) return byFide;
        Integer byName = fixtureCountByName.get(normName(p.name));
        return byName != null ? byName : 0;
    }

    public List<UpcomingGame> fixturesForPlayer(Player p) {
        List<UpcomingGame> result = new ArrayList<>();
        if (p == null) return result;
        long fide = knownFideId(p);
        String key = normName(p.name);
        for (UpcomingGame g : fixtures) {
            if ((fide > 0 && g.playerFideId == fide) || normName(g.playerName).equals(key)) {
                result.add(g);
            }
        }
        return result;
    }

    /** Owned cards whose player has upcoming games. */
    public int myPlayersWithGames() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        int n = 0;
        for (Card c : myCards()) {
            if (seen.add(c.playerId) && gamesForPlayer(playerOrUnknown(c.playerId)) > 0) n++;
        }
        return n;
    }

    // ------------------------------------------------- gameweek scoring

    private String isoWeekId(long millis) {
        LocalDate d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
        int week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = d.get(IsoFields.WEEK_BASED_YEAR);
        return String.format(Locale.US, "%d-W%02d", year, week);
    }

    public String currentGameweekId() {
        return state.currentGameweek;
    }

    public long gameweekEndMillis() {
        LocalDate today = Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return nextMonday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public long gameweekRemainingMs() {
        return Math.max(0, gameweekEndMillis() - System.currentTimeMillis());
    }

    /**
     * A player's score for the current gameweek. Real FIDE rating movement is
     * the performance signal; when a player has several games in the slate the
     * per-game average is used, matching "average is his score".
     */
    public int playerGameweekScore(String playerId) {
        Player p = getPlayer(playerId);
        if (p == null) return 0;
        Integer baseline = state.gwBaseline.get(playerId);
        int delta = baseline != null ? ratingOf(p) - baseline : 0;
        int games = Math.max(1, gamesForPlayer(p));
        double perGame = (double) (delta * 3) / games;   // average per game
        long score = Math.round(BASE_GAME_SCORE + perGame);
        return (int) Math.max(0, score);
    }

    /** The user's lineup: their top cards by value. */
    public List<Card> lineup() {
        List<Card> cards = myCards();
        return cards.size() > LINEUP_SIZE ? cards.subList(0, LINEUP_SIZE) : cards;
    }

    /** The user's points for the current gameweek (sum over the lineup). */
    public long managerGameweekPoints() {
        long total = 0;
        for (Card c : lineup()) total += playerGameweekScore(c.playerId);
        return total;
    }

    public long managerSeasonPoints() {
        return state.managerSeasonPoints;
    }

    // ------------------------------------------------- leaderboard

    private static final String[] RIVAL_NAMES = {
            "ShahMatMaster", "PawnStormPro", "TelAvivTactics", "HaifaHustler",
            "NegevKnight", "JerusalemGambit", "GalileeGrandmaster", "EilatEndgame",
            "PetahTikvaProdigy", "BeerShevaBishop", "RamatGanRook", "AshdodAttacker"
    };

    private void ensureLeaderboard() {
        if (state.managerName == null || state.managerName.isEmpty()) {
            state.managerName = "You";
        }
        if (state.rivals.isEmpty()) {
            List<String> names = new ArrayList<>(java.util.Arrays.asList(RIVAL_NAMES));
            Collections.shuffle(names, random);
            for (int i = 0; i < Math.min(RIVAL_COUNT, names.size()); i++) {
                double skill = 0.45 + random.nextDouble() * 0.5; // 0.45..0.95
                state.rivals.add(new RivalManager(names.get(i), skill));
            }
        }
        ensureGameweek();
    }

    /** Snapshot every owned/roster player's rating as the week's baseline. */
    private void snapshotBaselines() {
        state.gwBaseline.clear();
        for (Player p : roster) state.gwBaseline.put(p.id, ratingOf(p));
        for (Card c : myCards()) {
            Player p = getPlayer(c.playerId);
            if (p != null) state.gwBaseline.put(p.id, ratingOf(p));
        }
    }

    /** Rolls the gameweek over when the ISO week changes, paying rank rewards. */
    private void ensureGameweek() {
        String nowId = isoWeekId(System.currentTimeMillis());
        if (state.currentGameweek.isEmpty()) {
            state.currentGameweek = nowId;
            snapshotBaselines();
            return;
        }
        if (!nowId.equals(state.currentGameweek)) {
            closeGameweek();
            state.currentGameweek = nowId;
            snapshotBaselines();
            for (RivalManager r : state.rivals) r.points = 0;
        }
    }

    /** Finalizes the current gameweek: ranks managers and pays the user a pack. */
    private void closeGameweek() {
        long myPoints = managerGameweekPoints();
        state.managerSeasonPoints += myPoints;
        // Freeze rival points and accumulate their season totals.
        int rank = 1;
        for (RivalManager r : state.rivals) {
            r.seasonPoints += Math.round(r.points);
            if (r.points > myPoints) rank++;
        }
        PackType reward;
        if (rank == 1) reward = PackType.SUPER_RARE_PACK;
        else if (rank <= 3) reward = PackType.RARE_PACK;
        else if (rank <= 10) reward = PackType.LIMITED_PACK;
        else reward = PackType.FREE;
        grantPack(reward, 1);
        state.lastGwSummary = "Gameweek " + state.currentGameweek + " finished — you placed #"
                + rank + " with " + myPoints + " pts and won a " + reward.displayName + "!";
        syncRemote(true); // publish final standing + backup
    }

    /** Nudges rival points toward their skill target so the board feels live. */
    private void updateRivals() {
        long myPoints = managerGameweekPoints();
        long anchor = Math.max(180, myPoints); // keep rivals in a comparable range
        for (RivalManager r : state.rivals) {
            double target = anchor * (0.6 + r.skill * 0.8); // 0.6x..1.5x of anchor
            r.points += (target - r.points) * 0.15 + (random.nextDouble() - 0.5) * 12;
            if (r.points < 0) r.points = 0;
        }
    }

    public List<LeaderboardEntry> leaderboard() {
        List<LeaderboardEntry> list = new ArrayList<>();
        list.add(new LeaderboardEntry(state.managerName, managerGameweekPoints(),
                state.managerSeasonPoints, true));
        for (RivalManager r : state.rivals) {
            list.add(new LeaderboardEntry(r.name, Math.round(r.points), r.seasonPoints, false));
        }
        list.sort((a, b) -> Long.compare(b.points, a.points));
        return list;
    }

    public int myLeaderboardRank() {
        List<LeaderboardEntry> board = leaderboard();
        for (int i = 0; i < board.size(); i++) if (board.get(i).isUser) return i + 1;
        return board.size();
    }

    public String getManagerName() {
        return state.managerName;
    }

    public void setManagerName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            state.managerName = name.trim();
            save();
        }
    }

    /** Returns and clears the one-shot last-gameweek summary. */
    public String consumeLastGwSummary() {
        String s = state.lastGwSummary;
        state.lastGwSummary = "";
        if (s != null && !s.isEmpty()) save();
        return s;
    }

    // ------------------------------------------------- remote sync (Firebase)

    private long lastRemoteSyncAt = 0;
    private static final long REMOTE_SYNC_INTERVAL_MS = TimeUnit.MINUTES.toMillis(2);

    /**
     * Pushes the manager's standing (and, on force, a full state backup) to
     * Firebase. Throttled; a no-op when Firebase isn't configured.
     */
    public void syncRemote(boolean force) {
        FirebaseGateway fb = FirebaseGateway.get();
        if (!fb.isEnabled() || fb.uid() == null) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastRemoteSyncAt < REMOTE_SYNC_INTERVAL_MS) return;
        lastRemoteSyncAt = now;
        if (state.managerName == null || state.managerName.isEmpty() || "You".equals(state.managerName)) {
            state.managerName = "Manager-" + fb.uid().substring(0, Math.min(5, fb.uid().length()));
        }
        fb.syncManager(state.currentGameweek, state.managerName,
                managerGameweekPoints(), state.managerSeasonPoints);
        if (force) fb.backupState(gson.toJson(state));
    }

    // -------------------------------------------------------------------- ads

    private long todayEpochDay() {
        return System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1);
    }

    private void rollAdCounter() {
        long today = todayEpochDay();
        if (state.adsDayStamp != today) {
            state.adsDayStamp = today;
            state.adsWatchedToday = 0;
        }
    }

    public int adsRemainingToday() {
        rollAdCounter();
        return Math.max(0, ADS_PER_DAY - state.adsWatchedToday);
    }

    /** Grants the reward after a rewarded ad finished. Null when the daily cap is hit. */
    public List<Card> grantAdReward() {
        rollAdCounter();
        if (state.adsWatchedToday >= ADS_PER_DAY) return null;
        state.adsWatchedToday++;
        state.pawns += AD_BONUS_PAWNS;
        List<Card> cards = new ArrayList<>();
        if (random.nextDouble() < AD_LIMITED_CHANCE) {
            Card c = mintRandom(Rarity.LIMITED, USER_ID);
            if (c != null) cards.add(c);
        }
        if (cards.isEmpty()) {
            for (int i = 0; i < 2; i++) {
                Card c = mintRandom(Rarity.COMMON, USER_ID);
                if (c != null) cards.add(c);
            }
        }
        save();
        return cards;
    }

    // ---------------------------------------------------------- daily rewards

    /** Sum of the values of my 5 most valuable cards — higher ratings, better rewards. */
    public long collectionScore() {
        List<Card> cards = myCards();
        long score = 0;
        for (int i = 0; i < Math.min(5, cards.size()); i++) score += cardValue(cards.get(i));
        return score;
    }

    public String dailyRewardTierName() {
        long s = collectionScore();
        if (s < 1500) return "Club Beginner";
        if (s < 8000) return "Local Hero";
        if (s < 25000) return "Master Collector";
        if (s < 80000) return "Grandmaster Collector";
        return "World-Class Collector";
    }

    public boolean dailyRewardAvailable() {
        return state.lastDailyClaimDay != todayEpochDay();
    }

    /** Claims today's reward. Returns a human-readable summary, or null if already claimed. */
    public String claimDailyReward() {
        if (!dailyRewardAvailable()) return null;
        state.lastDailyClaimDay = todayEpochDay();
        long s = collectionScore();
        long pawns;
        Rarity bonusRarity = null;
        if (s < 1500) {
            pawns = 150;
        } else if (s < 8000) {
            pawns = 300;
            bonusRarity = Rarity.COMMON;
        } else if (s < 25000) {
            pawns = 800;
            bonusRarity = Rarity.LIMITED;
        } else if (s < 80000) {
            pawns = 2000;
            bonusRarity = Rarity.RARE;
        } else {
            pawns = 5000;
            bonusRarity = Rarity.SUPER_RARE;
        }
        state.pawns += pawns;
        StringBuilder msg = new StringBuilder(dailyRewardTierName())
                .append(": +").append(pawns).append(" Pawns");
        if (bonusRarity != null) {
            Card c = mintRandom(bonusRarity, USER_ID);
            if (c != null) {
                msg.append(" + ").append(bonusRarity.displayName)
                        .append(" ").append(playerOrUnknown(c.playerId).name);
            } else {
                long compensation = 500;
                state.pawns += compensation;
                msg.append(" + ").append(compensation).append(" Pawns (supply sold out)");
            }
        }
        save();
        return msg.toString();
    }

    // --------------------------------------------------------------- auctions

    public List<Auction> activeAuctions() {
        List<Auction> list = new ArrayList<>(state.auctions);
        list.sort(Comparator.comparingLong(a -> a.endsAt));
        return list;
    }

    public Auction getAuction(String id) {
        for (Auction a : state.auctions) if (a.id.equals(id)) return a;
        return null;
    }

    public String placeBid(String auctionId, long amount) {
        Auction a = getAuction(auctionId);
        if (a == null || a.isEnded(System.currentTimeMillis())) return "Auction has ended";
        if (USER_ID.equals(a.sellerId)) return "You can't bid on your own auction";
        if (amount < a.nextMinBid()) return "Minimum bid is " + a.nextMinBid() + " Pawns";
        long refund = USER_ID.equals(a.currentBidder) ? a.currentBid : 0;
        if (state.pawns + refund < amount) return "Not enough Pawns";
        state.pawns += refund;
        state.pawns -= amount;
        a.currentBid = amount;
        a.currentBidder = USER_ID;
        save();
        return null; // success
    }

    public String createAuction(String cardId, long minBid, long durationMs) {
        Card card = getCard(cardId);
        if (card == null || !USER_ID.equals(card.owner)) return "You don't own this card";
        if (isCardListed(cardId)) return "Card is already listed";
        if (minBid <= 0) return "Minimum bid must be positive";
        Auction a = new Auction();
        a.id = UUID.randomUUID().toString();
        a.cardId = cardId;
        a.sellerId = USER_ID;
        a.minBid = minBid;
        a.endsAt = System.currentTimeMillis() + durationMs;
        state.auctions.add(a);
        save();
        return null;
    }

    public String cancelAuction(String auctionId) {
        Auction a = getAuction(auctionId);
        if (a == null) return "Auction not found";
        if (!USER_ID.equals(a.sellerId)) return "Not your auction";
        if (a.currentBidder != null) return "Can't cancel — there are bids";
        state.auctions.remove(a);
        save();
        return null;
    }

    // ------------------------------------------------------------ direct sale

    public List<SaleListing> activeSales() {
        return new ArrayList<>(state.sales);
    }

    public SaleListing getSale(String id) {
        for (SaleListing s : state.sales) if (s.id.equals(id)) return s;
        return null;
    }

    public String listForSale(String cardId, long price) {
        Card card = getCard(cardId);
        if (card == null || !USER_ID.equals(card.owner)) return "You don't own this card";
        if (isCardListed(cardId)) return "Card is already listed";
        if (price <= 0) return "Price must be positive";
        state.sales.add(new SaleListing(UUID.randomUUID().toString(), cardId, USER_ID, price));
        save();
        return null;
    }

    public String cancelSale(String saleId) {
        SaleListing s = getSale(saleId);
        if (s == null) return "Listing not found";
        if (!USER_ID.equals(s.sellerId)) return "Not your listing";
        state.sales.remove(s);
        save();
        return null;
    }

    public String buyNow(String saleId) {
        SaleListing s = getSale(saleId);
        if (s == null) return "Listing no longer exists";
        if (USER_ID.equals(s.sellerId)) return "This is your own listing";
        if (state.pawns < s.price) return "Not enough Pawns";
        Card card = getCard(s.cardId);
        if (card == null) return "Card no longer exists";
        state.pawns -= s.price;
        card.owner = USER_ID;
        state.sales.remove(s);
        save();
        return null;
    }

    // ----------------------------------------------------------------- trades

    public List<TradeOffer> offersInvolvingMe() {
        List<TradeOffer> list = new ArrayList<>();
        for (TradeOffer o : state.offers) {
            if (USER_ID.equals(o.fromId) || USER_ID.equals(o.toId)) list.add(o);
        }
        list.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return list;
    }

    public TradeOffer getOffer(String id) {
        for (TradeOffer o : state.offers) if (o.id.equals(id)) return o;
        return null;
    }

    /** Sends a counter-offer from the user. Returns an error message or null on success. */
    public String sendOffer(String toId, List<String> offeredCardIds, long offeredPawns,
                            List<String> requestedCardIds, long requestedPawns) {
        if (offeredPawns < 0 || requestedPawns < 0) return "Invalid amount";
        if (offeredPawns > state.pawns) return "Not enough Pawns";
        if (offeredCardIds.isEmpty() && offeredPawns <= 0) return "Offer something first";
        for (String cardId : offeredCardIds) {
            Card c = getCard(cardId);
            if (c == null || !USER_ID.equals(c.owner)) return "You don't own all offered cards";
            if (isCardListed(cardId)) return "An offered card is listed on the market";
        }
        for (String cardId : requestedCardIds) {
            Card c = getCard(cardId);
            if (c == null || !toId.equals(c.owner)) return "Requested card is no longer available";
        }
        TradeOffer o = new TradeOffer();
        o.id = UUID.randomUUID().toString();
        o.fromId = USER_ID;
        o.toId = toId;
        o.offeredCardIds = new ArrayList<>(offeredCardIds);
        o.offeredPawns = offeredPawns;
        o.requestedCardIds = new ArrayList<>(requestedCardIds);
        o.requestedPawns = requestedPawns;
        o.createdAt = System.currentTimeMillis();
        state.offers.add(o);
        save();
        return null;
    }

    /** The user accepts an incoming offer. */
    public String acceptOffer(String offerId) {
        TradeOffer o = getOffer(offerId);
        if (o == null || o.status != TradeOffer.Status.PENDING) return "Offer is not pending";
        if (!USER_ID.equals(o.toId)) return "This offer isn't addressed to you";
        String error = validateTrade(o);
        if (error != null) {
            o.status = TradeOffer.Status.REJECTED;
            save();
            return error;
        }
        executeTrade(o);
        o.status = TradeOffer.Status.ACCEPTED;
        save();
        return null;
    }

    /** Reject an incoming offer, or cancel one of my outgoing pending offers. */
    public String rejectOffer(String offerId) {
        TradeOffer o = getOffer(offerId);
        if (o == null || o.status != TradeOffer.Status.PENDING) return "Offer is not pending";
        o.status = TradeOffer.Status.REJECTED;
        save();
        return null;
    }

    /** Checks both sides still own what the offer says. Null = valid. */
    private String validateTrade(TradeOffer o) {
        for (String cardId : o.offeredCardIds) {
            Card c = getCard(cardId);
            if (c == null || !o.fromId.equals(c.owner)) return "Sender no longer owns an offered card";
        }
        for (String cardId : o.requestedCardIds) {
            Card c = getCard(cardId);
            if (c == null || !o.toId.equals(c.owner)) return "A requested card changed hands";
        }
        long myPawnCost = USER_ID.equals(o.toId) ? o.requestedPawns
                : USER_ID.equals(o.fromId) ? o.offeredPawns : 0;
        if (myPawnCost > state.pawns) return "Not enough Pawns";
        return null;
    }

    /** Moves cards and pawns. Only the user's wallet is tracked; bots have deep pockets. */
    private void executeTrade(TradeOffer o) {
        for (String cardId : o.offeredCardIds) {
            Card c = getCard(cardId);
            if (c != null) c.owner = o.toId;
        }
        for (String cardId : o.requestedCardIds) {
            Card c = getCard(cardId);
            if (c != null) c.owner = o.fromId;
        }
        if (USER_ID.equals(o.fromId)) state.pawns += o.requestedPawns - o.offeredPawns;
        if (USER_ID.equals(o.toId)) state.pawns += o.offeredPawns - o.requestedPawns;
    }

    // ------------------------------------------------------- market simulation

    private String randomBot() {
        return BOTS[random.nextInt(BOTS.length)];
    }

    private boolean isBot(String id) {
        for (String b : BOTS) if (b.equals(id)) return true;
        return false;
    }

    /**
     * Advances the simulated world: resolves finished auctions, lets bots bid,
     * buy, answer trade offers and keep the market stocked. Safe to call often.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        resolveEndedAuctions(now);
        botsBid(now);
        botsBuyMySales();
        botsAnswerOffers();
        botsSendRandomOffer();
        replenishBotListings();
        pruneResolvedOffers();
        ensureGameweek();
        updateRivals();
        save();
        syncRemote(false);
    }

    private void resolveEndedAuctions(long now) {
        Iterator<Auction> it = state.auctions.iterator();
        while (it.hasNext()) {
            Auction a = it.next();
            if (!a.isEnded(now)) continue;
            Card card = getCard(a.cardId);
            if (card != null && a.currentBidder != null) {
                card.owner = a.currentBidder;
                // The user pre-paid (escrow) when bidding; sellers get paid on close.
                if (USER_ID.equals(a.sellerId) && !USER_ID.equals(a.currentBidder)) {
                    state.pawns += a.currentBid;
                }
            }
            it.remove();
        }
    }

    private void botsBid(long now) {
        for (Auction a : state.auctions) {
            if (a.isEnded(now)) continue;
            Card card = getCard(a.cardId);
            if (card == null) continue;
            long value = cardValue(card);
            // Outbid the user occasionally, but never above fair value.
            if (USER_ID.equals(a.currentBidder) && random.nextDouble() < 0.25) {
                long bid = Math.round(a.nextMinBid() * (1.0 + random.nextDouble() * 0.10));
                if (bid <= Math.round(value * 0.85)) {
                    state.pawns += a.currentBid; // refund the user's escrow
                    a.currentBid = bid;
                    a.currentBidder = randomBot();
                }
            } else if (a.currentBidder == null && random.nextDouble() < 0.15
                    && a.minBid <= Math.round(value * 0.8)) {
                a.currentBid = a.minBid;
                a.currentBidder = randomBot();
            }
        }
    }

    private void botsBuyMySales() {
        Iterator<SaleListing> it = state.sales.iterator();
        while (it.hasNext()) {
            SaleListing s = it.next();
            if (!USER_ID.equals(s.sellerId)) continue;
            Card card = getCard(s.cardId);
            if (card == null) {
                it.remove();
                continue;
            }
            long value = cardValue(card);
            if (s.price <= Math.round(value * 1.05) && random.nextDouble() < 0.2) {
                card.owner = randomBot();
                state.pawns += s.price;
                it.remove();
            }
        }
    }

    private void botsAnswerOffers() {
        List<TradeOffer> counters = new ArrayList<>();
        for (TradeOffer o : state.offers) {
            if (o.status != TradeOffer.Status.PENDING) continue;
            if (!isBot(o.toId)) continue;
            String invalid = validateTrade(o);
            if (invalid != null) {
                o.status = TradeOffer.Status.REJECTED;
                continue;
            }
            long offeredValue = o.offeredPawns;
            for (String cardId : o.offeredCardIds) offeredValue += cardValue(getCard(cardId));
            long requestedValue = o.requestedPawns;
            for (String cardId : o.requestedCardIds) requestedValue += cardValue(getCard(cardId));

            if (offeredValue >= Math.round(requestedValue * 0.9)) {
                executeTrade(o);
                o.status = TradeOffer.Status.ACCEPTED;
            } else if (offeredValue >= Math.round(requestedValue * 0.5)) {
                o.status = TradeOffer.Status.COUNTERED;
                long offeredCardsValue = 0;
                for (String cardId : o.offeredCardIds) offeredCardsValue += cardValue(getCard(cardId));
                long askPawns = Math.max(50, Math.round(requestedValue * 1.05) - offeredCardsValue);
                TradeOffer counter = new TradeOffer();
                counter.id = UUID.randomUUID().toString();
                counter.fromId = o.toId;
                counter.toId = USER_ID;
                counter.offeredCardIds = new ArrayList<>(o.requestedCardIds);
                counter.offeredPawns = 0;
                counter.requestedCardIds = new ArrayList<>(o.offeredCardIds);
                counter.requestedPawns = askPawns;
                counter.createdAt = System.currentTimeMillis();
                counters.add(counter);
            } else {
                o.status = TradeOffer.Status.REJECTED;
            }
        }
        state.offers.addAll(counters);
    }

    private void botsSendRandomOffer() {
        if (random.nextDouble() >= 0.10) return;
        // Don't spam: only one pending incoming offer at a time.
        for (TradeOffer o : state.offers) {
            if (o.status == TradeOffer.Status.PENDING && USER_ID.equals(o.toId)) return;
        }
        List<Card> targets = myTradableCards();
        if (targets.isEmpty()) return;
        Card target = targets.get(random.nextInt(targets.size()));
        long value = cardValue(target);
        TradeOffer o = new TradeOffer();
        o.id = UUID.randomUUID().toString();
        o.fromId = randomBot();
        o.toId = USER_ID;
        o.requestedCardIds.add(target.id);
        o.offeredPawns = Math.round(value * (0.75 + random.nextDouble() * 0.35));
        o.createdAt = System.currentTimeMillis();
        state.offers.add(o);
    }

    private void pruneResolvedOffers() {
        List<TradeOffer> resolved = new ArrayList<>();
        for (TradeOffer o : state.offers) {
            if (o.status != TradeOffer.Status.PENDING) resolved.add(o);
        }
        if (resolved.size() <= 20) return;
        resolved.sort(Comparator.comparingLong(o -> o.createdAt));
        for (int i = 0; i < resolved.size() - 20; i++) state.offers.remove(resolved.get(i));
    }

    /** Keeps the market stocked with bot auctions and direct sales. */
    private void replenishBotListings() {
        int botAuctions = 0;
        for (Auction a : state.auctions) if (isBot(a.sellerId)) botAuctions++;
        int botSales = 0;
        for (SaleListing s : state.sales) if (isBot(s.sellerId)) botSales++;

        while (botAuctions < 8) {
            Card card = botListingCard();
            if (card == null) break;
            Auction a = new Auction();
            a.id = UUID.randomUUID().toString();
            a.cardId = card.id;
            a.sellerId = card.owner;
            a.minBid = Math.max(10, Math.round(cardValue(card) * (0.6 + random.nextDouble() * 0.2)));
            a.endsAt = System.currentTimeMillis()
                    + TimeUnit.MINUTES.toMillis(3 + random.nextInt(23));
            state.auctions.add(a);
            botAuctions++;
        }
        while (botSales < 8) {
            Card card = botListingCard();
            if (card == null) break;
            long price = Math.max(15, Math.round(cardValue(card) * (0.9 + random.nextDouble() * 0.35)));
            state.sales.add(new SaleListing(UUID.randomUUID().toString(), card.id, card.owner, price));
            botSales++;
        }
    }

    /** A card a bot can list: an idle bot-owned card, or a freshly minted one. */
    private Card botListingCard() {
        List<Card> idle = new ArrayList<>();
        for (Card c : state.cards.values()) {
            if (isBot(c.owner) && !isCardListed(c.id)) idle.add(c);
        }
        if (!idle.isEmpty() && random.nextDouble() < 0.5) {
            return idle.get(random.nextInt(idle.size()));
        }
        double roll = random.nextDouble();
        Rarity rarity;
        if (roll < 0.35) rarity = Rarity.COMMON;
        else if (roll < 0.75) rarity = Rarity.LIMITED;
        else if (roll < 0.93) rarity = Rarity.RARE;
        else rarity = Rarity.SUPER_RARE; // bots never mint Uniques — those are for collectors
        Card minted = mintRandom(rarity, randomBot());
        if (minted == null && !idle.isEmpty()) return idle.get(random.nextInt(idle.size()));
        return minted;
    }
}
