package com.rosan.dhizuku;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;

public class ForwardDhizukuActivity extends Activity {

    public static final String PARAM_CLIENT_REQUEST_PERMISSION_BINDER = "request_permission_binder";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Intent original = getIntent();
            Bundle extras = original.getExtras();
            if (extras != null) {
                IBinder binder = extras.getBinder(PARAM_CLIENT_REQUEST_PERMISSION_BINDER);
                if (binder != null) {
                    android.os.Parcel data = android.os.Parcel.obtain();
                    android.os.Parcel reply = android.os.Parcel.obtain();
                    try {
                        data.writeInterfaceToken("com.rosan.dhizuku.api.IDhizukuRequestPermissionListener");
                        data.writeInt(PackageManager.PERMISSION_GRANTED);
                        binder.transact(android.os.IBinder.FIRST_CALL_TRANSACTION, data, reply, 0);
                        reply.readException();
                    } finally {
                        data.recycle();
                        reply.recycle();
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        finish();
    }
}
