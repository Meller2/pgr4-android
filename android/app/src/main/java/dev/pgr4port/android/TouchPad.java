package dev.pgr4port.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;

final class TouchPad extends FrameLayout {
    private static final int UP = 0x0001;
    private static final int DOWN = 0x0002;
    private static final int LEFT = 0x0004;
    private static final int RIGHT = 0x0008;
    private static final int START = 0x0010;
    private static final int A = 0x1000;
    private static final int B = 0x2000;
    private static final int BRAKE = 0x10000;
    private static final int THROTTLE = 0x20000;
    private int pressed;

    TouchPad(Context context) {
        super(context);
        setMotionEventSplittingEnabled(true);
        int lowerLeft = Gravity.BOTTOM | Gravity.LEFT;
        int lowerRight = Gravity.BOTTOM | Gravity.RIGHT;
        addButton("↑", "Up", UP, lowerLeft, 68, 110);
        addButton("←", "Left", LEFT, lowerLeft, 14, 56);
        addButton("↓", "Down", DOWN, lowerLeft, 68, 56);
        addButton("→", "Right", RIGHT, lowerLeft, 122, 56);
        addButton("A", "Confirm", A, lowerRight, 14, 110);
        addButton("B", "Back", B, lowerRight, 68, 110);
        addButton("RT", "Accelerate", THROTTLE, lowerRight, 14, 56);
        addButton("LT", "Brake", BRAKE, lowerRight, 68, 56);
        addButton("Start", "Start", START, Gravity.TOP | Gravity.RIGHT, 14, 14);
    }

    private void addButton(String text, String description, int bit, int gravity,
                           int horizontalMargin, int verticalMargin) {
        Button button = new Button(getContext());
        button.setText(text);
        button.setContentDescription(description);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundTintList(ColorStateList.valueOf(0xA0303030));
        button.setFocusable(false);
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressed |= bit;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    pressed &= ~bit;
                    break;
                default:
                    return false;
            }
            publish();
            return false;
        });

        LayoutParams params = new LayoutParams(dp(bit == START ? 64 : 52), dp(50), gravity);
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

    void reset() {
        pressed = 0;
        publish();
    }

    private void publish() {
        int leftX = ((pressed & RIGHT) != 0 ? 32767 : 0) - ((pressed & LEFT) != 0 ? 32767 : 0);
        nativeSetState(pressed & 0xffff, leftX,
                (pressed & BRAKE) == 0 ? 0 : 255, (pressed & THROTTLE) == 0 ? 0 : 255);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static native void nativeSetState(int buttons, int leftX, int brake, int throttle);
}
