package moe.shizuku.privileged.api;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

/**
 * Compatibility ContentProvider inside the Shizuku Stub ({@code moe.shizuku.privileged.api}).
 * <p>
 * Offloads legacy {@code moe.shizuku.privileged.api.shizuku} and
 * {@code moe.shizuku.privileged.api.connector} provider authorities from the main Shevery
 * APK and forwards binder/connector requests to {@code com.hamondev.shevery}.
 */
public class ForwardShizukuProvider extends ContentProvider {

    public static final String SHEVERY_PACKAGE = "com.hamondev.shevery";
    public static final String SHEVERY_PROVIDER_AUTHORITY = SHEVERY_PACKAGE + ".shevery";
    public static final String SHEVERY_LEGACY_PROVIDER_AUTHORITY = SHEVERY_PACKAGE + ".shizuku";
    public static final String SHEVERY_CONNECTOR_AUTHORITY = SHEVERY_PACKAGE + ".connector";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        Bundle forwardExtras = extras != null ? new Bundle(extras) : new Bundle();
        forwardExtras.putString("com.hamondev.shevery.intent.extra.PERMISSION_CHANNEL", "shizuku_stub");
        forwardExtras.putString("com.hamondev.shevery.intent.extra.REQUESTED_PERMISSION", "moe.shizuku.manager.permission.API_V23");

        String[] targetAuthorities = new String[]{
                SHEVERY_PROVIDER_AUTHORITY,
                SHEVERY_LEGACY_PROVIDER_AUTHORITY
        };
        for (String authority : targetAuthorities) {
            try {
                Bundle result;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    result = context.getContentResolver().call(Uri.parse("content://" + authority), method, arg, forwardExtras);
                } else {
                    result = context.getContentResolver().call(authority, method, arg, forwardExtras);
                }
                if (result != null) {
                    return result;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        try {
            Uri targetUri = Uri.parse("content://" + SHEVERY_CONNECTOR_AUTHORITY);
            return context.getContentResolver().query(targetUri, projection, selection, selectionArgs, sortOrder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
