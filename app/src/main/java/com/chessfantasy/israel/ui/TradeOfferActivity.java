package com.chessfantasy.israel.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Card;
import com.chessfantasy.israel.util.Format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Build a counter-offer: pick cards from your collection and/or add Pawns
 * in exchange for another collector's card.
 */
public class TradeOfferActivity extends AppCompatActivity {

    public static final String EXTRA_TO_ID = "to_id";
    public static final String EXTRA_REQUESTED_CARD_ID = "requested_card_id";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trade_offer);

        GameRepository repo = GameRepository.get();
        String toId = getIntent().getStringExtra(EXTRA_TO_ID);
        String requestedCardId = getIntent().getStringExtra(EXTRA_REQUESTED_CARD_ID);
        Card requested = requestedCardId != null ? repo.getCard(requestedCardId) : null;
        if (toId == null || requested == null) {
            finish();
            return;
        }

        TextView title = findViewById(R.id.trade_title);
        title.setText("Counter-offer to " + toId);

        TextView target = findViewById(R.id.trade_target);
        target.setText("You want: " + requested.rarity.displayName + " "
                + repo.playerOrUnknown(requested.playerId).titledName() + " "
                + requested.serialLabel()
                + "  (value ≈ " + Format.pawns(repo.cardValue(requested)) + " Pawns)");

        RecyclerView recycler = findViewById(R.id.trade_recycler);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        CardAdapter adapter = new CardAdapter(repo.myTradableCards(), true, null);
        recycler.setAdapter(adapter);

        EditText pawnsInput = findViewById(R.id.trade_pawns);
        Button send = findViewById(R.id.trade_btn_send);
        send.setOnClickListener(v -> {
            long pawns = 0;
            String text = pawnsInput.getText().toString().trim();
            if (!text.isEmpty()) {
                try {
                    pawns = Long.parseLong(text);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Enter a valid Pawns amount", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            List<String> offeredIds = new ArrayList<>(adapter.getSelectedIds());
            String error = repo.sendOffer(toId, offeredIds, pawns,
                    Collections.singletonList(requestedCardId), 0);
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Offer sent! " + toId + " will answer shortly — check the Offers tab.",
                    Toast.LENGTH_LONG).show();
            finish();
        });
    }
}
