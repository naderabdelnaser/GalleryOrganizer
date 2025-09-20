package com.nader.galleryorganizer;

import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class FolderPrefs {
    private static final String KEY = "known_folders";

    public static Set<String> get(SharedPreferences prefs) {
        Set<String> raw = prefs.getStringSet(KEY, null);
        return raw == null ? new HashSet<>() : new HashSet<>(raw);
    }

    public static void add(SharedPreferences prefs, String name) {
        Set<String> s = get(prefs);
        s.add(name);
        prefs.edit().putStringSet(KEY, s).apply();
    }

    public static void remove(SharedPreferences prefs, String name) {
        Set<String> s = get(prefs);
        s.remove(name);
        prefs.edit().putStringSet(KEY, s).apply();
    }

    public static void rename(SharedPreferences prefs, String oldName, String newName) {
        Set<String> s = get(prefs);
        if (s.remove(oldName)) s.add(newName);
        prefs.edit().putStringSet(KEY, s).apply();
    }
}