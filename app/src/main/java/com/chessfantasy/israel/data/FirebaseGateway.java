package com.chessfantasy.israel.data;

import android.content.Context;

import com.chessfantasy.israel.model.LeaderboardEntry;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional Firebase backend: anonymous auth, a cross-device game-state backup,
 * and a global weekly leaderboard in Firestore.
 *
 * Entirely optional and defensive: if the app was built without a
 * google-services.json, {@link #isEnabled()} stays false and every method is a
 * no-op, so the game runs fully in local mode. Add your Firebase config and the
 * same code lights up. Every Firebase call is wrapped so a backend hiccup can
 * never crash the game.
 *
 * Firestore layout:
 *   users/{uid}                         -> { state, updatedAt }
 *   leaderboards/{gameweek}/managers/{uid} -> { name, points, seasonPoints, updatedAt }
 */
public class FirebaseGateway {

    public interface LeaderboardCallback {
        void onLeaderboard(List<LeaderboardEntry> entries); // null on failure
    }

    private static FirebaseGateway instance;

    private boolean enabled;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String uid;

    public static synchronized FirebaseGateway get() {
        if (instance == null) instance = new FirebaseGateway();
        return instance;
    }

    /** Wires up Firebase if the app was built with a google-services.json. */
    public void init(Context context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context); // returns null without config
            }
            if (FirebaseApp.getApps(context).isEmpty()) {
                enabled = false;
                return;
            }
            auth = FirebaseAuth.getInstance();
            db = FirebaseFirestore.getInstance();
            enabled = true;
        } catch (Throwable t) {
            enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String uid() {
        return uid;
    }

    /** Signs in anonymously (once), then runs onReady on the main thread. */
    public void signIn(Runnable onReady) {
        if (!enabled) {
            if (onReady != null) onReady.run();
            return;
        }
        try {
            if (auth.getCurrentUser() != null) {
                uid = auth.getCurrentUser().getUid();
                if (onReady != null) onReady.run();
                return;
            }
            auth.signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful() && auth.getCurrentUser() != null) {
                    uid = auth.getCurrentUser().getUid();
                }
                if (onReady != null) onReady.run();
            });
        } catch (Throwable t) {
            if (onReady != null) onReady.run();
        }
    }

    /** Publishes this manager's current standing to the global leaderboard. */
    public void syncManager(String gameweek, String name, long points, long seasonPoints) {
        if (!enabled || uid == null || gameweek == null || gameweek.isEmpty()) return;
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("name", name);
            m.put("points", points);
            m.put("seasonPoints", seasonPoints);
            m.put("updatedAt", System.currentTimeMillis());
            db.collection("leaderboards").document(gameweek)
                    .collection("managers").document(uid).set(m);
        } catch (Throwable ignored) {
        }
    }

    /** Backs up the full game state for cross-device restore. */
    public void backupState(String stateJson) {
        if (!enabled || uid == null || stateJson == null) return;
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("state", stateJson);
            m.put("updatedAt", System.currentTimeMillis());
            db.collection("users").document(uid).set(m);
        } catch (Throwable ignored) {
        }
    }

    /** Fetches the top managers for a gameweek; passes null on any failure. */
    public void fetchLeaderboard(String gameweek, int limit, LeaderboardCallback cb) {
        if (!enabled || gameweek == null || gameweek.isEmpty()) {
            cb.onLeaderboard(null);
            return;
        }
        try {
            db.collection("leaderboards").document(gameweek).collection("managers")
                    .orderBy("points", Query.Direction.DESCENDING).limit(limit).get()
                    .addOnSuccessListener(snap -> {
                        List<LeaderboardEntry> out = new ArrayList<>();
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            String name = d.getString("name");
                            Long pts = d.getLong("points");
                            Long season = d.getLong("seasonPoints");
                            boolean isUser = uid != null && uid.equals(d.getId());
                            out.add(new LeaderboardEntry(name != null ? name : "Manager",
                                    pts != null ? pts : 0, season != null ? season : 0, isUser));
                        }
                        cb.onLeaderboard(out);
                    })
                    .addOnFailureListener(e -> cb.onLeaderboard(null));
        } catch (Throwable t) {
            cb.onLeaderboard(null);
        }
    }
}
