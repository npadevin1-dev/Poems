package com.example.poems;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.ConnectivityManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String POEMS_URL =
            "https://raw.githubusercontent.com/npadevin1-dev/Poems/main/poems.txt";
    private static final String LATEST_URL =
            "https://raw.githubusercontent.com/npadevin1-dev/Poems/main/versions/latest.json";
    private static final String PREFS = "poems_prefs";
    private static final String SEEN_KEYS = "seen_poems";
    private static final String FAVORITE_KEYS = "favorite_poems";
    private static final String CACHE_FILE = "poems_cache.txt";
    private static final long UPDATE_CHECK_INTERVAL = 24L * 60L * 60L * 1000L;

    private final ArrayList<ScreenState> backStack = new ArrayList<ScreenState>();
    private final ArrayList<Poem> allPoems = new ArrayList<Poem>();
    private final ArrayList<Album> allAlbums = new ArrayList<Album>();
    private FrameLayout root;
    private LinearLayout statusBar;
    private TextView statusText;
    private ListView mainList;
    private MainListAdapter mainAdapter;
    private EditText searchField;
    private String mainQuery = "";
    private String currentScreen = "main";
    private Album currentAlbum;
    private Poem currentPoem;
    private PoemView poemView;
    private boolean showingCachedData;
    private boolean fetchingPoems;
    private int statusGeneration;
    private int fontSize;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(AppColors.BACKGROUND);
        getWindow().setNavigationBarColor(AppColors.BACKGROUND);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        fontSize = preferences.getInt("font_size", 17);
        root = new FrameLayout(this);
        root.setBackgroundColor(AppColors.BACKGROUND);
        setContentView(root);

        loadInitialData();
        initializeSeenPoems();
        if (showingCachedData) {
            initializeSeenBaselineIfNeeded();
        }
        showMainList();
        if (isNetworkAvailable()) {
            fetchPoems(false);
            checkLatestVersion(false);
        } else {
            showStatusBar(showingCachedData ? "Офлайн: показаны сохранённые" : "Нет сети",
                    AppColors.STATUS_RED);
        }
    }

    @Override
    protected void onPause() {
        saveReadingPosition();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if ("main".equals(currentScreen) && mainQuery.length() > 0) {
            mainQuery = "";
            if (searchField != null) {
                searchField.setText("");
                searchField.clearFocus();
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(searchField.getWindowToken(), 0);
            }
            refreshMainList();
            return;
        }
        if (!backStack.isEmpty()) {
            saveReadingPosition();
            ScreenState previous = backStack.remove(backStack.size() - 1);
            restoreScreen(previous);
            return;
        }
        super.onBackPressed();
    }

    private void loadInitialData() {
        File cache = new File(getFilesDir(), CACHE_FILE);
        if (cache.isFile()) {
            try {
                parseIntoCatalog(readFile(cache));
                showingCachedData = true;
                return;
            } catch (Exception ignored) {
            }
        }
        try {
            InputStream input = getAssets().open("poems.txt");
            parseIntoCatalog(readStream(input));
            input.close();
        } catch (Exception ignored) {
            allPoems.clear();
            allAlbums.clear();
        }
    }

    private void initializeSeenPoems() {
        if (preferences.getBoolean("seen_initialized", false)) {
            return;
        }
        HashSet<String> seen = new HashSet<String>();
        for (Poem poem : allPoems) {
            seen.add(PoemsParser.key(poem));
        }
        preferences.edit().putStringSet(SEEN_KEYS, seen).putBoolean("seen_initialized", true).apply();
    }

    private void initializeSeenBaselineIfNeeded() {
        if (preferences.getBoolean("seen_net_initialized", false)) {
            return;
        }
        HashSet<String> seen = getSeenKeys();
        for (Poem poem : allPoems) {
            seen.add(PoemsParser.key(poem));
        }
        preferences.edit().putStringSet(SEEN_KEYS, seen)
                .putBoolean("seen_net_initialized", true).apply();
    }

    private void parseIntoCatalog(String text) throws Exception {
        PoemsParser.Catalog catalog = PoemsParser.parse(text);
        allPoems.clear();
        allPoems.addAll(catalog.poems);
        allAlbums.clear();
        allAlbums.addAll(catalog.albums);
    }

    private String readFile(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            return readStream(input);
        } finally {
            input.close();
        }
    }

    private String readStream(InputStream input) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        try {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
            return content.toString();
        } finally {
            reader.close();
        }
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager manager =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
            return info != null && info.isConnectedOrConnecting();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void fetchPoems(final boolean showProgress) {
        if (fetchingPoems) {
            return;
        }
        if (!isNetworkAvailable()) {
            showStatusBar(showingCachedData ? "Офлайн: показаны сохранённые" : "Нет сети",
                    AppColors.STATUS_RED);
            return;
        }
        fetchingPoems = true;
        if (showProgress) {
            showStatusBar("Обновление…", AppColors.ACCENT);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                FetchResult result = requestPoems();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        fetchingPoems = false;
                        if (result.code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                            hideStatusBar();
                            return;
                        }
                        if (result.code == HttpURLConnection.HTTP_OK && result.body != null) {
                            try {
                                parseIntoCatalog(result.body);
                                initializeSeenBaselineIfNeeded();
                                showingCachedData = true;
                                if ("main".equals(currentScreen)) {
                                    refreshMainList();
                                    showStatusBar("Стихи обновлены", AppColors.STATUS_GREEN);
                                }
                                return;
                            } catch (Exception ignored) {
                            }
                        }
                        showStatusBar(showingCachedData
                                        ? "Офлайн: показаны сохранённые" : "Нет сети",
                                AppColors.STATUS_RED);
                    }
                });
            }
        }).start();
    }

    private FetchResult requestPoems() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(POEMS_URL).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setUseCaches(false);
            String etag = preferences.getString("poems_etag", null);
            if (etag != null && etag.length() > 0) {
                connection.setRequestProperty("If-None-Match", etag);
            }
            int code = connection.getResponseCode();
            FetchResult result = new FetchResult();
            result.code = code;
            if (code == HttpURLConnection.HTTP_OK) {
                result.body = readStream(connection.getInputStream());
                String responseEtag = connection.getHeaderField("ETag");
                File cache = new File(getFilesDir(), CACHE_FILE);
                FileOutputStream output = new FileOutputStream(cache);
                try {
                    output.write(result.body.getBytes("UTF-8"));
                    output.getFD().sync();
                } finally {
                    output.close();
                }
                SharedPreferences.Editor editor = preferences.edit();
                if (responseEtag != null) {
                    editor.putString("poems_etag", responseEtag);
                }
                editor.apply();
            }
            return result;
        } catch (Exception ignored) {
            return new FetchResult();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void showStatusBar(String message, int backgroundColor) {
        if (statusBar == null || !"main".equals(currentScreen)) {
            return;
        }
        final int generation = ++statusGeneration;
        statusBar.setBackgroundColor(backgroundColor);
        statusText.setText(message);
        statusBar.setVisibility(View.VISIBLE);
        statusBar.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (generation == statusGeneration && statusBar != null) {
                    statusBar.setVisibility(View.GONE);
                }
            }
        }, 4000L);
    }

    private void hideStatusBar() {
        statusGeneration++;
        if (statusBar != null) {
            statusBar.setVisibility(View.GONE);
        }
    }

    private void showMainList() {
        currentScreen = "main";
        currentAlbum = null;
        currentPoem = null;
        poemView = null;
        root.removeAllViews();
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(AppColors.BACKGROUND);

        statusBar = new LinearLayout(this);
        statusBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        statusBar.setVisibility(View.GONE);
        statusText = new TextView(this);
        statusText.setTextColor(AppColors.TITLE);
        statusText.setTextSize(12.0f);
        statusText.setTypeface(Typeface.MONOSPACE);
        statusBar.addView(statusText);
        screen.addView(statusBar, new LinearLayout.LayoutParams(-1, -2));

        TextView header = new TextView(this);
        header.setText("сборники и стихи");
        header.setTextColor(AppColors.ACCENT);
        header.setTextSize(12.0f);
        header.setTypeface(Typeface.MONOSPACE);
        header.setLetterSpacing(0.3f);
        header.setPadding(dp(24), dp(24), dp(24), dp(8));
        screen.addView(header);

        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setText("".equals(mainQuery) ? "" : mainQuery);
        searchField.setHint("поиск");
        searchField.setTextColor(AppColors.TITLE);
        searchField.setHintTextColor(AppColors.SECONDARY);
        searchField.setTextSize(15.0f);
        searchField.setTypeface(Typeface.SERIF);
        searchField.setPadding(dp(24), dp(8), dp(24), dp(8));
        searchField.setBackgroundColor(0x00000000);
        if (Build.VERSION.SDK_INT >= 29) {
            GradientDrawable cursor = new GradientDrawable();
            cursor.setColor(AppColors.ACCENT);
            cursor.setSize(dp(2), dp(18));
            searchField.setTextCursorDrawable(cursor);
        }
        screen.addView(searchField, new LinearLayout.LayoutParams(-1, -2));
        View headerDivider = new View(this);
        headerDivider.setBackgroundColor(AppColors.DIVIDER);
        screen.addView(headerDivider, new LinearLayout.LayoutParams(-1, dp(1)));

        mainList = new ListView(this);
        mainList.setDivider(null);
        mainList.setDividerHeight(0);
        mainList.setSelector(android.R.color.transparent);
        mainList.setCacheColorHint(AppColors.BACKGROUND);
        mainList.setBackgroundColor(AppColors.BACKGROUND);
        mainAdapter = new MainListAdapter(this, buildMainItems(), getSeenKeys(), mainQuery);
        mainList.setAdapter(mainAdapter);
        mainList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object item = mainAdapter.getItem(position);
                if (item instanceof Album) {
                    openAlbum((Album) item);
                } else if (item instanceof Poem) {
                    openPoem((Poem) item);
                }
            }
        });
        installPullToRefresh(mainList);
        screen.addView(mainList, new LinearLayout.LayoutParams(-1, 0, 1.0f));

        TextView version = new TextView(this);
        version.setText("версия " + getVersionName() + " · проверить обновления");
        version.setTextColor(AppColors.SECONDARY);
        version.setTextSize(10.0f);
        version.setTypeface(Typeface.MONOSPACE);
        version.setGravity(Gravity.CENTER);
        version.setPadding(dp(8), dp(8), dp(8), dp(10));
        version.setBackgroundColor(0x00000000);
        version.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkLatestVersion(true);
            }
        });
        screen.addView(version, new LinearLayout.LayoutParams(-1, -2));
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                mainQuery = text.toString();
                refreshMainList();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private void refreshMainList() {
        if (mainAdapter != null && mainList != null) {
            mainAdapter.update(buildMainItems(), getSeenKeys(), mainQuery);
        }
    }

    private List<Object> buildMainItems() {
        ArrayList<Object> items = new ArrayList<Object>();
        if (mainQuery.length() > 0) {
            String query = normalize(mainQuery);
            for (Poem poem : allPoems) {
                if (matches(poem, query)) {
                    items.add(poem);
                }
            }
            return items;
        }
        ArrayList<Poem> favorites = getFavoritePoems();
        if (!favorites.isEmpty()) {
            Album favoriteAlbum = new Album();
            favoriteAlbum.title = "Избранное";
            favoriteAlbum.favorites = true;
            favoriteAlbum.poems.addAll(favorites);
            items.add(favoriteAlbum);
        }
        items.addAll(allAlbums);
        for (Poem poem : allPoems) {
            if (poem.albumTitle == null) {
                items.add(poem);
            }
        }
        return items;
    }

    private boolean matches(Poem poem, String query) {
        if (normalize(poem.title).contains(query)) {
            return true;
        }
        for (String line : poem.lines) {
            if (normalize(line).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private void installPullToRefresh(final ListView list) {
        list.setOnTouchListener(new View.OnTouchListener() {
            private float startY;
            private boolean eligible;
            private boolean triggered;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    eligible = list.getFirstVisiblePosition() == 0
                            && list.getChildCount() > 0 && list.getChildAt(0).getTop() >= 0;
                    startY = event.getY();
                    triggered = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE && eligible
                        && !triggered && event.getY() - startY > dp(72)) {
                    triggered = true;
                    fetchPoems(true);
                } else if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    eligible = false;
                }
                return false;
            }
        });
    }

    private void openAlbum(Album album) {
        backStack.add(new ScreenState("main", null, false));
        if (album.favorites) {
            Album copy = new Album();
            copy.title = "Избранное";
            copy.favorites = true;
            copy.poems.addAll(getFavoritePoems());
            currentAlbum = copy;
        } else {
            currentAlbum = album;
        }
        showAlbumPoems(currentAlbum);
    }

    private void showAlbumPoems(final Album album) {
        currentScreen = "album";
        currentAlbum = album;
        currentPoem = null;
        poemView = null;
        root.removeAllViews();
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(AppColors.BACKGROUND);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setPadding(dp(24), dp(40), dp(24), dp(16));
        View marker = new View(this);
        marker.setBackgroundColor(AppColors.ACCENT);
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(4), -1);
        markerParams.setMargins(0, 0, dp(16), 0);
        heading.addView(marker, markerParams);
        TextView title = new TextView(this);
        title.setText(album.title);
        title.setTextColor(AppColors.TITLE);
        title.setTextSize(22.0f);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1.0f));
        screen.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        View headingDivider = new View(this);
        headingDivider.setBackgroundColor(AppColors.DIVIDER);
        screen.addView(headingDivider, new LinearLayout.LayoutParams(-1, dp(1)));

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(AppColors.BACKGROUND);
        list.setBackgroundColor(AppColors.BACKGROUND);
        list.setAdapter(new PoemListAdapter(this, album.poems, getSeenKeys()));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openPoem(album.poems.get(position));
            }
        });
        screen.addView(list, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        View bottomDivider = new View(this);
        bottomDivider.setBackgroundColor(AppColors.DIVIDER);
        screen.addView(bottomDivider, new LinearLayout.LayoutParams(-1, dp(1)));
        TextView back = new TextView(this);
        back.setText("← назад");
        back.setTextColor(AppColors.ACCENT);
        back.setTextSize(16.0f);
        back.setTypeface(Typeface.MONOSPACE);
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, dp(20), 0, dp(20));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
        screen.addView(back, new LinearLayout.LayoutParams(-1, -2));
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openPoem(Poem poem) {
        backStack.add(new ScreenState(currentScreen, currentAlbum == null ? null : currentAlbum.title,
                currentAlbum != null && currentAlbum.favorites));
        currentPoem = poem;
        markSeen(poem);
        int savedPosition = preferences.getInt("scroll:" + PoemsParser.key(poem), 0);
        renderPoem(savedPosition);
    }

    private void renderPoem(int scrollPosition) {
        currentScreen = "poem";
        poemView = new PoemView(this, currentPoem, fontSize,
                getFavoriteKeys().contains(PoemsParser.key(currentPoem)), scrollPosition,
                new Runnable() {
                    @Override
                    public void run() {
                        onBackPressed();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        changeFontSize(-2);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        changeFontSize(2);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        toggleFavorite();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        shareCurrentPoem();
                    }
                });
        root.removeAllViews();
        root.addView(poemView, new FrameLayout.LayoutParams(-1, -1));
    }

    private void restoreScreen(ScreenState state) {
        if ("main".equals(state.screen)) {
            currentAlbum = null;
            showMainList();
        } else if ("album".equals(state.screen)) {
            Album album = state.favorites ? favoriteAlbum() : findAlbum(state.albumTitle);
            if (album != null) {
                showAlbumPoems(album);
            } else {
                showMainList();
            }
        } else if ("poem".equals(state.screen) && currentPoem != null) {
            renderPoem(preferences.getInt("scroll:" + PoemsParser.key(currentPoem), 0));
        } else {
            showMainList();
        }
    }

    private Album findAlbum(String title) {
        for (Album album : allAlbums) {
            if (album.title.equals(title)) {
                return album;
            }
        }
        return null;
    }

    private Album favoriteAlbum() {
        Album album = new Album();
        album.title = "Избранное";
        album.favorites = true;
        album.poems.addAll(getFavoritePoems());
        return album;
    }

    private void changeFontSize(int delta) {
        int newSize = Math.max(13, Math.min(27, fontSize + delta));
        if (newSize == fontSize) {
            return;
        }
        int position = poemView == null ? 0 : poemView.getScrollPosition();
        fontSize = newSize;
        preferences.edit().putInt("font_size", fontSize).apply();
        renderPoem(position);
    }

    private void toggleFavorite() {
        String key = PoemsParser.key(currentPoem);
        HashSet<String> favorites = getFavoriteKeys();
        if (!favorites.add(key)) {
            favorites.remove(key);
        }
        preferences.edit().putStringSet(FAVORITE_KEYS, favorites).apply();
        int position = poemView == null ? 0 : poemView.getScrollPosition();
        renderPoem(position);
    }

    private void markSeen(Poem poem) {
        HashSet<String> seen = getSeenKeys();
        seen.add(PoemsParser.key(poem));
        preferences.edit().putStringSet(SEEN_KEYS, seen).apply();
    }

    private HashSet<String> getSeenKeys() {
        Set<String> stored = preferences.getStringSet(SEEN_KEYS, new HashSet<String>());
        return new HashSet<String>(stored);
    }

    private HashSet<String> getFavoriteKeys() {
        Set<String> stored = preferences.getStringSet(FAVORITE_KEYS, new HashSet<String>());
        return new HashSet<String>(stored);
    }

    private ArrayList<Poem> getFavoritePoems() {
        HashSet<String> keys = getFavoriteKeys();
        ArrayList<Poem> favorites = new ArrayList<Poem>();
        for (Poem poem : allPoems) {
            if (keys.contains(PoemsParser.key(poem))) {
                favorites.add(poem);
            }
        }
        return favorites;
    }

    private void saveReadingPosition() {
        if (currentPoem != null && poemView != null && "poem".equals(currentScreen)) {
            preferences.edit().putInt("scroll:" + PoemsParser.key(currentPoem),
                    poemView.getScrollPosition()).apply();
        }
    }

    private void shareCurrentPoem() {
        StringBuilder text = new StringBuilder(currentPoem.title).append("\n\n");
        for (int i = 0; i < currentPoem.lines.size(); i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(currentPoem.lines.get(i));
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text.toString());
        startActivity(Intent.createChooser(intent, "поделиться"));
    }

    private void checkLatestVersion(final boolean manual) {
        if (!isNetworkAvailable()) {
            if (manual) {
                Toast.makeText(this, "Не удалось проверить обновления", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (!manual) {
            long lastChecked = preferences.getLong("latest_check_time", 0L);
            if (System.currentTimeMillis() - lastChecked < UPDATE_CHECK_INTERVAL) {
                return;
            }
            preferences.edit().putLong("latest_check_time", System.currentTimeMillis()).apply();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                LatestResult result = requestLatestVersion();
                final LatestResult finished = result;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (finished == null) {
                            if (manual) {
                                Toast.makeText(MainActivity.this, "Не удалось проверить обновления",
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else if (finished.versionCode > getVersionCode()) {
                            showUpdateDialog(finished);
                        } else if (manual) {
                            Toast.makeText(MainActivity.this, "У вас последняя версия",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private LatestResult requestLatestVersion() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(LATEST_URL).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setUseCaches(false);
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return null;
            }
            StringBuilder json = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            reader.close();
            JSONObject object = new JSONObject(json.toString());
            LatestResult result = new LatestResult();
            result.versionCode = object.getInt("versionCode");
            result.versionName = object.getString("versionName");
            result.file = object.getString("file");
            if (result.file.contains("/") || !result.file.endsWith(".apk")) {
                return null;
            }
            JSONArray changes = object.optJSONArray("changes");
            if (changes != null) {
                for (int i = 0; i < changes.length(); i++) {
                    result.changes.add(changes.getString(i));
                }
            }
            return result;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void showUpdateDialog(final LatestResult update) {
        StringBuilder message = new StringBuilder();
        for (String change : update.changes) {
            if (message.length() > 0) {
                message.append('\n');
            }
            message.append("• ").append(change);
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Доступна версия " + update.versionName)
                .setMessage(message.toString())
                .setPositiveButton("Скачать", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String url = "https://raw.githubusercontent.com/npadevin1-dev/Poems/main/versions/"
                                + Uri.encode(update.file);
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        } catch (Exception ignored) {
                            Toast.makeText(MainActivity.this, "Не удалось открыть ссылку",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Позже", null)
                .show();
    }

    private int getVersionCode() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionCode;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String getVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class FetchResult {
        int code;
        String body;
    }

    private static final class LatestResult {
        int versionCode;
        String versionName;
        String file;
        final List<String> changes = new ArrayList<String>();
    }

    private static final class ScreenState {
        final String screen;
        final String albumTitle;
        final boolean favorites;

        ScreenState(String screen, String albumTitle, boolean favorites) {
            this.screen = screen;
            this.albumTitle = albumTitle;
            this.favorites = favorites;
        }
    }
}
