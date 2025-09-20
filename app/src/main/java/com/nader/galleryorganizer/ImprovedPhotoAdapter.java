package com.nader.galleryorganizer;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ImprovedPhotoAdapter extends RecyclerView.Adapter<ImprovedPhotoAdapter.ViewHolder> {
    public interface OnPhotoClickListener { void onPhotoClick(PhotoInfo photo); }
    public interface OnPhotoLongClickListener { void onPhotoLongClick(PhotoInfo photo); }

    private List<PhotoInfo> photos;
    private List<PhotoInfo> selectedPhotos;
    private final OnPhotoClickListener clickListener;
    private final OnPhotoLongClickListener longClickListener;

    public ImprovedPhotoAdapter(List<PhotoInfo> photos,
                                OnPhotoClickListener clickListener,
                                OnPhotoLongClickListener longClickListener) {
        this.photos = photos != null ? photos : new ArrayList<PhotoInfo>();
        this.selectedPhotos = new ArrayList<>();
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        View selectionOverlay;
        ImageView favoriteIcon;
        ImageView checkIcon;

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageViewPhoto);

            String pkg = itemView.getContext().getPackageName();
            int idSel = itemView.getResources().getIdentifier("selectionOverlay", "id", pkg);
            if (idSel != 0) selectionOverlay = itemView.findViewById(idSel);

            int idFav = itemView.getResources().getIdentifier("imageViewFavorite", "id", pkg);
            if (idFav != 0) favoriteIcon = itemView.findViewById(idFav);

            int idChk = itemView.getResources().getIdentifier("imageViewCheck", "id", pkg);
            if (idChk != 0) checkIcon = itemView.findViewById(idChk);
        }
    }

    private boolean isContentUri(String path) {
        return path != null && path.startsWith("content://");
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_improved_photo, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        if (photos == null || position >= photos.size()) return;
        PhotoInfo photo = photos.get(position);
        if (photo == null || h.imageView == null) return;

        Object source = isContentUri(photo.path) ? Uri.parse(photo.path) : new File(photo.path);
        try {
            Class.forName("com.bumptech.glide.Glide");
            com.bumptech.glide.Glide.with(h.itemView.getContext())
                    .load(source)
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(h.imageView);
        } catch (Throwable t) {
            try {
                if (isContentUri(photo.path)) {
                    ContentResolver cr = h.itemView.getContext().getContentResolver();
                    try (InputStream in = cr.openInputStream(Uri.parse(photo.path))) {
                        Bitmap bm = BitmapFactory.decodeStream(in);
                        if (bm != null) {
                            h.imageView.setImageBitmap(bm);
                            h.imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        } else {
                            h.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    }
                } else {
                    loadImageFallback(h.imageView, photo.path);
                }
            } catch (Exception e) {
                h.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        }

        boolean isSelected = selectedPhotos.contains(photo);
        if (h.selectionOverlay != null) h.selectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        if (h.checkIcon != null) h.checkIcon.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        h.itemView.setScaleX(isSelected ? 0.93f : 1.0f);
        h.itemView.setScaleY(isSelected ? 0.93f : 1.0f);
        if (h.favoriteIcon != null) h.favoriteIcon.setVisibility(photo.favorite ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> {
            if (!selectedPhotos.isEmpty()) {
                if (clickListener != null) clickListener.onPhotoClick(photo);
            } else {
                openWithGallery(v, photo);
            }
        });

        h.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onPhotoLongClick(photo);
            return true;
        });
    }

    private void loadImageFallback(ImageView imageView, String path) {
        try {
            if (path == null || path.isEmpty()) { imageView.setImageResource(android.R.drawable.ic_menu_gallery); return; }
            File f = new File(path);
            if (!f.exists()) { imageView.setImageResource(android.R.drawable.ic_menu_gallery); return; }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            o.inSampleSize = calcInSample(o, 200, 200);
            o.inJustDecodeBounds = false;
            Bitmap bm = BitmapFactory.decodeFile(path, o);
            if (bm != null) {
                imageView.setImageBitmap(bm);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        } catch (Exception e) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    private int calcInSample(BitmapFactory.Options o, int reqW, int reqH) {
        int h = o.outHeight, w = o.outWidth, s = 1;
        if (h > reqH || w > reqW) {
            int hh = h / 2, hw = w / 2;
            while ((hh / s) >= reqH && (hw / s) >= reqW) s *= 2;
        }
        return s;
    }

    private void openWithGallery(View v, PhotoInfo photo) {
        try {
            if (photo == null || photo.path == null || photo.path.isEmpty()) {
                Toast.makeText(v.getContext(), "Invalid photo", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(Intent.ACTION_VIEW);
            Uri uri;
            if (isContentUri(photo.path)) {
                uri = Uri.parse(photo.path);
                i.setDataAndType(uri, "image/*");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                File file = new File(photo.path);
                if (!file.exists()) { Toast.makeText(v.getContext(), "File missing", Toast.LENGTH_SHORT).show(); return; }
                uri = FileProvider.getUriForFile(v.getContext(), v.getContext().getPackageName()+".provider", file);
                i.setDataAndType(uri, "image/*");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            if (i.resolveActivity(v.getContext().getPackageManager()) != null) v.getContext().startActivity(i);
            else Toast.makeText(v.getContext(), "No viewer found", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(v.getContext(), "Open error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override public int getItemCount() { return photos != null ? photos.size() : 0; }

    public void updatePhotos(List<PhotoInfo> newPhotos) {
        this.photos = newPhotos != null ? newPhotos : new ArrayList<PhotoInfo>();
        notifyDataSetChanged();
    }

    public void setSelectedPhotos(List<PhotoInfo> selected) {
        this.selectedPhotos = selected != null ? selected : new ArrayList<PhotoInfo>();
        notifyDataSetChanged();
    }
}