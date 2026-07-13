package com.chessfantasy.israel.model;

/**
 * One upcoming real game/tournament appearance for a player, from the
 * federation "upcoming games" API. Any field may be empty depending on what
 * the source provides.
 */
public class UpcomingGame {
    public String playerName = "";
    public String playerHebrew = "";
    public long playerFideId;

    public String tournament = "";
    public String location = "";
    public String date = "";      // human-readable start/round date
    public String round = "";
    public String opponent = "";
    public String color = "";     // "White"/"Black" if known

    /** One-line summary, e.g. "R3 · vs Nabaty, Tamir · White". */
    public String detail() {
        StringBuilder sb = new StringBuilder();
        if (!round.isEmpty()) sb.append(round);
        if (!opponent.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("vs ").append(opponent);
        }
        if (!color.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(color);
        }
        return sb.toString();
    }
}
