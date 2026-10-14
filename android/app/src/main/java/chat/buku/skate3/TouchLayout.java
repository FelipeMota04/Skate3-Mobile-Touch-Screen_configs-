package chat.buku.skate3;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent customisation of the on-screen controller: per-control size and
 * position, plus user-defined macro buttons that press several inputs at once.
 */
final class TouchLayout {
    static final int DPAD_UP = 0x0001;
    static final int DPAD_DOWN = 0x0002;
    static final int DPAD_LEFT = 0x0004;
    static final int DPAD_RIGHT = 0x0008;
    static final int START = 0x0010;
    static final int BACK = 0x0020;
    static final int L3 = 0x0040;
    static final int R3 = 0x0080;
    static final int LB = 0x0100;
    static final int RB = 0x0200;
    static final int A = 0x1000;
    static final int B = 0x2000;
    static final int X = 0x4000;
    static final int Y = 0x8000;
    /** Analog triggers are not part of the button mask, so macros carry flags. */
    static final int LT_FLAG = 0x10000;
    static final int RT_FLAG = 0x20000;

    static final float MIN_SCALE = 0.5f;
    static final float MAX_SCALE = 2.5f;

    /** Built-in controls: id, label used in the settings screen. */
    static final String[][] BUILTIN = {
        {"lstick", "Left stick"}, {"rstick", "Right stick"}, {"dpad", "D-pad"},
        {"A", "A"}, {"B", "B"}, {"X", "X"}, {"Y", "Y"},
        {"LT", "LT"}, {"RT", "RT"}, {"LB", "LB"}, {"RB", "RB"},
        {"BACK", "BACK"}, {"START", "START"}, {"L3", "L3"}, {"R3", "R3"},
    };

    /** Inputs that can be combined inside a macro. */
    static final String[] INPUT_NAMES = {
        "A", "B", "X", "Y", "LB", "RB", "LT", "RT", "L3", "R3", "START", "BACK",
        "D-pad up", "D-pad down", "D-pad left", "D-pad right",
    };
    static final int[] INPUT_MASKS = {
        A, B, X, Y, LB, RB, LT_FLAG, RT_FLAG, L3, R3, START, BACK,
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
    };

    static final class Macro {
        String id;
        String label;
        int mask;
        /** When true a tap latches the macro on until it is tapped again. */
        boolean latch;
    }

    private static final String PREFS = "skate3_touch_layout";
    private static final String KEY_MACROS = "macros";
    private static final String KEY_NEXT_ID = "next_macro_id";

    private final SharedPreferences prefs;

    TouchLayout(Context context) {
        prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    float scale(String id) {
        return clamp(prefs.getFloat("scale." + id, 1f));
    }

    void setScale(String id, float value) {
        prefs.edit().putFloat("scale." + id, clamp(value)).apply();
    }

    /** Horizontal offset as a fraction of the screen width. */
    float offsetX(String id) { return prefs.getFloat("dx." + id, 0f); }

    /** Vertical offset as a fraction of the screen height. */
    float offsetY(String id) { return prefs.getFloat("dy." + id, 0f); }

    void setOffset(String id, float dx, float dy) {
        prefs.edit().putFloat("dx." + id, dx).putFloat("dy." + id, dy).apply();
    }

    List<Macro> macros() {
        List<Macro> result = new ArrayList<>();
        String json = prefs.getString(KEY_MACROS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); ++i) {
                JSONObject object = array.getJSONObject(i);
                Macro macro = new Macro();
                macro.id = object.getString("id");
                macro.label = object.optString("label", "M");
                macro.mask = object.optInt("mask", 0);
                macro.latch = object.optBoolean("latch", false);
                result.add(macro);
            }
        } catch (JSONException ignored) {
            // A corrupt value simply means no macros.
        }
        return result;
    }

    void saveMacros(List<Macro> macros) {
        JSONArray array = new JSONArray();
        try {
            for (Macro macro : macros) {
                JSONObject object = new JSONObject();
                object.put("id", macro.id);
                object.put("label", macro.label);
                object.put("mask", macro.mask);
                object.put("latch", macro.latch);
                array.put(object);
            }
        } catch (JSONException ignored) {
            return;
        }
        prefs.edit().putString(KEY_MACROS, array.toString()).apply();
    }

    Macro newMacro(String label, int mask, boolean latch) {
        int next = prefs.getInt(KEY_NEXT_ID, 1);
        prefs.edit().putInt(KEY_NEXT_ID, next + 1).apply();
        Macro macro = new Macro();
        macro.id = "macro" + next;
        macro.label = label;
        macro.mask = mask;
        macro.latch = latch;
        return macro;
    }

    void removeMacro(Macro target) {
        List<Macro> macros = macros();
        for (int i = 0; i < macros.size(); ++i) {
            if (macros.get(i).id.equals(target.id)) {
                macros.remove(i);
                break;
            }
        }
        saveMacros(macros);
        prefs.edit().remove("scale." + target.id)
            .remove("dx." + target.id).remove("dy." + target.id).apply();
    }

    /** Restores default sizes and positions of every control; macros are kept. */
    void resetLayout() {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("scale.") || key.startsWith("dx.") || key.startsWith("dy.")) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    /** Factory reset: default sizes and positions, and deletes every macro. */
    void resetAll() {
        SharedPreferences.Editor editor = prefs.edit().clear();
        editor.apply();
    }

    static String describeMask(int mask) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < INPUT_MASKS.length; ++i) {
            if ((mask & INPUT_MASKS[i]) != 0) {
                if (text.length() > 0) text.append(" + ");
                text.append(INPUT_NAMES[i]);
            }
        }
        return text.toString();
    }

    private static float clamp(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }
}

