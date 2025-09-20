package com.nader.galleryorganizer;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("deprecation")
public class PhotosActivity extends AppCompatActivity {
    private static final String BASE_REL = "Pictures/GalleryOrganizer/";
    private static final String DL_REL = "Download/GalleryOrganizer/";
    private static final int REQ_MEDIA = 2002;

    private RecyclerView recyclerView;
    private ImprovedPhotoAdapter adapter;
    private final List<PhotoInfo> allPhotos = new ArrayList<>();
    private final List<PhotoInfo> selectedPhotos = new ArrayList<>();
    private SharedPreferences prefs;
    private SearchView searchView;

    private boolean isContentUri(String path) { return path != null && path.startsWith("content://"); }

    private Uri toShareUri(String path) {
        if (isContentUri(path)) return Uri.parse(path);
        File f = new File(path);
        return FileProvider.getUriForFile(this, getPackageName()+".provider", f);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photos);

        String folderName = getIntent().getStringExtra("folder_name");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(folderName != null ? folderName : "Photos");
        }

        prefs = getSharedPreferences("GalleryOrganizerPrefs", MODE_PRIVATE);

        searchView = findViewById(R.id.searchView);
        recyclerView = findViewById(R.id.recyclerViewPhotos);
        com.google.android.material.floatingactionbutton.FloatingActionButton fabActions = findViewById(R.id.fabActions);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 4));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { searchPhotos(q); return true; }
            @Override public boolean onQueryTextChange(String t) { if (t.isEmpty()) loadPhotos(); else searchPhotos(t); return true; }
        });

        fabActions.setOnClickListener(v -> showBatchActionsDialog());

        if (ensureMediaPermission()) loadPhotos();
    }

    @Override protected void onResume() {
        super.onResume();
        if (hasMediaPermission()) loadPhotos();
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

    private void loadPhotos() {
        allPhotos.clear();
        String folderPath = getIntent().getStringExtra("folder_path");     // legacy path
        String folderRel = getIntent().getStringExtra("folder_relpath");    // scoped relpath

        HashMap<String, Meta> meta = loadMetaMap();

        if (Build.VERSION.SDK_INT >= 29) {
            String sel;
            String[] args;
            if (folderRel != null) {
                sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
                args = new String[]{ folderRel + "%" };
            } else {
                sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
                args = new String[]{ BASE_REL + "%" };
            }
            String[] proj = { MediaStore.Images.Media._ID };
            try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, sel, args, null)) {
                if (c != null) {
                    int idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    while (c.moveToNext()) {
                        long id = c.getLong(idIdx);
                        Uri u = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                        PhotoInfo p = new PhotoInfo();
                        p.path = u.toString();
                        Meta m = meta.get(p.path);
                        if (m != null) {
                            p.tags = m.tags;
                            p.date = m.date;
                            p.favorite = m.favorite;
                        }
                        allPhotos.add(p);
                    }
                }
            } catch (SecurityException se) {
                if (ensureMediaPermission()) loadPhotos();
                return;
            } catch (Exception ignored) {}
        } else {
            if (folderPath != null) {
                addLegacyFilesRecursively(new File(folderPath), meta);
            } else {
                File base = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryOrganizer");
                File[] subs = base.listFiles(File::isDirectory);
                if (subs != null) for (File f : subs) addLegacyFilesRecursively(f, meta);
            }
        }

        if (adapter == null) {
            adapter = new ImprovedPhotoAdapter(allPhotos, this::onPhotoClick, this::onPhotoLongClick);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updatePhotos(allPhotos);
        }
        updateTitle();

        if (allPhotos.isEmpty()) {
            Toast.makeText(this, "No photos in this folder yet", Toast.LENGTH_SHORT).show();
        }
    }

    private void addLegacyFilesRecursively(File dir, HashMap<String, Meta> meta) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) { addLegacyFilesRecursively(f, meta); continue; }
            String n = f.getName().toLowerCase();
            if (!(n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))) continue;
            PhotoInfo p = new PhotoInfo();
            p.path = f.getAbsolutePath();
            Meta m = meta.get(p.path);
            if (m != null) {
                p.tags = m.tags;
                p.date = m.date;
                p.favorite = m.favorite;
            }
            allPhotos.add(p);
        }
    }

    private static class Meta { String tags=""; String date=""; boolean favorite=false; }
    private HashMap<String, Meta> loadMetaMap() {
        HashMap<String, Meta> map = new HashMap<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("photos", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String entry = arr.getString(i);
                String[] parts = entry.split("\\|");
                if (parts.length >= 5) {
                    Meta m = new Meta();
                    m.tags = parts[1];
                    m.date = parts[2];
                    m.favorite = "true".equals(parts[4]);
                    map.put(parts[0], m);
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    private void searchPhotos(String query) {
        List<PhotoInfo> results = new ArrayList<>();
        for (PhotoInfo p : allPhotos) {
            if ((p.tags != null && p.tags.toLowerCase().contains(query.toLowerCase()))
                    || (p.date != null && p.date.contains(query))
                    || (p.path != null && p.path.toLowerCase().contains(query.toLowerCase()))) {
                results.add(p);
            }
        }
        if (adapter != null) adapter.updatePhotos(results);
    }

    private void onPhotoClick(PhotoInfo photo) {
        if (selectedPhotos.contains(photo)) selectedPhotos.remove(photo);
        else selectedPhotos.add(photo);
        adapter.setSelectedPhotos(selectedPhotos);
        updateTitle();
    }

    private void onPhotoLongClick(PhotoInfo photo) {
        if (!selectedPhotos.contains(photo)) selectedPhotos.add(photo);
        adapter.setSelectedPhotos(selectedPhotos);
        updateTitle();
    }

    private void updateTitle() {
        if (getSupportActionBar() != null) {
            String folderName = getIntent().getStringExtra("folder_name");
            String base = folderName != null ? folderName : "Photos";
            getSupportActionBar().setTitle(selectedPhotos.isEmpty() ? base : selectedPhotos.size() + " selected");
        }
    }

    private void showBatchActionsDialog() {
        if (selectedPhotos.isEmpty()) {
            Toast.makeText(this, "No photos selected", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] baseOpts = {"Share", "Copy to Folder", "Move to Folder", "Add Tags", "Make PDF", "Delete"};
        String[] opts = selectedPhotos.size() == 1
                ? new String[]{"Share", "Copy to Folder", "Move to Folder", "Add Tags", "Make PDF", "Delete", "Details"}
                : baseOpts;

        new AlertDialog.Builder(this)
                .setTitle("Photo Actions (" + selectedPhotos.size() + " selected)")
                .setItems(opts, (dialog, which) -> {
                    String choice = opts[which];
                    switch (choice) {
                        case "Share": shareSelectedPhotos(); break;
                        case "Copy to Folder": showCopyToFolderDialog(); break;
                        case "Move to Folder": showMoveToFolderDialog(); break;
                        case "Add Tags": showAddTagsDialog(); break;
                        case "Make PDF": promptPdfNameAndCreate(); break;
                        case "Delete": deleteSelectedPhotos(); break;
                        case "Details": showDetails(selectedPhotos.get(0)); break;
                    }
                })
                .show();
    }

    private String[] getFolderNames() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>(FolderStore.get(prefs));
        if (Build.VERSION.SDK_INT >= 29) {
            String[] proj = { MediaStore.Images.Media.RELATIVE_PATH };
            String sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
            String[] args = new String[]{ BASE_REL + "%" };
            try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, sel, args, null)) {
                if (c != null) {
                    int idx = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);
                    while (c.moveToNext()) {
                        String rel = c.getString(idx);
                        if (rel == null || !rel.startsWith(BASE_REL)) continue;
                        String rem = rel.substring(BASE_REL.length());
                        int slash = rem.indexOf("/");
                        if (slash > 0) names.add(rem.substring(0, slash));
                    }
                }
            } catch (SecurityException ignored) {}
        } else {
            File appFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryOrganizer");
            File[] folders = appFolder.listFiles(File::isDirectory);
            if (folders != null) for (File f : folders) names.add(f.getName());
        }
        return names.toArray(new String[0]);
    }

    private void showCopyToFolderDialog() {
        String[] names = getFolderNames();
        if (names.length == 0) { Toast.makeText(this, "No folders", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this)
                .setTitle("Copy to folder")
                .setItems(names, (d, w) -> copyPhotosToFolder(names[w]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMoveToFolderDialog() {
        String[] names = getFolderNames();
        if (names.length == 0) { Toast.makeText(this, "No folders", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this)
                .setTitle("Move to folder")
                .setItems(names, (d, w) -> movePhotosToFolder(names[w]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void copyPhotosToFolder(String destFolderName) {
        int count = 0;
        for (PhotoInfo p : selectedPhotos) {
            try {
                if (isContentUri(p.path) && Build.VERSION.SDK_INT >= 29) {
                    Uri src = Uri.parse(p.path);
                    String dn = "IMG_" + System.currentTimeMillis() + ".jpg";
                    String mm = "image/jpeg";
                    try (Cursor c = getContentResolver().query(src, new String[]{ MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE }, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            dn = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                            mm = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE));
                        }
                    }
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Images.Media.DISPLAY_NAME, dn);
                    cv.put(MediaStore.Images.Media.MIME_TYPE, mm);
                    cv.put(MediaStore.Images.Media.RELATIVE_PATH, BASE_REL + destFolderName + "/");
                    Uri out = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    if (out == null) continue;
                    try (InputStream in = getContentResolver().openInputStream(src);
                         java.io.OutputStream os = getContentResolver().openOutputStream(out, "w")) {
                        if (in == null || os == null) { getContentResolver().delete(out, null, null); continue; }
                        byte[] buf = new byte[8192]; int len;
                        while ((len = in.read(buf)) > 0) os.write(buf, 0, len);
                    }
                    PhotoStore.addNew(prefs, out.toString(), "", false);
                    count++;
                } else {
                    File src = new File(p.path);
                    if (!src.exists()) continue;
                    File dest = new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryOrganizer"), destFolderName);
                    if (!dest.exists()) dest.mkdirs();
                    File dst = new File(dest, src.getName());
                    try (java.io.FileInputStream in = new java.io.FileInputStream(src);
                         java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
                        byte[] buf = new byte[8192]; int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    }
                    PhotoStore.addNew(prefs, dst.getAbsolutePath(), "", false);
                    count++;
                }
            } catch (Exception ignored) {}
        }
        Toast.makeText(this, count + " photos copied", Toast.LENGTH_SHORT).show();
        clearSelection();
        loadPhotos();
    }

    private void movePhotosToFolder(String destFolderName) {
        int count = 0;
        for (PhotoInfo p : selectedPhotos) {
            try {
                if (isContentUri(p.path) && Build.VERSION.SDK_INT >= 29) {
                    Uri src = Uri.parse(p.path);
                    String dn = "IMG_" + System.currentTimeMillis() + ".jpg";
                    String mm = "image/jpeg";
                    try (Cursor c = getContentResolver().query(src, new String[]{ MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE }, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            dn = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                            mm = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE));
                        }
                    }
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Images.Media.DISPLAY_NAME, dn);
                    cv.put(MediaStore.Images.Media.MIME_TYPE, mm);
                    cv.put(MediaStore.Images.Media.RELATIVE_PATH, BASE_REL + destFolderName + "/");
                    Uri out = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    if (out == null) continue;
                    try (InputStream in = getContentResolver().openInputStream(src);
                         java.io.OutputStream os = getContentResolver().openOutputStream(out, "w")) {
                        if (in == null || os == null) { getContentResolver().delete(out, null, null); continue; }
                        byte[] buf = new byte[8192]; int len;
                        while ((len = in.read(buf)) > 0) os.write(buf, 0, len);
                    }
                    getContentResolver().delete(src, null, null);
                    PhotoStore.replacePath(prefs, src.toString(), out.toString());
                    count++;
                } else {
                    File src = new File(p.path);
                    if (!src.exists()) continue;
                    File dest = new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryOrganizer"), destFolderName);
                    if (!dest.exists()) dest.mkdirs();
                    File dst = new File(dest, src.getName());
                    try (java.io.FileInputStream in = new java.io.FileInputStream(src);
                         java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
                        byte[] buf = new byte[8192]; int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    }
                    if (src.delete()) {
                        PhotoStore.replacePath(prefs, src.getAbsolutePath(), dst.getAbsolutePath());
                        count++;
                    }
                }
            } catch (Exception ignored) {}
        }
        Toast.makeText(this, count + " photos moved", Toast.LENGTH_SHORT).show();
        clearSelection();
        loadPhotos();
    }

    private void shareSelectedPhotos() {
        try {
            if (selectedPhotos.size() == 1) {
                Uri uri = toShareUri(selectedPhotos.get(0).path);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("image/*");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Share Photo"));
            } else {
                ArrayList<Uri> uris = new ArrayList<>();
                for (PhotoInfo p : selectedPhotos) uris.add(toShareUri(p.path));
                if (uris.isEmpty()) { Toast.makeText(this, "No valid files", Toast.LENGTH_SHORT).show(); return; }
                Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
                i.setType("image/*");
                i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Share Photos"));
            }
            clearSelection();
        } catch (Exception e) {
            Toast.makeText(this, "Share error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddTagsDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Add Tags to " + selectedPhotos.size() + " photos");
        final EditText et = new EditText(this);
        et.setHint("comma,separated,tags");
        b.setView(et);
        b.setPositiveButton("Add", (d, w) -> {
            String tags = et.getText().toString().trim();
            if (tags.isEmpty()) return;
            for (PhotoInfo p : selectedPhotos) {
                PhotoStore.setTags(prefs, p.path, tags);
            }
            Toast.makeText(this, "Tags added", Toast.LENGTH_SHORT).show();
            clearSelection();
            loadPhotos();
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    // PDF creation with name prompt and post-actions (open/share/rename) + saved location message
    private void promptPdfNameAndCreate() {
        final EditText et = new EditText(this);
        et.setHint("File name (without .pdf)");
        String def = "GalleryOrganizer_" + System.currentTimeMillis();
        et.setText(def);
        new AlertDialog.Builder(this)
                .setTitle("PDF name")
                .setView(et)
                .setPositiveButton("Create", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) name = def;
                    if (!name.toLowerCase().endsWith(".pdf")) name += ".pdf";
                    createPDFWithName(name);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void createPDFWithName(String fileName) {
        try {
            PdfDocument doc = new PdfDocument();

            for (int i = 0; i < selectedPhotos.size(); i++) {
                PhotoInfo ph = selectedPhotos.get(i);
                int imgW = 0, imgH = 0;
                try {
                    if (ph.path.startsWith("content://")) {
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inJustDecodeBounds = true;
                        try (InputStream in = getContentResolver().openInputStream(Uri.parse(ph.path))) {
                            BitmapFactory.decodeStream(in, null, o);
                        }
                        imgW = o.outWidth; imgH = o.outHeight;
                    } else {
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(ph.path, o);
                        imgW = o.outWidth; imgH = o.outHeight;
                    }
                } catch (Exception ignored) {}

                boolean landscape = imgW > imgH;
                int pageW = landscape ? 842 : 595;  // A4 landscape vs portrait
                int pageH = landscape ? 595 : 842;

                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create();
                PdfDocument.Page page = doc.startPage(info);
                Canvas canvas = page.getCanvas();

                Bitmap bm = decodeBitmapSmart(ph.path, pageW, pageH);
                if (bm != null) {
                    float scale = Math.min((float) pageW / bm.getWidth(), (float) pageH / bm.getHeight());
                    int w = Math.max(1, (int) (bm.getWidth() * scale));
                    int h = Math.max(1, (int) (bm.getHeight() * scale));
                    Bitmap scaled = Bitmap.createScaledBitmap(bm, w, h, true);
                    int x = (pageW - w) / 2, y = (pageH - h) / 2;
                    canvas.drawBitmap(scaled, x, y, null);
                    if (!scaled.isRecycled()) scaled.recycle();
                    if (!bm.isRecycled()) bm.recycle();
                }
                doc.finishPage(page);
            }

            Uri outUri;
            String locationText;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, DL_REL);
                outUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (outUri == null) throw new RuntimeException("Cannot create PDF");
                try (java.io.OutputStream os = getContentResolver().openOutputStream(outUri, "w")) {
                    doc.writeTo(os);
                }
                locationText = "Saved to: Downloads/GalleryOrganizer/" + fileName;
            } else {
                File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File dir = new File(dl, "GalleryOrganizer");
                if (!dir.exists()) dir.mkdirs();
                File pdf = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(pdf)) {
                    doc.writeTo(fos);
                }
                outUri = FileProvider.getUriForFile(this, getPackageName()+".provider", pdf);
                locationText = "Saved to: " + pdf.getAbsolutePath();
            }
            doc.close();

            Toast.makeText(this, locationText, Toast.LENGTH_LONG).show();
            showPDFOptionsDialog(outUri, fileName, locationText);
            clearSelection();
        } catch (Exception e) {
            Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showPDFOptionsDialog(Uri pdfUri, String fileName, String locationText) {
        String[] opts = {"Open", "Share", "Cancel"};
        new AlertDialog.Builder(this)
                .setTitle("PDF Created")
                .setMessage(locationText)
                .setItems(opts, (d, w) -> {
                    switch (w) {
                        case 0:
                            try {
                                Intent i = new Intent(Intent.ACTION_VIEW);
                                i.setDataAndType(pdfUri, "application/pdf");
                                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                if (i.resolveActivity(getPackageManager()) != null) startActivity(i);
                                else Toast.makeText(this, "No PDF viewer found", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) { Toast.makeText(this, "Open error", Toast.LENGTH_SHORT).show(); }
                            break;
                        case 1:
                            try {
                                Intent i = new Intent(Intent.ACTION_SEND);
                                i.setType("application/pdf");
                                i.putExtra(Intent.EXTRA_STREAM, pdfUri);
                                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                startActivity(Intent.createChooser(i, "Share PDF"));
                            } catch (Exception e) { Toast.makeText(this, "Share error", Toast.LENGTH_SHORT).show(); }
                            break;
                        case 2:
// Cancel = just dismiss
                            break;
                    }
                })
                .show();
    }

    private void promptRenamePdf(Uri pdfUri, String currentName) {
        final EditText et = new EditText(this);
        String base = currentName.endsWith(".pdf") ? currentName.substring(0, currentName.length()-4) : currentName;
        et.setText(base);
        new AlertDialog.Builder(this)
                .setTitle("Rename PDF")
                .setView(et)
                .setPositiveButton("Rename", (d, w) -> {
                    String newName = et.getText().toString().trim();
                    if (newName.isEmpty()) newName = base;
                    if (!newName.toLowerCase().endsWith(".pdf")) newName += ".pdf";
                    Uri newUri = renamePdfUri(pdfUri, newName);
                    if (newUri != null) {
                        Toast.makeText(this, "Renamed to: " + newName, Toast.LENGTH_LONG).show();
                        showPDFOptionsDialog(newUri, newName, "Saved to: Downloads/GalleryOrganizer/" + newName);
                    } else {
                        Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private Uri renamePdfUri(Uri oldUri, String newName) {
        try {
            if (Build.VERSION.SDK_INT >= 29 && "com.android.providers.downloads.documents".equals(oldUri.getAuthority())) {
                // Try simple update of DISPLAY_NAME
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, newName);
                int rows = getContentResolver().update(oldUri, cv, null, null);
                if (rows > 0) return oldUri;

                // Fallback: copy to new, delete old
                ContentValues cv2 = new ContentValues();
                cv2.put(MediaStore.Downloads.DISPLAY_NAME, newName);
                cv2.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                cv2.put(MediaStore.Downloads.RELATIVE_PATH, DL_REL);
                Uri newUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv2);
                if (newUri == null) return null;
                try (InputStream in = getContentResolver().openInputStream(oldUri);
                     java.io.OutputStream os = getContentResolver().openOutputStream(newUri, "w")) {
                    if (in == null || os == null) { getContentResolver().delete(newUri, null, null); return null; }
                    byte[] buf = new byte[8192]; int len;
                    while ((len = in.read(buf)) > 0) os.write(buf, 0, len);
                }
                getContentResolver().delete(oldUri, null, null);
                return newUri;
            } else if (Build.VERSION.SDK_INT >= 29) {
                // If it is a MediaStore Uri in Downloads collection
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, newName);
                int rows = getContentResolver().update(oldUri, cv, null, null);
                if (rows > 0) return oldUri;

                ContentValues cv2 = new ContentValues();
                cv2.put(MediaStore.Downloads.DISPLAY_NAME, newName);
                cv2.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                cv2.put(MediaStore.Downloads.RELATIVE_PATH, DL_REL);
                Uri newUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv2);
                if (newUri == null) return null;
                try (InputStream in = getContentResolver().openInputStream(oldUri);
                     java.io.OutputStream os = getContentResolver().openOutputStream(newUri, "w")) {
                    if (in == null || os == null) { getContentResolver().delete(newUri, null, null); return null; }
                    byte[] buf = new byte[8192]; int len;
                    while ((len = in.read(buf)) > 0) os.write(buf, 0, len);
                }
                getContentResolver().delete(oldUri, null, null);
                return newUri;
            } else {
                // Legacy file rename
                File oldFile = new File(oldUri.getPath());
                File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File dir = new File(dl, "GalleryOrganizer");
                if (!dir.exists()) dir.mkdirs();
                File newFile = new File(dir, newName);
                if (oldFile.renameTo(newFile)) {
                    return FileProvider.getUriForFile(this, getPackageName()+".provider", newFile);
                }
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void showDetails(PhotoInfo p) {
        String name = ""; long size = -1; int w = -1, h = -1; String date = "";
        String folder = getFolderNameForPhoto(p);

        try {
            if (p.path.startsWith("content://")) {
                Uri u = Uri.parse(p.path);
                String[] proj = { MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE,
                        MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
                        MediaStore.Images.Media.DATE_TAKEN };
                try (Cursor c = getContentResolver().query(u, proj, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        int idxN = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                        int idxS = c.getColumnIndex(MediaStore.Images.Media.SIZE);
                        int idxW = c.getColumnIndex(MediaStore.Images.Media.WIDTH);
                        int idxH = c.getColumnIndex(MediaStore.Images.Media.HEIGHT);
                        int idxD = c.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
                        if (idxN >= 0) name = c.getString(idxN);
                        if (idxS >= 0) size = c.getLong(idxS);
                        if (idxW >= 0) w = c.getInt(idxW);
                        if (idxH >= 0) h = c.getInt(idxH);
                        if (idxD >= 0) date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(new java.util.Date(c.getLong(idxD)));
                    }
                }
                if (w <= 0 || h <= 0) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    try (InputStream in = getContentResolver().openInputStream(u)) {
                        BitmapFactory.decodeStream(in, null, o);
                    }
                    w = o.outWidth; h = o.outHeight;
                }
            } else {
                File f = new File(p.path);
                name = f.getName();
                size = f.exists() ? f.length() : -1;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(p.path, o);
                w = o.outWidth; h = o.outHeight;
                long lm = f.lastModified();
                if (lm > 0) date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(new java.util.Date(lm));
            }
        } catch (Exception ignored) {}

        String tags = p.tags == null ? "" : p.tags;
        boolean fav = p.favorite;
        String msg = "Name: " + name +
                "\nFolder: " + (folder.isEmpty() ? "—" : folder) +
                "\nSize: " + (size >= 0 ? (size/1024) + " KB" : "—") +
                "\nResolution: " + (w>0&&h>0 ? w + "x" + h : "—") +
                "\nDate: " + (date.isEmpty() ? "—" : date) +
                "\nTags: " + (tags.isEmpty() ? "—" : tags) +
                "\nFavorite: " + (fav ? "Yes" : "No") +
                "\nPath: " + p.path;

        new AlertDialog.Builder(this).setTitle("Details").setMessage(msg)
                .setPositiveButton("OK", null).show();
    }

    private String getFolderNameForPhoto(PhotoInfo p) {
        try {
            if (p.path.startsWith("content://")) {
                Uri u = Uri.parse(p.path);
                try (Cursor c = getContentResolver().query(u,
                        new String[]{MediaStore.Images.Media.RELATIVE_PATH}, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        String rel = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH));
                        if (rel != null && rel.startsWith(BASE_REL)) {
                            String rem = rel.substring(BASE_REL.length());
                            if (rem.endsWith("/")) rem = rem.substring(0, rem.length()-1);
                            int slash = rem.indexOf("/");
                            return slash > 0 ? rem.substring(0, slash) : rem;
                        }
                    }
                }
            } else {
                String path = p.path.replace("\\", "/");
                int i = path.indexOf("/Pictures/GalleryOrganizer/");
                if (i > 0) {
                    String rem = path.substring(i + "/Pictures/GalleryOrganizer/".length());
                    int slash = rem.indexOf("/");
                    return slash > 0 ? rem.substring(0, slash) : rem;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private Bitmap decodeBitmapSmart(String path, int reqW, int reqH) {
        try {
            if (isContentUri(path)) {
                ContentResolver cr = getContentResolver();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                InputStream bounds = cr.openInputStream(Uri.parse(path));
                BitmapFactory.decodeStream(bounds, null, o);
                if (bounds != null) bounds.close();
                o.inJustDecodeBounds = false;
                o.inSampleSize = calcInSample(o, reqW, reqH);
                try (InputStream in = cr.openInputStream(Uri.parse(path))) {
                    return BitmapFactory.decodeStream(in, null, o);
                }
            } else {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, o);
                o.inJustDecodeBounds = false;
                o.inSampleSize = calcInSample(o, reqW, reqH);
                return BitmapFactory.decodeFile(path, o);
            }
        } catch (Exception e) { return null; }
    }

    private int calcInSample(BitmapFactory.Options o, int reqW, int reqH) {
        int h = o.outHeight, w = o.outWidth, s = 1;
        if (h > reqH || w > reqW) {
            int hh = h / 2, hw = w / 2;
            while ((hh / s) >= reqH && (hw / s) >= reqW) s *= 2;
        }
        return s;
    }

    private void deleteSelectedPhotos() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Photos")
                .setMessage("Delete " + selectedPhotos.size() + " photos?")
                .setPositiveButton("Delete", (d, w) -> {
                    for (PhotoInfo p : selectedPhotos) {
                        try {
                            if (isContentUri(p.path)) {
                                getContentResolver().delete(Uri.parse(p.path), null, null);
                            } else {
                                File f = new File(p.path);
                                if (f.exists()) f.delete();
                            }
                            PhotoStore.removeByExactPath(prefs, p.path);
                        } catch (Exception ignored) {}
                    }
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    clearSelection();
                    loadPhotos();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearSelection() {
        selectedPhotos.clear();
        if (adapter != null) adapter.setSelectedPhotos(selectedPhotos);
        updateTitle();
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA) {
            if (hasMediaPermission()) {
                loadPhotos();
            } else {
                Toast.makeText(this, "Permission required to view photos", Toast.LENGTH_LONG).show();
            }
        }
    }
}