package com.rosan.dhizuku;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

public class ForwardDhizukuActivity extends Activity {

    public static final String SHEVERY_PACKAGE = "com.hamondev.shevery";
    public static final String SHEVERY_REQUEST_ACTIVITY = "moe.shizuku.manager.authorization.RequestPermissionActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Intent forward = new Intent();
            forward.setComponent(new ComponentName(SHEVERY_PACKAGE, SHEVERY_REQUEST_ACTIVITY));
            String action = getIntent().getAction();
            if (action != null) {
                forward.setAction(action);
            } else {
                forward.setAction("com.rosan.dhizuku.action.REQUEST_DHIZUKU_PERMISSION");
            }
            if (getIntent().getExtras() != null) {
                forward.putExtras(getIntent().getExtras());
            }

            String callingPackage = getCallingPackage();
            int uid = -1;
            try {
                java.lang.reflect.Method m = Activity.class.getMethod("getLaunchedFromUid");
                Object res = m.invoke(this);
                if (res instanceof Integer) uid = (Integer) res;
            } catch (Throwable ignored) {}
            if (callingPackage == null) {
                try {
                    java.lang.reflect.Method m = Activity.class.getMethod("getLaunchedFromPackage");
                    Object res = m.invoke(this);
                    if (res instanceof String) callingPackage = (String) res;
                } catch (Throwable ignored) {}
            }
            if (callingPackage == null) {
                try {
                    Uri referrer = getReferrer();
                    if (referrer != null && "android-app".equals(referrer.getScheme())) {
                        callingPackage = referrer.getAuthority();
                    }
                } catch (Throwable ignored) {}
            }
            if (uid == -1 && callingPackage != null) {
                try {
                    uid = getPackageManager().getPackageUid(callingPackage, 0);
                } catch (Throwable ignored) {}
            }

            if (callingPackage != null) {
                forward.putExtra("callingPackage", callingPackage);
                forward.putExtra("packageName", callingPackage);
                forward.putExtra("client_package_name", callingPackage);
            }
            if (uid != -1) {
                forward.putExtra("uid", uid);
                forward.putExtra("client_uid", uid);
            }

            if (getCallingActivity() != null) {
                forward.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            }
            startActivity(forward);
            overridePendingTransition(0, 0);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        finish();
        overridePendingTransition(0, 0);
    }
}
