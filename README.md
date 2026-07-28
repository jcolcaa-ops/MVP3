package com.jorge.registrollamadas;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

public class PhoneStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(AppConfig.KEY_ENABLED, false)) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state) ||
                TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            prefs.edit().putBoolean(AppConfig.KEY_CALL_ACTIVE, true).apply();
            return;
        }

        if (TelephonyManager.EXTRA_STATE_IDLE.equals(state) &&
                prefs.getBoolean(AppConfig.KEY_CALL_ACTIVE, false)) {
            prefs.edit().putBoolean(AppConfig.KEY_CALL_ACTIVE, false).apply();
            scheduleCapture(context);
        }
    }

    public static void scheduleCapture(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        int jobId = 41000 + (int) (System.currentTimeMillis() % 1000);
        JobInfo job = new JobInfo.Builder(jobId,
                new ComponentName(context, CallCaptureJobService.class))
                .setMinimumLatency(2500)
                .setOverrideDeadline(15000)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build();
        scheduler.schedule(job);
    }
}
