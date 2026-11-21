package dev.pgr4port.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.widget.LinearLayout;
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
        buildTopArc();
        buildRightSideControls();

        // Always keep a compact circular D-Pad on the left upper side for crisp menu navigation
        RoundDpadView dpad = new RoundDpadView(context);
        LayoutParams dpadLp = new LayoutParams(dp(94), dp(94), Gravity.TOP | Gravity.LEFT);
        dpadLp.leftMargin = dp(16);
        dpadLp.topMargin = dp(72);
        addView(dpad, dpadLp);

        leftSteerHost = new FrameLayout(context);
        addView(leftSteerHost, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT));
        rebuildLeftSteering();
        setOpacityPercent(config.touchOpacity);
    }

    private void buildTopArc() {
        // LB & RB tucked at upper outer corners
        addCircleButton("LB", LB, Gravity.TOP | Gravity.LEFT, 14, 12, 44, 0x9912161E, 0x55FFFFFF, 12);
        addCircleButton("RB", RB, Gravity.TOP | Gravity.RIGHT, 14, 12, 44, 0x9912161E, 0x55FFFFFF, 12);

        // Symmetric top-center bar (under the compact FPS pill when enabled)
        int topCenterY = config.hudEnabled ? 42 : 12;
        LinearLayout topBar = new LinearLayout(getContext());
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView backBtn = makeBoundCircleButton("BACK", BACK, 42, 0x9912161E, 0x44FFFFFF, 10);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        bLp.rightMargin = dp(24);
        topBar.addView(backBtn, bLp);

        modeToggleBtn = makeCircleView(modeIcon(), 0x9912161E, 0x88E11D2A, 10);
        modeToggleBtn.setOnClickListener(v -> cycleSteeringMode());
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        mLp.rightMargin = dp(24);
        topBar.addView(modeToggleBtn, mLp);

        TextView startBtn = makeBoundCircleButton("START", START, 42, 0x9912161E, 0x44FFFFFF, 10);
        topBar.addView(startBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LayoutParams barLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        barLp.topMargin = dp(topCenterY);
        addView(topBar, barLp);
    }

    private TextView makeBoundCircleButton(String text, int bit, int sizeDp,
                                           int fillColor, int strokeColor, int spSize) {
        TextView button = makeCircleView(text, fillColor, strokeColor, spSize);
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    pressed |= bit;
                    view.setBackground(makeCircleDrawable(strokeColor, Color.WHITE));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    pressed &= ~bit;
                    view.setBackground(makeCircleDrawable(fillColor, strokeColor));
                    break;
                default:
                    return false;
            }
            publish();
            return true;
        });
        return button;
    }

    private void buildRightSideControls() {
        int lowerRight = Gravity.BOTTOM | Gravity.RIGHT;

        // Racing pedals at the bottom-right corner with horizontal grip grooves
        PedalView gas = new PedalView(getContext(), THROTTLE, "RT", 0x99103B24, 0xCC22C55E, 0xFF22C55E);
        LayoutParams gasLp = new LayoutParams(dp(64), dp(136), lowerRight);
        gasLp.rightMargin = dp(14);
        gasLp.bottomMargin = dp(14);
        addView(gas, gasLp);

        PedalView brake = new PedalView(getContext(), BRAKE, "LT", 0x994A141C, 0xCCE11D2A, 0xFFE11D2A);
        LayoutParams brakeLp = new LayoutParams(dp(56), dp(96), lowerRight);
        brakeLp.rightMargin = dp(88);
        brakeLp.bottomMargin = dp(14);
        addView(brake, brakeLp);

        // Classic Xbox diamond (Y top, X left, B right, A bottom) cleanly above pedals
        addCircleButton("Y", Y, lowerRight, 66, 260, 46, 0x9926200D, 0xCCF5B82E, 15);
        addCircleButton("X", X, lowerRight, 114, 214, 46, 0x990F2238, 0xCC3B82F6, 15);
        addCircleButton("B", B, lowerRight, 18, 214, 46, 0x99381118, 0xCCE11D2A, 15);
        addCircleButton("A", A, lowerRight, 66, 168, 46, 0x990F3320, 0xCC22C55E, 15);
    }

    private void rebuildLeftSteering() {
        leftSteerHost.removeAllViews();
        stickX = 0;
        stickY = 0;
        gyroX = 0;
        gyroView = null;

        if (Pgr4Config.STEER_GYRO.equals(steeringMode)) {
            gyroView = new GyroIndicatorView(getContext());
            LayoutParams gLp = new LayoutParams(dp(140), dp(46), Gravity.BOTTOM | Gravity.LEFT);
            gLp.leftMargin = dp(16);
            gLp.bottomMargin = dp(20);
            leftSteerHost.addView(gyroView, gLp);
        } else {
            AnalogStickView stick = new AnalogStickView(getContext());
            LayoutParams lp = new LayoutParams(dp(136), dp(136), Gravity.BOTTOM | Gravity.LEFT);
            lp.leftMargin = dp(16);
            lp.bottomMargin = dp(16);
            leftSteerHost.addView(stick, lp);
        }

        if (modeToggleBtn != null) {
            modeToggleBtn.setText(modeIcon());
        }
        updateSensorRegistration();
        publish();
    }

    private String modeIcon() {
        return Pgr4Config.STEER_GYRO.equals(steeringMode) ? "GYRO" : "STICK";
    }

    private void cycleSteeringMode() {
        steeringMode = Pgr4Config.STEER_GYRO.equals(steeringMode)
                ? Pgr4Config.STEER_STICK : Pgr4Config.STEER_GYRO;
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
        if (!Pgr4Config.STEER_GYRO.equals(steeringMode)
                || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
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
        gyroX = Math.round(gyroX * 0.45f + target * 0.55f);
        if (gyroView != null) {
            gyroView.setValue(gyroX / 32767.0f);
        }
        publish();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private TextView makeCircleView(String text, int fillColor, int strokeColor, int spSize) {
        TextView btn = new TextView(getContext());
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(makeCircleDrawable(fillColor, strokeColor));
        btn.setFocusable(false);
        return btn;
    }

    private void addCircleButton(String text, int bit, int gravity,
                                 int horizontalMarginDp, int verticalMarginDp,
                                 int sizeDp, int fillColor, int strokeColor, int spSize) {
        TextView button = makeCircleView(text, fillColor, strokeColor, spSize);
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    pressed |= bit;
                    view.setBackground(makeCircleDrawable(strokeColor, Color.WHITE));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    pressed &= ~bit;
                    view.setBackground(makeCircleDrawable(fillColor, strokeColor));
                    break;
                default:
                    return false;
            }
            publish();
            return true;
        });

        LayoutParams params = new LayoutParams(dp(sizeDp), dp(sizeDp), gravity);
        if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
            params.leftMargin = dp(horizontalMarginDp);
        } else if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) {
            params.rightMargin = dp(horizontalMarginDp);
        } else {
            params.leftMargin = dp(horizontalMarginDp);
        }
        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.TOP) {
            params.topMargin = dp(verticalMarginDp);
        } else {
            params.bottomMargin = dp(verticalMarginDp);
        }
        addView(button, params);
    }

    private GradientDrawable makeCircleDrawable(int fill, int stroke) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(fill);
        gd.setStroke(dp(2), stroke);
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
        if (Pgr4Config.STEER_GYRO.equals(steeringMode)) {
            finalX = dpadX != 0 ? dpadX : gyroX;
            finalY = 0;
        } else {
            finalX = stickX != 0 ? stickX : dpadX;
            finalY = stickY;
        }
        nativeSetState(pressed & 0xffff, finalX, finalY,
                (pressed & BRAKE) == 0 ? 0 : 255,
                (pressed & THROTTLE) == 0 ? 0 : 255);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class PedalView extends View {
        private final int bit;
        private final String label;
        private final int baseFill;
        private final int border;
        private final int activeFill;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint groovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean down;

        PedalView(Context context, int bit, String label, int baseFill, int border, int activeFill) {
            super(context);
            this.bit = bit;
            this.label = label;
            this.baseFill = baseFill;
            this.border = border;
            this.activeFill = activeFill;

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(2));
            borderPaint.setColor(border);

            groovePaint.setStyle(Paint.Style.STROKE);
            groovePaint.setStrokeWidth(dp(2));
            groovePaint.setStrokeCap(Paint.Cap.ROUND);
            groovePaint.setColor(0x88FFFFFF);

            textPaint.setColor(0xCCFFFFFF);
            textPaint.setTextSize(dp(11));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float r = dp(12);
            fillPaint.setColor(down ? activeFill : baseFill);
            canvas.drawRoundRect(dp(1), dp(1), w - dp(1), h - dp(1), r, r, fillPaint);
            canvas.drawRoundRect(dp(1), dp(1), w - dp(1), h - dp(1), r, r, borderPaint);

            // Racing pedal grip ribs
            float gx1 = w * 0.25f;
            float gx2 = w * 0.75f;
            int ribs = h > dp(110) ? 5 : 3;
            float startY = h * 0.34f;
            float stepY = dp(12);
            for (int i = 0; i < ribs; i++) {
                float y = startY + i * stepY;
                canvas.drawLine(gx1, y, gx2, y, groovePaint);
            }
            canvas.drawText(label, w * 0.5f, h * 0.20f, textPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    down = true;
                    pressed |= bit;
                    invalidate();
                    publish();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    down = false;
                    pressed &= ~bit;
                    invalidate();
                    publish();
                    return true;
                default:
                    return false;
            }
        }
    }

    private final class RoundDpadView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint activeArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int activeDir = 0;

        RoundDpadView(Context context) {
            super(context);
            bgPaint.setColor(0x8812161E);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(2));
            ringPaint.setColor(0x55FFFFFF);
            arrowPaint.setColor(0xBBFFFFFF);
            activeArrowPaint.setColor(0xFFE11D2A);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float r = Math.min(cx, cy) - dp(2);
            canvas.drawCircle(cx, cy, r, bgPaint);
            canvas.drawCircle(cx, cy, r, ringPaint);

            float d = r * 0.62f;
            float s = dp(6);
            drawTri(canvas, cx, cy - d, 0, s, (activeDir & UP) != 0);
            drawTri(canvas, cx + d, cy, 90, s, (activeDir & RIGHT) != 0);
            drawTri(canvas, cx, cy + d, 180, s, (activeDir & DOWN) != 0);
            drawTri(canvas, cx - d, cy, 270, s, (activeDir & LEFT) != 0);
        }

        private void drawTri(Canvas c, float x, float y, float deg, float s, boolean active) {
            c.save();
            c.translate(x, y);
            c.rotate(deg);
            Path p = new Path();
            p.moveTo(0, -s);
            p.lineTo(s * 0.85f, s * 0.65f);
            p.lineTo(-s * 0.85f, s * 0.65f);
            p.close();
            c.drawPath(p, active ? activeArrowPaint : arrowPaint);
            c.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - cx;
                    float dy = event.getY() - cy;
                    int dir = 0;
                    if (Math.hypot(dx, dy) > dp(8)) {
                        if (Math.abs(dx) > Math.abs(dy)) {
                            dir = dx > 0 ? RIGHT : LEFT;
                        } else {
                            dir = dy > 0 ? DOWN : UP;
                        }
                    }
                    pressed = (pressed & ~(UP | DOWN | LEFT | RIGHT)) | dir;
                    activeDir = dir;
                    invalidate();
                    publish();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    pressed &= ~(UP | DOWN | LEFT | RIGHT);
                    activeDir = 0;
                    invalidate();
                    publish();
                    return true;
                default:
                    return false;
            }
        }
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
                    float dy = (cy - event.getY()) / maxTravel;
                    float mag = (float) Math.hypot(dx, dy);
                    if (mag > 1.0f) {
                        dx /= mag;
                        dy /= mag;
                    }
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
