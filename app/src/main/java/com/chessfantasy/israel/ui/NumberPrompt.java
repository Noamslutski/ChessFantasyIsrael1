package com.chessfantasy.israel.ui;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.function.LongConsumer;

/** Small helper for "enter an amount of Pawns" dialogs. */
public final class NumberPrompt {

    private NumberPrompt() {
    }

    public static void show(Context context, String title, long defaultValue, LongConsumer onOk) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(defaultValue));
        input.setSelection(input.getText().length());

        FrameLayout container = new FrameLayout(context);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        container.setPadding(pad, 0, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(container)
                .setPositiveButton("OK", (dialog, which) -> {
                    try {
                        long value = Long.parseLong(input.getText().toString().trim());
                        onOk.accept(value);
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "Enter a valid number", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
