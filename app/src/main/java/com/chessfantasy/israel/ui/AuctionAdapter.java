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
import com.chessfantasy.israel.model.Auction;
import com.chessfantasy.israel.model.Card;
import com.chessfantasy.israel.model.Player;
import com.chessfantasy.israel.util.Format;

import java.util.List;

public class AuctionAdapter extends RecyclerView.Adapter<AuctionAdapter.Holder> {

    public interface Callbacks {
        void onBid(Auction auction);

        void onOffer(Auction auction);

        void onCancel(Auction auction);
    }

    private final List<Auction> auctions;
    private final Callbacks callbacks;

    public AuctionAdapter(List<Auction> auctions, Callbacks callbacks) {
        this.auctions = auctions;
        this.callbacks = callbacks;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_auction, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        GameRepository repo = GameRepository.get();
        Auction auction = auctions.get(position);
        Card card = repo.getCard(auction.cardId);
        if (card == null) return;
        Player player = repo.playerOrUnknown(card.playerId);

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(card.rarity.color == 0xFF15181C ? 0xFF000000 : card.rarity.color);
        holder.rarityDot.setBackground(dot);

        holder.cardName.setText(card.rarity.displayName + " · " + player.titledName()
                + " " + card.serialLabel());
        holder.time.setText(Format.timeLeft(auction.endsAt - System.currentTimeMillis()));
        holder.details.setText("Seller: " + sellerName(auction.sellerId)
                + " · Value ≈ " + Format.pawns(repo.cardValue(card)) + " Pawns");

        if (auction.currentBidder != null) {
            holder.bidInfo.setText("Current bid: " + Format.pawns(auction.currentBid)
                    + " (" + sellerName(auction.currentBidder) + ")");
        } else {
            holder.bidInfo.setText("Min bid: " + Format.pawns(auction.minBid));
        }

        boolean mine = GameRepository.USER_ID.equals(auction.sellerId);
        if (mine) {
            holder.btnOffer.setVisibility(View.GONE);
            boolean hasBids = auction.currentBidder != null;
            holder.btnBid.setEnabled(!hasBids);
            holder.btnBid.setText(hasBids ? "Bids placed" : "Cancel");
            holder.btnBid.setOnClickListener(v -> callbacks.onCancel(auction));
        } else {
            holder.btnOffer.setVisibility(View.VISIBLE);
            holder.btnBid.setEnabled(true);
            holder.btnBid.setText(R.string.place_bid);
            holder.btnBid.setOnClickListener(v -> callbacks.onBid(auction));
            holder.btnOffer.setOnClickListener(v -> callbacks.onOffer(auction));
        }
    }

    private String sellerName(String id) {
        return GameRepository.USER_ID.equals(id) ? "you" : id;
    }

    @Override
    public int getItemCount() {
        return auctions.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View rarityDot;
        final TextView cardName;
        final TextView time;
        final TextView details;
        final TextView bidInfo;
        final Button btnBid;
        final Button btnOffer;

        Holder(@NonNull View itemView) {
            super(itemView);
            rarityDot = itemView.findViewById(R.id.auction_rarity_dot);
            cardName = itemView.findViewById(R.id.auction_card_name);
            time = itemView.findViewById(R.id.auction_time);
            details = itemView.findViewById(R.id.auction_details);
            bidInfo = itemView.findViewById(R.id.auction_bid_info);
            btnBid = itemView.findViewById(R.id.auction_btn_bid);
            btnOffer = itemView.findViewById(R.id.auction_btn_offer);
        }
    }
}
