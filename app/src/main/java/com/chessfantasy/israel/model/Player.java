package com.chessfantasy.israel.model;

/** A real chess player of the club. Loaded from assets/players.json. */
public class Player {
    public String id;
    public String name;
    public String hebrewName;
    public String title;
    public int rating;
    public long fideId;

    public Player() {
    }

    public String titledName() {
        if (title == null || title.isEmpty()) return name;
        return title + " " + name;
    }

    public String initials() {
        if (name == null || name.isEmpty()) return "?";
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0)));
            if (sb.length() >= 2) break;
        }
        return sb.toString();
    }
}
