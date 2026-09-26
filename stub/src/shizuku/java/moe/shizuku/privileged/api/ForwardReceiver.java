package moe.shizuku.privileged.api;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public class ForwardReceiver extends BroadcastReceiver {

    public static final String SHEVERY_PACKAGE = "com.hamondev.shevery";
    public static final String SHEVERY_RECEIVER = "moe.shizuku.manager.receiver.ShizukuReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (!isSheveryInstalled(context)) {
                return;
            }

            Intent forward = new Intent(intent);
            forward.setPackage(SHEVERY_PACKAGE);
            forward.setComponent(new ComponentName(SHEVERY_PACKAGE, SHEVERY_RECEIVER));

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