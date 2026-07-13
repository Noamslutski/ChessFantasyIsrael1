package com.chessfantasy.israel.api;

import android.os.Handler;
import android.os.Looper;

import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.model.PlayerRatings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pulls live ratings for the whole roster from several real chess data sources
 * — official FIDE, the Israeli Chess Federation (chess.org.il) and chess.com —
 * merges them per player and hands the results back on the main thread.
 * Every source fails independently and gracefully: if the network is down the
 * app simply keeps the offline ratings and never crashes.
 */
public class RatingService {

    public interface Callback {
        void onFinished(Summary summary);
    }

    public static class Summary {
        public int playersUpdated;
        public int playersFailed;
        public final List<String> sourcesUsed = new ArrayList<>();

        public String describe() {
            if (playersUpdated == 0) {
                return "Couldn't reach any rating source — using offline ratings";
            }
            String sources = sourcesUsed.isEmpty() ? "" : " from " + String.join(", ", sourcesUsed);
            String failed = playersFailed > 0 ? " (" + playersFailed + " unavailable)" : "";
            return "Updated " + playersUpdated + " players" + sources + failed;
        }
    }

    private final List<RatingProvider> providers = Arrays.asList(
            new FideProvider(),
            new IsraeliChessProvider(),
            new ChessComProvider());

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public List<RatingProvider> providers() {
        return providers;
    }

    public void refreshAll(Callback callback) {
        executor.execute(() -> {
            GameRepository repo = GameRepository.get();
            List<Player> players = repo.getPlayers();
            List<PlayerRatings> collected = new ArrayList<>();
            List<String> playerIds = new ArrayList<>();
            Summary summary = new Summary();

            for (Player p : players) {
                PlayerRatings ratings = new PlayerRatings();
                ratings.fideId = repo.knownFideId(p);
                boolean any = false;
                for (RatingProvider provider : providers) {
                    boolean contributed = false;
                    try {
                        contributed = provider.enrich(p, ratings);
                    } catch (Exception ignored) {
                        // A misbehaving source must never sink the whole refresh.
                    }
                    any = any || contributed;
                }
                if (any && ratings.hasAny()) {
                    ratings.updatedAt = System.currentTimeMillis();
                    collected.add(ratings);
                    playerIds.add(p.id);
                    summary.playersUpdated++;
                    for (String s : ratings.sources) {
                        if (!summary.sourcesUsed.contains(s)) summary.sourcesUsed.add(s);
                    }
                } else {
                    summary.playersFailed++;
                }
            }

            // Game state is only ever mutated on the main thread.
            mainHandler.post(() -> {
                for (int i = 0; i < collected.size(); i++) {
                    repo.applyRatings(playerIds.get(i), collected.get(i));
                }
                callback.onFinished(summary);
            });
        });
    }
}
