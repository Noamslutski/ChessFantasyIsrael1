package com.chessfantasy.israel.ui;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.api.UpcomingGamesApi;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Card;
import com.chessfantasy.israel.model.UpcomingGame;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lists upcoming real games for Israeli players; flags players you own. */
public class UpcomingGamesActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private TextView status;
    private TextView empty;
    private Button refresh;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upcoming_games);

        recycler = findViewById(R.id.games_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        status = findViewById(R.id.games_status);
        empty = findViewById(R.id.games_empty);
        refresh = findViewById(R.id.games_btn_refresh);
        refresh.setOnClickListener(v -> doRefresh());

        bind();
    }

    private void doRefresh() {
        refresh.setEnabled(false);
        refresh.setText(R.string.loading_fixtures);
        new UpcomingGamesApi(this).refresh((games, error) -> {
            refresh.setEnabled(true);
            refresh.setText(R.string.refresh_fixtures);
            GameRepository.get().reloadFixtures();
            if (error != null && (games == null || games.isEmpty())) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
            bind();
        });
    }

    private void bind() {
        GameRepository repo = GameRepository.get();
        List<UpcomingGame> games = repo.allFixtures();
        long updated = repo.fixturesUpdatedAt();
        status.setText(games.size() + " upcoming games"
                + (updated > 0 ? " · updated " + DateUtils.getRelativeTimeSpanString(updated) : "")
                + " · " + repo.myPlayersWithGames() + " of your players are playing");
        empty.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setAdapter(new Adapter(games, ownedFideIds(), ownedNames()));
    }

    private Set<Long> ownedFideIds() {
        Set<Long> ids = new HashSet<>();
        GameRepository repo = GameRepository.get();
        for (Card c : repo.myCards()) {
            long f = repo.knownFideId(repo.playerOrUnknown(c.playerId));
            if (f > 0) ids.add(f);
        }
        return ids;
    }

    private Set<String> ownedNames() {
        Set<String> names = new HashSet<>();
        GameRepository repo = GameRepository.get();
        for (Card c : repo.myCards()) {
            String n = repo.playerOrUnknown(c.playerId).name;
            if (n != null) names.add(n.toLowerCase());
        }
        return names;
    }

    private static class Adapter extends RecyclerView.Adapter<Holder> {
        private final List<UpcomingGame> games;
        private final Set<Long> ownedFide;
        private final Set<String> ownedNames;

        Adapter(List<UpcomingGame> games, Set<Long> ownedFide, Set<String> ownedNames) {
            this.games = games;
            this.ownedFide = ownedFide;
            this.ownedNames = ownedNames;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_fixture, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            UpcomingGame g = games.get(position);
            h.player.setText(g.playerName.isEmpty() ? "Player" : g.playerName);
            String tour = g.tournament;
            if (!g.location.isEmpty()) tour += (tour.isEmpty() ? "" : " · ") + g.location;
            if (!g.date.isEmpty()) tour += (tour.isEmpty() ? "" : " · ") + g.date;
            h.tournament.setText(tour);
            h.detail.setText(g.detail());
            h.detail.setVisibility(g.detail().isEmpty() ? View.GONE : View.VISIBLE);

            boolean owned = (g.playerFideId > 0 && ownedFide.contains(g.playerFideId))
                    || ownedNames.contains(g.playerName.toLowerCase());
            h.owned.setVisibility(owned ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() {
            return games.size();
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView player, tournament, detail, owned;

        Holder(@NonNull View itemView) {
            super(itemView);
            player = itemView.findViewById(R.id.fx_player);
            tournament = itemView.findViewById(R.id.fx_tournament);
            detail = itemView.findViewById(R.id.fx_detail);
            owned = itemView.findViewById(R.id.fx_owned);
        }
    }
}
