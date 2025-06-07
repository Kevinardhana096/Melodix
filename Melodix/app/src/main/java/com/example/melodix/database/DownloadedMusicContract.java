package com.example.melodix.database;

import android.provider.BaseColumns;

public final class DownloadedMusicContract {
    private DownloadedMusicContract() {}

    public static class TrackEntry implements BaseColumns {
        public static final String TABLE_NAME = "downloaded_tracks";
        public static final String COLUMN_USER_ID = "user_id";  // New column for user ID
        public static final String COLUMN_TRACK_ID = "track_id";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_ARTIST = "artist";
        public static final String COLUMN_FILE_PATH = "file_path";
        public static final String COLUMN_DURATION = "duration";
        public static final String COLUMN_ALBUM_ART = "album_art";
        public static final String COLUMN_DOWNLOAD_DATE = "download_date";
    }
}