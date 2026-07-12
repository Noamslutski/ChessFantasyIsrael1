package com.chessfantasy.israel.model;

/** Fixed-price "buy now" listing. */
public class SaleListing {
    public String id;
    public String cardId;
    public String sellerId;
    public long price;

    public SaleListing() {
    }

    public SaleListing(String id, String cardId, String sellerId, long price) {
        this.id = id;
        this.cardId = cardId;
        this.sellerId = sellerId;
        this.price = price;
    }
}
