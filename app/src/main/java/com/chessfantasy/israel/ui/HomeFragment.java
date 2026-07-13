package com.chessfantasy.israel.ui;

import android.content.Intent;
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

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.util.Format;

public class HomeFragment extends Fragment implements Refreshable {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        GameRepository repo = GameRepository.get();

        TextView clubName = view.findViewById(R.id.home_club_name);
        TextView clubNameHebrew = view.findViewById(R.id.home_club_name_hebrew);
        clubName.setText(repo.getClubName());
        clubNameHebrew.setText(repo.getClubNameHebrew());

        Button daily = view.findViewById(R.id.home_btn_daily);
        daily.setOnClickListener(v -> {
            String result = repo.claimDailyReward();
            Toast.makeText(requireContext(),
                    result != null ? result : getString(R.string.daily_reward_claimed),
                    Toast.LENGTH_LONG).show();
            refreshData();
        });

        Button form = view.findViewById(R.id.home_btn_form);
        form.setOnClickListener(v -> {
            long amount = repo.claimFormRewards();
            Toast.makeText(requireContext(), amount > 0
                    ? "Form bonus: +" + Format.pawns(amount) + " Pawns from your players' real games!"
                    : getString(R.string.form_none), Toast.LENGTH_LONG).show();
            refreshData();
        });

        Button freePack = view.findViewById(R.id.home_btn_free_pack);
        freePack.setOnClickListener(v -> {
            if (repo.freePackRemainingMs() > 0) {
                Toast.makeText(requireContext(), "Free pack in "
                        + Format.timeLeft(repo.freePackRemainingMs()), Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(requireContext(), PackOpeningActivity.class);
            intent.putExtra(PackOpeningActivity.EXTRA_MODE, PackOpeningActivity.MODE_FREE);
            startActivity(intent);
        });

        Button watchAd = view.findViewById(R.id.home_btn_ad);
        watchAd.setOnClickListener(v -> {
            if (repo.adsRemainingToday() <= 0) {
                Toast.makeText(requireContext(), "No more ads today — come back tomorrow!",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(requireContext(), AdActivity.class));
        });

        Button spin = view.findViewById(R.id.home_btn_spin);
        spin.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SpinActivity.class)));

        Button league = view.findViewById(R.id.home_btn_league);
        league.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LeaderboardActivity.class)));

        Button games = view.findViewById(R.id.home_btn_games);
        games.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), UpcomingGamesActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void refreshData() {
        View view = getView();
        if (view == null || !isAdded()) return;
        GameRepository repo = GameRepository.get();

        TextView season = view.findViewById(R.id.home_season);
        season.setText("Season " + repo.getSeason());

        TextView gameweek = view.findViewById(R.id.home_gameweek);
        gameweek.setText("Gameweek " + repo.currentGameweekId()
                + " · #" + repo.myLeaderboardRank() + " of " + (GameRepository.RIVAL_COUNT + 1));
        TextView gwPoints = view.findViewById(R.id.home_gw_points);
        gwPoints.setText(repo.managerGameweekPoints() + " pts · ends in "
                + Format.timeLeft(repo.gameweekRemainingMs())
                + " · " + repo.myPlayersWithGames() + " of your players have games");

        // Show the result of a gameweek that just closed, once.
        String summary = repo.consumeLastGwSummary();
        if (summary != null && !summary.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Gameweek result")
                    .setMessage(summary)
                    .setPositiveButton("Nice!", null)
                    .show();
        }

        TextView balance = view.findViewById(R.id.home_balance);
        balance.setText(Format.pawns(repo.getPawns()));

        TextView tier = view.findViewById(R.id.home_tier);
        tier.setText("Reward tier: " + repo.dailyRewardTierName());

        TextView score = view.findViewById(R.id.home_score);
        score.setText("Higher-rated cards = better daily rewards. Score: "
                + Format.pawns(repo.collectionScore()));

        Button daily = view.findViewById(R.id.home_btn_daily);
        daily.setEnabled(repo.dailyRewardAvailable());
        daily.setText(repo.dailyRewardAvailable()
                ? getString(R.string.daily_reward) : "Claimed — back tomorrow");

        TextView formInfo = view.findViewById(R.id.home_form_info);
        Button formButton = view.findViewById(R.id.home_btn_form);
        long claimable = repo.claimableFormPawns();
        formButton.setEnabled(claimable > 0);
        formInfo.setText(claimable > 0
                ? "Your players gained rating in real games — " + Format.pawns(claimable) + " Pawns waiting!"
                : "Form bonus: earn Pawns when your players win real games (rating goes up). Refresh live ratings on the Players tab.");

        Button spin = view.findViewById(R.id.home_btn_spin);
        boolean spinReady = repo.spinAvailable();
        spin.setText(spinReady ? getString(R.string.daily_spin) + " — ready!"
                : "Daily Spin in " + Format.timeLeft(repo.spinRemainingMs()));

        TextView packsBanner = view.findViewById(R.id.home_packs_banner);
        int pending = repo.totalPackCount();
        StringBuilder banner = new StringBuilder();
        if (pending > 0) banner.append(getString(R.string.starter_packs_ready, pending));
        if (spinReady) {
            if (banner.length() > 0) banner.append("   ");
            banner.append(getString(R.string.spin_ready));
        }
        packsBanner.setText(banner.toString());
        packsBanner.setVisibility(banner.length() > 0 ? View.VISIBLE : View.GONE);

        Button freePack = view.findViewById(R.id.home_btn_free_pack);
        long remaining = repo.freePackRemainingMs();
        freePack.setEnabled(remaining <= 0);
        freePack.setText(remaining <= 0 ? getString(R.string.free_pack_ready)
                : "Free pack in " + Format.timeLeft(remaining));

        Button watchAd = view.findViewById(R.id.home_btn_ad);
        watchAd.setEnabled(repo.adsRemainingToday() > 0);

        TextView adsLeft = view.findViewById(R.id.home_ads_left);
        adsLeft.setText(repo.adsRemainingToday() + " rewarded ads left today");

        TextView cardsOwned = view.findViewById(R.id.home_cards_owned);
        cardsOwned.setText(String.valueOf(repo.myCards().size()));

        TextView collectionScore = view.findViewById(R.id.home_collection_score);
        collectionScore.setText(Format.pawns(repo.collectionScore()));
    }
}
