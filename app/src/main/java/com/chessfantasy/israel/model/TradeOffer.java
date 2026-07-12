package com.chessfantasy.israel.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A counter-offer / trade proposal between two collectors.
 * "from" gives {offeredCardIds + offeredPawns} and asks for
 * {requestedCardIds + requestedPawns} from "to".
 */
public class TradeOffer {
    public enum Status {PENDING, ACCEPTED, REJECTED, COUNTERED}

    public String id;
    public String fromId;
    public String toId;
    public List<String> offeredCardIds = new ArrayList<>();
    public long offeredPawns;
    public List<String> requestedCardIds = new ArrayList<>();
    public long requestedPawns;
    public Status status = Status.PENDING;
    public long createdAt;

    public TradeOffer() {
    }
}
