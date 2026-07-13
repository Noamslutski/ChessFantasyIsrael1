package com.chessfantasy.israel.model;

/** A simulated rival manager on the leaderboard (real ones arrive via Firebase). */
public class RivalManager {
    public String name;
    public double skill;          // 0..1, drives their typical points
    public double points;         // this gameweek's points
    public long seasonPoints;     // cumulative across gameweeks

    public RivalManager() {
    }

    public RivalManager(String name, double skill) {
        this.name = name;
        this.skill = skill;
    }
}
