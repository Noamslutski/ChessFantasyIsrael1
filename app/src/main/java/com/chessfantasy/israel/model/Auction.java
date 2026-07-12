package com.chessfantasy.israel.model;

/** Timed auction listing. Highest bidder when the clock runs out wins the card. */
public class Auction {
    public String id;
    public String cardId;
    public String sellerId;
    public long minBid;
    public long currentBid;      // 0 while there are no bids
    public String currentBidder; // null while there are no bids
    public long endsAt;          // epoch millis

    public Auction() {
    }

    public long nextMinBid() {
        if (currentBid <= 0) return minBid;
        return Math.max(currentBid + 1, Math.round(currentBid * 1.05));
    }

    public boolean isEnded(long now) {
        return now >= endsAt;
    }
}
