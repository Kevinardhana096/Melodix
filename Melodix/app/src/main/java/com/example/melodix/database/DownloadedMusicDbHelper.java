package com.example.melodix.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DownloadedMusicDbHelper extends SQLiteOpenHelper {
    private static final String TAG = "DownloadedMusicDbHelper";
    private static final String DATABASE_NAME = "downloadedmusic.db";
    private static final int DATABASE_VERSION = 2;

    public DownloadedMusicDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        final String SQL_CREATE_ENTRIES =
                "CREATE TABLE " + DownloadedMusicContract.TrackEntry.TABLE_NAME + " (" +
                        DownloadedMusicContract.TrackEntry._ID + " INTEGER PRIMARY KEY," +
                        DownloadedMusicContract.TrackEntry.COLUMN_USER_ID + " TEXT NOT NULL," +
                        DownloadedMusicContract.TrackEntry.COLUMN_TRACK_ID + " INTEGER," +
                        DownloadedMusicContract.TrackEntry.COLUMN_TITLE + " TEXT," +
                        DownloadedMusicContract.TrackEntry.COLUMN_ARTIST + " TEXT," +
                        DownloadedMusicContract.TrackEntry.COLUMN_FILE_PATH + " TEXT," +
                        DownloadedMusicContract.TrackEntry.COLUMN_DURATION + " INTEGER," +
                        DownloadedMusicContract.TrackEntry.COLUMN_ALBUM_ART + " TEXT," +
                        DownloadedMusicContract.TrackEntry.COLUMN_DOWNLOAD_DATE + " INTEGER," +
                        "UNIQUE(" + DownloadedMusicContract.TrackEntry.COLUMN_USER_ID + ", " +
                        DownloadedMusicContract.TrackEntry.COLUMN_TRACK_ID + "))";

        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + DownloadedMusicContract.TrackEntry.TABLE_NAME +
                    " ADD COLUMN " + DownloadedMusicContract.TrackEntry.COLUMN_USER_ID + " TEXT DEFAULT ''");
        }
    }
    public String getTrackFilePath(long trackId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String currentUser = getCurrentUserId();

        String[] projection = {DownloadedMusicContract.TrackEntry.COLUMN_FILE_PATH};
        String selection = DownloadedMusicContract.TrackEntry.COLUMN_USER_ID + "=? AND " +
                DownloadedMusicContract.TrackEntry.COLUMN_TRACK_ID + "=?";
        String[] selectionArgs = {currentUser, String.valueOf(trackId)};

        Cursor cursor = null;
        try {
            cursor = db.query(
                    DownloadedMusicContract.TrackEntry.TABLE_NAME,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );

            if (cursor.moveToFirst()) {
                int filePathIndex = cursor.getColumnIndex(DownloadedMusicContract.TrackEntry.COLUMN_FILE_PATH);
                if (filePathIndex >= 0) {
                    String filePath = cursor.getString(filePathIndex);
                    Log.d(TAG, "File path for track " + trackId + ": " + filePath);
                    return filePath;
                }
            }

            Log.d(TAG, "No file path found for track " + trackId);
            return null;

        } catch (Exception e) {
            Log.e(TAG, "Error getting track file path: " + e.getMessage(), e);
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String getCurrentUserId() {
        // 🔥 TODO: Implement based on your authentication system
        // For now, return the hardcoded user from your log
        return "mobilesaperhenibus";
    }
}