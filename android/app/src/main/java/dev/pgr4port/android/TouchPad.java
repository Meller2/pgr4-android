package dev.pgr4port.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

final class TouchPad extends FrameLayout implements SensorEventListener {
    private static final int UP = 0x0001;
    private static final int DOWN = 0x0002;
    private static final int LEFT = 0x0004;
    private static final int RIGHT = 0x0008;
    private static final int START = 0x0010;
    private static final int BACK = 0x0020;
    private static final int LB = 0x0100;
    private static final int RB = 0x0200;
    private static final int A = 0x1000;
    private static final int B = 0x2000;
    private static final int X = 0x4000;
    private static final int Y = 0x8000;
    private static final int BRAKE = 0x10000;
    private static final int THROTTLE = 0x20000;

    private final Pgr4Config config;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;

    private int pressed;
    private int stickX;
    private int stickY;
    private int gyroX;
    private String steeringMode;

    private FrameLayout leftSteerHost;
    private TextView modeToggleBtn;
    private GyroIndicatorView gyroView;
    private boolean resumed;

    TouchPad(Context context, Pgr4Config config) {
        super(context);
        this.config = config;
        this.steeringMode = config.steeringMode != null ? config.steeringMode : Pgr4Config.STEER_STICK;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;

        setMotionEventSplittingEnabled(true);
        buildRightSideControls();

        leftSteerHost = new FrameLayout(context);
        addView(leftSteerHost, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT));
        rebuildLeftSteering();
        setOpacityPercent(config.touchOpacity);
    }

    private void buildRightSideControls() {
        int lowerRight = Gravity.BOTTOM | Gravity.RIGHT;
        int upperRight = Gravity.TOP | Gravity.RIGHT;

        // Keep Start & A positions compatible with automated benchmark touch points.
        addPadButton("Start", START, upperRight, 14, 14, 64, 44, 0xCC1E2430, 0x44FFFFFF, 12);
        addPadButton("Back", BACK, upperRight, 14, 64, 52, 34, 0xB8181D27, 0x33FFFFFF, 11);
        addPadButton("RB", RB, upperRight, 72, 64, 44, 34, 0xB8181D27, 0x33FFFFFF, 11);
        addPadButton("LB", LB, upperRight, 122, 64, 44, 34, 0xB8181D27, 0x33FFFFFF, 11);

        // Quick steering mode switcher right next to LB
        modeToggleBtn = makePillView(modeLabel(), 0xB8181D27, 0x55E11D2A, 11);
        modeToggleBtn.setOnClickListener(v -> cycleSteeringMode());
        LayoutParams modeLp = new LayoutParams(dp(58), dp(34), upperRight);
        modeLp.rightMargin = dp(172);
        modeLp.topMargin = dp(64);
        addView(modeToggleBtn, modeLp);

        // Primary face buttons (A kept at right:14, bottom:110; B at right:74, bottom:110)
        addPadButton("A", A, lowerRight, 14, 110, 52, 50, 0xCC164A2C, 0xAA22C55E, 15);
        addPadButton("B", B, lowerRight, 74, 110, 52, 50, 0xCC4A161D, 0xAAE11D2A, 15);
        // Handbrake (X) & Camera (Y) above B/A
        addPadButton("Y", Y, lowerRight, 14, 166, 52, 42, 0xB84A3B12, 0xAAF5B82E, 14);
        addPadButton("X", X, lowerRight, 74, 166, 52, 42, 0xB8162F4A, 0xAA3B82F6, 14);

        // Gas (RT) & Brake (LT) pedals neatly tucked at the bottom-right corner
        addPadButton("RT", THROTTLE, lowerRight, 14, 16, 54, 86, 0xD019522F, 0xCC22C55E, 14);
        addPadButton("LT", BRAKE, lowerRight, 76, 16, 54, 86, 0xD05C1922, 0xCCE11D2A, 14);
    }

    private void rebuildLeftSteering() {
        leftSteerHost.removeAllViews();
        stickX = 0;
        stickY = 0;
        gyroX = 0;
        gyroView = null;

        if (Pgr4Config.STEER_STICK.equals(steeringMode)) {
            AnalogStickView stick = new AnalogStickView(getContext());
            LayoutParams lp = new LayoutParams(dp(146), dp(146), Gravity.BOTTOM | Gravity.LEFT);
            lp.leftMargin = dp(14);
            lp.bottomMargin = dp(16);
            leftSteerHost.addView(stick, lp);
        } else if (Pgr4Config.STEER_GYRO.equals(steeringMode)) {
            LinearLayoutColBuilder box = new LinearLayoutColBuilder(getContext());
            gyroView = new GyroIndicatorView(getContext());
            LayoutParams gLp = new LayoutParams(dp(136), dp(46), Gravity.BOTTOM | Gravity.LEFT);
            gLp.leftMargin = dp(14);
            gLp.bottomMargin = dp(16);
            leftSteerHost.addView(gyroView, gLp);

            // Compact D-pad Up/Down for menu navigation while in Gyro mode
            addPadButtonTo(leftSteerHost, "↑", UP, Gravity.BOTTOM | Gravity.LEFT, 14, 122, 48, 44);
            addPadButtonTo(leftSteerHost, "↓", DOWN, Gravity.BOTTOM | Gravity.LEFT, 14, 72, 48, 44);
            addPadButtonTo(leftSteerHost, "←", LEFT, Gravity.BOTTOM | Gravity.LEFT, 70, 72, 40, 44);
            addPadButtonTo(leftSteerHost, "→", RIGHT, Gravity.BOTTOM | Gravity.LEFT, 114, 72, 40, 44);
            box.unused();
        } else {
            addPadButtonTo(leftSteerHost, "↑", UP, Gravity.BOTTOM | Gravity.LEFT, 68, 114, 52, 50);
            addPadButtonTo(leftSteerHost, "←", LEFT, Gravity.BOTTOM | Gravity.LEFT, 12, 56, 52, 52);
            addPadButtonTo(leftSteerHost, "↓", DOWN, Gravity.BOTTOM | Gravity.LEFT, 68, 56, 52, 50);
            addPadButtonTo(leftSteerHost, "→", RIGHT, Gravity.BOTTOM | Gravity.LEFT, 124, 56, 52, 52);
        }

        if (modeToggleBtn != null) {
            modeToggleBtn.setText(modeLabel());
        }
        updateSensorRegistration();
        publish();
    }

    private String modeLabel() {
        if (Pgr4Config.STEER_GYRO.equals(steeringMode)) return "Gyro";
        if (Pgr4Config.STEER_BUTTONS.equals(steeringMode)) return "D-Pad";
        return "Stick";
    }

    private void cycleSteeringMode() {
        if (Pgr4Config.STEER_STICK.equals(steeringMode)) {
            steeringMode = Pgr4Config.STEER_GYRO;
        } else if (Pgr4Config.STEER_GYRO.equals(steeringMode)) {
            steeringMode = Pgr4Config.STEER_BUTTONS;
        } else {
            steeringMode = Pgr4Config.STEER_STICK;
        }
        config.steeringMode = steeringMode;
        config.savePrefsOnly();
        rebuildLeftSteering();
    }

    void resume() {
        resumed = true;
        updateSensorRegistration();
    }

    void pause() {
        resumed = false;
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        reset();
    }

    private void updateSensorRegistration() {
        if (sensorManager == null) return;
        sensorManager.unregisterListener(this);
        if (resumed && Pgr4Config.STEER_GYRO.equals(steeringMode) && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!Pgr4Config.STEER_GYRO.equals(steeringMode) || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }
        float ax = event.values[0];
        float ay = event.values[1];

        int rotation = Surface.ROTATION_90;
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            Display d = wm.getDefaultDisplay();
            if (d != null) rotation = d.getRotation();
        }
        // Horizontal tilt axis in landscape
        float tilt = (rotation == Surface.ROTATION_270) ? -ay : ay;
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            tilt = (rotation == Surface.ROTATION_180) ? ax : -ax;
        }

        float sens = config.gyroSensitivity / 100.0f;
        float normalized = (tilt / 5.5f) * sens;
        if (Math.abs(normalized) < 0.06f) {
            normalized = 0f;
        } else {
            normalized = Math.max(-1.0f, Math.min(1.0f, normalized));
        }
        int target = Math.round(normalized * 32767f);
        // Smooth low-pass filter
        gyroX = Math.round(gyroX * 0.45f + target * 0.55f);
        if (gyroView != null) {
            gyroView.setValue(gyroX / 32767.0f);
        }
        publish();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private TextView makePillView(String text, int fillColor, int strokeColor, int spSize) {
        TextView btn = new TextView(getContext());
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(makeKeyDrawable(fillColor, strokeColor, 14));
        btn.setFocusable(false);
        return btn;
    }

    private void addPadButton(String text, int bit, int gravity,
                              int horizontalMargin, int verticalMargin,
                              int widthDp, int heightDp,
                              int fillColor, int strokeColor, int spSize) {
        TextView button = makePillView(text, fillColor, strokeColor, spSize);
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    pressed |= bit;
                    view.setBackground(makeKeyDrawable(0xFFE11D2A, 0xFFFFFFFF, 14));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    pressed &= ~bit;
                    view.setBackground(makeKeyDrawable(fillColor, strokeColor, 14));
                    break;
                default:
                    return false;
            }
            publish();
            return true;
        });

        LayoutParams params = new LayoutParams(dp(widthDp), dp(heightDp), gravity);
        if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) {
            params.rightMargin = dp(horizontalMargin);
        } else {
            params.leftMargin = dp(horizontalMargin);
        }
        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.TOP) {
            params.topMargin = dp(verticalMargin);
        } else {
            params.bottomMargin = dp(verticalMargin);
        }
        addView(button, params);
    }

    private void addPadButtonTo(FrameLayout host, String text, int bit, int gravity,
                                int leftDp, int bottomDp, int wDp, int hDp) {
        int fill = 0xC0181D27;
        int stroke = 0x38FFFFFF;
        TextView button = makePillView(text, fill, stroke, 15);
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressed |= bit;
                    view.setBackground(makeKeyDrawable(0xFFE11D2A, 0xFFFFFFFF, 14));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    pressed &= ~bit;
                    view.setBackground(makeKeyDrawable(fill, stroke, 14));
                    break;
                default:
                    return false;
            }
            publish();
            return true;
        });
        LayoutParams lp = new LayoutParams(dp(wDp), dp(hDp), gravity);
        lp.leftMargin = dp(leftDp);
        lp.bottomMargin = dp(bottomDp);
        host.addView(button, lp);
    }

    private GradientDrawable makeKeyDrawable(int fill, int stroke, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(dp(radiusDp));
        gd.setStroke(dp(1), stroke);
        return gd;
    }

    void setOpacityPercent(int percent) {
        int clamped = Math.max(15, Math.min(100, percent));
        setAlpha(clamped / 100.0f);
    }

    void reset() {
        pressed = 0;
        stickX = 0;
        stickY = 0;
        gyroX = 0;
        publish();
    }

    private void publish() {
        int dpadX = ((pressed & RIGHT) != 0 ? 32767 : 0) - ((pressed & LEFT) != 0 ? 32767 : 0);
        int finalX;
        int finalY;
        if (Pgr4Config.STEER_STICK.equals(steeringMode)) {
            finalX = stickX != 0 ? stickX : dpadX;
            finalY = stickY;
        } else if (Pgr4Config.STEER_GYRO.equals(steeringMode)) {
            finalX = dpadX != 0 ? dpadX : gyroX;
            finalY = 0;
        } else {
            finalX = dpadX;
            finalY = 0;
        }
        nativeSetState(pressed & 0xffff, finalX, finalY,
                (pressed & BRAKE) == 0 ? 0 : 255,
                (pressed & THROTTLE) == 0 ? 0 : 255);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class LinearLayoutColBuilder {
        LinearLayoutColBuilder(Context c) {}
        void unused() {}
    }

    private final class AnalogStickView extends View {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knobStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float normX;
        private float normY;
        private boolean active;

        AnalogStickView(Context context) {
            super(context);
            fillPaint.setColor(0x8811151E);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(2));
            ringPaint.setColor(0x55FFFFFF);
            knobPaint.setColor(0xDDE11D2A);
            knobStroke.setStyle(Paint.Style.STROKE);
            knobStroke.setStrokeWidth(dp(2));
            knobStroke.setColor(0xCCFFFFFF);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float outerR = Math.min(cx, cy) - dp(4);
            float knobR = outerR * 0.34f;
            float maxTravel = outerR - knobR;

            canvas.drawCircle(cx, cy, outerR, fillPaint);
            canvas.drawCircle(cx, cy, outerR, ringPaint);

            float kx = cx + normX * maxTravel;
            float ky = cy - normY * maxTravel;
            knobPaint.setColor(active ? 0xFFF43F5E : 0xBBE11D2A);
            canvas.drawCircle(kx, ky, knobR, knobPaint);
            canvas.drawCircle(kx, ky, knobR, knobStroke);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float outerR = Math.min(cx, cy) - dp(4);
            float maxTravel = Math.max(1f, outerR * 0.66f);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    active = true;
                    float dx = (event.getX() - cx) / maxTravel;
                    float dy = (cy - event.getY()) / maxTravel; // up is positive in XInput
                    float mag = (float) Math.hypot(dx, dy);
                    if (mag > 1.0f) {
                        dx /= mag;
                        dy /= mag;
                    }
                    // Tiny deadzone in the very center
                    if (Math.abs(dx) < 0.08f) dx = 0f;
                    if (Math.abs(dy) < 0.08f) dy = 0f;
                    normX = dx;
                    normY = dy;
                    stickX = Math.round(normX * 32767f);
                    stickY = Math.round(normY * 32767f);
                    invalidate();
                    publish();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    active = false;
                    normX = 0f;
                    normY = 0f;
                    stickX = 0;
                    stickY = 0;
                    invalidate();
                    publish();
                    return true;
                default:
                    return false;
            }
        }
    }

    private final class GyroIndicatorView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float val;

        GyroIndicatorView(Context context) {
            super(context);
            bgPaint.setColor(0x9912161E);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(1));
            borderPaint.setColor(0x44FFFFFF);
            dotPaint.setColor(0xFFE11D2A);
        }

        void setValue(float v) {
            this.val = Math.max(-1f, Math.min(1f, v));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float r = dp(12);
            canvas.drawRoundRect(0, 0, w, h, r, r, bgPaint);
            canvas.drawRoundRect(0, 0, w, h, r, r, borderPaint);
            float pad = dp(14);
            float cx = w * 0.5f + val * (w * 0.5f - pad);
            canvas.drawCircle(cx, h * 0.5f, dp(8), dotPaint);
        }
    }

    private static native void nativeSetState(int buttons, int leftX, int leftY, int brake, int throttle);
}
