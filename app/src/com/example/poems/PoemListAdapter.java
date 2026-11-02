package com.example.poems;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.Set;

public class PoemListAdapter extends BaseAdapter {
    private final Context context;
    private final List<Poem> poems;
    private final Set<String> seenKeys;

    public PoemListAdapter(Context context, List<Poem> poems, Set<String> seenKeys) {
        this.context = context;
        this.poems = poems;
        this.seenKeys = seenKeys;
    }

    @Override
    public int getCount() {
        return poems.size();
    }

    @Override
    public Poem getItem(int position) {
        return poems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View recycled, ViewGroup parent) {
        Poem poem = poems.get(position);
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(AppColors.BACKGROUND);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(18));

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(context);
        title.setText(poem.title);
        title.setTextColor(AppColors.TITLE);
        title.setTextSize(19.0f);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setGravity(0x00800003);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (!seenKeys.contains(PoemsParser.key(poem))) {
            TextView badge = new TextView(context);
            badge.setText("новое");
            badge.setTextColor(AppColors.ACCENT);
            badge.setTextSize(10.0f);
            badge.setTypeface(Typeface.MONOSPACE);
            badge.setPadding(dp(8), 0, 0, 0);
            titleRow.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        }
        content.addView(titleRow);

        TextView subtitle = new TextView(context);
        subtitle.setText(summary(poem));
        subtitle.setTextColor(AppColors.SECONDARY);
        subtitle.setTextSize(13.0f);
        subtitle.setTypeface(Typeface.SERIF);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setPadding(0, dp(6), 0, 0);
        content.addView(subtitle);
        wrapper.addView(content, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(context);
        divider.setBackgroundColor(AppColors.DIVIDER);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.leftMargin = dp(20);
        dividerParams.rightMargin = dp(20);
        wrapper.addView(divider, dividerParams);
        return wrapper;
    }

    private String summary(Poem poem) {
        StringBuilder summary = new StringBuilder();
        int count = Math.min(2, poem.lines.size());
        for (int i = 0; i < count; i++) {
            String line = poem.lines.get(i).trim();
            if (line.length() > 0) {
                if (summary.length() > 0) {
                    summary.append(" · ");
                }
                summary.append(line);
            }
        }
        return summary.toString();
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
