package com.example.melodix.listener;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.example.melodix.database.DownloadedMusicContract;
import com.example.melodix.database.DownloadedMusicDbHelper;
import com.example.melodix.model.Track;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicDownloader {
    private static final String TAG = "MusicDownloader";
    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface DownloadCallback {
        void onDownloadStarted();
        void onDownloadProgress(int progress);
        void onDownloadComplete(File downloadedFile);
        void onDownloadError(String message);
    }

    public static void downloadTrack(Context context, Track track, DownloadCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String userId = currentUser != null ? currentUser.getUid() : "anonymous";

        if (track == null || track.getPreviewUrl() == null) {
            mainHandler.post(() -> callback.onDownloadError("Invalid track or preview URL"));
            return;
        }
        if (isTrackDownloaded(context, track.getId())) {
            mainHandler.post(() -> {
                File downloadsDir = new File(context.getExternalFilesDir(null), "downloads");
                String filename = track.getId() + "_" + sanitizeFilename(track.getTitle()) + ".mp3";
                File outputFile = new File(downloadsDir, filename);
                if (outputFile.exists()) {
                    callback.onDownloadComplete(outputFile);
                } else {
                    Toast.makeText(context, "Track already in your downloads", Toast.LENGTH_SHORT).show();
                    callback.onDownloadComplete(null);
                }
            });
            return;
        }
        File downloadsDir = new File(context.getExternalFilesDir(null), "downloads");
        if (!downloadsDir.exists()) {
            if (!downloadsDir.mkdirs()) {
                mainHandler.post(() -> callback.onDownloadError("Could not create downloads directory"));
                return;
            }
        }
        String filename = track.getId() + "_" + sanitizeFilename(track.getTitle()) + ".mp3";
        File outputFile = new File(downloadsDir, filename);
        if (outputFile.exists()) {
            mainHandler.post(() -> callback.onDownloadComplete(outputFile));
            return;
        }

        mainHandler.post(callback::onDownloadStarted);

        executorService.execute(() -> {
            HttpURLConnection connection = null;
            InputStream input = null;
            FileOutputStream output = null;

            try {
                URL url = new URL(track.getPreviewUrl());
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP error code: " + connection.getResponseCode());
                }
                int fileLength = connection.getContentLength();
                input = connection.getInputStream();
                output = new FileOutputStream(outputFile);

                byte[] data = new byte[4096];
                long total = 0;
                int count;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        final int progress = (int) (total * 100 / fileLength);
                        mainHandler.post(() -> callback.onDownloadProgress(progress));
                    }

                    output.write(data, 0, count);
                }
                saveTrackToDatabase(context, track, outputFile.getAbsolutePath());
                saveToCloudDownloadHistory(context, track, outputFile.getAbsolutePath(), new UserPreferencesManager.DataCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean success) {
                        Log.d(TAG, "Successfully synced download to cloud: " + track.getTitle());
                    }
                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "Failed to sync download to cloud (offline mode): " + error);
                    }
                });
                mainHandler.post(() -> callback.onDownloadComplete(outputFile));

            } catch (Exception e) {
                Log.e(TAG, "Download error", e);
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                mainHandler.post(() -> callback.onDownloadError("Download failed: " + e.getMessage()));
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (IOException ignored) { }

                if (connection != null) connection.disconnect();
            }
        });
    }
    private static void saveToCloudDownloadHistory(Context context, Track track, String localFilePath, UserPreferencesManager.DataCallback<Boolean> callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "User not logged in, skipping cloud sync");
            if (callback != null) callback.onError("User not logged in");
            return;
        }
        UserPreferencesManager.addToDownloadHistoryAsync(context, track, localFilePath, callback);
    }

    private static String sanitizeFilename(String input) {
        return input.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static void saveTrackToDatabase(Context context, Track track, String filePath) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String userId = currentUser != null ? currentUser.getUid() : "anonymous";

        DownloadedMusicDbHelper dbHelper = new DownloadedMusicDbHelper(context);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(DownloadedMusicContract.TrackEntry.COLUMN_USER_ID, userId);
        values.put(DownloadedMusicContract.TrackEntry.COLUMN_TRACK_ID, track.getId());
        values.put(DownloadedMusicContract.TrackEntry.COLUMN_TITLE, track.getTitle());
        values.put(DownloadedMusicContract.TrackEntry.COLUMN_ARTIST, track.getArtist() != null ? track.getArtist().getName() : "Unknown");
        values.put(DownloadedMusicContract.TrackEntry.COLUMN_FILE_PATH, filePath);
        values.put(DownloadedMusicContract.TrackEntry.COLUMN_DURATION, track.getDuration());
        if (track.getAlbum() != null) {
            values.put(DownloadedMusicContract.TrackEntry.COLUMN_ALBUM_ART, track.getAlbum().getCoverMedium());
        }
        values.put(DownloadedMusicContract.TrackEntry.COLUMN_DOWNLOAD_DATE, System.currentTimeMillis());

        db.insert(DownloadedMusicContract.TrackEntry.TABLE_NAME, null, values);
        db.close();
    }

    public static boolean isTrackDownloaded(Context context, long trackId) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String userId = currentUser != null ? currentUser.getUid() : "anonymous";
        return isTrackDownloaded(context, userId, trackId);
    }

    public static boolean isTrackDownloaded(Context context, String userId, long trackId) {
        DownloadedMusicDbHelper dbHelper = new DownloadedMusicDbHelper(context);
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String selection = DownloadedMusicContract.TrackEntry.COLUMN_USER_ID + " = ? AND " +
                DownloadedMusicContract.TrackEntry.COLUMN_TRACK_ID + " = ?";
        String[] selectionArgs = { userId, String.valueOf(trackId) };

        boolean isDownloaded = false;

        try {
            android.database.Cursor cursor = db.query(
                    DownloadedMusicContract.TrackEntry.TABLE_NAME,
                    new String[] { DownloadedMusicContract.TrackEntry._ID },
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );

            isDownloaded = cursor != null && cursor.getCount() > 0;

            if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking if track is downloaded", e);
        } finally {
            db.close();
        }

        return isDownloaded;
    }
}