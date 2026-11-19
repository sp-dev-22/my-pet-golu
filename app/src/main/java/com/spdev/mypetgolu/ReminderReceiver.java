package com.spdev.mypetgolu;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/**
 * Daily study reminder. SettingsActivity schedules/cancels it via the
 * static methods below; when the alarm fires, a notification invites
 * the student back to Golu Study Buddy for a quick revision.
 * (Manifest registration is done separately.)
 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final int REQUEST_CODE = 7001;
    private static final int NOTIFICATION_ID = 7001;
    private static final String CHANNEL_ID = "golu_reminder";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Study reminders", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Daily reminder to study with Golu");
            nm.createNotificationChannel(channel);
        }

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, REQUEST_CODE, open, pendingIntentFlags());

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setContentTitle("Time to study with Golu!")
                .setContentText("Open Golu Study Buddy to revise with Golu.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(contentIntent)
                .setAutoCancel(true);

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    /** Schedules the daily reminder at the time saved in Settings. */
    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.HOUR_OF_DAY, Prefs.getReminderHour(c));
        trigger.set(Calendar.MINUTE, Prefs.getReminderMinute(c));
        trigger.set(Calendar.SECOND, 0);
        trigger.set(Calendar.MILLISECOND, 0);
        if (trigger.getTimeInMillis() <= System.currentTimeMillis()) {
            trigger.add(Calendar.DAY_OF_YEAR, 1);
        }

        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, broadcastIntent(c));
    }

    /** Cancels the daily reminder. */
    public static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = broadcastIntent(c);
        if (am != null) am.cancel(pi);
        pi.cancel();
    }

    private static PendingIntent broadcastIntent(Context c) {
        Intent intent = new Intent(c, ReminderReceiver.class);
        return PendingIntent.getBroadcast(c, REQUEST_CODE, intent, pendingIntentFlags());
    }

    /** FLAG_IMMUTABLE exists from API 23; older versions just use UPDATE_CURRENT. */
    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
