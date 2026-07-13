package com.chessfantasy.israel.model;

/** A single row on the leaderboard (the user or a rival). */
public class LeaderboardEntry {
    public String name;
    public long points;         // this gameweek
    public long seasonPoints;
    public boolean isUser;

    public LeaderboardEntry(String name, long points, long seasonPoints, boolean isUser) {
        this.name = name;
        this.points = points;
        this.seasonPoints = seasonPoints;
        this.isUser = isUser;
    }
}
