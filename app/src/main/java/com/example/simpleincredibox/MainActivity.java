package com.example.simpleincredibox;

import android.content.ClipData;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple Incredibox-like music mixer.
 * Drag characters onto stage slots to play looping tones.
 */
public class MainActivity extends AppCompatActivity {

    private static final int SLOT_COUNT = 7;

    // Sound types inspired by Incredibox categories
    private enum SoundType {
        BEAT(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 80, Color.parseColor("#E63946"), "Beat"),
        EFFECT(ToneGenerator.TONE_PROP_BEEP, 120, Color.parseColor("#F4A261"), "Effect"),
        MELODY(ToneGenerator.TONE_DTMF_1, 200, Color.parseColor("#2A9D8F"), "Melody"),
        VOICE(ToneGenerator.TONE_DTMF_A, 280, Color.parseColor("#E9C46A"), "Voice");

        final int toneType;
        final int durationMs;
        final int color;
        final String label;

        SoundType(int toneType, int durationMs, int color, String label) {
            this.toneType = toneType;
            this.durationMs = durationMs;
            this.color = color;
            this.label = label;
        }
    }

    private final FrameLayout[] slots = new FrameLayout[SLOT_COUNT];
    private final SoundType[] slotSounds = new SoundType[SLOT_COUNT];
    private final Handler[] handlers = new Handler[SLOT_COUNT];
    private final Runnable[] runnables = new Runnable[SLOT_COUNT];
    private ToneGenerator toneGenerator;
    private LinearLayout paletteLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
        } catch (Exception e) {
            Toast.makeText(this, "Audio init failed", Toast.LENGTH_SHORT).show();
        }

        // Find slots
        slots[0] = findViewById(R.id.slot0);
        slots[1] = findViewById(R.id.slot1);
        slots[2] = findViewById(R.id.slot2);
        slots[3] = findViewById(R.id.slot3);
        slots[4] = findViewById(R.id.slot4);
        slots[5] = findViewById(R.id.slot5);
        slots[6] = findViewById(R.id.slot6);

        for (int i = 0; i < SLOT_COUNT; i++) {
            final int index = i;
            slots[i].setOnDragListener(new SlotDragListener(index));
            slots[i].setOnClickListener(v -> clearSlot(index));
            handlers[i] = new Handler(Looper.getMainLooper());
        }

        paletteLayout = findViewById(R.id.paletteLayout);
        createPalette();
    }

    private void createPalette() {
        SoundType[] types = SoundType.values();
        // Create a few of each type for variety
        for (int i = 0; i < types.length; i++) {
            for (int j = 0; j < 2; j++) { // 2 of each
                addCharacterToPalette(types[i], i * 2 + j);
            }
        }
    }

    private void addCharacterToPalette(SoundType type, int id) {
        TextView charView = new TextView(this);
        int size = (int) (72 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(8, 4, 8, 4);
        charView.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(type.color);
        charView.setBackground(bg);

        charView.setText(type.label.substring(0, 1));
        charView.setTextColor(Color.WHITE);
        charView.setTextSize(20);
        charView.setGravity(Gravity.CENTER);
        charView.setTag(type);

        charView.setOnLongClickListener(v -> {
            ClipData data = ClipData.newPlainText("sound", type.name());
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            v.startDragAndDrop(data, shadow, v, 0);
            return true;
        });

        paletteLayout.addView(charView);
    }

    private class SlotDragListener implements View.OnDragListener {
        private final int slotIndex;

        SlotDragListener(int slotIndex) {
            this.slotIndex = slotIndex;
        }

        @Override
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    v.setAlpha(0.7f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    v.setAlpha(1.0f);
                    return true;
                case DragEvent.ACTION_DROP:
                    v.setAlpha(1.0f);
                    View dragged = (View) event.getLocalState();
                    if (dragged != null && dragged.getTag() instanceof SoundType) {
                        SoundType type = (SoundType) dragged.getTag();
                        placeSound(slotIndex, type);
                    }
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setAlpha(1.0f);
                    return true;
                default:
                    return false;
            }
        }
    }

    private void placeSound(int slotIndex, SoundType type) {
        // Stop previous if any
        clearSlot(slotIndex);

        slotSounds[slotIndex] = type;

        // Visual
        FrameLayout slot = slots[slotIndex];
        slot.removeAllViews();
        slot.setBackgroundColor(type.color);

        TextView label = new TextView(this);
        label.setText(type.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        label.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        label.setLayoutParams(lp);
        slot.addView(label);

        // Start looping tone
        startLoop(slotIndex, type);
    }

    private void startLoop(int slotIndex, SoundType type) {
        final int idx = slotIndex;
        final SoundType t = type;

        runnables[idx] = new Runnable() {
            @Override
            public void run() {
                if (slotSounds[idx] == t && toneGenerator != null) {
                    try {
                        toneGenerator.startTone(t.toneType, t.durationMs);
                    } catch (Exception ignored) {}
                    // Loop interval roughly based on duration + gap
                    handlers[idx].postDelayed(this, t.durationMs + 150);
                }
            }
        };
        handlers[idx].post(runnables[idx]);
    }

    private void clearSlot(int slotIndex) {
        if (runnables[slotIndex] != null) {
            handlers[slotIndex].removeCallbacks(runnables[slotIndex]);
            runnables[slotIndex] = null;
        }
        slotSounds[slotIndex] = null;

        FrameLayout slot = slots[slotIndex];
        slot.removeAllViews();
        slot.setBackgroundColor(Color.parseColor("#2A2A4A"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < SLOT_COUNT; i++) {
            clearSlot(i);
        }
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Optionally pause all
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (runnables[i] != null) {
                handlers[i].removeCallbacks(runnables[i]);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Restart active loops
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slotSounds[i] != null) {
                startLoop(i, slotSounds[i]);
            }
        }
    }
}
