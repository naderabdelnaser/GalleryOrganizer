package com.nader.galleryorganizer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.ViewHolder> {

    public interface OnPhotoClickListener { void onPhotoClick(PhotoInfo photo); }
    public interface OnPhotoLongClickListener { void onPhotoLongClick(PhotoInfo photo); }

    private List<PhotoInfo> photos;
    private List<PhotoInfo> selectedPhotos;
    private final OnPhotoClickListener clickListener;
    private final OnPhotoLongClickListener longClickListener;

    public PhotoAdapter(List<PhotoInfo> photos, OnPhotoClickListener c, OnPhotoLongClickListener l) {
        this.photos = photos != null ? photos : new ArrayList<>();
        this.selectedPhotos = new ArrayList<>();
        this.clickListener = c;
        this.longClickListener = l;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView dateText, tagsText;
        ImageView favoriteIcon;
        ViewHolder(View v) {
            super(v);
            imageView = v.findViewById(R.id.imageViewPhoto);
            dateText = v.findViewById(R.id.textViewDate);
            tagsText = v.findViewById(R.id.textViewTags);
            favoriteIcon = v.findViewById(R.id.imageViewFavorite);
        }
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        if (photos == null || position >= photos.size()) return;
        PhotoInfo photo = photos.get(position);
        loadImage(h.imageView, photo.path);

        if (h.dateText != null) h.dateText.setText(photo.date == null || photo.date.isEmpty() ? "No date" : photo.date);
        if (h.tagsText != null) h.tagsText.setText(photo.tags == null || photo.tags.isEmpty() ? "No tags" : photo.tags);
        if (h.favoriteIcon != null) h.favoriteIcon.setVisibility(photo.favorite ? View.VISIBLE : View.GONE);

        boolean selected = selectedPhotos.contains(photo);
        h.itemView.setAlpha(selected ? 0.7f : 1f);

        h.itemView.setOnClickListener(v -> { if (clickListener != null) clickListener.onPhotoClick(photo); });
        h.itemView.setOnLongClickListener(v -> { if (longClickListener != null) longClickListener.onPhotoLongClick(photo); return true; });
    }

    private void loadImage(ImageView iv, String path) {
        try {
            if (path == null || path.isEmpty()) { iv.setImageResource(android.R.drawable.ic_menu_gallery); return; }
            File f = new File(path);
            if (!f.exists()) { iv.setImageResource(android.R.drawable.ic_menu_gallery); return; }
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = 4;
            android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(path, o);
            if (bm != null) { iv.setImageBitmap(bm); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); }
            else iv.setImageResource(android.R.drawable.ic_menu_gallery);
        } catch (Exception e) {
            iv.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    @Override public int getItemCount() { return photos != null ? photos.size() : 0; }

    public void updatePhotos(List<PhotoInfo> newPhotos) {
        this.photos = newPhotos != null ? newPhotos : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setSelectedPhotos(List<PhotoInfo> selected) {
        this.selectedPhotos = selected != null ? selected : new ArrayList<>();
        notifyDataSetChanged();
    }
}