package com.nader.galleryorganizer;
import android.content.SharedPreferences;

import java.util.LinkedHashSet;
import java.util.Set;

public class FolderStore {
    private static final String KEY = "folders_set";

    public static synchronized Set<String> get(SharedPreferences prefs) {
        Set<String> set = prefs.getStringSet(KEY, null);
        if (set == null) return new LinkedHashSet<>();
        return new LinkedHashSet<>(set);
    }

    public static synchronized void add(SharedPreferences prefs, String name) {
        Set<String> set = get(prefs);
        set.add(name);
        prefs.edit().putStringSet(KEY, set).apply();
    }

    public static synchronized void remove(SharedPreferences prefs, String name) {
        Set<String> set = get(prefs);
        set.remove(name);
        prefs.edit().putStringSet(KEY, set).apply();
    }

    public static synchronized void rename(SharedPreferences prefs, String oldName, String newName) {
        Set<String> set = get(prefs);
        if (set.remove(oldName)) set.add(newName);
        else set.add(newName);
        prefs.edit().putStringSet(KEY, set).apply();
    }

    public static synchronized boolean contains(SharedPreferences prefs, String name) {
        return get(prefs).contains(name);
    }
}