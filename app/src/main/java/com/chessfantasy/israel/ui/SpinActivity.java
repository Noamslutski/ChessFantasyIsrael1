package com.chessfantasy.israel.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.chessfantasy.israel.R;
import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.PackType;
import com.chessfantasy.israel.util.Format;

/**
 * The once-per-24h spin wheel. Tapping "Spin" chooses a weighted-random pack
 * tier, animates the wheel to land on it, grants the pack and opens it.
 */
public class SpinActivity extends AppCompatActivity {

    private boolean spinning = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spin);

        SpinWheelView wheel = findViewById(R.id.spin_wheel);
        Button spinBtn = findViewById(R.id.spin_btn);
        TextView subtitle = findViewById(R.id.spin_subtitle);

        GameRepository repo = GameRepository.get();
        if (!repo.spinAvailable()) {
            subtitle.setText("Next spin in " + Format.timeLeft(repo.spinRemainingMs()));
            spinBtn.setEnabled(false);
            spinBtn.setText("Come back later");
        }

        spinBtn.setOnClickListener(v -> {
            if (spinning) return;
            PackType won = repo.spin();
            if (won == null) {
                Toast.makeText(this, "Next spin in " + Format.timeLeft(repo.spinRemainingMs()),
                        Toast.LENGTH_SHORT).show();
                spinBtn.setEnabled(false);
                return;
            }
            spinning = true;
            spinBtn.setEnabled(false);
            animateTo(wheel, won);
        });
    }

    private void animateTo(SpinWheelView wheel, PackType won) {
        int n = GameRepository.SPIN_SEGMENTS.length;
        int index = 0;
        for (int i = 0; i < n; i++) {
            if (GameRepository.SPIN_SEGMENTS[i] == won) {
                index = i;
                break;
            }
        }
        float sweep = 360f / n;
        float segmentCenter = index * sweep + sweep / 2f;
        // Bring the winning segment's center under the top pointer (270°).
        float target = 270f - segmentCenter;
        while (target < 0) target += 360f;
        float finalRotation = 360f * 5 + target; // 5 full turns for effect

        wheel.setRotation(0f);
        wheel.animate()
                .rotation(finalRotation)
                .setDuration(3200)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        onWon(won);
                    }
                })
                .start();
    }

    private void onWon(PackType won) {
        Toast.makeText(this, "You won a " + won.displayName + "!", Toast.LENGTH_LONG).show();
        // The pack is already in the inventory; open it now.
        Intent intent = new Intent(this, PackOpeningActivity.class);
        intent.putExtra(PackOpeningActivity.EXTRA_MODE, PackOpeningActivity.MODE_INVENTORY);
        intent.putExtra(PackOpeningActivity.EXTRA_PACK, won.name());
        startActivity(intent);
        finish();
    }
}
