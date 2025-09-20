package com.nader.galleryorganizer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class RecentPhotosWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.recent_photos_widget);

            // Tap opens Organized Gallery
            Intent i = new Intent(context, OrganizedGalleryActivity.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(context, appWidgetId, i, flags);

            views.setOnClickPendingIntent(R.id.btnOpenGallery, pi);
            // Ensure icon is set (in case of layout cache)
            views.setImageViewResource(R.id.btnOpenGallery, R.drawable.ic_newr_ecent);

            manager.updateAppWidget(appWidgetId, views);
        }
    }
}