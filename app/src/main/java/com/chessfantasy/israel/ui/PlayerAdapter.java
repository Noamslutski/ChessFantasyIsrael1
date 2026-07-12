package com.chessfantasy.israel.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.model.Rarity;

import java.util.List;
import java.util.Locale;

public class PlayerAdapter extends RecyclerView.Adapter<PlayerAdapter.Holder> {

    private final List<Player> players;

    public PlayerAdapter(List<Player> players) {
        this.players = players;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_player, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        GameRepository repo = GameRepository.get();
        Player p = players.get(position);
        holder.title.setText(p.title == null || p.title.isEmpty() ? "—" : p.title);
        holder.name.setText(p.name);
        holder.nameHebrew.setText(p.hebrewName != null ? p.hebrewName : "");
        holder.rating.setText(String.valueOf(repo.ratingOf(p)));
        holder.achievements.setText(p.achievements != null ? p.achievements : "");
        holder.supply.setText(String.format(Locale.US,
                "Cards minted this season — Unique %d/1 · Super Rare %d/10 · Rare %d/100 · Limited %d/5000",
                repo.mintedCount(p.id, Rarity.UNIQUE),
                repo.mintedCount(p.id, Rarity.SUPER_RARE),
                repo.mintedCount(p.id, Rarity.RARE),
                repo.mintedCount(p.id, Rarity.LIMITED)));

        // Form = real FIDE rating movement since the last form-bonus claim.
        int delta = repo.formDelta(p.id);
        if (delta > 0) {
            holder.form.setText("▲ +" + delta);
            holder.form.setTextColor(0xFF4CAF7D);
        } else if (delta < 0) {
            holder.form.setText("▼ " + delta);
            holder.form.setTextColor(0xFFE5484D);
        } else {
            holder.form.setText("· 0");
            holder.form.setTextColor(0xFF9AA4AF);
        }

        holder.itemView.setOnClickListener(v -> showPlayerDialog(v.getContext(), p));
    }

    /** Career info plus links to the player's real FIDE profile and real games. */
    private void showPlayerDialog(Context context, Player p) {
        GameRepository repo = GameRepository.get();
        long fideId = repo.knownFideId(p);
        StringBuilder message = new StringBuilder();
        if (p.achievements != null && !p.achievements.isEmpty()) {
            message.append(p.achievements).append("\n\n");
        }
        message.append("FIDE rating: ").append(repo.ratingOf(p));
        int delta = repo.formDelta(p.id);
        if (delta != 0) {
            message.append("  (").append(delta > 0 ? "+" : "").append(delta)
                    .append(" since last form claim)");
        }
        if (fideId > 0) {
            message.append("\nFIDE ID: ").append(fideId);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(p.titledName())
                .setMessage(message.toString())
                .setNegativeButton("Close", null);
        if (fideId > 0) {
            builder.setPositiveButton(R.string.fide_profile, (d, w) ->
                    openUrl(context, "https://ratings.fide.com/profile/" + fideId));
            builder.setNeutralButton(R.string.real_games, (d, w) ->
                    openUrl(context, "https://ratings.fide.com/view_games.phtml?id=" + fideId));
        }
        builder.show();
    }

    private void openUrl(Context context, String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return players.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView name;
        final TextView nameHebrew;
        final TextView rating;
        final TextView form;
        final TextView achievements;
        final TextView supply;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.player_title);
            name = itemView.findViewById(R.id.player_name);
            nameHebrew = itemView.findViewById(R.id.player_name_hebrew);
            rating = itemView.findViewById(R.id.player_rating);
            form = itemView.findViewById(R.id.player_form);
            achievements = itemView.findViewById(R.id.player_achievements);
            supply = itemView.findViewById(R.id.player_supply);
        }
    }
}
