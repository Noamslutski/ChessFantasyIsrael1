package com.chessfantasy.israel.model;

/** A real chess player of the club. Loaded from assets/players.json. */
public class Player {
    public String id;
    public String name;
    public String hebrewName;
    public String title;
    public int rating;
    /** FIDE (international) id — chess.org.il uses this player's global id. */
    public long fideId;
    /** Israeli Chess Federation (chess.org.il) player id, 0 if unknown. */
    public long ilId;
    /** chess.com username, empty if unknown. */
    public String chessComUser;
    /** Real career highlights, shown on the Players screen. */
    public String achievements;

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
