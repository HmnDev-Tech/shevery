package moe.shizuku.privileged.api;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public class ForwardReceiver extends BroadcastReceiver {

    public static final String SHEVERY_PACKAGE = "com.hamondev.shevery";
    public static final String SHEVERY_RECEIVER = "moe.shizuku.manager.receiver.ShizukuReceiver";
    public static final String EXTRA_PERMISSION_CHANNEL = "com.hamondev.shevery.intent.extra.PERMISSION_CHANNEL";
    public static final String EXTRA_REQUESTED_PERMISSION = "com.hamondev.shevery.intent.extra.REQUESTED_PERMISSION";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (!isSheveryInstalled(context)) {
                return;
            }

            Intent forward = new Intent(intent);
            forward.setPackage(SHEVERY_PACKAGE);
            forward.setComponent(new ComponentName(SHEVERY_PACKAGE, SHEVERY_RECEIVER));
            forward.putExtra(EXTRA_PERMISSION_CHANNEL, "shizuku_stub");
            forward.putExtra(EXTRA_REQUESTED_PERMISSION, "moe.shizuku.manager.permission.API_V23");

            context.sendBroadcast(forward);
        } catch (Throwable ignored) {
        }
    }

    private boolean isSheveryInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(SHEVERY_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }
}