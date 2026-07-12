package com.chessfantasy.israel.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Auction;
import com.chessfantasy.israel.model.Card;
import com.chessfantasy.israel.model.SaleListing;
import com.chessfantasy.israel.util.Format;

import java.util.concurrent.TimeUnit;

/** One owned card: value, mint info, and sell/auction actions. */
public class CardDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CARD_ID = "card_id";
    private static final long AUCTION_DURATION_MS = TimeUnit.MINUTES.toMillis(30);

    private String cardId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_detail);
        cardId = getIntent().getStringExtra(EXTRA_CARD_ID);
        if (cardId == null || GameRepository.get().getCard(cardId) == null) {
            finish();
            return;
        }

        GameRepository repo = GameRepository.get();
        Card card = repo.getCard(cardId);
        long value = repo.cardValue(card);

        Button sell = findViewById(R.id.detail_btn_sell);
        sell.setOnClickListener(v -> NumberPrompt.show(this,
                "Sale price (value ≈ " + Format.pawns(value) + " Pawns)",
                value,
                price -> {
                    String error = repo.listForSale(cardId, price);
                    Toast.makeText(this, error == null
                            ? "Listed for sale — collectors will see it on the market"
                            : error, Toast.LENGTH_SHORT).show();
                    bindState();
                }));

        Button auction = findViewById(R.id.detail_btn_auction);
        auction.setOnClickListener(v -> NumberPrompt.show(this,
                "Auction min bid (30 minutes)",
                Math.max(10, Math.round(value * 0.6)),
                minBid -> {
                    String error = repo.createAuction(cardId, minBid, AUCTION_DURATION_MS);
                    Toast.makeText(this, error == null
                            ? "Auction started — 30 minutes on the clock!"
                            : error, Toast.LENGTH_SHORT).show();
                    bindState();
                }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindState();
    }

    private void bindState() {
        GameRepository repo = GameRepository.get();
        Card card = repo.getCard(cardId);
        if (card == null) {
            finish();
            return;
        }

        View cardView = findViewById(R.id.detail_card);
        CardAdapter.bindCardView(cardView, card);

        TextView value = findViewById(R.id.detail_value);
        value.setText(getString(R.string.estimated_value) + ": "
                + Format.pawns(repo.cardValue(card)) + " Pawns");

        TextView info = findViewById(R.id.detail_info);
        int minted = repo.mintedCount(card.playerId, card.rarity);
        String supply = card.rarity.isLimitedSupply()
                ? minted + " of " + card.rarity.mintCapPerSeason + " minted this season"
                : minted + " minted this season (unlimited)";
        info.setText(card.rarity.displayName + " · Season " + card.season + " · " + supply);

        TextView listedNote = findViewById(R.id.detail_listed_note);
        Button sell = findViewById(R.id.detail_btn_sell);
        Button auction = findViewById(R.id.detail_btn_auction);
        Button cancel = findViewById(R.id.detail_btn_cancel_listing);

        Auction activeAuction = null;
        for (Auction a : repo.activeAuctions()) {
            if (a.cardId.equals(cardId)) {
                activeAuction = a;
                break;
            }
        }
        SaleListing activeSale = null;
        for (SaleListing s : repo.activeSales()) {
            if (s.cardId.equals(cardId)) {
                activeSale = s;
                break;
            }
        }

        boolean listed = activeAuction != null || activeSale != null;
        sell.setVisibility(listed ? View.GONE : View.VISIBLE);
        auction.setVisibility(listed ? View.GONE : View.VISIBLE);
        cancel.setVisibility(listed ? View.VISIBLE : View.GONE);
        listedNote.setVisibility(listed ? View.VISIBLE : View.GONE);

        if (activeAuction != null) {
            final Auction a = activeAuction;
            listedNote.setText("Live auction — "
                    + (a.currentBidder != null
                    ? "current bid " + Format.pawns(a.currentBid)
                    : "min bid " + Format.pawns(a.minBid))
                    + " · ends in " + Format.timeLeft(a.endsAt - System.currentTimeMillis()));
            cancel.setOnClickListener(v -> {
                String error = repo.cancelAuction(a.id);
                if (error != null) Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                bindState();
            });
        } else if (activeSale != null) {
            final SaleListing s = activeSale;
            listedNote.setText("Listed for sale at " + Format.pawns(s.price) + " Pawns");
            cancel.setOnClickListener(v -> {
                String error = repo.cancelSale(s.id);
                if (error != null) Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                bindState();
            });
        }
    }
}
