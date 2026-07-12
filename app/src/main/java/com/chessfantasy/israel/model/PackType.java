package com.chessfantasy.israel.model;

/** Purchasable pack tiers. FREE is the gray/common pack. */
public enum PackType {
    FREE("Free Pack", Rarity.COMMON, 0,
            new Rarity[]{Rarity.COMMON, Rarity.COMMON, Rarity.COMMON}),
    LIMITED_PACK("Limited Pack", Rarity.LIMITED, 600,
            new Rarity[]{Rarity.COMMON, Rarity.COMMON, Rarity.LIMITED}),
    RARE_PACK("Rare Pack", Rarity.RARE, 2600,
            new Rarity[]{Rarity.LIMITED, Rarity.LIMITED, Rarity.RARE}),
    SUPER_RARE_PACK("Super Rare Pack", Rarity.SUPER_RARE, 9500,
            new Rarity[]{Rarity.LIMITED, Rarity.RARE, Rarity.SUPER_RARE}),
    UNIQUE_PACK("Unique Pack", Rarity.UNIQUE, 45000,
            new Rarity[]{Rarity.RARE, Rarity.SUPER_RARE, Rarity.UNIQUE});

    public final String displayName;
    public final Rarity tier;
    public final long price;
    public final Rarity[] contents;

    PackType(String displayName, Rarity tier, long price, Rarity[] contents) {
        this.displayName = displayName;
        this.tier = tier;
        this.price = price;
        this.contents = contents;
    }
}
