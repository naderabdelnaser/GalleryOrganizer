package com.nader.galleryorganizer;

import android.content.SharedPreferences;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PhotoStore {
    public static final String KEY = "photos";
    // Format: path|tags|date(yyyy-MM-dd)|ts(ms)|favorite(true/false)|
// Older entries might be: path||date|ts|false|

    public static synchronized void addNew(SharedPreferences prefs, String path, String tags, boolean favorite) {
        try {
            JSONArray arr = load(prefs);
            int idx = indexOf(arr, path);
            if (idx >= 0) {
                String[] p = parse(arr.getString(idx));
                String newTags = (tags != null && !tags.trim().isEmpty()) ? tags.trim() : p[1];
                String newFav = favorite ? "true" : p[4];
                arr.put(idx, build(path, newTags, p[2], p[3], newFav));
            } else {
                String date = today();
                String ts = String.valueOf(System.currentTimeMillis());
                arr.put(build(path, safe(tags), date, ts, favorite ? "true" : "false"));
            }
            save(prefs, arr);
        } catch (Exception e) {
            // last resort simple append (avoid duplicates if detected)
            String json = prefs.getString(KEY, "[]");
            if (json != null && json.contains("\"" + path + "|")) return;
            String entry = path + "|" + safe(tags) + "|" + today() + "|" + System.currentTimeMillis() + "|" + (favorite ? "true" : "false") + "|";
            if ("[]".equals(json)) json = "[\"" + entry + "\"]";
            else json = json.substring(0, json.length() - 1) + ",\"" + entry + "\"]";
            prefs.edit().putString(KEY, json).apply();
        }
    }

    public static synchronized void setTags(SharedPreferences prefs, String path, String tags) {
        try {
            JSONArray arr = load(prefs);
            int idx = indexOf(arr, path);
            if (idx >= 0) {
                String[] p = parse(arr.getString(idx));
                arr.put(idx, build(p[0], safe(tags), p[2], p[3], p[4]));
            } else {
                String date = today();
                String ts = String.valueOf(System.currentTimeMillis());
                arr.put(build(path, safe(tags), date, ts, "false"));
            }
            save(prefs, arr);
        } catch (Exception e) {
            String json = prefs.getString(KEY, "[]");
            if (json.contains(path + "||")) {
                json = json.replace(path + "||", path + "|" + safe(tags) + "|");
                prefs.edit().putString(KEY, json).apply();
            } else {
                addNew(prefs, path, tags, false);
            }
        }
    }

    public static synchronized void setFavorite(SharedPreferences prefs, String path, boolean favorite) {
        try {
            JSONArray arr = load(prefs);
            int idx = indexOf(arr, path);
            if (idx >= 0) {
                String[] p = parse(arr.getString(idx));
                arr.put(idx, build(p[0], p[1], p[2], p[3], favorite ? "true" : "false"));
            } else {
                String date = today();
                String ts = String.valueOf(System.currentTimeMillis());
                arr.put(build(path, "", date, ts, favorite ? "true" : "false"));
            }
            save(prefs, arr);
        } catch (Exception e) {
            String json = prefs.getString(KEY, "[]");
            String tokenStart = "\"" + path + "|";
            int i = json.indexOf(tokenStart);
            if (i >= 0) {
                int j = json.indexOf("\"", i + 1);
                if (j > i) {
                    String token = json.substring(i + 1, j);
                    String[] p = parse(token);
                    String normalized = build(p[0], p[1], p[2], p[3], favorite ? "true" : "false");
                    json = json.substring(0, i + 1) + normalized + json.substring(j);
                    prefs.edit().putString(KEY, json).apply();
                }
            } else {
                addNew(prefs, path, "", favorite);
            }
        }
    }

    public static synchronized void replacePath(SharedPreferences prefs, String oldPath, String newPath) {
        try {
            JSONArray arr = load(prefs);
            int idx = indexOf(arr, oldPath);
            if (idx >= 0) {
                String[] p = parse(arr.getString(idx));
                arr.put(idx, build(newPath, p[1], p[2], p[3], p[4]));
                save(prefs, arr);
                return;
            }
            String json = prefs.getString(KEY, "[]");
            json = json.replace(oldPath, newPath);
            prefs.edit().putString(KEY, json).apply();
        } catch (Exception e) {
            String json = prefs.getString(KEY, "[]");
            json = json.replace(oldPath, newPath);
            prefs.edit().putString(KEY, json).apply();
        }
    }

    public static synchronized void removeByExactPath(SharedPreferences prefs, String path) {
        try {
            JSONArray arr = load(prefs);
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.getString(i);
                if (s != null && s.startsWith(path + "|")) continue;
                out.put(s);
            }
            save(prefs, out);
        } catch (Exception e) {
            String json = prefs.getString(KEY, "[]");
            String tokenStart = "\"" + path + "|";
            int i = json.indexOf(tokenStart);
            if (i >= 0) {
                int j = json.indexOf("\"", i + 1);
                if (j > i) {
                    String token = json.substring(i, j + 1);
                    json = json.replace(token, "").replace(",,", ",").replace("[,", "[").replace(",]", "]");
                    if (json.trim().isEmpty()) json = "[]";
                    prefs.edit().putString(KEY, json).apply();
                }
            }
        }
    }

    public static synchronized boolean exists(SharedPreferences prefs, String path) {
        try {
            JSONArray arr = load(prefs);
            return indexOf(arr, path) >= 0;
        } catch (Exception e) {
            String json = prefs.getString(KEY, "[]");
            return json != null && (json.contains("\"" + path + "|") || json.contains("\"" + path + "||"));
        }
    }

// Helpers

    private static JSONArray load(SharedPreferences prefs) {
        try {
            return new JSONArray(prefs.getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void save(SharedPreferences prefs, JSONArray arr) {
        prefs.edit().putString(KEY, arr.toString()).apply();
    }

    private static int indexOf(JSONArray arr, String path) {
        if (path == null) return -1;
        for (int i = 0; i < arr.length(); i++) {
            try {
                String s = arr.getString(i);
                if (s != null && s.startsWith(path + "|")) return i;
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    private static String[] parse(String entry) {
        String[] raw = entry.split("\\|", -1);
        String path = raw.length > 0 ? raw[0] : "";
        String tags = "";
        String date = today();
        String ts = String.valueOf(System.currentTimeMillis());
        String fav = "false";

        if (raw.length >= 5) {
            tags = safe(raw[1]);
            date = safe(raw[2]).isEmpty() ? today() : raw[2];
            ts = safe(raw[3]).isEmpty() ? String.valueOf(System.currentTimeMillis()) : raw[3];
            fav = "true".equals(raw[4]) ? "true" : "false";
        } else if (raw.length >= 4) {
            // legacy path||date|ts|fav?
            tags = "";
            date = safe(raw[1]).isEmpty() ? today() : raw[1];
            ts = safe(raw[2]).isEmpty() ? String.valueOf(System.currentTimeMillis()) : raw[2];
            fav = (raw.length > 3 && "true".equals(raw[3])) ? "true" : "false";
        }
        return new String[]{path, tags, date, ts, fav};
    }

    private static String build(String path, String tags, String date, String ts, String fav) {
        return path + "|" + safe(tags) + "|" + safe(date) + "|" + safe(ts) + "|" + ("true".equals(fav) ? "true" : "false") + "|";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}