package com.chessfantasy.israel.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.api.FideRatingApi;
import com.chessfantasy.israel.data.GameRepository;

public class PlayersFragment extends Fragment implements Refreshable {

    private RecyclerView recycler;
    private Button refreshButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_players, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        GameRepository repo = GameRepository.get();
        recycler = view.findViewById(R.id.players_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        TextView title = view.findViewById(R.id.players_title);
        TextView subtitle = view.findViewById(R.id.players_subtitle);
        title.setText(repo.getClubName());
        subtitle.setText(repo.getClubNameHebrew());

        refreshButton = view.findViewById(R.id.players_btn_refresh);
        refreshButton.setOnClickListener(v -> refreshRatings());
    }

    private void refreshRatings() {
        refreshButton.setEnabled(false);
        refreshButton.setText(R.string.ratings_updating);
        new FideRatingApi().refreshAll((updated, failed) -> {
            if (!isAdded()) return;
            refreshButton.setEnabled(true);
            refreshButton.setText(R.string.refresh_ratings);
            String message = updated > 0
                    ? "Updated " + updated + " live ratings" + (failed > 0 ? " (" + failed + " unavailable)" : "")
                    : "Couldn't reach the FIDE database — using offline ratings";
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            refreshData();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void refreshData() {
        if (recycler == null || !isAdded()) return;
        recycler.setAdapter(new PlayerAdapter(GameRepository.get().getPlayers()));
    }
}
