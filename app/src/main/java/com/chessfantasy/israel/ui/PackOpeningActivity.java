package com.chessfantasy.israel.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Card;
import com.chessfantasy.israel.model.PackType;
import com.chessfantasy.israel.model.Rarity;
import com.chessfantasy.israel.util.Format;

import java.util.ArrayList;
import java.util.List;

/**
 * Buys/opens a pack and reveals the cards. Modes:
 * buy    — purchase EXTRA_PACK with Pawns, then open it
 * free   — open the free (Common/gray) pack
 * reveal — show already-granted cards (rewarded-ad prize), ids in EXTRA_CARD_IDS
 */
public class PackOpeningActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_PACK = "pack";
    public static final String EXTRA_CARD_IDS = "card_ids";
    public static final String MODE_BUY = "buy";
    public static final String MODE_FREE = "free";
    public static final String MODE_REVEAL = "reveal";
    public static final String MODE_INVENTORY = "inventory";

    private boolean opened = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pack_opening);

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_FREE;
        final String finalMode = mode;

        PackType type = PackType.FREE;
        if (MODE_BUY.equals(mode) || MODE_INVENTORY.equals(mode)) {
            try {
                type = PackType.valueOf(getIntent().getStringExtra(EXTRA_PACK));
            } catch (Exception e) {
                finish();
                return;
            }
        }
        final PackType packType = type;

        TextView title = findViewById(R.id.pack_open_title);
        TextView packName = findViewById(R.id.pack_open_name);
        FrameLayout visual = findViewById(R.id.pack_open_visual);
        Button openButton = findViewById(R.id.pack_open_btn);
        RecyclerView recycler = findViewById(R.id.pack_open_recycler);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));

        Rarity tier = MODE_REVEAL.equals(mode) ? Rarity.COMMON : packType.tier;
        visual.setBackgroundResource(CardAdapter.backgroundFor(tier));
        int textColor = CardAdapter.textColorFor(tier);

        String heading;
        if (MODE_REVEAL.equals(mode)) {
            heading = "Ad reward!";
            packName.setText("Reward Pack");
        } else if (MODE_FREE.equals(mode)) {
            heading = "Free Pack";
            packName.setText(PackType.FREE.displayName);
        } else if (MODE_INVENTORY.equals(mode)) {
            heading = packType.displayName;
            packName.setText(packType.displayName);
        } else {
            heading = packType.displayName;
            packName.setText(packType.displayName + " · " + Format.pawns(packType.price) + " Pawns");
        }
        title.setText(heading);
        packName.setTextColor(textColor);

        openButton.setOnClickListener(v -> {
            if (opened) {
                finish();
                return;
            }
            List<Card> cards = obtainCards(finalMode, packType);
            if (cards == null || cards.isEmpty()) {
                finish();
                return;
            }
            opened = true;
            visual.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            recycler.setAlpha(0f);
            recycler.setAdapter(new CardAdapter(cards, false, null));
            recycler.animate().alpha(1f).setDuration(500).start();
            openButton.setText("Done");
        });
    }

    @Nullable
    private List<Card> obtainCards(String mode, PackType packType) {
        GameRepository repo = GameRepository.get();
        if (MODE_BUY.equals(mode)) {
            List<Card> cards = repo.buyPack(packType);
            if (cards == null) {
                Toast.makeText(this, "Purchase failed — not enough Pawns or supply sold out",
                        Toast.LENGTH_SHORT).show();
            }
            return cards;
        }
        if (MODE_FREE.equals(mode)) {
            List<Card> cards = repo.openFreePack();
            if (cards == null) {
                Toast.makeText(this, "Free pack in " + Format.timeLeft(repo.freePackRemainingMs()),
                        Toast.LENGTH_SHORT).show();
            }
            return cards;
        }
        if (MODE_INVENTORY.equals(mode)) {
            List<Card> cards = repo.openInventoryPack(packType);
            if (cards == null) {
                Toast.makeText(this, "No such pack to open (or supply sold out)",
                        Toast.LENGTH_SHORT).show();
            }
            return cards;
        }
        String[] ids = getIntent().getStringArrayExtra(EXTRA_CARD_IDS);
        List<Card> cards = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                Card c = repo.getCard(id);
                if (c != null) cards.add(c);
            }
        }
        return cards;
    }
}
