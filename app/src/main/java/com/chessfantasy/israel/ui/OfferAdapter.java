package com.chessfantasy.israel.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Card;
import com.chessfantasy.israel.model.TradeOffer;
import com.chessfantasy.israel.util.Format;

import java.util.List;

public class OfferAdapter extends RecyclerView.Adapter<OfferAdapter.Holder> {

    public interface Callbacks {
        void onAccept(TradeOffer offer);

        void onReject(TradeOffer offer);
    }

    private final List<TradeOffer> offers;
    private final Callbacks callbacks;

    public OfferAdapter(List<TradeOffer> offers, Callbacks callbacks) {
        this.offers = offers;
        this.callbacks = callbacks;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_offer, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TradeOffer offer = offers.get(position);
        boolean incoming = GameRepository.USER_ID.equals(offer.toId);

        holder.header.setText(incoming
                ? "Offer from " + offer.fromId
                : "Your offer to " + offer.toId);

        String give = incoming ? side(offer.requestedCardIds, offer.requestedPawns)
                : side(offer.offeredCardIds, offer.offeredPawns);
        String receive = incoming ? side(offer.offeredCardIds, offer.offeredPawns)
                : side(offer.requestedCardIds, offer.requestedPawns);
        holder.body.setText("You receive: " + receive + "\nYou give: " + give);

        holder.status.setText(offer.status.name());
        int statusColor;
        switch (offer.status) {
            case ACCEPTED:
                statusColor = 0xFF4CAF7D;
                break;
            case REJECTED:
                statusColor = 0xFFE5484D;
                break;
            case COUNTERED:
                statusColor = 0xFF2F80ED;
                break;
            case PENDING:
            default:
                statusColor = 0xFFF2B90D;
                break;
        }
        holder.status.setTextColor(statusColor);

        boolean pending = offer.status == TradeOffer.Status.PENDING;
        if (pending && incoming) {
            holder.buttons.setVisibility(View.VISIBLE);
            holder.btnAccept.setVisibility(View.VISIBLE);
            holder.btnReject.setText(R.string.reject);
            holder.btnAccept.setOnClickListener(v -> callbacks.onAccept(offer));
            holder.btnReject.setOnClickListener(v -> callbacks.onReject(offer));
        } else if (pending) {
            holder.buttons.setVisibility(View.VISIBLE);
            holder.btnAccept.setVisibility(View.GONE);
            holder.btnReject.setText("Cancel offer");
            holder.btnReject.setOnClickListener(v -> callbacks.onReject(offer));
        } else {
            holder.buttons.setVisibility(View.GONE);
        }
    }

    /** Text like "Rare GM Boris Gelfand #7/100 + 1,200 Pawns". */
    private String side(List<String> cardIds, long pawns) {
        GameRepository repo = GameRepository.get();
        StringBuilder sb = new StringBuilder();
        for (String cardId : cardIds) {
            Card c = repo.getCard(cardId);
            if (c == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.rarity.displayName).append(" ")
                    .append(repo.playerOrUnknown(c.playerId).titledName())
                    .append(" ").append(c.serialLabel());
        }
        if (pawns > 0) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(Format.pawns(pawns)).append(" Pawns");
        }
        if (sb.length() == 0) sb.append("nothing");
        return sb.toString();
    }

    @Override
    public int getItemCount() {
        return offers.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView header;
        final TextView body;
        final TextView status;
        final LinearLayout buttons;
        final Button btnAccept;
        final Button btnReject;

        Holder(@NonNull View itemView) {
            super(itemView);
            header = itemView.findViewById(R.id.offer_header);
            body = itemView.findViewById(R.id.offer_body);
            status = itemView.findViewById(R.id.offer_status);
            buttons = itemView.findViewById(R.id.offer_buttons);
            btnAccept = itemView.findViewById(R.id.offer_btn_accept);
            btnReject = itemView.findViewById(R.id.offer_btn_reject);
        }
    }
}
