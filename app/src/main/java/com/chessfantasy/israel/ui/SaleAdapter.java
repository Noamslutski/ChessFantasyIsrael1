package com.chessfantasy.israel.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Card;
import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.model.SaleListing;
import com.chessfantasy.israel.util.Format;

import java.util.List;

public class SaleAdapter extends RecyclerView.Adapter<SaleAdapter.Holder> {

    public interface Callbacks {
        void onBuy(SaleListing sale);

        void onOffer(SaleListing sale);

        void onCancel(SaleListing sale);
    }

    private final List<SaleListing> sales;
    private final Callbacks callbacks;

    public SaleAdapter(List<SaleListing> sales, Callbacks callbacks) {
        this.sales = sales;
        this.callbacks = callbacks;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sale, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        GameRepository repo = GameRepository.get();
        SaleListing sale = sales.get(position);
        Card card = repo.getCard(sale.cardId);
        if (card == null) return;
        Player player = repo.playerOrUnknown(card.playerId);

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(card.rarity.color == 0xFF15181C ? 0xFF000000 : card.rarity.color);
        holder.rarityDot.setBackground(dot);

        holder.cardName.setText(card.rarity.displayName + " · " + player.titledName()
                + " " + card.serialLabel());
        holder.price.setText(Format.pawns(sale.price));
        holder.details.setText("Seller: " + sellerName(sale.sellerId)
                + " · Value ≈ " + Format.pawns(repo.cardValue(card)) + " Pawns");

        boolean mine = GameRepository.USER_ID.equals(sale.sellerId);
        if (mine) {
            holder.btnOffer.setVisibility(View.GONE);
            holder.btnBuy.setText("Cancel");
            holder.btnBuy.setOnClickListener(v -> callbacks.onCancel(sale));
        } else {
            holder.btnOffer.setVisibility(View.VISIBLE);
            holder.btnBuy.setText(R.string.buy_now);
            holder.btnBuy.setOnClickListener(v -> callbacks.onBuy(sale));
            holder.btnOffer.setOnClickListener(v -> callbacks.onOffer(sale));
        }
    }

    private String sellerName(String id) {
        return GameRepository.USER_ID.equals(id) ? "you" : id;
    }

    @Override
    public int getItemCount() {
        return sales.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View rarityDot;
        final TextView cardName;
        final TextView price;
        final TextView details;
        final Button btnBuy;
        final Button btnOffer;

        Holder(@NonNull View itemView) {
            super(itemView);
            rarityDot = itemView.findViewById(R.id.sale_rarity_dot);
            cardName = itemView.findViewById(R.id.sale_card_name);
            price = itemView.findViewById(R.id.sale_price);
            details = itemView.findViewById(R.id.sale_details);
            btnBuy = itemView.findViewById(R.id.sale_btn_buy);
            btnOffer = itemView.findViewById(R.id.sale_btn_offer);
        }
    }
}
