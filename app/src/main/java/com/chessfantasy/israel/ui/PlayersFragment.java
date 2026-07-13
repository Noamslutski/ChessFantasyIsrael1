package com.chessfantasy.israel.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.api.FideFullListLoader;
import com.chessfantasy.israel.api.IsraelPlayerSearchApi;
import com.chessfantasy.israel.api.RatingService;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.util.Format;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PlayersFragment extends Fragment implements Refreshable {

    private RecyclerView recycler;
    private PlayerAdapter adapter;
    private Button refreshButton;
    private Button loadAllButton;
    private Button onlineButton;
    private ProgressBar progress;
    private EditText search;
    private TextView count;

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
        adapter = new PlayerAdapter(new ArrayList<>());
        recycler.setAdapter(adapter);

        TextView title = view.findViewById(R.id.players_title);
        title.setText(repo.getClubName());

        count = view.findViewById(R.id.players_count);
        progress = view.findViewById(R.id.players_progress);

        refreshButton = view.findViewById(R.id.players_btn_refresh);
        refreshButton.setOnClickListener(v -> refreshRatings());

        loadAllButton = view.findViewById(R.id.players_btn_load_all);
        loadAllButton.setOnClickListener(v -> confirmLoadAll());

        onlineButton = view.findViewById(R.id.players_btn_online);
        onlineButton.setOnClickListener(v -> searchOnline());

        search = view.findViewById(R.id.players_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void refreshRatings() {
        refreshButton.setEnabled(false);
        refreshButton.setText(R.string.ratings_updating);
        new RatingService().refreshAll(summary -> {
            if (!isAdded()) return;
            refreshButton.setEnabled(true);
            refreshButton.setText(R.string.refresh_ratings);
            Toast.makeText(requireContext(), summary.describe(), Toast.LENGTH_LONG).show();
            refreshData();
        });
    }

    private void confirmLoadAll() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.load_all_israel)
                .setMessage(R.string.load_all_warning)
                .setPositiveButton(android.R.string.ok, (d, w) -> loadAll())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadAll() {
        loadAllButton.setEnabled(false);
        loadAllButton.setText(R.string.loading_all_israel);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        new FideFullListLoader(requireContext()).downloadAsync(
                (bytesRead, totalBytes) -> {
                    if (!isAdded()) return;
                    if (totalBytes > 0) {
                        progress.setIndeterminate(false);
                        progress.setProgress((int) Math.min(100, bytesRead * 100 / totalBytes));
                    } else {
                        progress.setIndeterminate(true);
                    }
                },
                (isrCount, error) -> {
                    if (!isAdded()) return;
                    progress.setVisibility(View.GONE);
                    progress.setIndeterminate(false);
                    loadAllButton.setEnabled(true);
                    loadAllButton.setText(R.string.load_all_israel);
                    if (error != null) {
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                        return;
                    }
                    GameRepository.get().reloadPool();
                    Toast.makeText(requireContext(),
                            "Loaded " + isrCount + " FIDE-rated Israeli players!",
                            Toast.LENGTH_LONG).show();
                    refreshData();
                });
    }

    /** Queries the real Israel federation database (parse.bot API) by name. */
    private void searchOnline() {
        String query = search.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), "Type a name, then search online", Toast.LENGTH_SHORT).show();
            return;
        }
        onlineButton.setEnabled(false);
        onlineButton.setText(R.string.searching_online);
        new IsraelPlayerSearchApi().search(query, (players, error) -> {
            if (!isAdded()) return;
            onlineButton.setEnabled(true);
            onlineButton.setText(R.string.search_online);
            if (error != null && (players == null || players.isEmpty())) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                return;
            }
            showResultsDialog(players);
        });
    }

    private void showResultsDialog(List<Player> results) {
        String[] labels = new String[results.size()];
        boolean[] checked = new boolean[results.size()];
        for (int i = 0; i < results.size(); i++) {
            Player p = results.get(i);
            String title = p.title == null || p.title.isEmpty() ? "" : p.title + " ";
            String hebrew = p.hebrewName != null && !p.hebrewName.isEmpty()
                    && !p.hebrewName.equals(p.name) ? "  " + p.hebrewName : "";
            labels[i] = title + p.name + "  (" + p.rating + ")" + hebrew;
            checked[i] = true;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Add players to your game")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Add / fix names", (d, w) -> {
                    List<Player> chosen = new ArrayList<>();
                    for (int i = 0; i < results.size(); i++) if (checked[i]) chosen.add(results.get(i));
                    int[] result = GameRepository.get().mergeSearched(chosen);
                    int added = result[0], fixed = result[1];
                    String msg;
                    if (added == 0 && fixed == 0) {
                        msg = "No changes — already up to date";
                    } else {
                        StringBuilder sb = new StringBuilder();
                        if (added > 0) sb.append("Added ").append(added).append(" player").append(added == 1 ? "" : "s");
                        if (fixed > 0) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append("corrected ").append(fixed).append(" name").append(fixed == 1 ? "" : "s");
                        }
                        msg = sb.toString();
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    refreshData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void refreshData() {
        if (recycler == null || !isAdded()) return;
        updateCountLabel();
        applyFilter();
    }

    private void updateCountLabel() {
        GameRepository repo = GameRepository.get();
        int total = repo.getPlayers().size();
        StringBuilder sb = new StringBuilder(total + " players");
        if (repo.hasPool()) {
            sb.append(" (incl. ").append(Format.pawns(repo.poolCount()))
                    .append(" from FIDE Israel list");
            if (repo.poolUpdatedAt() > 0) {
                sb.append(", ").append(DateFormat.getDateInstance(DateFormat.MEDIUM)
                        .format(new Date(repo.poolUpdatedAt())));
            }
            sb.append(")");
        } else {
            sb.append(" — tap \"Load ALL\" for the full FIDE Israel list");
        }
        count.setText(sb.toString());
    }

    private void applyFilter() {
        if (adapter == null) return;
        String query = search != null ? search.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
        List<Player> all = GameRepository.get().getPlayers();
        if (query.isEmpty()) {
            adapter.setPlayers(new ArrayList<>(all));
            return;
        }
        List<Player> filtered = new ArrayList<>();
        for (Player p : all) {
            if (p.name != null && p.name.toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(p);
            } else if (p.hebrewName != null && p.hebrewName.contains(query)) {
                filtered.add(p);
            }
        }
        adapter.setPlayers(filtered);
    }
}
