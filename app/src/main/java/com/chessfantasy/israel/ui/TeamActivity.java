package com.chessfantasy.israel.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Sorare-style team picker: choose 5 of your cards to score each gameweek. */
public class TeamActivity extends AppCompatActivity {

    private CardAdapter adapter;
    private TextView summary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team);

        GameRepository repo = GameRepository.get();
        summary = findViewById(R.id.team_summary);

        RecyclerView recycler = findViewById(R.id.team_recycler);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));

        List<Card> myCards = repo.myCards();
        findViewById(R.id.team_empty).setVisibility(myCards.isEmpty() ? View.VISIBLE : View.GONE);

        adapter = new CardAdapter(myCards, true, null);
        adapter.setMaxSelection(GameRepository.LINEUP_SIZE);
        adapter.preselect(repo.getTeamCardIds());
        adapter.setSelectionListener(this::updateSummary);
        recycler.setAdapter(adapter);

        updateSummary(new java.util.HashSet<>(repo.getTeamCardIds()));

        Button save = findViewById(R.id.team_btn_save);
        save.setOnClickListener(v -> {
            repo.setTeam(new ArrayList<>(adapter.getSelectedIds()));
            Toast.makeText(this, "Team saved — " + repo.teamSize() + " players", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void updateSummary(Set<String> selected) {
        GameRepository repo = GameRepository.get();
        long projected = 0;
        for (String cardId : selected) {
            Card c = repo.getCard(cardId);
            if (c != null) projected += repo.playerGameweekScore(c.playerId);
        }
        summary.setText(selected.size() + "/" + GameRepository.LINEUP_SIZE
                + " picked · projected " + projected + " pts this gameweek");
    }
}
