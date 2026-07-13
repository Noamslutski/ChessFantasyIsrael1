package com.chessfantasy.israel.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Card;
import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.model.Rarity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Renders collectible cards in grids; supports multi-select for trade offers. */
public class CardAdapter extends RecyclerView.Adapter<CardAdapter.Holder> {

    public interface OnCardClick {
        void onClick(Card card);
    }

    public interface OnSelectionChanged {
        void changed(Set<String> selected);
    }

    private final List<Card> cards;
    private final boolean selectable;
    private final OnCardClick listener;
    private final Set<String> selected = new HashSet<>();
    private int maxSelection = 0;   // 0 = unlimited
    private OnSelectionChanged selectionListener;

    public CardAdapter(List<Card> cards, boolean selectable, OnCardClick listener) {
        this.cards = cards;
        this.selectable = selectable;
        this.listener = listener;
    }

    public Set<String> getSelectedIds() {
        return selected;
    }

    public void setMaxSelection(int max) {
        this.maxSelection = max;
    }

    public void preselect(java.util.Collection<String> ids) {
        selected.clear();
        if (ids != null) selected.addAll(ids);
    }

    public void setSelectionListener(OnSelectionChanged l) {
        this.selectionListener = l;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_card, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Card card = cards.get(position);
        bindCardView(holder.itemView, card);

        boolean isSelected = selected.contains(card.id);
        holder.itemView.findViewById(R.id.card_selected_overlay)
                .setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.itemView.findViewById(R.id.card_selected_check)
                .setVisibility(isSelected ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (selectable) {
                if (!selected.remove(card.id)) {
                    if (maxSelection > 0 && selected.size() >= maxSelection) {
                        android.widget.Toast.makeText(v.getContext(),
                                "You can pick " + maxSelection + " players", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selected.add(card.id);
                }
                notifyItemChanged(holder.getBindingAdapterPosition());
                if (selectionListener != null) selectionListener.changed(selected);
            } else if (listener != null) {
                listener.onClick(card);
            }
        });
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View itemView) {
            super(itemView);
        }
    }

    /** Paints one item_card view for the given card. Also used by the detail screen. */
    public static void bindCardView(View root, Card card) {
        GameRepository repo = GameRepository.get();
        Player player = repo.playerOrUnknown(card.playerId);

        root.setBackgroundResource(backgroundFor(card.rarity));
        int primary = textColorFor(card.rarity);
        int secondary = secondaryTextColorFor(card.rarity);

        TextView rarity = root.findViewById(R.id.card_rarity);
        TextView serial = root.findViewById(R.id.card_serial);
        TextView name = root.findViewById(R.id.card_name);
        TextView nameHebrew = root.findViewById(R.id.card_name_hebrew);
        TextView rating = root.findViewById(R.id.card_rating);
        TextView season = root.findViewById(R.id.card_season);
        ImageView pawn = root.findViewById(R.id.card_pawn);

        rarity.setText(card.rarity.displayName.toUpperCase());
        rarity.setTextColor(primary);
        serial.setText(card.serialLabel());
        name.setText(player.titledName());
        name.setTextColor(primary);
        nameHebrew.setText(player.hebrewName != null ? player.hebrewName : "");
        nameHebrew.setTextColor(secondary);
        rating.setText(String.valueOf(repo.ratingOf(player)));
        rating.setTextColor(card.rarity == Rarity.UNIQUE ? 0xFFF2B90D : primary);
        season.setText("Season " + card.season);
        season.setTextColor(secondary);
        pawn.setColorFilter(primary);
    }

    public static int backgroundFor(Rarity rarity) {
        switch (rarity) {
            case LIMITED:
                return R.drawable.bg_rarity_limited;
            case RARE:
                return R.drawable.bg_rarity_rare;
            case SUPER_RARE:
                return R.drawable.bg_rarity_super_rare;
            case UNIQUE:
                return R.drawable.bg_rarity_unique;
            case COMMON:
            default:
                return R.drawable.bg_rarity_common;
        }
    }

    /** Yellow cards need dark text for contrast; everything else uses white. */
    public static int textColorFor(Rarity rarity) {
        return rarity == Rarity.LIMITED ? 0xFF231A00 : 0xFFFFFFFF;
    }

    public static int secondaryTextColorFor(Rarity rarity) {
        return rarity == Rarity.LIMITED ? 0xCC231A00 : 0xCCFFFFFF;
    }
}
