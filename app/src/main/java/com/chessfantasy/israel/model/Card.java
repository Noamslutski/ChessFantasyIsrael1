package com.chessfantasy.israel.model;

/** A single minted collectible card of one player, in one rarity, for one season. */
public class Card {
    public String id;
    public String playerId;
    public Rarity rarity;
    public int serial;      // mint number within the season, starts at 1
    public String season;
    public String owner;    // GameRepository.USER_ID or a bot id

    public Card() {
    }

    public Card(String id, String playerId, Rarity rarity, int serial, String season, String owner) {
        this.id = id;
        this.playerId = playerId;
        this.rarity = rarity;
        this.serial = serial;
        this.season = season;
        this.owner = owner;
    }

    public String serialLabel() {
        if (rarity != null && rarity.isLimitedSupply()) {
            return "#" + serial + "/" + rarity.mintCapPerSeason;
        }
        return "#" + serial;
    }
}
