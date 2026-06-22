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

/**
 * Handles all notification creation and management for the location tracking service.
 *
 * Responsibilities:
 *  - Create the notification channel (required API 26+, safe to call repeatedly)
 *  - Build the initial foreground service notification
 *  - Update the notification content when a new location fix arrives
 *  - Provide the notification ID used by the service to call startForeground()
 *
 * The service calls this class exclusively — no notification code lives in the
 * service itself. This keeps NotificationCompat setup isolated and testable.
 */
public class NotificationHelper {

    private static final String TAG = Constants.LOG_TAG + "/NotificationHelper";

    private final Context context;
    private final NotificationManager notificationManager;

    /**
     * @param context Any context — application context extracted internally
     *                to prevent Activity leaks in a long-lived helper.
     */
    public NotificationHelper(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager =
                (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    // ─── Channel Setup ───────────────────────────────────────────────────────

    /**
     * Create the notification channel for location tracking updates.
     *
     * Required on API 26+ — notifications posted without a valid channel
     * are silently dropped on these versions. On API < 26 this is a no-op.
     *
     * Idempotent: calling this multiple times with the same channel ID is
     * safe — Android ignores duplicate creation calls. We call it on every
     * service start to cover the case where the service starts from a
     * BootReceiver before the app UI has ever been opened.
     *
     * Channel properties intentionally set to low-impact:
     *  - IMPORTANCE_LOW: no sound, no vibration, no heads-up popup
     *  - setShowBadge(false): no app icon badge for a background service
     *  - setSound(null, null): explicitly silence this channel
     */
    public void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,
                    Constants.NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW  // silent — no sound or vibration
            );
            channel.setDescription(Constants.NOTIFICATION_CHANNEL_DESC);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.enableLights(false);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created/verified: "
                        + Constants.NOTIFICATION_CHANNEL_ID);
            } else {
                Log.e(TAG, "createNotificationChannel: NotificationManager is null.");
            }
        }
    }

    // ─── Notification Building ───────────────────────────────────────────────

    /**
     * Build the initial foreground service notification shown when tracking starts.
     *
     * This notification:
     *  - Is ongoing (cannot be swiped away by the user)
     *  - Opens MainActivity when tapped
     *  - Uses PRIORITY_LOW to avoid sound/vibration on every update
     *  - Includes a stop action so the user can stop tracking from the notification
     *
     * The service passes this to startForeground(NOTIFICATION_ID, notification).
     *
     * @return A fully built Notification ready for startForeground().
     */
    @NonNull
    public Notification buildTrackingNotification() {
        return buildNotification(
                context.getString(R.string.notification_title_tracking),
                context.getString(R.string.notification_text_waiting)
        );
    }

    /**
     * Build an updated notification showing the most recent location fix.
     *
     * Called after each successful location update. Uses the same notification ID
     * as the initial notification — the system updates the existing notification
     * in-place rather than showing a new one.
     *
     * @param latitude  Latest latitude, decimal degrees.
     * @param longitude Latest longitude, decimal degrees.
     * @param timeStr   Formatted time string of the fix (e.g. "14:35:22").
     * @return Updated Notification to post via NotificationManager.notify().
     */
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

    /**
     * Internal builder used by all public build methods.
     * Centralises NotificationCompat options that apply to every notification
     * this helper produces, avoiding repetition between the variants.
     *
     * PendingIntent flags:
     *  FLAG_UPDATE_CURRENT — if a pending intent for this action already exists,
     *  update its extras rather than creating a new one.
     *  FLAG_IMMUTABLE — required API 31+. Our intent doesn't need to be mutated
     *  by the receiving component, so immutable is correct and more secure.
     *
     * @param title   Notification title line.
     * @param content Notification body/content line.
     * @return Fully configured Notification.
     */
    @NonNull
    private Notification buildNotification(@NonNull String title, @NonNull String content) {
        // Tapping the notification opens MainActivity.
        // singleTop launch mode on the Activity prevents stacking.
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // FLAG_IMMUTABLE required on API 31+; available from API 23.
            // Using it whenever possible (API 23+) is a security best practice.
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                pendingFlags
        );

        return new NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_location_notification)
                .setContentIntent(openAppPendingIntent)
                .setOngoing(true)           // cannot be dismissed by swipe
                .setPriority(NotificationCompat.PRIORITY_LOW)   // no sound, no popup
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(false)         // don't show timestamp — it doesn't update live
                .setForegroundServiceBehavior(
                        NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE  // show immediately, no delay
                )
                .build();
    }

    // ─── Notification Update ─────────────────────────────────────────────────

    /**
     * Post an updated notification in-place using the fixed notification ID.
     *
     * The service calls this after building an updated notification via
     * buildLocationUpdateNotification(). Using the same NOTIFICATION_ID
     * as startForeground() updates the existing notification without
     * creating a new one or making sound.
     *
     * @param notification The updated notification to display.
     */
    public void updateNotification(@NonNull Notification notification) {
        if (notificationManager != null) {
            notificationManager.notify(Constants.NOTIFICATION_ID, notification);
        }
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────────

    /**
     * Cancel the tracking notification.
     * The service calls stopForeground(true) which handles this automatically,
     * but this method is available for edge cases where manual cancellation
     * is needed (e.g. if the service couldn't start properly).
     */
    public void cancelNotification() {
        if (notificationManager != null) {
            notificationManager.cancel(Constants.NOTIFICATION_ID);
            Log.d(TAG, "Notification cancelled.");
        }
    }
}
