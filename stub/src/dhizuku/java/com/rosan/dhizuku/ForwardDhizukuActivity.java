package com.rosan.dhizuku;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
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
            forward.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            startActivity(forward);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        finish();
    }
}
