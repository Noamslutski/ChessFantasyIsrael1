package com.chessfantasy.israel.ui;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.Card;

import java.util.List;

/**
 * Simulated rewarded ad: a 15-second sponsored screen. Watching it to the end
 * grants a free reward pack — usually Common cards, with a lucky chance of a
 * Limited (Pro) card. Swap this screen for a real rewarded-ad SDK (e.g. AdMob)
 * by keeping the same grantAdReward() call on completion.
 */
public class AdActivity extends AppCompatActivity {

    private static final long AD_DURATION_MS = 15_000;

    private CountDownTimer timer;
    private ObjectAnimator pulse;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GameRepository repo = GameRepository.get();
        if (repo.adsRemainingToday() <= 0) {
            Toast.makeText(this, "No more ads today — come back tomorrow!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_ad);

        ImageView pawn = findViewById(R.id.ad_pawn);
        TextView countdown = findViewById(R.id.ad_countdown);
        ProgressBar progress = findViewById(R.id.ad_progress);
        Button claim = findViewById(R.id.ad_btn_claim);

        pulse = ObjectAnimator.ofPropertyValuesHolder(pawn,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.15f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.15f));
        pulse.setDuration(700);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();

        timer = new CountDownTimer(AD_DURATION_MS, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdown.setText(String.valueOf(millisUntilFinished / 1000 + 1));
                progress.setProgress((int) ((AD_DURATION_MS - millisUntilFinished) * 100 / AD_DURATION_MS));
            }

            @Override
            public void onFinish() {
                countdown.setText("✓");
                progress.setProgress(100);
                claim.setEnabled(true);
            }
        }.start();

        claim.setOnClickListener(v -> {
            List<Card> cards = repo.grantAdReward();
            if (cards == null || cards.isEmpty()) {
                Toast.makeText(this, "No more ads today — come back tomorrow!", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            String[] ids = new String[cards.size()];
            for (int i = 0; i < cards.size(); i++) ids[i] = cards.get(i).id;
            Intent intent = new Intent(this, PackOpeningActivity.class);
            intent.putExtra(PackOpeningActivity.EXTRA_MODE, PackOpeningActivity.MODE_REVEAL);
            intent.putExtra(PackOpeningActivity.EXTRA_CARD_IDS, ids);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        if (pulse != null) pulse.cancel();
    }
}
