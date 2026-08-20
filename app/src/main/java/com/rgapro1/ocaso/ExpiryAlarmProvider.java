package com.rgapro1.ocaso;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/** Auto-start hook: refreshes expiry alarms whenever the app process is created. */
public class ExpiryAlarmProvider extends ContentProvider {
    @Override public boolean onCreate() {
        if (getContext() != null) ExpiryAlarmScheduler.scheduleAll(getContext());
        return true;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
