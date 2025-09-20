package com.nader.galleryorganizer;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("deprecation")
public class FoldersActivity extends AppCompatActivity {
    private static final String BASE_REL = "Pictures/GalleryOrganizer/";
    private static final int REQ_MEDIA = 2001;

    private RecyclerView recyclerView;
    private FolderAdapter adapter;
    private final List<FolderItem> folders = new ArrayList<>();
    private SharedPreferences prefs;

    private boolean isScoped() { return Build.VERSION.SDK_INT >= 29; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folders);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Folders");
        }

        prefs = getSharedPreferences("GalleryOrganizerPrefs", MODE_PRIVATE);

        recyclerView = findViewById(R.id.recyclerViewFolders);
        FloatingActionButton fab = findViewById(R.id.fabNewFolder);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        fab.setOnClickListener(v -> showGlobalFolderOptions());

        if (ensureMediaPermission()) loadFolders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasMediaPermission()) loadFolders();
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean ensureMediaPermission() {
        if (hasMediaPermission()) return true;
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_MEDIA_IMAGES}, REQ_MEDIA);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_MEDIA);
        }
        return false;
    }

    private File getAppBaseLegacy() {
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File appFolder = new File(picturesDir, "GalleryOrganizer");
        if (!appFolder.exists()) appFolder.mkdirs();
        return appFolder;
    }

    private void loadFolders() {
        folders.clear();

        if (isScoped()) {
            Map<String, Integer> counts = new LinkedHashMap<>();

            // include user-created empty folders
            Set<String> stored = new LinkedHashSet<>(FolderStore.get(prefs));
            for (String n : stored) counts.put(n, 0);

            String[] proj = { MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH };
            String sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
            String[] args = new String[]{ BASE_REL + "%" };

            try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, sel, args, null)) {
                if (c != null) {
                    int relIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);
                    while (c.moveToNext()) {
                        String rel = c.getString(relIdx);
                        if (rel == null || !rel.startsWith(BASE_REL)) continue;
                        String rem = rel.substring(BASE_REL.length());
                        int slash = rem.indexOf("/");
                        if (slash <= 0) continue;
                        String name = rem.substring(0, slash);
                        counts.put(name, counts.getOrDefault(name, 0) + 1);
                    }
                }
            } catch (SecurityException ignored) {}

            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                folders.add(new FolderItem(e.getKey(), BASE_REL + e.getKey() + "/", null, e.getValue()));
            }
        } else {
            File base = getAppBaseLegacy();
            File[] subs = base.listFiles(File::isDirectory);
            if (subs != null) {
                for (File f : subs) {
                    File[] images = f.listFiles(pathname -> {
                        if (pathname == null || !pathname.isFile()) return false;
                        String n = pathname.getName().toLowerCase();
                        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
                    });
                    int count = images != null ? images.length : 0;
                    folders.add(new FolderItem(f.getName(), null, f, count));
                }
            }
        }

        if (adapter == null) {
            adapter = new FolderAdapter(folders, this::openFolder, this::onFolderLongClick);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateFolders(folders);
        }
    }

    private void openFolder(FolderItem folder) {
        Intent i = new Intent(this, PhotosActivity.class);
        if (isScoped()) {
            i.putExtra("folder_relpath", folder.relativePath);
            i.putExtra("folder_name", folder.name);
        } else {
            i.putExtra("folder_path", folder.file.getAbsolutePath());
            i.putExtra("folder_name", folder.name);
        }
        startActivity(i);
    }

    private void onFolderLongClick(FolderItem folder) {
        new AlertDialog.Builder(this)
                .setTitle("Folder: " + folder.name)
                .setItems(new String[]{
                        "Rename",
                        "Delete",
                        "Copy folder to...",
                        "Move folder to...",
                        "Copy photos to...",
                        "Move photos to..."
                }, (dialog, which) -> {
                    switch (which) {
                        case 0: showRenameDialog(folder); break;
                        case 1: confirmDelete(folder); break;
                        case 2: chooseDestinationForFolder(folder, true); break;
                        case 3: chooseDestinationForFolder(folder, false); break;
                        case 4: pickFolderThenCopyPhotosOnly(folder); break;
                        case 5: pickFolderThenMovePhotosOnly(folder); break;
                    }
                })
                .show();
    }

    private void showGlobalFolderOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Folder Options")
                .setItems(new String[]{"Create New Folder", "Copy Folder", "Move Folder"}, (dialog, which) -> {
                    switch (which) {
                        case 0: showCreateFolderDialog(); break;
                        case 1: selectSourceThenDestination(true); break;
                        case 2: selectSourceThenDestination(false); break;
                    }
                })
                .show();
    }

    private void showCreateFolderDialog() {
        EditText et = new EditText(this);
        et.setHint("Folder name");
        new AlertDialog.Builder(this)
                .setTitle("Create New Folder")
                .setView(et)
                .setPositiveButton("Create", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(this, "Enter name", Toast.LENGTH_SHORT).show(); return; }
                    if (isScoped()) {
                        FolderStore.add(prefs, name);
                        Toast.makeText(this, "Created", Toast.LENGTH_SHORT).show();
                    } else {
                        File base = getAppBaseLegacy();
                        File newFolder = new File(base, name);
                        if (newFolder.exists()) { Toast.makeText(this, "Already exists", Toast.LENGTH_SHORT).show(); return; }
                        if (newFolder.mkdirs()) Toast.makeText(this, "Created", Toast.LENGTH_SHORT).show();
                        else Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show();
                    }
                    loadFolders();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showRenameDialog(FolderItem folder) {
        EditText et = new EditText(this);
        et.setText(folder.name);
        new AlertDialog.Builder(this)
                .setTitle("Rename Folder")
                .setView(et)
                .setPositiveButton("Rename", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(this, "Enter name", Toast.LENGTH_SHORT).show(); return; }
                    if (isScoped()) {
                        int moved = moveFolderTopLevelScoped(folder.name, name);
                        FolderStore.rename(prefs, folder.name, name);
                        Toast.makeText(this, moved >= 0 ? "Renamed" : "Failed", Toast.LENGTH_SHORT).show();
                    } else {
                        File newFolder = new File(folder.file.getParentFile(), name);
                        if (newFolder.exists()) { Toast.makeText(this, "Name used", Toast.LENGTH_SHORT).show(); return; }
                        if (folder.file.renameTo(newFolder)) {
                            String json = prefs.getString("photos", "[]");
                            json = json.replace(folder.file.getAbsolutePath(), newFolder.getAbsolutePath());
                            prefs.edit().putString("photos", json).apply();
                            Toast.makeText(this, "Renamed", Toast.LENGTH_SHORT).show();
                        } else Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show();
                    }
                    loadFolders();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void confirmDelete(FolderItem folder) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Folder")
                .setMessage("Delete '" + folder.name + "' and all contents?")
                .setPositiveButton("Delete", (d, w) -> {
                    if (isScoped()) {
                        List<Uri> toDelete = listUrisInFolder(folder.name, true);
                        for (Uri u : toDelete) {
                            try { getContentResolver().delete(u, null, null); } catch (Exception ignored) {}
                            PhotoStore.removeByExactPath(prefs, u.toString());
                        }
                        FolderStore.remove(prefs, folder.name);
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        deleteRecursive(folder.file);
                        removeFolderEntriesFromPrefsLegacy(folder.file.getAbsolutePath());
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    }
                    loadFolders();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void selectSourceThenDestination(boolean copy) {
        if (folders.isEmpty()) { Toast.makeText(this, "No folders available", Toast.LENGTH_SHORT).show(); return; }
        String[] names = getTopLevelFolderNames();
        new AlertDialog.Builder(this)
                .setTitle(copy ? "Select folder to copy" : "Select folder to move")
                .setItems(names, (d, w) -> {
                    FolderItem source = findByName(names[w]);
                    if (source == null) { Toast.makeText(this, "Folder not found", Toast.LENGTH_SHORT).show(); return; }
                    chooseDestinationForFolder(source, copy);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void chooseDestinationForFolder(FolderItem sourceFolder, boolean copy) {
        String[] destNames = getTopLevelFolderNames();
        if (destNames.length == 0) { Toast.makeText(this, "No destination folders", Toast.LENGTH_SHORT).show(); return; }

        new AlertDialog.Builder(this)
                .setTitle((copy ? "Copy '" : "Move '") + sourceFolder.name + "' to:")
                .setItems(destNames, (d, w) -> {
                    String destParent = destNames[w];
                    if (destParent.equals(sourceFolder.name)) {
                        Toast.makeText(this, "Cannot " + (copy ? "copy" : "move") + " into itself", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (isScoped()) {
                        int count = copyOrMoveFolderUnderParentScoped(sourceFolder.name, destParent, !copy);
                        Toast.makeText(this, (copy ? "Copied " : "Moved ") + count + " photos", Toast.LENGTH_SHORT).show();
                    } else {
                        File destParentFile = new File(getAppBaseLegacy(), destParent);
                        if (copy) copyFolderUnderParentLegacy(sourceFolder.file, destParentFile);
                        else moveFolderUnderParentLegacy(sourceFolder.file, destParentFile);
                    }
                    loadFolders();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private String[] getTopLevelFolderNames() {
        List<String> list = new ArrayList<>();
        for (FolderItem f : folders) list.add(f.name);
        return list.toArray(new String[0]);
    }

    private FolderItem findByName(String name) {
        for (FolderItem f : folders) if (f.name.equals(name)) return f;
        return null;
    }

// ===== Scoped helpers =====

    private static class ImageRow { long id; String displayName; String mime; String relPath; Uri uri; }

    private List<ImageRow> queryImages(String sel, String[] args) {
        List<ImageRow> out = new ArrayList<>();
        String[] proj = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.RELATIVE_PATH
        };
        try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, sel, args, null)) {
            if (c != null) {
                int idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int dnIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                int mmIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);
                int rpIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);
                while (c.moveToNext()) {
                    ImageRow r = new ImageRow();
                    r.id = c.getLong(idIdx);
                    r.displayName = c.getString(dnIdx);
                    r.mime = c.getString(mmIdx);
                    r.relPath = c.getString(rpIdx);
                    r.uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, r.id);
                    out.add(r);
                }
            }
        } catch (SecurityException ignored) {}
        return out;
    }

    private List<Uri> listUrisInFolder(String folderName, boolean includeSubfolders) {
        String sel, arg;
        if (includeSubfolders) {
            sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
            arg = BASE_REL + folderName + "/%";
        } else {
            sel = MediaStore.Images.Media.RELATIVE_PATH + " = ?";
            arg = BASE_REL + folderName + "/";
        }
        List<ImageRow> rows = queryImages(sel, new String[]{arg});
        List<Uri> out = new ArrayList<>();
        for (ImageRow r : rows) out.add(r.uri);
        return out;
    }

    private int copyOrMoveFolderUnderParentScoped(String srcFolder, String destParent, boolean move) {
        String sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
        String[] args = new String[]{ BASE_REL + srcFolder + "/%" };
        List<ImageRow> rows = queryImages(sel, args);
        int count = 0;
        ContentResolver cr = getContentResolver();
        for (ImageRow r : rows) {
            try {
                String srcBase = BASE_REL + srcFolder + "/";
                String rest = r.relPath != null && r.relPath.startsWith(srcBase) ? r.relPath.substring(srcBase.length()) : "";
                String destRel = BASE_REL + destParent + "/" + srcFolder + "/" + rest;

                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, r.displayName);
                cv.put(MediaStore.Images.Media.MIME_TYPE, r.mime != null ? r.mime : "image/jpeg");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, destRel);
                Uri out = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (out == null) continue;

                try (java.io.InputStream in = cr.openInputStream(r.uri);
                     java.io.OutputStream os = cr.openOutputStream(out, "w")) {
                    if (in == null || os == null) { cr.delete(out, null, null); continue; }
                    byte[] buf = new byte[8192]; int len; while ((len = in.read(buf)) > 0) os.write(buf, 0, len);
                }

                if (move) {
                    cr.delete(r.uri, null, null);
                    PhotoStore.replacePath(prefs, r.uri.toString(), out.toString());
                } else {
                    PhotoStore.addNew(prefs, out.toString(), "", false);
                }
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    private int moveFolderTopLevelScoped(String srcFolder, String newFolder) {
        String sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
        String[] args = new String[]{ BASE_REL + srcFolder + "/%" };
        List<ImageRow> rows = queryImages(sel, args);
        int count = 0;
        ContentResolver cr = getContentResolver();
        for (ImageRow r : rows) {
            try {
                String srcBase = BASE_REL + srcFolder + "/";
                String rest = r.relPath != null && r.relPath.startsWith(srcBase) ? r.relPath.substring(srcBase.length()) : "";
                String destRel = BASE_REL + newFolder + "/" + rest;

                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, r.displayName);
                cv.put(MediaStore.Images.Media.MIME_TYPE, r.mime != null ? r.mime : "image/jpeg");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, destRel);
                Uri out = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (out == null) continue;

                try (java.io.InputStream in = cr.openInputStream(r.uri);
                     java.io.OutputStream os = cr.openOutputStream(out, "w")) {
                    if (in == null || os == null) { cr.delete(out, null, null); continue; }
                    byte[] buf = new byte[8192]; int len; while ((len = in.read(buf)) > 0) os.write(buf, 0, len);
                }

                cr.delete(r.uri, null, null);
                PhotoStore.replacePath(prefs, r.uri.toString(), out.toString());
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

// ===== Legacy helpers =====

    private void copyFolderUnderParentLegacy(File sourceFolder, File destParent) {
        File destFolder = new File(destParent, sourceFolder.getName());
        if (destFolder.exists()) destFolder = new File(destParent, sourceFolder.getName() + "_copy_" + System.currentTimeMillis());
        if (copyDirRecursive(sourceFolder, destFolder)) {
            registerFilesUnderLegacy(destFolder);
            Toast.makeText(this, "Folder copied", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Copy failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void moveFolderUnderParentLegacy(File sourceFolder, File destParent) {
        File destFolder = new File(destParent, sourceFolder.getName());
        if (destFolder.exists()) destFolder = new File(destParent, sourceFolder.getName() + "_moved_" + System.currentTimeMillis());
        if (copyDirRecursive(sourceFolder, destFolder)) {
            String oldBase = sourceFolder.getAbsolutePath();
            String newBase = destFolder.getAbsolutePath();
            String json = prefs.getString("photos", "[]");
            json = json.replace(oldBase, newBase);
            prefs.edit().putString("photos", json).apply();
            deleteRecursive(sourceFolder);
            Toast.makeText(this, "Folder moved", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Move failed", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean copyDirRecursive(File src, File dst) {
        try {
            if (src.isDirectory()) {
                if (!dst.exists() && !dst.mkdirs()) return false;
                File[] children = src.listFiles();
                if (children != null) {
                    for (File c : children) {
                        File nd = new File(dst, c.getName());
                        if (!copyDirRecursive(c, nd)) return false;
                    }
                }
            } else {
                try (FileInputStream in = new FileInputStream(src);
                     FileOutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[8192]; int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
            }
            return true;
        } catch (Exception e) { return false; }
    }

    private void registerFilesUnderLegacy(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) registerFilesUnderLegacy(f);
            else if (isImageLegacy(f)) {
                String date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
                String entry = f.getAbsolutePath() + "||" + date + "|" + System.currentTimeMillis() + "|false|";
                String json = prefs.getString("photos", "[]");
                if ("[]".equals(json)) json = "[\"" + entry + "\"]";
                else json = json.substring(0, json.length() - 1) + "," + "\"" + entry + "\"]";
                prefs.edit().putString("photos", json).apply();
            }
        }
    }

    private boolean isImageLegacy(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    private void removeFolderEntriesFromPrefsLegacy(String basePath) {
        String json = prefs.getString("photos", "[]");
        if (json == null || json.length() < 2) return;
        while (true) {
            int start = json.indexOf("\"" + basePath);
            if (start < 0) break;
            int end = json.indexOf("\"", start + 1);
            if (end < 0) break;
            json = json.substring(0, start) + json.substring(end + 1);
            json = json.replace(",,", ",").replace("[,", "[").replace(",]", "]");
        }
        String trimmed = json.replace("[", "").replace("]", "").replace(",", "").trim();
        if (trimmed.isEmpty()) json = "[]";
        prefs.edit().putString("photos", json).apply();
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] c = file.listFiles();
            if (c != null) for (File child : c) deleteRecursive(child);
        }
        try { file.delete(); } catch (Exception ignored) {}
    }

    private void pickFolderThenCopyPhotosOnly(FolderItem sourceFolder) {
        if (folders.isEmpty()) { Toast.makeText(this, "No destination folders", Toast.LENGTH_SHORT).show(); return; }
        String[] names = getTopLevelFolderNames();
        new AlertDialog.Builder(this)
                .setTitle("Copy photos from '" + sourceFolder.name + "' to:")
                .setItems(names, (d, w) -> {
                    String dest = names[w];
                    int copied;
                    if (isScoped()) copied = copyOrMoveTopLevelPhotosScoped(sourceFolder.name, dest, false);
                    else copied = copyOnlyImagesLegacy(sourceFolder.file, new File(getAppBaseLegacy(), dest), false);
                    Toast.makeText(this, copied + " photos copied", Toast.LENGTH_SHORT).show();
                    loadFolders();
                }).setNegativeButton("Cancel", null).show();
    }

    private void pickFolderThenMovePhotosOnly(FolderItem sourceFolder) {
        if (folders.isEmpty()) { Toast.makeText(this, "No destination folders", Toast.LENGTH_SHORT).show(); return; }
        String[] names = getTopLevelFolderNames();
        new AlertDialog.Builder(this)
                .setTitle("Move photos from '" + sourceFolder.name + "' to:")
                .setItems(names, (d, w) -> {
                    String dest = names[w];
                    int moved;
                    if (isScoped()) moved = copyOrMoveTopLevelPhotosScoped(sourceFolder.name, dest, true);
                    else moved = copyOnlyImagesLegacy(sourceFolder.file, new File(getAppBaseLegacy(), dest), true);
                    Toast.makeText(this, moved + " photos moved", Toast.LENGTH_SHORT).show();
                    loadFolders();
                }).setNegativeButton("Cancel", null).show();
    }

    private int copyOnlyImagesLegacy(File srcFolder, File destFolder, boolean deleteSrcAfter) {
        if (destFolder != null && !destFolder.exists()) destFolder.mkdirs();
        File[] images = srcFolder.listFiles(f -> {
            if (f == null || !f.isFile()) return false;
            String n = f.getName().toLowerCase();
            return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
        });
        if (images == null) return 0;

        int count = 0;
        for (File img : images) {
            try {
                File dst = new File(destFolder, img.getName());
                try (FileInputStream in = new FileInputStream(img);
                     FileOutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[8192]; int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
                MediaScannerConnection.scanFile(this, new String[]{dst.getAbsolutePath()}, null, null);
                if (deleteSrcAfter) {
                    if (img.delete()) {
                        String json = prefs.getString("photos", "[]");
                        json = json.replace(img.getAbsolutePath(), dst.getAbsolutePath());
                        prefs.edit().putString("photos", json).apply();
                    }
                } else {
                    PhotoStore.addNew(prefs, dst.getAbsolutePath(), "", false);
                }
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    private int copyOrMoveTopLevelPhotosScoped(String srcFolder, String destFolder, boolean move) {
        String sel = MediaStore.Images.Media.RELATIVE_PATH + " = ?";
        String[] args = new String[]{ BASE_REL + srcFolder + "/" };
        List<ImageRow> rows = queryImages(sel, args);
        ContentResolver cr = getContentResolver();
        int count = 0;
        for (ImageRow r : rows) {
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, r.displayName);
                cv.put(MediaStore.Images.Media.MIME_TYPE, r.mime != null ? r.mime : "image/jpeg");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, BASE_REL + destFolder + "/");
                Uri out = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (out == null) continue;

                try (java.io.InputStream in = cr.openInputStream(r.uri);
                     java.io.OutputStream os = cr.openOutputStream(out, "w")) {
                    if (in == null || os == null) { cr.delete(out, null, null); continue; }
                    byte[] buf = new byte[8192]; int len;
                    while ((len = in.read(buf)) > 0) os.write(buf, 0, len);
                }

                if (move) {
                    cr.delete(r.uri, null, null);
                    PhotoStore.replacePath(prefs, r.uri.toString(), out.toString());
                } else {
                    PhotoStore.addNew(prefs, out.toString(), "", false);
                }
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    @Override
    public boolean onSupportNavigateUp() { onBackPressed(); return true; }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA) {
            if (hasMediaPermission()) loadFolders();
            else Toast.makeText(this, "Permission required to view folders", Toast.LENGTH_LONG).show();
        }
    }
}