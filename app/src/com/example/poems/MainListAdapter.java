package com.example.poems;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainListAdapter extends BaseAdapter {
    private final Context context;
    private final List<Object> items = new ArrayList<Object>();
    private Set<String> seenKeys;
    private String searchQuery;

    public MainListAdapter(Context context, List<Object> items, Set<String> seenKeys, String searchQuery) {
        this.context = context;
        this.items.addAll(items);
        this.seenKeys = seenKeys;
        this.searchQuery = searchQuery;
    }

    public void update(List<Object> newItems, Set<String> newSeenKeys, String newSearchQuery) {
        items.clear();
        items.addAll(newItems);
        seenKeys = newSeenKeys;
        searchQuery = newSearchQuery;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View recycled, ViewGroup parent) {
        Object item = items.get(position);
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(AppColors.BACKGROUND);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        View marker = new View(context);
        if (item instanceof Album) {
            marker.setBackgroundColor(AppColors.ACCENT);
        } else {
            marker.setBackgroundColor(AppColors.BACKGROUND);
        }
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(4), -1);
        markerParams.setMargins(dp(20), dp(18), 0, dp(18));
        row.addView(marker, markerParams);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(18), dp(20), dp(18));
        if (item instanceof Album) {
            addAlbumText(content, (Album) item);
        } else if (item instanceof Poem) {
            addPoemText(content, (Poem) item);
        }
        row.addView(content, new LinearLayout.LayoutParams(0, -2, 1.0f));
        wrapper.addView(row, new LinearLayout.LayoutParams(-1, -2));
        addDivider(wrapper);
        return wrapper;
    }

    private void addAlbumText(LinearLayout parent, Album album) {
        TextView title = new TextView(context);
        title.setText(album.title);
        title.setTextColor(AppColors.TITLE);
        title.setTextSize(20.0f);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        parent.addView(title);

        int newCount = 0;
        for (Poem poem : album.poems) {
            if (!seenKeys.contains(PoemsParser.key(poem))) {
                newCount++;
            }
        }
        TextView subtitle = new TextView(context);
        String text = "сборник стихов · " + album.poems.size() + " " + plural(album.poems.size());
        if (newCount > 0) {
            text += " · новое: " + newCount;
        }
        subtitle.setText(text);
        subtitle.setTextColor(AppColors.SECONDARY);
        subtitle.setTextSize(12.0f);
        subtitle.setTypeface(Typeface.SERIF);
        subtitle.setPadding(0, dp(6), 0, 0);
        parent.addView(subtitle);
    }

    private void addPoemText(LinearLayout parent, Poem poem) {
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(context);
        title.setText(poem.title);
        title.setTextColor(AppColors.TITLE);
        title.setTextSize(19.0f);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
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
        parent.addView(titleRow);

        TextView subtitle = new TextView(context);
        String summary = searchQuery.length() > 0 ? searchSummary(poem) : firstLines(poem);
        subtitle.setText(summary);
        subtitle.setTextColor(AppColors.SECONDARY);
        subtitle.setTextSize(13.0f);
        subtitle.setTypeface(Typeface.SERIF);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setPadding(0, dp(6), 0, 0);
        parent.addView(subtitle);
    }

    private String searchSummary(Poem poem) {
        String query = normalize(searchQuery);
        String line = matchingLine(poem, query);
        if (line == null) {
            line = firstLine(poem);
        }
        String albumTitle = poem.albumTitle == null ? "" : poem.albumTitle.trim();
        if (albumTitle.length() == 0) {
            return line;
        }
        return line.length() == 0 ? albumTitle : albumTitle + " · " + line;
    }

    private String matchingLine(Poem poem, String query) {
        for (String line : poem.lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 0 && normalize(trimmed).contains(query)) {
                return trimmed;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private String firstLines(Poem poem) {
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

    private String firstLine(Poem poem) {
        for (String line : poem.lines) {
            line = line.trim();
            if (line.length() > 0) {
                return line;
            }
        }
        return "";
    }

    private String plural(int count) {
        int lastTwo = count % 100;
        if (lastTwo >= 11 && lastTwo <= 14) {
            return "стихов";
        }
        switch (count % 10) {
            case 1:
                return "стих";
            case 2:
            case 3:
            case 4:
                return "стиха";
            default:
                return "стихов";
        }
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(context);
        divider.setBackgroundColor(AppColors.DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.leftMargin = dp(20);
        params.rightMargin = dp(20);
        parent.addView(divider, params);
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
