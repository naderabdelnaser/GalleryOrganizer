package com.nader.galleryorganizer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("About " + getString(R.string.app_name));
        }

        Button btnContact = findViewById(R.id.btnContact);
        Button btnCollaborate = findViewById(R.id.btnCollaborate);
        Button btnSuggest = findViewById(R.id.btnSuggest);

        btnContact.setOnClickListener(v ->
                sendContactOptions("Contact",
                        "Hello, I would like to get in touch regarding " + getString(R.string.app_name) + ":")
        );

        btnCollaborate.setOnClickListener(v ->
                sendContactOptions("Collaboration Inquiry",
                        "Hello, I am interested in collaborating on the " + getString(R.string.app_name) + " project:")
        );

        // Turn "Suggest Feature" button into "Privacy Policy" and show it in a dialog
        btnSuggest.setText("Privacy Policy");
        btnSuggest.setOnClickListener(v -> showPrivacyPolicyDialog());
    }

    private void sendContactOptions(String subject, String body) {
        String[] options = {"WhatsApp", "Email"};

        new android.app.AlertDialog.Builder(this)
                .setTitle("Contact via")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // WhatsApp
                        try {
                            String url = "https://wa.me/201060747927?text=" + Uri.encode(body);
                            Intent whatsappIntent = new Intent(Intent.ACTION_VIEW);
                            whatsappIntent.setData(Uri.parse(url));
                            startActivity(whatsappIntent);
                        } catch (Exception e) {
                            android.widget.Toast.makeText(this,
                                    "WhatsApp not installed", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 1) {
                        // Email
                        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                        emailIntent.setData(Uri.parse("mailto:"));
                        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"nader3bnaser@gmail.com"});
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " - " + subject);
                        emailIntent.putExtra(Intent.EXTRA_TEXT, body);

                        try {
                            startActivity(Intent.createChooser(emailIntent, "Send Email"));
                        } catch (android.content.ActivityNotFoundException ex) {
                            android.widget.Toast.makeText(this,
                                    "No email app found. Email: nader3bnaser@gmail.com",
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .show();
    }

    private void showPrivacyPolicyDialog() {
        ScrollView sv = new ScrollView(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        TextView tv = new TextView(this);
        tv.setText(getPrivacyPolicyText());
        tv.setTextSize(14);
        tv.setLineSpacing(0, 1.2f);
        tv.setPadding(pad, pad, pad, pad);
        tv.setTextIsSelectable(true);

        sv.addView(tv);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Privacy Policy")
                .setView(sv)
                .setPositiveButton("Close", null)
                .show();
    }

    private String getPrivacyPolicyText() {
        String app = getString(R.string.app_name);
        String lastUpdated = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());

        return app + " – Privacy Policy\n"
                + "Last updated: " + lastUpdated + "\n\n"
                + "Overview\n"
                + app + " helps you capture photos, organize them into folders, add tags and favorites, and create PDFs — all locally on your device. "
                + "We do not collect, store, or transmit your personal data to any server.\n\n"

                + "Data We Access & Purpose\n"
                + "- Camera: To capture photos when you use the in‑app camera or the Camera widget.\n"
                + "- Photos/Media (Android 13+ READ_MEDIA_IMAGES; older Android may use Storage permission): To read, save, copy, move, and delete photos you select inside the app.\n"
                + "- Widgets: Camera widget opens the camera quickly; Recent Photos widget shows a count and opens your gallery.\n\n"

                + "What We Store\n"
                + "- Photos: Saved to your device under Pictures/GalleryOrganizer (MediaStore on Android 10+).\n"
                + "- Metadata (tags/favorites/dates): Stored locally in app preferences on your device.\n"
                + "- PDFs: Saved to Downloads/GalleryOrganizer by default.\n\n"

                + "How Your Data Is Used\n"
                + "- All processing happens on your device. We do not upload your photos or tags.\n"
                + "- Sharing is only performed when you tap Share and pick a target app. We grant temporary read permission for that share only.\n\n"

                + "Third‑Party Libraries\n"
                + "- AndroidX libraries (FileProvider, MediaStore) for secure file access and storage.\n"
                + "- Glide (image loading) to display thumbnails efficiently.\n"
                + "These libraries run locally and do not send your data to remote servers.\n\n"

                + "Security Measures\n"
                + "- Uses MediaStore (scoped storage) on Android 10+.\n"
                + "- Uses FileProvider content URIs for secure, permission‑scoped sharing.\n"
                + "- No analytics, no ads, no tracking, and no external data collection.\n\n"

                + "Your Choices & Controls\n"
                + "- You control all saves, shares, copies, and deletes.\n"
                + "- Deleting the app removes app data (tags/favorites). Photos saved to system folders remain on device unless you delete them.\n"
                + "- You can remove any widget at any time from your home screen.\n\n"

                + "Children’s Privacy\n"
                + app + " is not directed to children under 13. We do not knowingly collect personal information from children.\n\n"

                + "International Data Transfers\n"
                + "- None. All data stays on your device.\n\n"

                + "Changes to This Policy\n"
                + "- We may update this policy to reflect app changes. We’ll update the “Last updated” date above.\n\n"

                + "Contact\n"
                + "- Email: nader3bnaser@gmail.com\n\n"

                + "By using " + app + ", you agree with this policy. If you disagree, please stop using the app and uninstall it.";
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}