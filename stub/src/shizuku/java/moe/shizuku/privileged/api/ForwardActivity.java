package moe.shizuku.privileged.api;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

public class ForwardActivity extends Activity {

    public static final String SHEVERY_PACKAGE = "com.hamondev.shevery";
    public static final String PERMISSION_ACTIVITY = "moe.shizuku.manager.authorization.RequestPermissionActivity";
    public static final String LEGACY_ACTIVITY = "moe.shizuku.manager.legacy.LegacyIsNotSupportedActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Intent original = getIntent();
            Intent forward = new Intent(original);
            forward.setPackage(SHEVERY_PACKAGE);

            String action = original.getAction();
            if (action != null && action.contains("REQUEST_AUTHORIZATION")) {
                forward.setClassName(SHEVERY_PACKAGE, LEGACY_ACTIVITY);
            } else {
                forward.setClassName(SHEVERY_PACKAGE, PERMISSION_ACTIVITY);
            }

            if (original.getExtras() != null) {
                forward.putExtras(original.getExtras());
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
            } else {
                forward.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            startActivity(forward);
            overridePendingTransition(0, 0);
        } catch (Throwable ignored) {
        }

        finish();
        overridePendingTransition(0, 0);
    }
}
