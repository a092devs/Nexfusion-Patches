package app.template.extension.telegram;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Adds a native Telegram Settings-styled NexFusion card to the main Settings screen.
 * Perfectly matches standard Telegram settings rows (like Account, Chat Settings).
 */
public final class MorpheSettingsEntry {

    private MorpheSettingsEntry() { }

    public static View createSettingsRow(Context context) {
        if (context == null) {
            throw new IllegalStateException("Telegram application context is not ready");
        }
        final Context appContext = context;
        final boolean isDark = TelegramConfig.isDarkMode(context);
        final int bgColor = TelegramConfig.getThemeColor("key_windowBackgroundWhite", isDark ? 0xFF18222D : Color.WHITE);
        final int textColor = TelegramConfig.getThemeColor("key_windowBackgroundWhiteBlackText", isDark ? Color.WHITE : 0xFF202124);
        final int grayTextColor = TelegramConfig.getThemeColor("key_windowBackgroundWhiteGrayText", isDark ? 0xFFAAAAAA : 0xFF707579);
        final int dividerColor = TelegramConfig.getThemeColor("key_divider", isDark ? 0x22FFFFFF : 0x1F000000);

        // Standalone rounded card container matching Telegram's settings cards
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setContentDescription("NexFusion Settings");
        card.setTag("NexFusion");

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        // Margins matching standard Telegram cards (12dp left/right, 4dp top, 4dp bottom)
        cardParams.setMargins(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4));
        card.setLayoutParams(cardParams);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(bgColor);
        cardBg.setCornerRadius(dp(context, 16));
        if (isDark) {
            cardBg.setStroke(dp(context, 0.75f), dividerColor);
        }
        card.setBackground(cardBg);

        // Inner row item
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinimumHeight(dp(context, 54));
        item.setPadding(dp(context, 16), dp(context, 9), dp(context, 16), dp(context, 9));
        item.setBackground(selectableBackground(context));
        item.setClickable(true);
        item.setFocusable(true);

        // Settings gear icon in vibrant Telegram blue rounded squircle (36dp x 36dp)
        SettingsGearView icon = new SettingsGearView(context);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dp(context, 36), dp(context, 36));
        item.addView(icon, iconParams);

        // Title & subtitle column
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(context, 16);
        item.addView(labels, labelsParams);

        TextView title = new TextView(context);
        title.setText("NexFusion");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        title.setTextColor(textColor);
        labels.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(context);
        subtitle.setText("Features & Patches");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        subtitle.setTextColor(grayTextColor);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(context, 2);
        labels.addView(subtitle, subtitleParams);

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override public void onClick(View view) {
                Context targetContext = view.getContext() != null ? view.getContext() : appContext;
                MorpheSettingsActivity.showInDialog(targetContext);
            }
        };

        item.setOnClickListener(clickListener);
        card.setOnClickListener(clickListener);

        card.addView(item, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return card;
    }

    /**
     * Settings gear view inside a Telegram-blue squircle.
     * Uses direct Canvas draw operations to ensure crisp, vibrant blue squircle + white gear.
     */
    private static final class SettingsGearView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path gearPath = new Path();

        SettingsGearView(Context context) {
            super(context);
            bgPaint.setColor(0xFF2AABEE); // Telegram primary squircle blue
            bgPaint.setStyle(Paint.Style.FILL);
            gearPaint.setColor(Color.WHITE);
            gearPaint.setStyle(Paint.Style.FILL);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float side = Math.min(getWidth(), getHeight());
            if (side <= 0) return;
            float left = (getWidth() - side) * 0.5f;
            float top = (getHeight() - side) * 0.5f;
            canvas.save();
            canvas.translate(left, top);

            // 1. Draw vibrant Telegram blue rounded squircle
            float radius = side * (10f / 36f); // 10dp corner radius on 36dp side
            RectF rect = new RectF(0, 0, side, side);
            canvas.drawRoundRect(rect, radius, radius, bgPaint);

            // 2. Draw crisp white settings gear cog
            float cx = side * 0.5f;
            float cy = side * 0.5f;
            float outerR = side * 0.31f;
            float innerR = side * 0.22f;
            float holeR = side * 0.11f;

            gearPath.reset();
            gearPath.setFillType(Path.FillType.EVEN_ODD);

            int teeth = 8;
            float angleStep = (float) (2 * Math.PI / teeth);
            for (int i = 0; i < teeth; i++) {
                float aCenter = i * angleStep;
                float wTooth = angleStep * 0.22f;
                float wValley = angleStep * 0.28f;

                float x1 = cx + innerR * (float) Math.cos(aCenter - wValley);
                float y1 = cy + innerR * (float) Math.sin(aCenter - wValley);
                float x2 = cx + innerR * (float) Math.cos(aCenter - wTooth * 1.1f);
                float y2 = cy + innerR * (float) Math.sin(aCenter - wTooth * 1.1f);
                float x3 = cx + outerR * (float) Math.cos(aCenter - wTooth * 0.8f);
                float y3 = cy + outerR * (float) Math.sin(aCenter - wTooth * 0.8f);
                float x4 = cx + outerR * (float) Math.cos(aCenter + wTooth * 0.8f);
                float y4 = cy + outerR * (float) Math.sin(aCenter + wTooth * 0.8f);
                float x5 = cx + innerR * (float) Math.cos(aCenter + wTooth * 1.1f);
                float y5 = cy + innerR * (float) Math.sin(aCenter + wTooth * 1.1f);
                float x6 = cx + innerR * (float) Math.cos(aCenter + wValley);
                float y6 = cy + innerR * (float) Math.sin(aCenter + wValley);

                if (i == 0) {
                    gearPath.moveTo(x1, y1);
                } else {
                    gearPath.lineTo(x1, y1);
                }
                gearPath.lineTo(x2, y2);
                gearPath.lineTo(x3, y3);
                gearPath.lineTo(x4, y4);
                gearPath.lineTo(x5, y5);
                gearPath.lineTo(x6, y6);
            }
            gearPath.close();

            // Circular cutout in center
            gearPath.addCircle(cx, cy, holeR, Path.Direction.CW);

            canvas.drawPath(gearPath, gearPaint);
            canvas.restore();
        }
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static android.graphics.drawable.Drawable selectableBackground(Context context) {
        if (context == null) return new GradientDrawable();
        try {
            TypedArray array = context.obtainStyledAttributes(
                    new int[]{android.R.attr.selectableItemBackground});
            try {
                int resource = array.getResourceId(0, 0);
                return resource != 0 ? context.getDrawable(resource) : new GradientDrawable();
            } finally {
                array.recycle();
            }
        } catch (Throwable ignored) {
            return new GradientDrawable();
        }
    }
}
