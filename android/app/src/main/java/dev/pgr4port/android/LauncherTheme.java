package dev.pgr4port.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

final class LauncherTheme {
    static final int BG_DARK = 0xFF0A0C10;
    static final int CARD_GLASS = 0xD412161E;
    static final int CARD_ELEVATED = 0xE6181E2A;
    static final int CARD_INNER = 0xB80E1219;
    static final int BORDER_SUBTLE = 0x24FFFFFF;
    static final int BORDER_ACTIVE = 0xCCE11D2A;
    static final int RED_PRIMARY = 0xFFE11D2A;
    static final int RED_GLOW = 0xFFFF3342;
    static final int RED_DARK = 0xFFA30E18;
    static final int GOLD_WARN = 0xFFF5B82E;
    static final int GREEN_READY = 0xFF22C55E;
    static final int TEXT_PRIMARY = 0xFFF5F7FA;
    static final int TEXT_SECONDARY = 0xFFB4BDCB;
    static final int TEXT_MUTED = 0xFF7B8596;

    static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    static Drawable cardBg(Context ctx, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(active ? CARD_ELEVATED : CARD_GLASS);
        gd.setCornerRadius(dp(ctx, 16));
        gd.setStroke(dp(ctx, 1), active ? BORDER_ACTIVE : BORDER_SUBTLE);
        return gd;
    }

    static Drawable innerCardBg(Context ctx, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(active ? 0x40E11D2A : CARD_INNER);
        gd.setCornerRadius(dp(ctx, 12));
        gd.setStroke(dp(ctx, 1), active ? BORDER_ACTIVE : 0x1AFFFFFF);
        return gd;
    }

    static Drawable primaryButtonBg(Context ctx) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{RED_GLOW, RED_PRIMARY, RED_DARK});
        gd.setCornerRadius(dp(ctx, 14));
        gd.setStroke(dp(ctx, 1), 0x44FFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), gd, null);
    }

    static Drawable secondaryButtonBg(Context ctx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xCC1A202B);
        gd.setCornerRadius(dp(ctx, 12));
        gd.setStroke(dp(ctx, 1), 0x2EFFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), gd, null);
    }

    static Drawable pillBg(Context ctx, boolean selected) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(selected ? 0xFFE11D2A : 0xB8171C26);
        gd.setCornerRadius(dp(ctx, 20));
        gd.setStroke(dp(ctx, 1), selected ? 0xFFFF5C67 : 0x26FFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), gd, null);
    }

    static Drawable navItemBg(Context ctx, boolean selected) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(selected ? 0x38E11D2A : Color.TRANSPARENT);
        gd.setCornerRadius(dp(ctx, 12));
        gd.setStroke(dp(ctx, 1), selected ? 0x99E11D2A : Color.TRANSPARENT);
        return new RippleDrawable(ColorStateList.valueOf(0x28FFFFFF), gd, null);
    }

    static Drawable dotDrawable(Context ctx, int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        int size = dp(ctx, 8);
        gd.setSize(size, size);
        return gd;
    }

    static LinearLayout makeCard(Context ctx, boolean active) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(cardBg(ctx, active));
        int p = dp(ctx, 16);
        box.setPadding(p, p, p, p);
        return box;
    }

    static TextView makeTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(TEXT_PRIMARY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    static TextView makeSubtitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(TEXT_SECONDARY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setLineSpacing(0, 1.2f);
        return tv;
    }

    static TextView makeCaption(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(TEXT_MUTED);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.08f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    static TextView makePrimaryButton(Context ctx, String text, View.OnClickListener onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setLetterSpacing(0.06f);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(primaryButtonBg(ctx));
        btn.setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 14));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setOnClickListener(onClick);
        return btn;
    }

    static TextView makeSecondaryButton(Context ctx, String text, View.OnClickListener onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(TEXT_PRIMARY);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(secondaryButtonBg(ctx));
        btn.setPadding(dp(ctx, 16), dp(ctx, 11), dp(ctx, 16), dp(ctx, 11));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setOnClickListener(onClick);
        return btn;
    }

    static TextView makePillButton(Context ctx, String text, boolean selected, View.OnClickListener onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(selected ? Color.WHITE : TEXT_SECONDARY);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(pillBg(ctx, selected));
        btn.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setOnClickListener(onClick);
        return btn;
    }

    interface ToggleListener {
        void onChanged(boolean checked);
    }

    static LinearLayout makeToggleRow(Context ctx, String title, String desc,
                                      boolean checked, ToggleListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(innerCardBg(ctx, false));
        int h = dp(ctx, 14);
        int v = dp(ctx, 12);
        row.setPadding(h, v, h, v);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tView = makeTitle(ctx, title);
        tView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        texts.addView(tView);
        if (desc != null && !desc.isEmpty()) {
            TextView dView = makeSubtitle(ctx, desc);
            dView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            dView.setPadding(0, dp(ctx, 2), 0, 0);
            texts.addView(dView);
        }
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.rightMargin = dp(ctx, 12);
        row.addView(texts, textLp);

        Switch sw = new Switch(ctx);
        sw.setChecked(checked);
        sw.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.WHITE, 0xFF9AA3B2}));
        sw.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{RED_PRIMARY, 0xFF2B3240}));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onChanged(isChecked));
        row.setOnClickListener(v1 -> sw.toggle());
        row.addView(sw);
        return row;
    }
}
