package com.zayan.locationtracker.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.zayan.locationtracker.R;
import com.zayan.locationtracker.ui.main.MainActivity;
import com.zayan.locationtracker.utils.Constants;

public class NotificationHelper {

    private static final String TAG = Constants.LOG_TAG + "/NotificationHelper";

    private final Context context;
    private final NotificationManager notificationManager;

    public NotificationHelper(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager =
                (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,
                    Constants.NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(Constants.NOTIFICATION_CHANNEL_DESC);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.enableLights(false);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created: " + Constants.NOTIFICATION_CHANNEL_ID);
            } else {
                Log.e(TAG, "createNotificationChannel: NotificationManager is null.");
            }
        }
    }

    @NonNull
    public Notification buildTrackingNotification() {
        return buildNotification(
                context.getString(R.string.notification_title_tracking),
                context.getString(R.string.notification_text_waiting)
        );
    }

    @NonNull
    public Notification buildLocationUpdateNotification(
            double latitude, double longitude, @NonNull String timeStr) {
        String contentText = String.format(
                context.getString(R.string.notification_text_location),
                latitude, longitude, timeStr
        );
        return buildNotification(
                context.getString(R.string.notification_title_tracking),
                contentText
        );
    }

    @NonNull
    private Notification buildNotification(@NonNull String title, @NonNull String content) {
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent, pendingFlags);

        return new NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_location_notification)
                .setContentIntent(openAppPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    public void updateNotification(@NonNull Notification notification) {
        if (notificationManager != null) {
            notificationManager.notify(Constants.NOTIFICATION_ID, notification);
        }
    }

    public void cancelNotification() {
        if (notificationManager != null) {
            notificationManager.cancel(Constants.NOTIFICATION_ID);
            Log.d(TAG, "Notification cancelled.");
        }
    }
}
