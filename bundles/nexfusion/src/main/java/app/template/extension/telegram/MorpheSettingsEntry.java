package app.template.extension.telegram;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Adds a Telegram Settings-styled Morphe card to the main Settings screen.
 * Seamlessly matches Day, Night, Tinted, and AMOLED Black Telegram themes.
 */
public final class MorpheSettingsEntry {
    private static final int MORPHE_BLUE = 0xff079fc5;

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

        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(context, 12), dp(context, 8),
                dp(context, 12), dp(context, 8));
        section.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 20), dp(context, 12),
                dp(context, 18), dp(context, 12));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(bgColor);
        cardBg.setCornerRadius(dp(context, 20));
        if (isDark) {
            cardBg.setStroke(dp(context, 1), dividerColor);
        }
        card.setBackground(cardBg);

        section.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinimumHeight(dp(context, 72));
        item.setBackground(selectableBackground(context));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription("Morphe Settings");
        card.addView(item, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        MorpheMarkView icon = new MorpheMarkView(context);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dp(context, 52), dp(context, 52));
        item.addView(icon, iconParams);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(context, 16);
        item.addView(labels, labelsParams);

        TextView title = new TextView(context);
        title.setText("Morphe Settings");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        title.setTextColor(textColor);
        labels.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(context);
        subtitle.setText("N-Settings, Privacy & Ghost, Media, and UI Tweaks");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        subtitle.setLineSpacing(0f, 1.0f);
        subtitle.setTextColor(grayTextColor);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(context, 3);
        labels.addView(subtitle, subtitleParams);

        TextView chevron = new TextView(context);
        chevron.setText("\u203A");
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        chevron.setTextColor(grayTextColor);
        chevron.setGravity(Gravity.CENTER);
        item.addView(chevron, new LinearLayout.LayoutParams(
                dp(context, 24), dp(context, 48)));

        item.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Context targetContext = view.getContext() != null ? view.getContext() : appContext;
                MorpheSettingsActivity.showInDialog(targetContext);
            }
        });
        return section;
    }

    private static final class MorpheMarkView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path mark = new Path();
        private final GradientDrawable tile = new GradientDrawable();

        MorpheMarkView(Context context) {
            super(context);
            tile.setColor(MORPHE_BLUE);
            tile.setCornerRadius(dp(context, 14));
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float side = Math.min(getWidth(), getHeight());
            float left = (getWidth() - side) * 0.5f;
            float top = (getHeight() - side) * 0.5f;
            canvas.save();
            canvas.translate(left, top);
            tile.setBounds(0, 0, (int) side, (int) side);
            tile.draw(canvas);

            float unit = side / 56f;
            canvas.scale(unit, unit);
            mark.reset();
            mark.moveTo(13f, 39f);
            mark.lineTo(13f, 17f);
            mark.cubicTo(13f, 14.5f, 16f, 13.5f, 18f, 15.5f);
            mark.lineTo(28f, 27f);
            mark.lineTo(38f, 15.5f);
            mark.cubicTo(40f, 13.5f, 43f, 14.5f, 43f, 17f);
            mark.lineTo(43f, 39f);
            mark.lineTo(36.5f, 39f);
            mark.lineTo(36.5f, 25.5f);
            mark.lineTo(30.5f, 32.5f);
            mark.cubicTo(29f, 34.2f, 27f, 34.2f, 25.5f, 32.5f);
            mark.lineTo(19.5f, 25.5f);
            mark.lineTo(19.5f, 39f);
            mark.close();
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(mark, paint);
            canvas.restore();
        }
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static android.graphics.drawable.Drawable selectableBackground(Context context) {
        TypedArray array = context.obtainStyledAttributes(
                new int[]{android.R.attr.selectableItemBackground});
        try {
            int resource = array.getResourceId(0, 0);
            return resource != 0 ? context.getDrawable(resource) : new GradientDrawable();
        } catch (Throwable ignored) {
            return new GradientDrawable();
        } finally {
            array.recycle();
        }
    }
}
