package com.grobo.notifications.utils;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.grobo.notifications.R;
import com.grobo.notifications.work.DeleteWorker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.grobo.notifications.utils.Constants.KEY_CURRENT_VERSION;
import static com.grobo.notifications.utils.Constants.LOGIN_STATUS;
import static com.grobo.notifications.utils.Constants.ROLL_NUMBER;
import static com.grobo.notifications.utils.Constants.USER_BRANCH;
import static com.grobo.notifications.utils.Constants.USER_YEAR;

public class MyApplication extends Application {

    SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(this);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        createNotificationChannel();
        subscribeFcmTopics();

        scheduleTask();

        remoteConfig();
    }

    private void subscribeFcmTopics() {
        FirebaseMessaging fcm = FirebaseMessaging.getInstance();

        fcm.subscribeToTopic("all");
        fcm.subscribeToTopic("dev");
        if (prefs.getBoolean(LOGIN_STATUS, false)) {
            fcm.subscribeToTopic(prefs.getString(USER_BRANCH, "junk"));
            fcm.subscribeToTopic(prefs.getString(USER_YEAR, "junk"));
            fcm.subscribeToTopic(prefs.getString(USER_YEAR, "junk") + prefs.getString(USER_BRANCH, ""));
            fcm.subscribeToTopic(prefs.getString(ROLL_NUMBER, "junk"));
        }
    }


    private void scheduleTask() {
        Constraints constraints = new Constraints.Builder()
                .setRequiresCharging(true)
                .build();

        PeriodicWorkRequest deleteRequest = new PeriodicWorkRequest.Builder(DeleteWorker.class, 1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork("delete_feed", ExistingPeriodicWorkPolicy.KEEP, deleteRequest);
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(getString(R.string.default_notification_channel_id), name, importance);
            channel.enableLights(true);
            channel.setLightColor(Color.BLUE);
            channel.setDescription(getString(R.string.default_notification_channel_description));
            channel.enableVibration(true);
            channel.setShowBadge(true);

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void remoteConfig() {
        final FirebaseRemoteConfig firebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        Map<String, Object> remoteConfigDefaults = new HashMap<>();
        remoteConfigDefaults.put(Constants.KEY_UPDATE_REQUIRED, false);
        remoteConfigDefaults.put(Constants.KEY_CURRENT_VERSION, "1.0.0");
        remoteConfigDefaults.put(Constants.TIMETABLE_URL,"https://timetable-grobo.firebaseio.com/");
        remoteConfigDefaults.put( Constants.MESS_MENU_URL,"https://i.ytimg.com/vi/OjIXzZ25tjA/maxresdefault.jpg" );

        firebaseRemoteConfig.setDefaults(remoteConfigDefaults);
        // fetch every minutes

        firebaseRemoteConfig.fetch(60).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    Log.e("Application", "remote config is fetched.");
                    firebaseRemoteConfig.activateFetched();
                    Log.e("Application", firebaseRemoteConfig.getString(KEY_CURRENT_VERSION));
                } else {
                    Log.e("Application", "remote config fetch failed.");
                }
            }
        });
    }
}
