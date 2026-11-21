package app.template.extension.telegram;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import app.template.extension.forwarding.ForwardingRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Native-styled Morphe Settings Screen for Telegram.
 * Implements NagramXF's N-settings architecture with categories for:
 * General, Ghost Mode & Privacy, Media & Downloads, Chat & Content, and Experimental Forwarding.
 */
public final class MorpheSettingsActivity extends Activity {

    public static void showInDialog(Context context) {
        if (context == null) return;
        Activity activity = findActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        boolean isDark = TelegramConfig.isDarkMode(activity);
        int themeRes = isDark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar;

        final Dialog dialog = new Dialog(activity, themeRes);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            int bgColor = TelegramConfig.isAmoledDark(activity) ? Color.BLACK :
                    TelegramConfig.getThemeColor("key_windowBackgroundGray", isDark ? 0xFF0E1621 : 0xFFF0F2F5);
            window.setBackgroundDrawable(new ColorDrawable(bgColor));
        }

        View contentView = buildSettingsView(activity, dialog);
        dialog.setContentView(contentView);
        dialog.show();
    }

    private static Activity findActivity(Context context) {
        if (context instanceof Activity) return (Activity) context;
        if (context instanceof android.content.ContextWrapper) {
            Context base = ((android.content.ContextWrapper) context).getBaseContext();
            if (base instanceof Activity) return (Activity) base;
        }
        return null;
    }

    private static View buildSettingsView(final Activity activity, final Dialog dialog) {
        final boolean isDark = TelegramConfig.isDarkMode(activity);
        final boolean isAmoled = TelegramConfig.isAmoledDark(activity);

        final int pageBg = isAmoled ? Color.BLACK :
                TelegramConfig.getThemeColor("key_windowBackgroundGray", isDark ? 0xFF0E1621 : 0xFFF0F2F5);
        final int cardBg = isAmoled ? 0xFF0D0D0D :
                TelegramConfig.getThemeColor("key_windowBackgroundWhite", isDark ? 0xFF17212B : Color.WHITE);
        final int primaryText = TelegramConfig.getThemeColor("key_windowBackgroundWhiteBlackText", isDark ? Color.WHITE : 0xFF222222);
        final int secondaryText = TelegramConfig.getThemeColor("key_windowBackgroundWhiteGrayText", isDark ? 0xFFA0A5A9 : 0xFF707579);
        final int accentColor = TelegramConfig.getThemeColor("key_windowBackgroundWhiteBlueHeader", 0xFF2481CC);
        final int dividerColor = TelegramConfig.getThemeColor("key_divider", isDark ? 0x1FFFFFFF : 0x1F000000);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(pageBg);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Top Action Bar
        LinearLayout actionBar = new LinearLayout(activity);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        actionBar.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12));
        actionBar.setBackgroundColor(cardBg);

        TextView backBtn = new TextView(activity);
        backBtn.setText("\u2190");
        backBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        backBtn.setTextColor(primaryText);
        backBtn.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 16), dp(activity, 4));
        backBtn.setClickable(true);
        backBtn.setOnClickListener(v -> dialog.dismiss());
        actionBar.addView(backBtn);

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleCol.setLayoutParams(titleParams);

        TextView title = new TextView(activity);
        title.setText("Morphe Settings");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(primaryText);
        titleCol.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText("N-Settings & Unified Tweaks");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setTextColor(secondaryText);
        titleCol.addView(subtitle);

        actionBar.addView(titleCol);

        // Header Restart Action Button
        Button restartBtn = new Button(activity);
        restartBtn.setText("Restart");
        restartBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        restartBtn.setTextColor(Color.WHITE);
        GradientDrawable rstBg = new GradientDrawable();
        rstBg.setColor(accentColor);
        rstBg.setCornerRadius(dp(activity, 16));
        restartBtn.setBackground(rstBg);
        restartBtn.setPadding(dp(activity, 14), dp(activity, 6), dp(activity, 14), dp(activity, 6));
        restartBtn.setOnClickListener(v -> {
            Toast.makeText(activity, "Restarting Telegram...", Toast.LENGTH_SHORT).show();
            v.postDelayed(() -> TelegramConfig.restartTelegram(activity), 300);
        });
        actionBar.addView(restartBtn);

        root.addView(actionBar);

        View abDivider = new View(activity);
        abDivider.setBackgroundColor(dividerColor);
        root.addView(abDivider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1)));

        // Scrollable Settings List
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 24));

        // 1. GENERAL
        addSectionHeader(activity, list, "GENERAL", accentColor);
        LinearLayout generalCard = createCardContainer(activity, cardBg, dividerColor, isDark);
        addSettingRow(activity, generalCard, TelegramConfig.KEY_EXACT_NUMBERS,
                "Exact Numbers", "Show precise counts without rounding (e.g. 4,812 instead of 4.8K)", true, primaryText, secondaryText, accentColor);
        addDivider(activity, generalCard, dividerColor);
        addSettingRow(activity, generalCard, TelegramConfig.KEY_BYPASS_INTEGRITY,
                "Bypass Certificate Check", "Spoofs certificate SHA-256 fingerprint for signature verification", true, primaryText, secondaryText, accentColor);
        addDivider(activity, generalCard, dividerColor);
        addActionRow(activity, generalCard, "Restart Telegram", "Perform clean process rebirth to apply all updates",
                primaryText, secondaryText, v -> TelegramConfig.restartTelegram(activity));
        addDivider(activity, generalCard, dividerColor);
        addActionRow(activity, generalCard, "Reset Morphe Settings", "Restore all settings to their default values",
                0xFFE53935, secondaryText, v -> {
                    new AlertDialog.Builder(activity)
                            .setTitle("Reset Settings")
                            .setMessage("Are you sure you want to reset all Morphe settings?")
                            .setPositiveButton("Reset", (d, w) -> {
                                TelegramConfig.resetDefaults(activity);
                                dialog.dismiss();
                                showInDialog(activity);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
        list.addView(generalCard);

        // 2. PRIVACY & GHOST MODE
        addSectionHeader(activity, list, "PRIVACY & GHOST MODE", accentColor);
        LinearLayout ghostCard = createCardContainer(activity, cardBg, dividerColor, isDark);
        addSettingRow(activity, ghostCard, TelegramConfig.KEY_ANTI_DELETE,
                "Anti-Delete Messages", "Keep deleted messages from being removed from local storage and chat", true, primaryText, secondaryText, accentColor);
        addDivider(activity, ghostCard, dividerColor);
        addSettingRow(activity, ghostCard, TelegramConfig.KEY_ANTI_DISAPPEARING,
                "Anti-Disappearing Media", "Keep view-once and timer-based media viewable indefinitely", true, primaryText, secondaryText, accentColor);
        addDivider(activity, ghostCard, dividerColor);
        addSettingRow(activity, ghostCard, TelegramConfig.KEY_HIDE_TYPING,
                "Ghost Typing", "Suppress typing status so other users cannot see when you type", false, primaryText, secondaryText, accentColor);
        addDivider(activity, ghostCard, dividerColor);
        addSettingRow(activity, ghostCard, TelegramConfig.KEY_HIDE_STORIES_READ,
                "Stealth Stories View", "View stories anonymously without sending read receipts", false, primaryText, secondaryText, accentColor);
        addDivider(activity, ghostCard, dividerColor);
        addSettingRow(activity, ghostCard, TelegramConfig.KEY_ANTI_SCREENSHOT,
                "Suppress Screenshot Alerts", "Prevent sending screenshot notifications in secret chats", true, primaryText, secondaryText, accentColor);
        list.addView(ghostCard);

        // 3. MEDIA & DOWNLOADS
        addSectionHeader(activity, list, "MEDIA & DOWNLOADS", accentColor);
        LinearLayout mediaCard = createCardContainer(activity, cardBg, dividerColor, isDark);
        addSettingRow(activity, mediaCard, TelegramConfig.KEY_DOWNLOAD_BOOST,
                "Download Speed Boost", "Accelerate downloads using multi-connection 1 MB streaming chunks", true, primaryText, secondaryText, accentColor);
        addDivider(activity, mediaCard, dividerColor);
        addSettingRow(activity, mediaCard, TelegramConfig.KEY_VOICE_TO_MUSIC,
                "Voice Notes in Music Player", "Allow voice messages to be played in Telegram's background music player", false, primaryText, secondaryText, accentColor);
        list.addView(mediaCard);

        // 4. CHAT & CONTENT RESTRICTIONS
        addSectionHeader(activity, list, "CHAT & CONTENT RESTRICTIONS", accentColor);
        LinearLayout chatCard = createCardContainer(activity, cardBg, dividerColor, isDark);
        addSettingRow(activity, chatCard, TelegramConfig.KEY_BYPASS_CHANNEL_RESTRICTIONS,
                "Bypass Channel Restrictions", "Allow forwarding, copying, and saving media from restricted channels", true, primaryText, secondaryText, accentColor);
        addDivider(activity, chatCard, dividerColor);
        addSettingRow(activity, chatCard, TelegramConfig.KEY_BYPASS_CONTENT_RESTRICTIONS,
                "Bypass Forward Restrictions", "Enable forwarding on copy-protected messages and private chats", true, primaryText, secondaryText, accentColor);
        addDivider(activity, chatCard, dividerColor);
        addSettingRow(activity, chatCard, TelegramConfig.KEY_REMOVE_ADS,
                "Remove Sponsored Messages", "Hide channel advertisements and sponsored message cards", true, primaryText, secondaryText, accentColor);
        addDivider(activity, chatCard, dividerColor);
        addSettingRow(activity, chatCard, TelegramConfig.KEY_DISABLE_DOUBLE_TAP,
                "Disable Double-Tap Reaction", "Prevent accidental reaction sending when double tapping messages", false, primaryText, secondaryText, accentColor);
        addDivider(activity, chatCard, dividerColor);
        addSettingRow(activity, chatCard, TelegramConfig.KEY_REVEAL_SPOILERS,
                "Auto-Reveal Spoilers", "Automatically display hidden spoiler text without tapping", false, primaryText, secondaryText, accentColor);
        addDivider(activity, chatCard, dividerColor);
        addSettingRow(activity, chatCard, TelegramConfig.KEY_DISABLE_VIBRATION,
                "Disable Vibration", "Turn off haptic vibration feedback for in-app gestures", false, primaryText, secondaryText, accentColor);
        addDivider(activity, chatCard, dividerColor);
        addSettingRow(activity, chatCard, TelegramConfig.KEY_SHOW_SECONDS,
                "Show Message Seconds", "Display exact seconds in message time stamps", false, primaryText, secondaryText, accentColor);
        list.addView(chatCard);

        // 5. EXPERIMENTAL & FORWARDING
        addSectionHeader(activity, list, "EXPERIMENTAL & FORWARDING", accentColor);
        LinearLayout expCard = createCardContainer(activity, cardBg, dividerColor, isDark);
        addActionRow(activity, expCard, "Auto-Forwarding Rules", "Configure automated message forwarding between chats",
                primaryText, secondaryText, v -> showForwardingRulesDialog(activity));
        addDivider(activity, expCard, dividerColor);
        addSettingRow(activity, expCard, TelegramConfig.KEY_DISABLE_AUTO_UPDATE,
                "Disable Auto-Updates", "Suppress in-app update checks and version upgrade prompts", true, primaryText, secondaryText, accentColor);
        addDivider(activity, expCard, dividerColor);
        addSettingRow(activity, expCard, TelegramConfig.KEY_DISABLE_ANALYTICS,
                "Disable Analytics", "Block telemetry, crashlytics and event reporting to Telegram", true, primaryText, secondaryText, accentColor);
        list.addView(expCard);

        // Footer
        TextView footer = new TextView(activity);
        footer.setText("NexFusion Morphe \u2022 Native Telegram Design");
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        footer.setTextColor(secondaryText);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(activity, 20), 0, dp(activity, 16));
        list.addView(footer);

        scrollView.addView(list);
        root.addView(scrollView);
        return root;
    }

    private static void addSectionHeader(Context context, LinearLayout parent, String title, int color) {
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(color);
        header.setPadding(dp(context, 14), dp(context, 16), dp(context, 14), dp(context, 6));
        parent.addView(header);
    }

    private static LinearLayout createCardContainer(Context context, int bgColor, int borderColor, boolean isDark) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(context, 16));
        if (isDark) {
            gd.setStroke(dp(context, 1), borderColor);
        }
        card.setBackground(gd);
        card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static void addDivider(Context context, LinearLayout parent, int dividerColor) {
        View divider = new View(context);
        divider.setBackgroundColor(dividerColor);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 0.75f));
        params.leftMargin = dp(context, 18);
        params.rightMargin = dp(context, 18);
        parent.addView(divider, params);
    }

    private static void addSettingRow(Context context, LinearLayout parent, String prefKey,
                                      String titleText, String descText, boolean defaultVal,
                                      int primaryColor, int secondaryColor, int accentColor) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12));
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.rightMargin = dp(context, 12);
        textCol.setLayoutParams(textParams);

        TextView title = new TextView(context);
        title.setText(titleText);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        title.setTextColor(primaryColor);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(title);

        if (descText != null && !descText.isEmpty()) {
            TextView desc = new TextView(context);
            desc.setText(descText);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            desc.setTextColor(secondaryColor);
            desc.setPadding(0, dp(context, 2), 0, 0);
            textCol.addView(desc);
        }

        row.addView(textCol);

        Switch sw = new Switch(context);
        boolean currentVal = TelegramConfig.getBoolean(context, prefKey, defaultVal);
        sw.setChecked(currentVal);

        int[][] states = new int[][]{
                new int[]{-android.R.attr.state_checked},
                new int[]{android.R.attr.state_checked}
        };
        int[] thumbColors = new int[]{0xFFB0B0B0, accentColor};
        int[] trackColors = new int[]{0x40808080, Color.argb(128, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))};
        sw.setThumbTintList(new ColorStateList(states, thumbColors));
        sw.setTrackTintList(new ColorStateList(states, trackColors));

        sw.setOnCheckedChangeListener((btn, isChecked) -> {
            TelegramConfig.setBoolean(context, prefKey, isChecked);
        });

        row.setOnClickListener(v -> sw.toggle());
        row.addView(sw);
        parent.addView(row);
    }

    private static void addActionRow(Context context, LinearLayout parent, String titleText,
                                     String descText, int primaryColor, int secondaryColor,
                                     View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 18), dp(context, 13), dp(context, 18), dp(context, 13));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(onClick);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.rightMargin = dp(context, 12);
        textCol.setLayoutParams(textParams);

        TextView title = new TextView(context);
        title.setText(titleText);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        title.setTextColor(primaryColor);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(title);

        if (descText != null && !descText.isEmpty()) {
            TextView desc = new TextView(context);
            desc.setText(descText);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            desc.setTextColor(secondaryColor);
            desc.setPadding(0, dp(context, 2), 0, 0);
            textCol.addView(desc);
        }

        row.addView(textCol);

        TextView chevron = new TextView(context);
        chevron.setText("\u203A");
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        chevron.setTextColor(secondaryColor);
        row.addView(chevron);

        parent.addView(row);
    }

    // Forwarding Rules Dialog (Preserves forwarding feature from previous build)
    private static void showForwardingRulesDialog(Activity activity) {
        final SharedPreferencesForwardingRuleStore store = new SharedPreferencesForwardingRuleStore(activity);
        final List<ForwardingRule> rules = store.loadAllRules();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Auto-Forwarding Rules (" + rules.size() + ")");

        CharSequence[] items = new CharSequence[rules.size() + 1];
        for (int i = 0; i < rules.size(); i++) {
            ForwardingRule r = rules.get(i);
            String src = r.getSourceChatIds().isEmpty() ? "All" : r.getSourceChatIds().toString();
            String dst = r.getDestinationChatIds().isEmpty() ? "None" : r.getDestinationChatIds().toString();
            items[i] = (r.isEnabled() ? "[\u2713] " : "[ ] ") + r.getName() + " (" + src + " \u2192 " + dst + ")";
        }
        items[rules.size()] = "+ Create New Rule";

        builder.setItems(items, (dialog, which) -> {
            if (which == rules.size()) {
                showEditRuleDialog(activity, null, store);
            } else {
                showEditRuleDialog(activity, rules.get(which), store);
            }
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private static void showEditRuleDialog(Activity activity, ForwardingRule existingRule, SharedPreferencesForwardingRuleStore store) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(existingRule == null ? "New Forwarding Rule" : "Edit Rule");

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity, 20), dp(activity, 10), dp(activity, 20), dp(activity, 10));

        final EditText nameInput = createFormInput(activity, "Rule Name", existingRule != null ? existingRule.getName() : "");
        String existingSrc = "";
        if (existingRule != null && !existingRule.getSourceChatIds().isEmpty()) {
            existingSrc = String.valueOf(existingRule.getSourceChatIds().iterator().next());
        }
        String existingDst = "";
        if (existingRule != null && !existingRule.getDestinationChatIds().isEmpty()) {
            existingDst = String.valueOf(existingRule.getDestinationChatIds().iterator().next());
        }
        final EditText sourceInput = createFormInput(activity, "Source Chat ID", existingSrc);
        final EditText destInput = createFormInput(activity, "Destination Chat ID", existingDst);

        form.addView(nameInput);
        form.addView(sourceInput);
        form.addView(destInput);

        builder.setView(form);
        builder.setPositiveButton("Save", (d, w) -> {
            try {
                String name = nameInput.getText().toString().trim();
                long sourceId = Long.parseLong(sourceInput.getText().toString().trim());
                long destId = Long.parseLong(destInput.getText().toString().trim());
                String id = existingRule != null ? existingRule.getId() : UUID.randomUUID().toString();

                Set<Long> sources = new LinkedHashSet<>();
                sources.add(sourceId);
                Set<Long> destinations = new LinkedHashSet<>();
                destinations.add(destId);

                ForwardingRule rule = new ForwardingRule(
                        id,
                        name.isEmpty() ? "Rule" : name,
                        true,
                        sources,
                        destinations,
                        existingRule != null ? existingRule.getIncludeKeywords() : new ArrayList<>(),
                        existingRule != null ? existingRule.getExcludeKeywords() : new ArrayList<>(),
                        existingRule != null ? existingRule.getMatchMode() : ForwardingRule.MatchMode.ALL,
                        existingRule != null ? existingRule.isCaseSensitive() : false,
                        existingRule != null ? existingRule.getDeliveryMode() : ForwardingRule.DeliveryMode.COPY,
                        existingRule != null ? existingRule.isShowSenderName() : false
                );
                List<ForwardingRule> all = store.loadAllRules();
                if (existingRule != null) {
                    all.removeIf(r -> r.getId().equals(existingRule.getId()));
                }
                all.add(rule);
                store.saveRules(all);
                Toast.makeText(activity, "Rule saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(activity, "Invalid chat IDs", Toast.LENGTH_SHORT).show();
            }
        });
        if (existingRule != null) {
            builder.setNeutralButton("Delete", (d, w) -> {
                List<ForwardingRule> all = store.loadAllRules();
                all.removeIf(r -> r.getId().equals(existingRule.getId()));
                store.saveRules(all);
                Toast.makeText(activity, "Rule deleted", Toast.LENGTH_SHORT).show();
            });
        }
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private static EditText createFormInput(Context context, String hint, String text) {
        EditText et = new EditText(context);
        et.setHint(hint);
        et.setText(text);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        et.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        return et;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
