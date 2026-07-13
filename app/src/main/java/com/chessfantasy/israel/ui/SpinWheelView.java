package com.chessfantasy.israel.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.chessfantasy.israel.data.GameRepository;
import com.chessfantasy.israel.model.PackType;
import com.chessfantasy.israel.model.Rarity;

/** Draws the daily-spin wheel: one colored segment per pack tier. */
public class SpinWheelView extends View {

    private static final PackType[] SEGMENTS = GameRepository.SPIN_SEGMENTS;
    private static final String[] LABELS = {"FREE", "LIMITED", "RARE", "SUPER", "UNIQUE"};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    public SpinWheelView(Context context) {
        super(context);
        init();
    }

    public SpinWheelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(0xFF101418);
        stroke.setStrokeWidth(6f);
        label.setColor(Color.WHITE);
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
        hub.setColor(0xFFF2B90D);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = SEGMENTS.length;
        float sweep = 360f / n;
        float size = Math.min(getWidth(), getHeight());
        float pad = 8f;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = size / 2f - pad;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        label.setTextSize(radius * 0.11f);

        for (int i = 0; i < n; i++) {
            Rarity tier = SEGMENTS[i].tier;
            fill.setColor(tier == Rarity.UNIQUE ? 0xFF15181C : tier.color);
            canvas.drawArc(oval, i * sweep, sweep, true, fill);
            canvas.drawArc(oval, i * sweep, sweep, true, stroke);
        }

        // Labels, rotated to sit along each segment.
        for (int i = 0; i < n; i++) {
            float mid = i * sweep + sweep / 2f;
            label.setColor(SEGMENTS[i].tier == Rarity.LIMITED ? 0xFF231A00 : Color.WHITE);
            canvas.save();
            canvas.rotate(mid, cx, cy);
            canvas.drawText(LABELS[i % LABELS.length], cx + radius * 0.55f, cy + label.getTextSize() / 3f, label);
            canvas.restore();
        }

        canvas.drawCircle(cx, cy, radius * 0.12f, hub);
        canvas.drawCircle(cx, cy, radius * 0.12f, stroke);
    }
}
