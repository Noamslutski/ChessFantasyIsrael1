package com.chessfantasy.israel.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.chessfantasy.israel.model.Auction;
import com.chessfantasy.israel.model.Card;
import com.chessfantasy.israel.model.GameState;
import com.chessfantasy.israel.model.PackType;
import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.model.Rarity;
import com.chessfantasy.israel.model.SaleListing;
import com.chessfantasy.israel.model.TradeOffer;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
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
    public static final int ADS_PER_DAY = 10;
    public static final long AD_BONUS_PAWNS = 25;
    /** Chance that a rewarded ad upgrades the prize to a Limited ("Pro") card. */
    public static final double AD_LIMITED_CHANCE = 0.15;

    private static GameRepository instance;

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final Random random = new Random();

    private GameState state;
    private List<Player> roster;
    private String clubName = "Hapoel Petah Tikva Chess";
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
        prefs = context.getSharedPreferences("chess_fantasy", Context.MODE_PRIVATE);
        PlayerCatalog.CatalogFile catalog = PlayerCatalog.load(context);
        roster = catalog.players;
        if (catalog.club != null && !catalog.club.isEmpty()) clubName = catalog.club;
        if (catalog.clubHebrew != null) clubNameHebrew = catalog.clubHebrew;
        load();
        ensureSeeded();
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
        // Starter collection so the app feels alive on first launch.
        for (int i = 0; i < 4; i++) mintRandom(Rarity.COMMON, USER_ID);
        mintRandom(Rarity.LIMITED, USER_ID);
        replenishBotListings();
        save();
    }

    // ----------------------------------------------------------------- roster

    public String getClubName() {
        return clubName;
    }

    public String getClubNameHebrew() {
        return clubNameHebrew;
    }

    public List<Player> getPlayers() {
        return roster;
    }

    public Player getPlayer(String playerId) {
        for (Player p : roster) if (p.id.equals(playerId)) return p;
        return null;
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

    public void applyLiveRating(String playerId, int rating, long resolvedFideId) {
        if (rating > 0) state.ratingOverrides.put(playerId, rating);
        if (resolvedFideId > 0) state.resolvedFideIds.put(playerId, resolvedFideId);
        save();
    }

    public long knownFideId(Player p) {
        Long resolved = state.resolvedFideIds.get(p.id);
        if (resolved != null && resolved > 0) return resolved;
        return p.fideId;
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
        for (Player p : roster) if (remainingSupply(p.id, rarity) > 0) return true;
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
        if (roster.isEmpty()) return null;
        List<Player> shuffled = new ArrayList<>(roster);
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
        save();
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
