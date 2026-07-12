package com.chessfantasy.israel.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
        holder.supply.setText(String.format(Locale.US,
                "Cards minted this season — Unique %d/1 · Super Rare %d/10 · Rare %d/100 · Limited %d/5000",
                repo.mintedCount(p.id, Rarity.UNIQUE),
                repo.mintedCount(p.id, Rarity.SUPER_RARE),
                repo.mintedCount(p.id, Rarity.RARE),
                repo.mintedCount(p.id, Rarity.LIMITED)));
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
        final TextView supply;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.player_title);
            name = itemView.findViewById(R.id.player_name);
            nameHebrew = itemView.findViewById(R.id.player_name_hebrew);
            rating = itemView.findViewById(R.id.player_rating);
            supply = itemView.findViewById(R.id.player_supply);
        }
    }
}
