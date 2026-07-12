package com.chessfantasy.israel.model;

/**
 * Card scarcity tiers. Mint caps are per player, per season:
 * Unique = 1, Super Rare = 10, Rare = 100, Limited = 5000, Common = unlimited.
 */
public enum Rarity {
    COMMON("Common", 0xFF9AA0A6, Integer.MAX_VALUE, 1.0),
    LIMITED("Limited", 0xFFF2B90D, 5000, 3.0),
    RARE("Rare", 0xFFE5484D, 100, 10.0),
    SUPER_RARE("Super Rare", 0xFF2F80ED, 10, 40.0),
    UNIQUE("Unique", 0xFF15181C, 1, 200.0);

    public final String displayName;
    public final int color;
    public final int mintCapPerSeason;
    public final double valueMultiplier;

    Rarity(String displayName, int color, int mintCapPerSeason, double valueMultiplier) {
        this.displayName = displayName;
        this.color = color;
        this.mintCapPerSeason = mintCapPerSeason;
        this.valueMultiplier = valueMultiplier;
    }

    public boolean isLimitedSupply() {
        return mintCapPerSeason != Integer.MAX_VALUE;
    }
}
