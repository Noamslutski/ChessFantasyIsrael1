package com.chessfantasy.israel.data;

import android.content.Context;

import com.chessfantasy.israel.model.Player;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads the club roster from assets/players.json. */
public class PlayerCatalog {

    public static class CatalogFile {
        public String club;
        public String clubHebrew;
        public List<Player> players;
    }

    public static CatalogFile load(Context context) {
        try (InputStream in = context.getAssets().open("players.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            CatalogFile file = new Gson().fromJson(sb.toString(), CatalogFile.class);
            if (file == null) file = new CatalogFile();
            if (file.players == null) file.players = new ArrayList<>();
            return file;
        } catch (Exception e) {
            CatalogFile empty = new CatalogFile();
            empty.players = new ArrayList<>();
            return empty;
        }
    }
}
