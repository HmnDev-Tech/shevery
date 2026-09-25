package com.rosan.dhizuku;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public class ForwardDhizukuProvider extends ContentProvider {

    public static final String SHEVERY_AUTHORITY = "com.hamondev.shevery.dhizuku_server.provider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        try {
            if (getContext() != null) {
                Uri uri = Uri.parse("content://" + SHEVERY_AUTHORITY);
                return getContext().getContentResolver().call(uri, method, arg, extras);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
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
