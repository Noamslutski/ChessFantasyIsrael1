package com.chessfantasy.israel;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.ui.CollectionFragment;
import com.chessfantasy.israel.ui.HomeFragment;
import com.chessfantasy.israel.ui.MarketFragment;
import com.chessfantasy.israel.ui.PacksFragment;
import com.chessfantasy.israel.ui.PlayersFragment;
import com.chessfantasy.israel.ui.Refreshable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Single-activity shell with bottom navigation. No login — straight into the game. */
public class MainActivity extends AppCompatActivity {

    private static final long TICK_INTERVAL_MS = 20_000;

    private final Handler tickHandler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            GameRepository.get().tick();
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (current instanceof Refreshable && current.isResumed()) {
                ((Refreshable) current).refreshData();
            }
            tickHandler.postDelayed(this, TICK_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment fragment;
            if (id == R.id.nav_collection) fragment = new CollectionFragment();
            else if (id == R.id.nav_packs) fragment = new PacksFragment();
            else if (id == R.id.nav_market) fragment = new MarketFragment();
            else if (id == R.id.nav_players) fragment = new PlayersFragment();
            else fragment = new HomeFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        });

        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_home);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        GameRepository.get().tick();
        tickHandler.postDelayed(tickRunnable, TICK_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        tickHandler.removeCallbacks(tickRunnable);
    }
}
