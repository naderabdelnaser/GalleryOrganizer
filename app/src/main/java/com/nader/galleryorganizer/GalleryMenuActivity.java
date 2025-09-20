package com.nader.galleryorganizer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class GalleryMenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery_menu);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Your Gallery");
        }

        Button btnFolders = findViewById(R.id.btnFolders);
        Button btnPhotos = findViewById(R.id.btnPhotos);

        btnFolders.setOnClickListener(v -> {
            Intent intent = new Intent(this, FoldersActivity.class);
            startActivity(intent);
        });

        btnPhotos.setOnClickListener(v -> {
            Intent intent = new Intent(this, PhotosActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}