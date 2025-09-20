package com.nader.galleryorganizer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_PERMISSIONS = 101;
    private static final String BASE_REL = "Pictures/GalleryOrganizer/";
    private static final String STATE_TEMP_PATH = "state_temp_path";
    private static final String STATE_TEMP_URI = "state_temp_uri";
    private SharedPreferences prefs;

    // Camera temp capture (FileProvider)
    private File tempCaptureFile;
    private Uri tempCaptureUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("GalleryOrganizerPrefs", MODE_PRIVATE);
        boolean dark = prefs.getBoolean("dark", false);
        AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // restore temp state across process/rotation
        if (savedInstanceState != null) {
            String p = savedInstanceState.getString(STATE_TEMP_PATH, null);
            String u = savedInstanceState.getString(STATE_TEMP_URI, null);
            if (p != null) tempCaptureFile = new File(p);
            if (u != null) tempCaptureUri = Uri.parse(u);
        }

        Button btnOpenCamera = findViewById(R.id.btnOpenCamera);
        Button btnYourGallery = findViewById(R.id.btnYourGallery);
        Button btnAbout = findViewById(R.id.btnAbout);

        // Optional views resolved dynamically
        int switchId = getResources().getIdentifier("switchTheme", "id", getPackageName());
        Switch switchTheme = switchId != 0 ? findViewById(switchId) : null;
        int fabId = getResources().getIdentifier("fabCamera", "id", getPackageName());
        FloatingActionButton fabCamera = fabId != 0 ? findViewById(fabId) : null;

        btnOpenCamera.setOnClickListener(v -> openCamera());
        btnYourGallery.setOnClickListener(v -> startActivity(new Intent(this, GalleryMenuActivity.class)));
        btnAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        if (fabCamera != null) fabCamera.setOnClickListener(v -> openCamera());

        if (switchTheme != null) {
            switchTheme.setChecked(dark);
            switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("dark", isChecked).apply();
                AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            });
        }

        checkPermissions();

        if (getIntent().getBooleanExtra("openCamera", false)) {
            openCamera();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (tempCaptureFile != null)
            outState.putString(STATE_TEMP_PATH, tempCaptureFile.getAbsolutePath());
        if (tempCaptureUri != null) outState.putString(STATE_TEMP_URI, tempCaptureUri.toString());
    }

    private void checkPermissions() {
        ArrayList<String> need = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    // Create a temp file in app-specific external storage (stable across OEM cameras)
    private File createTempImageFile() {
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir == null) dir = getExternalFilesDir(null);
            File tempDir = new File(dir, "temp");
            if (!tempDir.exists()) tempDir.mkdirs();
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            return new File(tempDir, "capture_" + ts + ".jpg");
        } catch (Exception e) {
            return null;
        }
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }
        try {
            // 1) create a temp file
            tempCaptureFile = createTempImageFile();
            if (tempCaptureFile == null) {
                Toast.makeText(this, "Cannot create temp file", Toast.LENGTH_SHORT).show();
                return;
            }
            // 2) get FileProvider URI
            tempCaptureUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", tempCaptureFile);

            // 3) launch camera
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, tempCaptureUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                intent.setClipData(ClipData.newRawUri("output", tempCaptureUri));
            } catch (Throwable ignored) {
            }

            // grant to all camera activities
            for (android.content.pm.ResolveInfo ri :
                    getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
                grantUriPermission(ri.activityInfo.packageName, tempCaptureUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, REQUEST_CAMERA);
            } else {
                Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAMERA) {
            if (resultCode == RESULT_OK && tempCaptureFile != null && tempCaptureFile.exists() && tempCaptureFile.length() > 0) {
                showSaveDialog();
            } else {
                cleanupTemp();
                Toast.makeText(this, "Camera canceled or failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showSaveDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Save Photo - " + getCurrentDateString())
                .setMessage("Choose how to save this photo:")
                .setPositiveButton("Save in Current Folder", (d, w) -> showExistingFoldersDialog())
                .setNeutralButton("Save in New Folder", (d, w) -> showNewFolderDialog())
                .setNegativeButton("Cancel", (d, w) -> cleanupTemp())
                .show();
    }

    private void showExistingFoldersDialog() {
        String[] names = getFolderNames();
        if (names.length == 0) {
            Toast.makeText(this, "No folders found. Create new folder.", Toast.LENGTH_SHORT).show();
            showNewFolderDialog();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Select Folder")
                .setItems(names, (dialog, which) -> moveTempToFolder(names[which]))
                .setNegativeButton("Cancel", (d, w) -> cleanupTemp())
                .show();
    }

    private void showNewFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create New Folder");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(p, p, p, p);

        final EditText et = new EditText(this);
        et.setHint("Folder name");
        et.setText(getCurrentDateString() + "_Photos");
        layout.addView(et);
        builder.setView(layout);

        builder.setPositiveButton("Create & Save", (d, w) -> {
            String name = et.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter a folder name", Toast.LENGTH_SHORT).show();
                cleanupTemp();
                return;
            }
            // Track empty folder so it shows in UI even before first photo
            FolderStore.add(prefs, name);
            moveTempToFolder(name);
        });
        builder.setNegativeButton("Cancel", (d, w) -> cleanupTemp());
        builder.show();
    }

    // Copy from FileProvider temp file into MediaStore at the chosen folder
    private void moveTempToFolder(String destFolderName) {
        if (tempCaptureFile == null || !tempCaptureFile.exists()) {
            Toast.makeText(this, "No temp image", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String display = "IMG_" + ts + ".jpg";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, display);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, BASE_REL + destFolderName + "/");
            Uri finalUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (finalUri == null) {
                Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show();
                cleanupTemp();
                return;
            }

            try (FileInputStream in = new FileInputStream(tempCaptureFile);
                 OutputStream out = getContentResolver().openOutputStream(finalUri, "w")) {
                if (out == null) throw new RuntimeException("Output stream null");
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }

            try {
                tempCaptureFile.delete();
            } catch (Exception ignored) {
            }
            tempCaptureFile = null;
            tempCaptureUri = null;

            PhotoStore.addNew(prefs, finalUri.toString(), "", false);
            Toast.makeText(this, "Saved to " + destFolderName, Toast.LENGTH_SHORT).show();
            showPhotoOptionsDialog(finalUri.toString());
        } catch (Exception e) {
            Toast.makeText(this, "Save error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            cleanupTemp();
        }
    }

    private void showPhotoOptionsDialog(String photoPath) {
        new AlertDialog.Builder(this)
                .setTitle("Photo Saved")
                .setMessage("Add tags or mark favorite?")
                .setPositiveButton("Add Tags", (d, w) -> showAddTagsDialog(photoPath))
                .setNeutralButton("⭐ Favorite", (d, w) -> {
                    PhotoStore.setFavorite(prefs, photoPath, true);
                    Toast.makeText(this, "Marked favorite", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Done", null)
                .show();
    }

    private void showAddTagsDialog(String photoPath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Tags");
        final EditText et = new EditText(this);
        et.setHint("comma,separated,tags");
        builder.setView(et);
        builder.setPositiveButton("Save", (d, w) -> {
            String tags = et.getText().toString().trim();
            if (!tags.isEmpty()) {
                PhotoStore.setTags(prefs, photoPath, tags);
                Toast.makeText(this, "Tags saved", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void cleanupTemp() {
        try {
            if (tempCaptureFile != null && tempCaptureFile.exists()) tempCaptureFile.delete();
        } catch (Exception ignored) {
        }
        tempCaptureFile = null;
        tempCaptureUri = null;
    }

    private String getCurrentDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private String[] getFolderNames() {
        Set<String> names = new LinkedHashSet<>(FolderStore.get(prefs));
        if (Build.VERSION.SDK_INT >= 29) {
            String[] proj = {MediaStore.Images.Media.RELATIVE_PATH};
            String sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
            String[] args = new String[]{BASE_REL + "%"};
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
            } catch (Exception ignored) {
            }
        } else {
            File base = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryOrganizer");
            File[] subs = base.listFiles(File::isDirectory);
            if (subs != null) for (File f : subs) names.add(f.getName());
        }
        return names.toArray(new String[0]);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean cameraGranted = false;
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.CAMERA.equals(permissions[i]) &&
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    cameraGranted = true;
                }
            }
            if (cameraGranted) {
                openCamera();
            } else {
                Toast.makeText(this, "Permissions required for camera", Toast.LENGTH_LONG).show();
            }
        }
    }
}