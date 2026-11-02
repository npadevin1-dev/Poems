package com.example.poems;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PoemsParser {
    public static final class Catalog {
        public final List<Poem> poems = new ArrayList<Poem>();
        public final List<Album> albums = new ArrayList<Album>();
    }

    private PoemsParser() {
    }

    public static Catalog parse(String text) throws IOException {
        Catalog catalog = new Catalog();
        Map<String, Album> albumsByName = new LinkedHashMap<String, Album>();
        BufferedReader reader = new BufferedReader(new StringReader(text));
        Poem poem = null;
        Album album = null;
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.replace("\r", "");
            if (line.startsWith("## ")) {
                String name = line.substring(3).trim();
                album = albumsByName.get(name);
                if (album == null) {
                    album = new Album();
                    album.title = name;
                    albumsByName.put(name, album);
                    catalog.albums.add(album);
                }
                poem = null;
            } else if (line.startsWith("# ")) {
                poem = new Poem();
                poem.title = line.substring(2).trim();
                poem.albumTitle = album == null ? null : album.title;
                catalog.poems.add(poem);
                if (album != null) {
                    album.poems.add(poem);
                }
            } else if (poem != null) {
                poem.lines.add(line);
            }
        }
        reader.close();
        return catalog;
    }

    public static String key(Poem poem) {
        return (poem.albumTitle == null ? "" : poem.albumTitle) + "/" + poem.title;
    }
}
