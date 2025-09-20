package com.nader.galleryorganizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {
    public interface OnFolderClickListener { void onFolderClick(FolderItem folder); }
    public interface OnFolderLongClickListener { void onFolderLongClick(FolderItem folder); }

    private List<FolderItem> folders;
    private final OnFolderClickListener clickListener;
    private final OnFolderLongClickListener longClickListener;

    public FolderAdapter(List<FolderItem> folders,
                         OnFolderClickListener clickListener,
                         OnFolderLongClickListener longClickListener) {
        this.folders = folders;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        FolderItem folder = folders.get(position);
        h.folderName.setText(folder.name);
        h.photoCount.setText(folder.photoCount + " photos");
        try { h.folderIcon.setImageResource(R.drawable.ic_folder); }
        catch (Exception e) { h.folderIcon.setImageResource(android.R.drawable.ic_menu_gallery); }

        h.itemView.setOnClickListener(v -> { if (clickListener != null) clickListener.onFolderClick(folder); });
        h.itemView.setOnLongClickListener(v -> { if (longClickListener != null) longClickListener.onFolderLongClick(folder); return true; });
    }

    @Override
    public int getItemCount() { return folders != null ? folders.size() : 0; }

    public void updateFolders(List<FolderItem> newFolders) {
        this.folders = newFolders;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView folderIcon;
        TextView folderName;
        TextView photoCount;
        ViewHolder(View itemView) {
            super(itemView);
            folderIcon = itemView.findViewById(R.id.imageViewFolderIcon);
            folderName = itemView.findViewById(R.id.textViewFolderName);
            photoCount = itemView.findViewById(R.id.textViewPhotoCount);
        }
    }
}