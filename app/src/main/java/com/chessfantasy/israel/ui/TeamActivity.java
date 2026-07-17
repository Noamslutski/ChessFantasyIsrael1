package com.chessfantasy.israel.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.model.Rarity;
import com.chessfantasy.israel.model.UpcomingGame;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sorare-style team picker: choose up to 5 of your cards. Each row shows the
 * player's rating, projected gameweek score and their upcoming real games.
 */
public class TeamActivity extends AppCompatActivity {

    private final Set<String> selected = new LinkedHashSet<>();
    private List<Card> myCards;
    private TextView summary;
    private TextView squad;
    private Adapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team);

        GameRepository repo = GameRepository.get();
        summary = findViewById(R.id.team_summary);
        squad = findViewById(R.id.team_squad);
        selected.addAll(repo.getTeamCardIds());

        myCards = repo.myCards();
        findViewById(R.id.team_empty).setVisibility(myCards.isEmpty() ? View.VISIBLE : View.GONE);

        RecyclerView recycler = findViewById(R.id.team_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        recycler.setAdapter(adapter);

        Button fixtures = findViewById(R.id.team_btn_fixtures);
        fixtures.setOnClickListener(v -> {
            fixtures.setEnabled(false);
            fixtures.setText(R.string.loading_fixtures);
            new UpcomingGamesApi(this).refresh((games, error) -> {
                fixtures.setEnabled(true);
                fixtures.setText(R.string.refresh_fixtures);
                GameRepository.get().reloadFixtures();
                if (error != null && (games == null || games.isEmpty())) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                }
                adapter.notifyDataSetChanged();
                updateSummary();
            });
        });

        Button save = findViewById(R.id.team_btn_save);
        save.setOnClickListener(v -> {
            repo.setTeam(new ArrayList<>(selected));
            Toast.makeText(this, "Team saved — " + repo.teamSize() + " players", Toast.LENGTH_SHORT).show();
            finish();
        });

        updateSummary();
    }

    private void updateSummary() {
        GameRepository repo = GameRepository.get();
        long projected = 0;
        StringBuilder names = new StringBuilder();
        for (String cardId : selected) {
            Card c = repo.getCard(cardId);
            if (c == null) continue;
            projected += repo.playerGameweekScore(c.playerId);
            if (names.length() > 0) names.append("\n");
            Player p = repo.playerOrUnknown(c.playerId);
            names.append("• ").append(p.name).append("  (")
                    .append(repo.playerGameweekScore(c.playerId)).append(" pts)");
        }
        summary.setText(selected.size() + "/" + GameRepository.LINEUP_SIZE
                + " picked · projected " + projected + " pts this gameweek");
        squad.setText(names.length() > 0 ? "Your squad:\n" + names
                : "Your squad is empty — tap players below to add them.");
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_team_pick, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            GameRepository repo = GameRepository.get();
            Card card = myCards.get(position);
            Player p = repo.playerOrUnknown(card.playerId);
            boolean isSel = selected.contains(card.id);

            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(card.rarity == Rarity.UNIQUE ? 0xFF000000 : card.rarity.color);
            h.dot.setBackground(dot);

            h.name.setText(p.name);
            h.meta.setText(card.rarity.displayName + " · " + repo.ratingOf(p) + " Elo · " + card.serialLabel());
            h.score.setText(String.valueOf(repo.playerGameweekScore(card.playerId)));

            int games = repo.gamesForPlayer(p);
            List<UpcomingGame> list = repo.fixturesForPlayer(p);
            if (games > 0 && !list.isEmpty()) {
                UpcomingGame next = list.get(0);
                String s = games + " game" + (games == 1 ? "" : "s") + " this week";
                if (!next.tournament.isEmpty()) s += " · " + next.tournament;
                if (!next.detail().isEmpty()) s += " · " + next.detail();
                h.games.setText(s);
            } else {
                h.games.setText("No upcoming games loaded (tap refresh)");
            }

            h.check.setText(isSel ? "✓" : "＋");
            h.check.setTextColor(isSel ? 0xFF4CAF7D : 0xFF9AA4AF);
            h.root.setOnClickListener(v -> toggle(card, h.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return myCards.size();
        }
    }

    private void toggle(Card card, int pos) {
        if (!selected.remove(card.id)) {
            if (selected.size() >= GameRepository.LINEUP_SIZE) {
                Toast.makeText(this, "You can pick " + GameRepository.LINEUP_SIZE + " players", Toast.LENGTH_SHORT).show();
                return;
            }
            selected.add(card.id);
        }
        if (pos != RecyclerView.NO_POSITION) adapter.notifyItemChanged(pos);
        updateSummary();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View root;
        final View dot;
        final TextView name, meta, games, score, check;

        Holder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.tp_root);
            dot = itemView.findViewById(R.id.tp_rarity_dot);
            name = itemView.findViewById(R.id.tp_name);
            meta = itemView.findViewById(R.id.tp_meta);
            games = itemView.findViewById(R.id.tp_games);
            score = itemView.findViewById(R.id.tp_score);
            check = itemView.findViewById(R.id.tp_check);
        }
    }
}
