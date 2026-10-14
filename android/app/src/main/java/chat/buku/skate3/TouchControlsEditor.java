package chat.buku.skate3;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

/**
 * Touch-controls settings panel. Used both from the launcher and from the
 * in-game overlay, so it only depends on a {@link Context} and a callback that
 * tells the overlay (if any) to reload the layout.
 */
final class TouchControlsEditor {
    private final Context context;
    private final TouchLayout layout;
    private final Runnable onChanged;
    private final Runnable onMoveOnScreen;
    private AlertDialog dialog;

    private TouchControlsEditor(Context context, TouchLayout layout,
                                Runnable onChanged, Runnable onMoveOnScreen) {
        this.context = context;
        this.layout = layout;
        this.onChanged = onChanged;
        this.onMoveOnScreen = onMoveOnScreen;
    }

    /**
     * @param onChanged      called after any change (may be null)
     * @param onMoveOnScreen if non-null a "move on screen" button is offered
     */
    static void show(Context context, Runnable onChanged, Runnable onMoveOnScreen) {
        new TouchControlsEditor(context, new TouchLayout(context),
                                onChanged, onMoveOnScreen).open();
    }

    private void open() {
        dialog = new AlertDialog.Builder(context)
            .setTitle(t("Touch controls"))
            .setView(buildContent())
            .setPositiveButton(t("Done"), null)
            .setNeutralButton(t("Factory reset"), null)
            .create();
        dialog.setOnShowListener(ignored -> {
            // Override so the dialog stays open while the reset is confirmed.
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                new AlertDialog.Builder(context)
                    .setMessage(t("Factory reset: restore the default layout and delete all macros?"))
                    .setNegativeButton(t("Cancel"), null)
                    .setPositiveButton(t("Reset"), (d, w) -> {
                        layout.resetAll();
                        changed();
                        rebuild();
                    }).show());
        });
        dialog.show();
    }

    private void rebuild() {
        dialog.setView(buildContent());
    }

    private void changed() {
        if (onChanged != null) onChanged.run();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(context);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(8));
        scroll.addView(panel);

        if (onMoveOnScreen != null) {
            Button move = new Button(context);
            move.setAllCaps(false);
            move.setText(t("Move buttons on screen"));
            move.setOnClickListener(v -> {
                dialog.dismiss();
                onMoveOnScreen.run();
            });
            panel.addView(move);
        }

        panel.addView(header(t("Button size")));
        for (String[] control : TouchLayout.BUILTIN) {
            panel.addView(sizeRow(control[0], control[1]));
        }

        panel.addView(header(t("Macros")));
        TextView hint = new TextView(context);
        hint.setText(t("A macro is an extra button that presses several inputs at once, e.g. LT + RT for Hall of Meat."));
        hint.setTextSize(12);
        panel.addView(hint);

        List<TouchLayout.Macro> macros = layout.macros();
        for (TouchLayout.Macro macro : macros) {
            panel.addView(macroRow(macro));
            panel.addView(sizeRow(macro.id, macro.label));
        }

        Button add = new Button(context);
        add.setAllCaps(false);
        add.setText(t("Add macro"));
        add.setOnClickListener(v -> showMacroDialog(null));
        panel.addView(add);

        Button preset = new Button(context);
        preset.setAllCaps(false);
        preset.setText(t("Quick macros (presets)"));
        preset.setOnClickListener(v -> showPresets());
        panel.addView(preset);
        return scroll;
    }

    /** Ready-made macros: label, description, mask, toggle mode. */
    private static final Object[][] PRESETS = {
        {"LT+RT", "Hall of Meat: both triggers held together",
            TouchLayout.LT_FLAG | TouchLayout.RT_FLAG, false},
        {"LT+RT", "Both triggers locked (tap on, tap off) - frees both thumbs",
            TouchLayout.LT_FLAG | TouchLayout.RT_FLAG, true},
        {"HLD L", "Hold LT locked (tap on, tap off)", TouchLayout.LT_FLAG, true},
        {"HLD R", "Hold RT locked (tap on, tap off)", TouchLayout.RT_FLAG, true},
        {"LB+RB", "Both bumpers with one finger",
            TouchLayout.LB | TouchLayout.RB, false},
        {"LT+A", "LT + A together", TouchLayout.LT_FLAG | TouchLayout.A, false},
        {"RT+A", "RT + A together", TouchLayout.RT_FLAG | TouchLayout.A, false},
        {"LT+X", "LT + X together", TouchLayout.LT_FLAG | TouchLayout.X, false},
        {"RT+B", "RT + B together", TouchLayout.RT_FLAG | TouchLayout.B, false},
        {"L3+R3", "Both stick clicks with one finger",
            TouchLayout.L3 | TouchLayout.R3, false},
    };

    private void showPresets() {
        String[] items = new String[PRESETS.length];
        for (int i = 0; i < items.length; ++i) {
            items[i] = PRESETS[i][0] + " - " + t((String) PRESETS[i][1]);
        }
        new AlertDialog.Builder(context)
            .setTitle(t("Quick macros (presets)"))
            .setItems(items, (d, which) -> {
                Object[] preset = PRESETS[which];
                List<TouchLayout.Macro> all = layout.macros();
                all.add(layout.newMacro((String) preset[0], (Integer) preset[2],
                                        (Boolean) preset[3]));
                layout.saveMacros(all);
                changed();
                rebuild();
            })
            .setNegativeButton(t("Cancel"), null)
            .show();
    }

    private TextView header(String title) {
        TextView view = new TextView(context);
        view.setText(title);
        view.setTextSize(16);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setTextColor(Color.rgb(255, 112, 28));
        view.setPadding(0, dp(14), 0, dp(4));
        return view;
    }

    private View sizeRow(String id, String name) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(context);
        label.setText(name);
        row.addView(label, new LinearLayout.LayoutParams(dp(78),
                                                         ViewGroup.LayoutParams.WRAP_CONTENT));

        // Slider range is MIN_SCALE..MAX_SCALE in steps of 5%.
        int steps = Math.round((TouchLayout.MAX_SCALE - TouchLayout.MIN_SCALE) * 20f);
        SeekBar bar = new SeekBar(context);
        bar.setMax(steps);
        bar.setProgress(Math.round((layout.scale(id) - TouchLayout.MIN_SCALE) * 20f));
        row.addView(bar, new LinearLayout.LayoutParams(0,
                                                       ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView percent = new TextView(context);
        percent.setGravity(Gravity.END);
        percent.setText(percentText(layout.scale(id)));
        row.addView(percent, new LinearLayout.LayoutParams(dp(52),
                                                           ViewGroup.LayoutParams.WRAP_CONTENT));

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = TouchLayout.MIN_SCALE + progress / 20f;
                percent.setText(percentText(value));
                if (fromUser) {
                    layout.setScale(id, value);
                    changed();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        return row;
    }

    private View macroRow(TouchLayout.Macro macro) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        TextView label = new TextView(context);
        label.setText(macro.label + "  (" + TouchLayout.describeMask(macro.mask) +
                      (macro.latch ? ", " + t("toggle") : "") + ")");
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0,
                                                         ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button edit = new Button(context);
        edit.setAllCaps(false);
        edit.setText(t("Edit"));
        edit.setOnClickListener(v -> showMacroDialog(macro));
        row.addView(edit);

        Button delete = new Button(context);
        delete.setAllCaps(false);
        delete.setText(t("Delete"));
        delete.setOnClickListener(v -> {
            layout.removeMacro(macro);
            changed();
            rebuild();
        });
        row.addView(delete);
        return row;
    }

    /** Creates a macro, or edits {@code existing} when it is non-null. */
    private void showMacroDialog(TouchLayout.Macro existing) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(8));

        EditText name = new EditText(context);
        name.setHint(t("Button label (max 5 characters)"));
        name.setSingleLine(true);
        name.setFilters(new android.text.InputFilter[] {
            new android.text.InputFilter.LengthFilter(5) });
        if (existing != null) name.setText(existing.label);
        panel.addView(name);

        TextView inputs = new TextView(context);
        inputs.setText(t("Inputs pressed together"));
        inputs.setPadding(0, dp(10), 0, 0);
        panel.addView(inputs);

        CheckBox[] boxes = new CheckBox[TouchLayout.INPUT_NAMES.length];
        for (int i = 0; i < boxes.length; ++i) {
            boxes[i] = new CheckBox(context);
            boxes[i].setText(TouchLayout.INPUT_NAMES[i]);
            boxes[i].setChecked(existing != null &&
                                (existing.mask & TouchLayout.INPUT_MASKS[i]) != 0);
            panel.addView(boxes[i]);
        }

        CheckBox latch = new CheckBox(context);
        latch.setText(t("Toggle mode (tap to hold, tap again to release)"));
        latch.setChecked(existing != null && existing.latch);
        panel.addView(latch);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);

        AlertDialog macroDialog = new AlertDialog.Builder(context)
            .setTitle(t(existing == null ? "New macro" : "Edit macro"))
            .setView(scroll)
            .setNegativeButton(t("Cancel"), null)
            .setPositiveButton(t("Save"), null)
            .create();
        macroDialog.setOnShowListener(ignored ->
            macroDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int mask = 0;
                for (int i = 0; i < boxes.length; ++i) {
                    if (boxes[i].isChecked()) mask |= TouchLayout.INPUT_MASKS[i];
                }
                if (mask == 0) {
                    name.setError(t("Select at least one input"));
                    return;
                }
                String label = name.getText().toString().trim();
                if (label.isEmpty()) label = TouchLayout.describeMask(mask).replace(" + ", "+");
                if (label.length() > 5) label = label.substring(0, 5);
                if (existing == null) {
                    List<TouchLayout.Macro> all = layout.macros();
                    all.add(layout.newMacro(label, mask, latch.isChecked()));
                    layout.saveMacros(all);
                } else {
                    List<TouchLayout.Macro> all = layout.macros();
                    for (TouchLayout.Macro macro : all) {
                        if (macro.id.equals(existing.id)) {
                            macro.label = label;
                            macro.mask = mask;
                            macro.latch = latch.isChecked();
                        }
                    }
                    layout.saveMacros(all);
                }
                changed();
                macroDialog.dismiss();
                rebuild();
            }));
        macroDialog.show();
    }

    private static String percentText(float scale) {
        return Math.round(scale * 100) + "%";
    }

    private String t(String english) {
        return LauncherStrings.text(context, english);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

