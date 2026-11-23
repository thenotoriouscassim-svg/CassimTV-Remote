package com.cassimtv.remote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences("tv_settings", Context.MODE_PRIVATE);
        String deviceId = prefs.getString("device_id", null);
        String token = prefs.getString("receiver_token", null);
        if (deviceId == null || token == null) return;
        Intent service = new Intent(context, TvReceiverService.class);
        service.putExtra("device_id", deviceId);
        service.putExtra("receiver_token", token);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
        else context.startService(service);
    }
}
