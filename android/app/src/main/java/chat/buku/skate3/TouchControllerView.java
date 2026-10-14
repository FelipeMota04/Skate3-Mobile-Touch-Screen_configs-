package chat.buku.skate3;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.SDLControllerManager;

import static chat.buku.skate3.TouchLayout.*;

import java.util.ArrayList;
import java.util.List;

/** A lightweight multitouch Xbox-style controller layered over the SDL surface. */
public final class TouchControllerView extends View {
    private static final String PREFS = "skate3_touch_controller";
    private static final String PREF_VISIBLE = "visible";

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Control> controls = new ArrayList<>();
    private final SparseArray<Control> pointers = new SparseArray<>();
    private final SharedPreferences preferences;
    private boolean controlsVisible;
    private RectF toggle = new RectF();
    private final RectF gear = new RectF();
    private final RectF editDone = new RectF();
    private final RectF editMinus = new RectF();
    private final RectF editPlus = new RectF();
    private final TouchLayout layout;
    private boolean editMode;
    private String selectedId;
    private int dragPointer = -1;
    private float dragX;
    private float dragY;
    private float lastWidth;
    private float lastHeight;

    private Stick leftStick;
    private Stick rightStick;

    public TouchControllerView(Context context) {
        super(context);
        setWillNotDraw(false);
        setFocusable(false);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        label.setColor(Color.WHITE);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        layout = new TouchLayout(context);
        controlsVisible = preferences.contains(PREF_VISIBLE)
            ? preferences.getBoolean(PREF_VISIBLE, true)
            : !hasPhysicalController();
    }

    public void clearInput() {
        for (Control control : controls) control.reset();
        pointers.clear();
        sendState();
        invalidate();
    }

