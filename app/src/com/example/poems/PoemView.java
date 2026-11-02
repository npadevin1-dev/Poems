package com.example.poems;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class PoemView extends LinearLayout {
    private final ScrollView scrollView;

    public PoemView(Context context, Poem poem, int fontSize, boolean favorite, int scrollPosition,
            Runnable onBack, Runnable onFontDown, Runnable onFontUp, Runnable onFavorite, Runnable onShare) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        setBackgroundColor(AppColors.BACKGROUND);

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(32), dp(28), dp(48));

        TextView title = new TextView(context);
        title.setText(poem.title);
        title.setTextColor(AppColors.TITLE);
        title.setTextSize(24.0f * fontSize / 17.0f);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(32));
        content.addView(title);
        for (String line : poem.lines) {
            TextView lineView = new TextView(context);
            lineView.setText(line.length() == 0 ? " " : line);
            lineView.setTextColor(AppColors.POEM_TEXT);
            lineView.setTextSize(fontSize);
            lineView.setTypeface(Typeface.SERIF);
            lineView.setLineSpacing(dp(4), 1.0f);
            lineView.setPadding(0, dp(2), 0, dp(2));
            lineView.setHorizontallyScrolling(false);
            lineView.setSingleLine(false);
            lineView.setBreakStrategy(1);
            content.addView(lineView, new LinearLayout.LayoutParams(-1, -2));
        }
        scrollView.addView(content);
        addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, Math.max(0, scrollPosition));
            }
        });

        View divider = new View(context);
        divider.setBackgroundColor(AppColors.DIVIDER);
        addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.addView(button(context, "←", onBack), new LinearLayout.LayoutParams(0, dp(58), 1.0f));
        bottomBar.addView(button(context, "A−", onFontDown), new LinearLayout.LayoutParams(0, dp(58), 1.0f));
        bottomBar.addView(button(context, "A+", onFontUp), new LinearLayout.LayoutParams(0, dp(58), 1.0f));
        bottomBar.addView(button(context, favorite ? "★" : "☆", onFavorite),
                new LinearLayout.LayoutParams(0, dp(58), 1.0f));
        bottomBar.addView(button(context, "поделиться", onShare),
                new LinearLayout.LayoutParams(0, dp(58), 1.75f));
        addView(bottomBar, new LinearLayout.LayoutParams(-1, -2));
    }

    public int getScrollPosition() {
        return scrollView.getScrollY();
    }

    private TextView button(Context context, String label, final Runnable action) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextColor(AppColors.ACCENT);
        button.setTextSize(label.equals("поделиться") ? 11.0f : 15.0f);
        button.setTypeface(Typeface.MONOSPACE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setBackgroundColor(0x00000000);
        button.setClickable(true);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                action.run();
            }
        });
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
