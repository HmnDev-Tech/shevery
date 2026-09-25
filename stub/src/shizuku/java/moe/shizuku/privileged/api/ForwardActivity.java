package moe.shizuku.privileged.api;

import android.app.Activity;
import android.content.Intent;
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
            forward.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            startActivity(forward);
        } catch (Throwable ignored) {
        }

        finish();
    }
}
