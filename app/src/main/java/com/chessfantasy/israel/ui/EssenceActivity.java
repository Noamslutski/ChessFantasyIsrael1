package com.chessfantasy.israel.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Card;

import java.util.ArrayList;
import java.util.List;

/** The Essence Forge: recycle Common cards into essence, spend it on upgrade boxes. */
public class EssenceActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private CardAdapter adapter;
    private TextView balance;
    private TextView target;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_essence);

        balance = findViewById(R.id.essence_balance);
        target = findViewById(R.id.essence_target);
        recycler = findViewById(R.id.essence_recycler);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));

        Button box = findViewById(R.id.essence_btn_box);
        box.setOnClickListener(v -> openBox());

        Button recycle = findViewById(R.id.essence_btn_recycle);
        recycle.setOnClickListener(v -> recycleSelected());

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        GameRepository repo = GameRepository.get();
        balance.setText("🧪 " + repo.getEssence() + " Essence");
        int avg = repo.essenceAvgFed();
        target.setText(avg > 0
                ? "Recycled avg " + avg + " Elo · a box gives 3 Commons rated ~" + repo.boxTargetRating()
                : "Recycle commons to raise your box quality · first box ≈ " + repo.boxTargetRating() + " Elo");

        Button box = findViewById(R.id.essence_btn_box);
        box.setEnabled(repo.canOpenBox());

        List<Card> commons = repo.recyclableCommons();
        findViewById(R.id.essence_empty).setVisibility(commons.isEmpty() ? View.VISIBLE : View.GONE);
        adapter = new CardAdapter(commons, true, null);
        recycler.setAdapter(adapter);
    }

    private void recycleSelected() {
        if (adapter == null || adapter.getSelectedIds().isEmpty()) {
            Toast.makeText(this, "Select Common cards to recycle", Toast.LENGTH_SHORT).show();
            return;
        }
        long gained = GameRepository.get().recycleCommons(new ArrayList<>(adapter.getSelectedIds()));
        Toast.makeText(this, gained > 0 ? "+" + gained + " Essence" : "Nothing recycled",
                Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void openBox() {
        GameRepository repo = GameRepository.get();
        List<Card> cards = repo.openEssenceBox();
        if (cards == null || cards.isEmpty()) {
            Toast.makeText(this, "Need " + GameRepository.BOX_COST_ESSENCE + " Essence to open a box",
                    Toast.LENGTH_SHORT).show();
            refresh();
            return;
        }
        String[] ids = new String[cards.size()];
        for (int i = 0; i < cards.size(); i++) ids[i] = cards.get(i).id;
        Intent intent = new Intent(this, PackOpeningActivity.class);
        intent.putExtra(PackOpeningActivity.EXTRA_MODE, PackOpeningActivity.MODE_REVEAL);
        intent.putExtra(PackOpeningActivity.EXTRA_CARD_IDS, ids);
        startActivity(intent);
        refresh();
    }
}