    public void disconnect() {
        for (Control control : controls) control.release();
        pointers.clear();
        TouchControllerBridge.trySetState(-1, 0, 0, 0, 0, 0, 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sendState();
    }

    @Override
    protected void onDetachedFromWindow() {
        disconnect();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        buildControls(width, height);
    }

    private float posX(String id, float basePx) {
        float value = basePx + layout.offsetX(id) * lastWidth;
        return Math.max(0, Math.min(lastWidth, value));
    }

    private float posY(String id, float basePx) {
        float value = basePx + layout.offsetY(id) * lastHeight;
        return Math.max(0, Math.min(lastHeight, value));
    }

    private void addButton(String id, String text, float x, float y, float radius, int mask) {
        float r = radius * layout.scale(id);
        controls.add(new ButtonControl(text, posX(id, x), posY(id, y), r, mask).withId(id));
    }

    private void addRect(String id, String text, float x, float y, float w, float h,
                         int mask, int triggerFlag) {
        float s = layout.scale(id);
        controls.add(new RectButton(text, posX(id, x), posY(id, y), w * s, h * s,
                                    mask, triggerFlag).withId(id));
    }

    private void reload() {
        if (lastWidth > 0 && lastHeight > 0) buildControls(lastWidth, lastHeight);
    }

    private void buildControls(float width, float height) {
        lastWidth = width;
        lastHeight = height;
        controls.clear();
        float unit = Math.min(width / 16f, height / 9f);
        float stickRadius = unit * 0.78f;
        float buttonRadius = unit * 0.38f;

        leftStick = new Stick("L", posX("lstick", width * .16f), posY("lstick", height * .73f),
                              stickRadius * layout.scale("lstick"), true);
        rightStick = new Stick("R", posX("rstick", width * .67f), posY("rstick", height * .77f),
                               stickRadius * layout.scale("rstick"), false);
        controls.add(leftStick.withId("lstick"));
        controls.add(rightStick.withId("rstick"));

        controls.add(new Dpad(posX("dpad", width * .32f), posY("dpad", height * .72f),
                              unit * .98f * layout.scale("dpad")).withId("dpad"));

        float faceX = width * .85f;
        float faceY = height * .68f;
        addButton("A", "A", faceX, faceY + unit * .68f, buttonRadius, A);
        addButton("B", "B", faceX + unit * .68f, faceY, buttonRadius, B);
        addButton("X", "X", faceX - unit * .68f, faceY, buttonRadius, TouchLayout.X);
        addButton("Y", "Y", faceX, faceY - unit * .68f, buttonRadius, TouchLayout.Y);

        float shoulderW = unit * 1.25f;
        float shoulderH = unit * .55f;
        addRect("LT", "LT", width * .07f, height * .10f, shoulderW, shoulderH, 0, LT_FLAG);
        addRect("LB", "LB", width * .20f, height * .10f, shoulderW, shoulderH, LB, 0);
        addRect("RB", "RB", width * .80f, height * .10f, shoulderW, shoulderH, RB, 0);
        addRect("RT", "RT", width * .93f, height * .10f, shoulderW, shoulderH, 0, RT_FLAG);

        addRect("BACK", "BACK", width * .44f, height * .79f, unit * .82f, unit * .45f, BACK, 0);
        addRect("START", "START", width * .55f, height * .79f, unit * .92f, unit * .45f, START, 0);
        addButton("L3", "L3", width * .07f, height * .48f, buttonRadius * .82f, L3);
        addButton("R3", "R3", width * .73f, height * .48f, buttonRadius * .82f, R3);

        List<Macro> macros = layout.macros();
        for (int i = 0; i < macros.size(); ++i) {
            Macro macro = macros.get(i);
            float baseX = width * (.93f - .09f * (i / 4));
            float baseY = height * (.27f + .13f * (i % 4));
            float radius = buttonRadius * 1.05f * layout.scale(macro.id);
            controls.add(new MacroControl(macro, posX(macro.id, baseX),
                                          posY(macro.id, baseY), radius).withId(macro.id));
        }

        float toggleWidth = controlsVisible ? unit * 1.05f : unit * 1.35f;
        float toggleTop = Math.max(dp(18), height * .035f);
        float barHeight = unit * .46f;
        toggle.set(width * .5f - toggleWidth * .5f, toggleTop,
                   width * .5f + toggleWidth * .5f, toggleTop + barHeight);
        gear.set(toggle.right + dp(8), toggleTop, toggle.right + dp(8) + barHeight,
                 toggleTop + barHeight);

        // Edit-layout toolbar: [ - ] [ DONE ] [ + ]
        float doneW = unit * 1.3f;
        editDone.set(width * .5f - doneW / 2, toggleTop, width * .5f + doneW / 2,
                     toggleTop + barHeight);
        editMinus.set(editDone.left - dp(8) - barHeight, toggleTop, editDone.left - dp(8),
                      toggleTop + barHeight);
        editPlus.set(editDone.right + dp(8), toggleTop, editDone.right + dp(8) + barHeight,
                     toggleTop + barHeight);
        sendState();
        invalidate();
    }

    /** Opens the in-game touch controls settings panel. */
    private void openSettings() {
        clearInput();
        TouchControlsEditor.show(getContext(), this::reload, () -> {
            editMode = true;
            selectedId = null;
            invalidate();
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (editMode) {
            for (Control control : controls) control.draw(canvas);
            drawEditOverlay(canvas);
            return;
        }
        drawToggle(canvas);
        if (!controlsVisible) return;
        for (Control control : controls) control.draw(canvas);
    }

    private void drawBarButton(Canvas canvas, RectF rect, String text) {
        fill.setColor(Color.argb(175, 15, 15, 18));
        stroke.setColor(Color.argb(220, 255, 112, 28));
        canvas.drawRoundRect(rect, dp(9), dp(9), fill);
        canvas.drawRoundRect(rect, dp(9), dp(9), stroke);
        drawLabel(canvas, text, rect.centerX(), rect.centerY(), dp(11));
    }

    private void drawEditOverlay(Canvas canvas) {
        canvas.drawColor(Color.argb(70, 0, 0, 0));
        for (Control control : controls) {
            if (control.id != null && control.id.equals(selectedId)) {
                stroke.setColor(Color.argb(255, 255, 112, 28));
                stroke.setStrokeWidth(dp(3));
                RectF b = control.bounds();
                canvas.drawRoundRect(b.left - dp(6), b.top - dp(6), b.right + dp(6),
                                     b.bottom + dp(6), dp(10), dp(10), stroke);
                stroke.setStrokeWidth(dp(2));
            }
        }
        drawBarButton(canvas, editMinus, "-");
        drawBarButton(canvas, editDone, LauncherStrings.text(getContext(), "Done"));
        drawBarButton(canvas, editPlus, "+");
        label.setTextSize(dp(11));
        canvas.drawText(LauncherStrings.text(getContext(), "EDIT LAYOUT") + " - " +
                        LauncherStrings.text(getContext(), "Drag a button to move it"),
                        width() / 2f, editDone.bottom + dp(22), label);
    }

    private float width() { return getWidth(); }

    private void drawToggle(Canvas canvas) {
        fill.setColor(Color.argb(175, 15, 15, 18));
        stroke.setColor(Color.argb(220, 255, 112, 28));
        canvas.drawRoundRect(toggle, dp(9), dp(9), fill);
        canvas.drawRoundRect(toggle, dp(9), dp(9), stroke);
        drawLabel(canvas, controlsVisible ? "HIDE" : "TOUCH", toggle.centerX(),
                  toggle.centerY(), dp(11));
        canvas.drawRoundRect(gear, dp(9), dp(9), fill);
        canvas.drawRoundRect(gear, dp(9), dp(9), stroke);
        drawLabel(canvas, "\u2699", gear.centerX(), gear.centerY(), dp(16));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pointerId = event.getPointerId(index);
        float x = event.getX(index);
        float y = event.getY(index);

        if (editMode) return handleEditTouch(event, action, index, pointerId, x, y);

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (gear.contains(x, y)) {
                openSettings();
                return true;
            }
            if (toggle.contains(x, y)) {
                setControlsVisible(!controlsVisible);
                return true;
            }
            if (!controlsVisible) return false;
            for (int i = controls.size() - 1; i >= 0; --i) {
                Control control = controls.get(i);
                if (!control.inUse && control.contains(x, y)) {
                    pointers.put(pointerId, control);
                    control.press(x, y);
                    sendState();
                    invalidate();
                    return true;
                }
            }
            return false;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            boolean handled = false;
            for (int i = 0; i < event.getPointerCount(); ++i) {
                Control control = pointers.get(event.getPointerId(i));
                if (control != null) {
                    control.move(event.getX(i), event.getY(i));
                    handled = true;
                }
            }
            if (handled) {
                sendState();
                invalidate();
            }
            return handled;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            Control control = pointers.get(pointerId);
            if (control == null) return false;
            control.release();
            pointers.remove(pointerId);
            sendState();
            invalidate();
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            clearInput();
            return true;
        }
        return false;
    }

    private boolean handleEditTouch(MotionEvent event, int action, int index,
                                    int pointerId, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (editDone.contains(x, y)) {
                editMode = false;
                selectedId = null;
                dragPointer = -1;
                invalidate();
                return true;
            }
            if (editMinus.contains(x, y) || editPlus.contains(x, y)) {
                if (selectedId != null) {
                    float delta = editPlus.contains(x, y) ? .1f : -.1f;
                    layout.setScale(selectedId, layout.scale(selectedId) + delta);
                    reload();
                }
                return true;
            }
            for (int i = controls.size() - 1; i >= 0; --i) {
                Control control = controls.get(i);
                if (control.id != null && control.contains(x, y)) {
                    selectedId = control.id;
                    dragPointer = pointerId;
                    dragX = x;
                    dragY = y;
                    invalidate();
                    return true;
                }
            }
            selectedId = null;
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && selectedId != null && dragPointer >= 0) {
            int i = event.findPointerIndex(dragPointer);
            if (i >= 0 && lastWidth > 0 && lastHeight > 0) {
                float mx = event.getX(i), my = event.getY(i);
                layout.setOffset(selectedId,
                    layout.offsetX(selectedId) + (mx - dragX) / lastWidth,
                    layout.offsetY(selectedId) + (my - dragY) / lastHeight);
                dragX = mx;
                dragY = my;
                reload();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP ||
            action == MotionEvent.ACTION_CANCEL) {
            if (pointerId == dragPointer || action == MotionEvent.ACTION_CANCEL) {
                dragPointer = -1;
            }
        }
        return true;
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        preferences.edit().putBoolean(PREF_VISIBLE, visible).apply();
        for (Control control : controls) control.reset();
        pointers.clear();
        if (lastWidth > 0 && lastHeight > 0) buildControls(lastWidth, lastHeight);
        sendState();
        invalidate();
    }

    private void sendState() {
        if (!controlsVisible) {
            TouchControllerBridge.trySetState(-1, 0, 0, 0, 0, 0, 0);
            return;
        }
        int buttons = 0;
        int triggers = 0;
        for (Control control : controls) {
            buttons |= control.buttons();
            triggers |= control.triggers();
        }
        TouchControllerBridge.trySetState(
            buttons,
            leftStick == null ? 0 : leftStick.xValue,
            leftStick == null ? 0 : leftStick.yValue,
            rightStick == null ? 0 : rightStick.xValue,
            rightStick == null ? 0 : rightStick.yValue,
            (triggers & LT_FLAG) != 0 ? 1 : 0,
            (triggers & RT_FLAG) != 0 ? 1 : 0);
    }

    private boolean hasPhysicalController() {
        for (int deviceId : InputDevice.getDeviceIds()) {
            if (SDLControllerManager.isDeviceSDLJoystick(deviceId)) return true;
        }
        return false;
    }

    private abstract class Control {
        boolean inUse;
        String id;
        Control withId(String value) { id = value; return this; }
        abstract boolean contains(float x, float y);
        /** Bounding box, used to highlight the selected control in edit mode. */
        abstract RectF bounds();
        /** Release including any latched state (used when input is cleared). */
        void reset() { release(); }
        /** Analog trigger flags (LT_FLAG / RT_FLAG) currently held. */
        int triggers() { return 0; }
        void press(float x, float y) { inUse = true; move(x, y); }
        void move(float x, float y) {}
        void release() { inUse = false; }
        int buttons() { return 0; }
        abstract void draw(Canvas canvas);
    }

    private final class ButtonControl extends Control {
        final String text;
        final float x;
        final float y;
        final float radius;
        final int mask;
        ButtonControl(String text, float x, float y, float radius, int mask) {
            this.text = text; this.x = x; this.y = y; this.radius = radius; this.mask = mask;
        }
        boolean contains(float px, float py) {
            float dx = px - x, dy = py - y;
            return dx * dx + dy * dy <= radius * radius * 1.35f;
        }
        RectF bounds() { return new RectF(x - radius, y - radius, x + radius, y + radius); }
        int buttons() { return inUse ? mask : 0; }
        void draw(Canvas canvas) {
            drawCircle(canvas, x, y, radius, inUse);
            drawLabel(canvas, text, x, y, radius * .72f);
        }
    }

    /** A user-defined button that presses several inputs together. */
    private final class MacroControl extends Control {
        final Macro macro;
        final float x;
        final float y;
        final float radius;
        boolean latched;
        MacroControl(Macro macro, float x, float y, float radius) {
            this.macro = macro; this.x = x; this.y = y; this.radius = radius;
        }
        boolean contains(float px, float py) {
            float dx = px - x, dy = py - y;
            return dx * dx + dy * dy <= radius * radius * 1.35f;
        }
        RectF bounds() { return new RectF(x - radius, y - radius, x + radius, y + radius); }
        boolean active() { return macro.latch ? latched : inUse; }
        void press(float px, float py) {
            super.press(px, py);
            if (macro.latch) latched = !latched;
        }
        void reset() { release(); latched = false; }
        int buttons() { return active() ? macro.mask & 0xFFFF : 0; }
        int triggers() { return active() ? macro.mask & (LT_FLAG | RT_FLAG) : 0; }
        void draw(Canvas canvas) {
            drawCircle(canvas, x, y, radius, active());
            drawLabel(canvas, macro.label, x, y, radius * (macro.label.length() > 3 ? .45f : .6f));
        }
    }

    private final class RectButton extends Control {
        final String text;
        final RectF bounds;
        final int mask;
        final int triggerFlag;
        RectButton(String text, float x, float y, float width, float height,
                   int mask, int triggerFlag) {
            this.text = text;
            this.bounds = new RectF(x - width / 2, y - height / 2,
                                    x + width / 2, y + height / 2);
            this.mask = mask;
            this.triggerFlag = triggerFlag;
        }
        boolean contains(float x, float y) { return bounds.contains(x, y); }
        RectF bounds() { return bounds; }
        int buttons() { return inUse ? mask : 0; }
        int triggers() { return inUse ? triggerFlag : 0; }
        void draw(Canvas canvas) {
            fill.setColor(inUse ? Color.argb(190, 255, 112, 28) : Color.argb(105, 12, 12, 15));
            stroke.setColor(Color.argb(205, 255, 255, 255));
            canvas.drawRoundRect(bounds, dp(10), dp(10), fill);
            canvas.drawRoundRect(bounds, dp(10), dp(10), stroke);
            drawLabel(canvas, text, bounds.centerX(), bounds.centerY(),
                      Math.min(dp(11) * Math.max(1f, bounds.height() / dp(34)), bounds.height() * .6f));
        }
    }

    private final class Stick extends Control {
        final String text;
        final float centerX;
        final float centerY;
        final float radius;
        final boolean left;
        float xValue;
        float yValue;
        Stick(String text, float x, float y, float radius, boolean left) {
            this.text = text; this.centerX = x; this.centerY = y;
            this.radius = radius; this.left = left;
        }
        boolean contains(float x, float y) {
            float dx = x - centerX, dy = y - centerY;
            return dx * dx + dy * dy <= radius * radius * 1.55f;
        }
        RectF bounds() {
            return new RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        }
        void move(float x, float y) {
            float dx = (x - centerX) / radius;
            float dy = (y - centerY) / radius;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length > 1) { dx /= length; dy /= length; }
            float deadzone = .08f;
            xValue = Math.abs(dx) < deadzone ? 0 : dx;
            yValue = Math.abs(dy) < deadzone ? 0 : -dy;
        }
        void release() { super.release(); xValue = 0; yValue = 0; }
        void draw(Canvas canvas) {
            drawCircle(canvas, centerX, centerY, radius, false);
            float knobX = centerX + xValue * radius * .58f;
            float knobY = centerY - yValue * radius * .58f;
            drawCircle(canvas, knobX, knobY, radius * .43f, inUse);
            drawLabel(canvas, text, knobX, knobY, radius * .34f);
        }
    }

    private final class Dpad extends Control {
        final float centerX;
        final float centerY;
        final float radius;
        int mask;
        Dpad(float x, float y, float radius) { centerX = x; centerY = y; this.radius = radius; }
        boolean contains(float x, float y) {
            return Math.abs(x - centerX) <= radius && Math.abs(y - centerY) <= radius;
        }
        RectF bounds() {
            return new RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        }
        void move(float x, float y) {
            float dx = x - centerX, dy = y - centerY;
            float threshold = radius * .20f;
            mask = 0;
            if (dx < -threshold) mask |= DPAD_LEFT;
            if (dx > threshold) mask |= DPAD_RIGHT;
            if (dy < -threshold) mask |= DPAD_UP;
            if (dy > threshold) mask |= DPAD_DOWN;
        }
        void release() { super.release(); mask = 0; }
        int buttons() { return inUse ? mask : 0; }
        void draw(Canvas canvas) {
            float arm = radius * .36f;
            fill.setColor(inUse ? Color.argb(180, 255, 112, 28) : Color.argb(105, 12, 12, 15));
            stroke.setColor(Color.argb(205, 255, 255, 255));
            RectF vertical = new RectF(centerX - arm, centerY - radius,
                                       centerX + arm, centerY + radius);
            RectF horizontal = new RectF(centerX - radius, centerY - arm,
                                         centerX + radius, centerY + arm);
            canvas.drawRoundRect(vertical, dp(6), dp(6), fill);
            canvas.drawRoundRect(horizontal, dp(6), dp(6), fill);
            canvas.drawRoundRect(vertical, dp(6), dp(6), stroke);
            canvas.drawRoundRect(horizontal, dp(6), dp(6), stroke);
        }
    }

    private void drawCircle(Canvas canvas, float x, float y, float radius, boolean pressed) {
        fill.setColor(pressed ? Color.argb(190, 255, 112, 28) : Color.argb(105, 12, 12, 15));
        stroke.setColor(Color.argb(205, 255, 255, 255));
        canvas.drawCircle(x, y, radius, fill);
        canvas.drawCircle(x, y, radius, stroke);
    }

    private void drawLabel(Canvas canvas, String text, float x, float y, float size) {
        label.setTextSize(size);
        Paint.FontMetrics metrics = label.getFontMetrics();
        canvas.drawText(text, x, y - (metrics.ascent + metrics.descent) / 2, label);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
