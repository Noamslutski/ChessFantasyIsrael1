package com.chessfantasy.israel.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.FirebaseGateway;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.LeaderboardEntry;
import com.chessfantasy.israel.util.Format;

import java.util.List;

/** Weekly leaderboard: the user vs rival managers, with gameweek + rewards info. */
public class LeaderboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        GameRepository repo = GameRepository.get();

        TextView gameweek = findViewById(R.id.lb_gameweek);
        gameweek.setText(getString(R.string.gameweek) + " " + repo.currentGameweekId()
                + " · ends in " + Format.timeLeft(repo.gameweekRemainingMs()));

        EditText nameInput = findViewById(R.id.lb_manager_name);
        nameInput.setText(repo.getManagerName());
        nameInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                repo.setManagerName(nameInput.getText().toString());
                bind();
                return true;
            }
            return false;
        });

        RecyclerView recycler = findViewById(R.id.lb_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new Adapter(GameRepository.get().leaderboard()));

        bind();
    }

    @Override
    protected void onResume() {
        super.onResume();
        GameRepository repo = GameRepository.get();
        repo.tick();
        bind();
        showLocalBoard();

        // If Firebase is connected, prefer the real global leaderboard.
        FirebaseGateway fb = FirebaseGateway.get();
        if (fb.isEnabled()) {
            repo.syncRemote(true);
            fb.fetchLeaderboard(repo.currentGameweekId(), 50, entries -> {
                if (isFinishing() || entries == null || entries.isEmpty()) return;
                RecyclerView recycler = findViewById(R.id.lb_recycler);
                recycler.setAdapter(new Adapter(entries));
                TextView gameweek = findViewById(R.id.lb_gameweek);
                gameweek.setText(getString(R.string.gameweek) + " " + repo.currentGameweekId()
                        + " · global · ends in " + Format.timeLeft(repo.gameweekRemainingMs()));
            });
        }
    }

    private void showLocalBoard() {
        RecyclerView recycler = findViewById(R.id.lb_recycler);
        recycler.setAdapter(new Adapter(GameRepository.get().leaderboard()));
    }

    private void bind() {
        GameRepository repo = GameRepository.get();
        int rank = repo.myLeaderboardRank();
        ((TextView) findViewById(R.id.lb_my_rank)).setText("You are #" + rank
                + " of " + (GameRepository.RIVAL_COUNT + 1));
        ((TextView) findViewById(R.id.lb_my_points)).setText(
                repo.managerGameweekPoints() + " pts this gameweek · "
                        + Format.pawns(repo.managerSeasonPoints()) + " season pts");
        ((TextView) findViewById(R.id.lb_reward_note)).setText(rewardNote(rank));
    }

    private String rewardNote(int rank) {
        String tier;
        if (rank == 1) tier = "Super Rare pack";
        else if (rank <= 3) tier = "Rare pack";
        else if (rank <= 10) tier = "Limited pack";
        else tier = "Free pack";
        return "If the gameweek ended now you'd win a " + tier + ". Play the market and refresh live ratings to climb!";
    }

    private static class Adapter extends RecyclerView.Adapter<Holder> {
        private final List<LeaderboardEntry> entries;

        Adapter(List<LeaderboardEntry> entries) {
            this.entries = entries;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_leaderboard, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            LeaderboardEntry e = entries.get(position);
            h.rank.setText("#" + (position + 1));
            h.name.setText(e.name + (e.isUser ? "  (you)" : ""));
            h.name.setTextColor(e.isUser ? 0xFFF2B90D : 0xFFF3F4F6);
            h.points.setText(String.valueOf(e.points));
            h.season.setText(Format.pawns(e.seasonPoints) + " season");
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView rank, name, points, season;

        Holder(@NonNull View itemView) {
            super(itemView);
            rank = itemView.findViewById(R.id.lb_rank);
            name = itemView.findViewById(R.id.lb_name);
            points = itemView.findViewById(R.id.lb_points);
            season = itemView.findViewById(R.id.lb_season);
        }
    }
}
