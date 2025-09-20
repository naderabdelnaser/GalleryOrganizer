package com.nader.galleryorganizer;
import java.io.File;

public class FolderItem {
    public String name; // display name
    public String relativePath; // e.g., "Pictures/GalleryOrganizer/MyFolder/"
    public File file; // legacy (pre-Android 10)
    public int photoCount;
    public FolderItem(String name, String relativePath, File file, int photoCount) {
        this.name = name;
        this.relativePath = relativePath;
        this.file = file;
        this.photoCount = photoCount;
    }
}