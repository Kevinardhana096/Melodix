package com.example.melodix.listener;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;

import com.example.melodix.api.DeezerRepository;
import com.example.melodix.database.DownloadedMusicDbHelper;
import com.example.melodix.model.Track;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayer {
    private static final String TAG = "MusicPlayer";
    private static MusicPlayer instance;
    private MediaPlayer mediaPlayer;
    private boolean isPrepared = false;
    private boolean isPlaying = false;
    private OnPlaybackStatusListener playbackStatusListener;
    private Handler handler;
    private Runnable updateSeekBarTask;
    private Track currentTrack;
    private List<Track> playlist = new ArrayList<>();
    private int currentTrackIndex = -1;
    private boolean autoPlayNext = true;
    private List<Integer> playedTrackIndices = new ArrayList<>();
    private Context applicationContext;
    public interface OnPlaybackStatusListener {
        void onPlaybackStarted();
        void onPlaybackPaused();
        void onPlaybackStopped();
        void onPlaybackCompleted();
        void onDurationChanged(int duration);
        void onPositionChanged(int position);
        void onPrepareStart();
        void onPrepareComplete();
        void onError(String message);
        default void onTrackChanged(Track newTrack) {}
        default void onPlaylistChanged(List<Track> playlist, int currentIndex) {}
    }
    public boolean isPlaying() {
        return isPlaying;
    }
    private MusicPlayer() {
        mediaPlayer = new MediaPlayer();
        handler = new Handler();
        mediaPlayer.setOnCompletionListener(mp -> {
            isPlaying = false;
            if (playbackStatusListener != null) {
                playbackStatusListener.onPlaybackCompleted();
            }
            if (autoPlayNext) {
                playNextTrack();
            }
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
            isPlaying = false;
            isPrepared = false;
            if (playbackStatusListener != null) {
                playbackStatusListener.onError("Media player error: " + what);
            }
            return true;
        });
        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            if (playbackStatusListener != null) {
                playbackStatusListener.onPrepareComplete();
                playbackStatusListener.onDurationChanged(mp.getDuration());
            }
            play();
        });
    }
    public static synchronized MusicPlayer getInstance() {
        if (instance == null) {
            instance = new MusicPlayer();
        }
        return instance;
    }
    public void setPlaybackStatusListener(OnPlaybackStatusListener listener) {
        this.playbackStatusListener = listener;
    }

    private void savePlaybackState() {
        if (applicationContext == null) return;
        UserPreferencesManager.saveCurrentTrack(applicationContext, currentTrack);
        UserPreferencesManager.savePlaybackPosition(applicationContext, mediaPlayer.getCurrentPosition());
        UserPreferencesManager.savePlaybackQueue(applicationContext, playlist);
        Log.d(TAG, "Playback state saved");
    }

    public Track getCurrentTrack() {
        return currentTrack;
    }

    public void setPlaylist(Context context, List<Track> tracks, int startTrackIndex) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        playlist.clear();
        playlist.addAll(tracks);
        playedTrackIndices.clear();

        if (startTrackIndex < 0) startTrackIndex = 0;
        if (startTrackIndex >= tracks.size()) startTrackIndex = tracks.size() - 1;

        currentTrackIndex = startTrackIndex;
        playedTrackIndices.add(currentTrackIndex);

        for (Track track : playlist) {
            track.setContext(context);
        }

        prepareFromUrl(context, playlist.get(currentTrackIndex));
        if (playbackStatusListener != null) {
            playbackStatusListener.onPlaylistChanged(playlist, currentTrackIndex);
        }
    }
    public boolean playRandomTrack() {
        if (playlist.isEmpty()) {
            Log.e(TAG, "Cannot play random track: playlist is empty");
            if (applicationContext != null && playlist.isEmpty()) {
                List<Track> savedQueue = UserPreferencesManager.getPlaybackQueue(applicationContext);
                if (savedQueue != null && !savedQueue.isEmpty()) {
                    Log.d(TAG, "Restoring queue for random play.");
                    playlist.addAll(savedQueue);
                    Track savedCurrentTrack = UserPreferencesManager.getCurrentTrack(applicationContext);
                    if (savedCurrentTrack != null) {
                        for (int i = 0; i < playlist.size(); i++) {
                            if (playlist.get(i).getId() == savedCurrentTrack.getId()) {
                                currentTrackIndex = i;
                                break;
                            }
                        }
                    }
                    if (currentTrackIndex == -1 && !playlist.isEmpty()) currentTrackIndex = 0; // Default to first if not found
                } else {
                    return false;
                }
            } else if (applicationContext == null) {
                 Log.e(TAG, "Application context is null, cannot restore queue for random play");
                 return false;
            }
        }
        if (playlist.size() == 1) {
            currentTrackIndex = 0;
            Context context = getValidContext();
            if (context == null) {
                Log.e(TAG, "Cannot play track: context is null");
                return false;
            }
            prepareFromUrl(context, playlist.get(0));
            return true;
        }
        if (playedTrackIndices.size() >= playlist.size() - 1) {
            Log.d(TAG, "All tracks have been played, resetting play history");
            playedTrackIndices.clear();
            if (currentTrackIndex >= 0 && currentTrackIndex < playlist.size()) {
                playedTrackIndices.add(currentTrackIndex);
            }
        }
        int randomIndex;
        int maxAttempts = 10;
        int attempts = 0;

        do {
            randomIndex = (int) (Math.random() * playlist.size());
            attempts++;
            if (attempts > maxAttempts) {
                boolean foundUnplayed = false;
                for (int i = 0; i < playlist.size(); i++) {
                    if (!playedTrackIndices.contains(i)) {
                        randomIndex = i;
                        foundUnplayed = true;
                        break;
                    }
                }
                if (!foundUnplayed && !playlist.isEmpty()) {
                    randomIndex = 0;
                    playedTrackIndices.clear();
                }
                break;
            }
        } while (playedTrackIndices.contains(randomIndex));
        playedTrackIndices.add(randomIndex);
        currentTrackIndex = randomIndex;

        if (currentTrackIndex < 0 || currentTrackIndex >= playlist.size()) {
            Log.e(TAG, "Invalid track index: " + currentTrackIndex + ", playlist size: " + playlist.size());
            return false;
        }

        Log.d(TAG, "Playing track index " + randomIndex + ", played history size: " + playedTrackIndices.size());
        Context context = getValidContext();

        if (context != null) {
            Track randomTrack = playlist.get(currentTrackIndex);
            Log.d(TAG, "Playing random track: " + randomTrack.getTitle());
            if (playbackStatusListener != null) {
                playbackStatusListener.onTrackChanged(randomTrack);
            }
            prepareFromUrl(context, randomTrack);
            return true;
        } else {
            Log.e(TAG, "Context is null, can't play random track");
            return false;
        }
    }
    private Context getValidContext() {
        Context context = null;
        if (context == null && currentTrack != null) {
            context = currentTrack.getContext();
        }
        if (context == null && !playlist.isEmpty()) {
            for (Track track : playlist) {
                if (track != null && track.getContext() != null) {
                    context = track.getContext();
                    break;
                }
            }
        }

        return context;
    }
    public boolean playNextTrack() {
        if (playlist.isEmpty()) {
            Log.e(TAG, "Cannot play next track: playlist is empty");
            return false;
        }
        if (currentTrackIndex >= playlist.size() - 1) {
            currentTrackIndex = 0;
        } else {
            currentTrackIndex++;
        }
        Context context = getValidContext();
        if (context != null) {
            Track nextTrack = playlist.get(currentTrackIndex);
            Log.d(TAG, "Playing next track: " + nextTrack.getTitle());
            if (!playedTrackIndices.contains(currentTrackIndex)) {
                playedTrackIndices.add(currentTrackIndex);
            }
            currentTrack = nextTrack;
            if (playbackStatusListener != null) {
                playbackStatusListener.onTrackChanged(nextTrack);
            }
            prepareFromUrl(context, nextTrack);
            return true;
        } else {
            Log.e(TAG, "Context is null, can't play next track");
            return false;
        }
    }
    public boolean playPreviousTrack() {
        if (playlist.isEmpty()) {
            Log.e(TAG, "Cannot play previous track: playlist is empty");
            return false;
        }
        if (getCurrentPosition() > 3000) {
            seekTo(0);
            return true;
        }
        if (currentTrackIndex <= 0) {
            currentTrackIndex = playlist.size() - 1;
        } else {
            currentTrackIndex--;
        }
        Context context = getValidContext();
        if (context != null) {
            if (currentTrackIndex >= 0 && currentTrackIndex < playlist.size()) {
                Track prevTrack = playlist.get(currentTrackIndex);
                Log.d(TAG, "Playing previous track: " + prevTrack.getTitle());
                currentTrack = prevTrack;

                if (!playedTrackIndices.contains(currentTrackIndex)) {
                    playedTrackIndices.add(currentTrackIndex);
                }

                if (playbackStatusListener != null) {
                    playbackStatusListener.onTrackChanged(prevTrack);
                }
                prepareFromUrl(context, prevTrack);
                return true;
            } else {
                Log.e(TAG, "Invalid track index: " + currentTrackIndex);
                return false;
            }
        } else {
            Log.e(TAG, "Context is null, can't play previous track");
            return false;
        }
    }
    public boolean hasPreviousTrack() {
        return !playlist.isEmpty() && currentTrackIndex > 0;
    }

    public void prepareFromUrl(Context context, Track track) {
        prepareFromUrl(context, track, true);
    }

    public void prepareFromUrl(Context context, Track track, boolean autoPlay) {
        Log.d(TAG, "=== PREPARE FROM URL ===");
        Log.d(TAG, "Track: " + (track != null ? track.getTitle() : "null"));
        Log.d(TAG, "Auto play: " + autoPlay);

        if (track == null) {
            Log.e(TAG, "❌ Track is null, cannot prepare.");
            if (playbackStatusListener != null) {
                playbackStatusListener.onError("Invalid track");
            }
            return;
        }
        if (context == null) {
            Log.e(TAG, "❌ Context is null, cannot prepare.");
            if (playbackStatusListener != null) {
                playbackStatusListener.onError("Context unavailable");
            }
            return;
        }
        if (track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) {
            Log.e(TAG, "❌ No preview URL available for track: " + track.getTitle());
            if (playbackStatusListener != null) {
                playbackStatusListener.onError("No preview available for this track");
            }
            return;
        }
        currentTrack = track;
        this.applicationContext = context.getApplicationContext();

        if (playbackStatusListener != null) {
            playbackStatusListener.onPrepareStart();
        }
        prepareMediaPlayerWithRetry(track, autoPlay, 0);
    }
    private void prepareMediaPlayerWithRetry(Track track, boolean autoPlay, int retryCount) {
        final int MAX_RETRIES = 2;

        Log.d(TAG, "=== PREPARING MEDIA PLAYER ===");
        Log.d(TAG, "Retry count: " + retryCount);
        Log.d(TAG, "Track: " + track.getTitle());
        Log.d(TAG, "URL: " + track.getPreviewUrl());

        try {
            mediaPlayer.reset();
            isPrepared = false;

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build();
            mediaPlayer.setAudioAttributes(audioAttributes);
            prepareDataSource(track);
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d(TAG, "✅ MediaPlayer prepared successfully");
                isPrepared = true;

                if (playbackStatusListener != null) {
                    playbackStatusListener.onPrepareComplete();
                    playbackStatusListener.onDurationChanged(mp.getDuration());
                }

                if (autoPlay) {
                    play();
                }

                savePlaybackState();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "❌ MediaPlayer error: what=" + what + ", extra=" + extra);
                isPrepared = false;
                isPlaying = false;

                handleMediaPlayerError(track, autoPlay, retryCount, what, extra);
                return true;
            });

            Log.d(TAG, "Starting async preparation...");
            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            Log.e(TAG, "❌ IOException in prepareMediaPlayer: " + e.getMessage(), e);
            handleIOException(track, autoPlay, retryCount, e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "❌ IllegalArgumentException: " + e.getMessage(), e);
            handlePreparationError(track, autoPlay, retryCount, "Invalid URL format");
        } catch (IllegalStateException e) {
            Log.e(TAG, "❌ IllegalStateException: " + e.getMessage(), e);
            handlePreparationError(track, autoPlay, retryCount, "MediaPlayer in invalid state");
        } catch (Exception e) {
            Log.e(TAG, "❌ Unexpected error: " + e.getMessage(), e);
            handlePreparationError(track, autoPlay, retryCount, "Unexpected error: " + e.getMessage());
        }
    }
    private void prepareDataSource(Track track) throws IOException {
        String url = track.getPreviewUrl();

        Log.d(TAG, "Preparing data source for URL: " + url);

        if (isLocalFile(url)) {
            Log.d(TAG, "📁 Setting up local file");
            android.net.Uri uri = android.net.Uri.parse(url);
            mediaPlayer.setDataSource(applicationContext, uri);
        } else if (isDeezerUrl(url)) {
            Log.d(TAG, "🎵 Setting up Deezer URL with headers");
            setupDeezerDataSource(url);
        } else {
            Log.d(TAG, "🌐 Setting up standard URL");
            mediaPlayer.setDataSource(url);
        }
    }
    private void setupDeezerDataSource(String url) throws IOException {
        Log.d(TAG, "Setting up Deezer URL: " + url);

        if (isDeezerUrlExpired(url)) {
            Log.w(TAG, "⚠️ Deezer URL appears to be expired, but trying anyway");
        }
        try {
            mediaPlayer.setDataSource(url);
            Log.d(TAG, "✅ Deezer URL set successfully (without headers)");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to set Deezer URL: " + e.getMessage());
            throw new IOException("Failed to set Deezer data source: " + e.getMessage(), e);
        }
    }

    private boolean isDeezerUrl(String url) {
        return url != null && url.contains("dzcdn.net");
    }
    private boolean isLocalFile(String url) {
        return url != null && url.startsWith("file://");
    }
    private boolean isDeezerUrlExpired(String url) {
        try {
            if (url.contains("exp=")) {
                String[] parts = url.split("exp=");
                if (parts.length > 1) {
                    String expPart = parts[1].split("~")[0];
                    long expTime = Long.parseLong(expPart);
                    long currentTime = System.currentTimeMillis() / 1000;

                    boolean isExpired = currentTime > expTime;
                    Log.d(TAG, "URL expiration check - Current: " + currentTime + ", Expires: " + expTime + ", Expired: " + isExpired);
                    return isExpired;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse URL expiration: " + e.getMessage());
        }
        return false;
    }
    private void handleMediaPlayerError(Track track, boolean autoPlay, int retryCount, int what, int extra) {
        final int MAX_RETRIES = 2;

        String errorMsg = "MediaPlayer error: " + what + " (extra: " + extra + ")";
        Log.e(TAG, "🔥 " + errorMsg);

        String userMessage = mapErrorToUserMessage(what, extra);

        if (retryCount < MAX_RETRIES) {
            Log.d(TAG, "🔄 Retrying preparation (attempt " + (retryCount + 1) + "/" + MAX_RETRIES + ")");

            handler.postDelayed(() -> {
                prepareMediaPlayerWithRetry(track, autoPlay, retryCount + 1);
            }, 1000 * (retryCount + 1));

        } else {
            Log.e(TAG, "❌ Max retries reached, trying alternative strategies");
            tryAlternativeStrategies(track, autoPlay, userMessage);
        }
    }

    private void handleIOException(Track track, boolean autoPlay, int retryCount, IOException e) {
        final int MAX_RETRIES = 2;

        Log.e(TAG, "🔥 IOException: " + e.getMessage());

        if (retryCount < MAX_RETRIES) {
            Log.d(TAG, "🔄 Retrying after IOException (attempt " + (retryCount + 1) + "/" + MAX_RETRIES + ")");

            handler.postDelayed(() -> {
                prepareMediaPlayerWithRetry(track, autoPlay, retryCount + 1);
            }, 2000 * (retryCount + 1));

        } else {
            String userMessage = "Network error. Please check your connection.";
            tryAlternativeStrategies(track, autoPlay, userMessage);
        }
    }

    private void handlePreparationError(Track track, boolean autoPlay, int retryCount, String errorMessage) {
        final int MAX_RETRIES = 2;

        if (retryCount < MAX_RETRIES && !errorMessage.contains("Invalid URL")) {
            Log.d(TAG, "🔄 Retrying after error: " + errorMessage);

            handler.postDelayed(() -> {
                prepareMediaPlayerWithRetry(track, autoPlay, retryCount + 1);
            }, 1500 * (retryCount + 1));

        } else {
            tryAlternativeStrategies(track, autoPlay, errorMessage);
        }
    }
    private void tryAlternativeStrategies(Track track, boolean autoPlay, String originalError) {
        Log.d(TAG, "🔄 Trying alternative strategies for: " + track.getTitle());

        if (track.isDownloaded() && applicationContext != null) {
            Log.d(TAG, "📱 Trying downloaded version");
            tryPlayDownloadedVersion(track, autoPlay, originalError);
            return;
        }

        if (isDeezerUrl(track.getPreviewUrl())) {
            Log.d(TAG, "🔄 Trying to refresh Deezer URL");
            tryRefreshTrackUrl(track, autoPlay, originalError);
            return;
        }

        if (!playlist.isEmpty() && playlist.size() > 1) {
            Log.d(TAG, "⏭️ Skipping to next track due to error");
            skipToNextTrackOnError(originalError);
            return;
        }

        Log.e(TAG, "❌ No alternative strategies available");
        if (playbackStatusListener != null) {
            playbackStatusListener.onError(originalError);
        }
    }

    private void tryPlayDownloadedVersion(Track track, boolean autoPlay, String originalError) {
        try {
            String localPath = getLocalFilePath(track);
            if (localPath != null && new java.io.File(localPath).exists()) {
                Log.d(TAG, "✅ Found local file: " + localPath);
                Track localTrack = createLocalTrack(track, localPath);
                prepareMediaPlayerWithRetry(localTrack, autoPlay, 0);
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error trying downloaded version: " + e.getMessage());
        }
        tryRefreshTrackUrl(track, autoPlay, originalError);
    }
    private String getLocalFilePath(Track track) {
        if (applicationContext == null) {
            Log.w(TAG, "Application context is null");
            return null;
        }
        try {
            DownloadedMusicDbHelper dbHelper = new DownloadedMusicDbHelper(applicationContext);
            String filePath = dbHelper.getTrackFilePath(track.getId());

            if (filePath != null) {
                Log.d(TAG, "Found local file path: " + filePath);
                java.io.File file = new java.io.File(filePath);
                if (file.exists() && file.canRead()) {
                    return filePath;
                } else {
                    Log.w(TAG, "Local file does not exist or cannot be read: " + filePath);
                    return null;
                }
            }
            Log.d(TAG, "No local file path found for track ID: " + track.getId());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting local file path: " + e.getMessage(), e);
            return null;
        }
    }
    private Track createLocalTrack(Track originalTrack, String localPath) {
        Track localTrack = originalTrack.copy();
        localTrack.setPreviewUrl("file://" + localPath);
        localTrack.setContext(applicationContext);
        return localTrack;
    }
    private void tryRefreshTrackUrl(Track track, boolean autoPlay, String originalError) {
        if (applicationContext == null) {
            skipToNextTrackOnError(originalError);
            return;
        }
        Log.d(TAG, "🔄 Attempting to refresh URL for: " + track.getTitle());
        DeezerRepository repository = DeezerRepository.getInstance();
        String searchQuery = track.getTitle();
        if (track.getArtist() != null) {
            searchQuery += " " + track.getArtist().getName();
        }
        repository.searchTracks(searchQuery, new DeezerRepository.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> tracks) {
                Track refreshedTrack = findMatchingTrack(tracks, track);

                if (refreshedTrack != null &&
                        refreshedTrack.getPreviewUrl() != null &&
                        !refreshedTrack.getPreviewUrl().equals(track.getPreviewUrl())) {

                    Log.d(TAG, "✅ Got refreshed URL, retrying");

                    track.setPreviewUrl(refreshedTrack.getPreviewUrl());
                    prepareMediaPlayerWithRetry(track, autoPlay, 0);

                } else {
                    Log.w(TAG, "❌ Could not get fresh URL");
                    skipToNextTrackOnError(originalError);
                }
            }
            @Override
            public void onError(String message) {
                Log.e(TAG, "❌ Error refreshing track: " + message);
                skipToNextTrackOnError(originalError);
            }
        });
    }
    private Track findMatchingTrack(List<Track> tracks, Track originalTrack) {
        if (tracks == null || tracks.isEmpty()) return null;
        for (Track track : tracks) {
            if (track.getId() == originalTrack.getId()) {
                return track;
            }
        }
        for (Track track : tracks) {
            if (track.getTitle().equalsIgnoreCase(originalTrack.getTitle()) &&
                    track.getArtist() != null && originalTrack.getArtist() != null &&
                    track.getArtist().getName().equalsIgnoreCase(originalTrack.getArtist().getName())) {
                return track;
            }
        }
        return null;
    }
    private void skipToNextTrackOnError(String originalError) {
        Log.d(TAG, "⏭️ Auto-skipping to next track due to error");

        if (playbackStatusListener != null) {
            playbackStatusListener.onError("Skipping track: " + originalError);
        }
        handler.postDelayed(() -> {
            if (!playNextTrack()) {
                if (playbackStatusListener != null) {
                    playbackStatusListener.onError("No more tracks available");
                }
            }
        }, 1000);
    }
    private String mapErrorToUserMessage(int what, int extra) {
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_IO:
                return "Network connection error. Please check your internet connection.";
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                return "Invalid audio format. Skipping to next track.";
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                return "Audio format not supported. Skipping to next track.";
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                return "Connection timed out. Please try again.";
            default:
                return "Playback error occurred. Trying next track.";
        }
    }
    public void play() {
        if (isPrepared && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            isPlaying = true;
            if (playbackStatusListener != null) {
                playbackStatusListener.onPlaybackStarted();
            }
            startProgressUpdate();
            savePlaybackState();
        } else if (!isPrepared) {
            Log.w(TAG, "MediaPlayer not prepared, cannot play.");
        }
    }

    public void pause() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            if (playbackStatusListener != null) {
                playbackStatusListener.onPlaybackPaused();
            }
            stopProgressUpdate();
            savePlaybackState();
        }
    }

    public void stop() {
        if (mediaPlayer.isPlaying() || isPrepared) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            isPrepared = false;
            isPlaying = false;
            currentTrack = null;
            currentTrackIndex = -1;
            if (playbackStatusListener != null) {
                playbackStatusListener.onPlaybackStopped();
            }
            stopProgressUpdate();
            UserPreferencesManager.clearPlaybackState(applicationContext);
        }
    }

    public void release() {
        stopProgressUpdate();
        if (mediaPlayer != null) {
            if (currentTrack != null && (isPlaying || isPrepared)) {
                savePlaybackState();
            } else {
                if (applicationContext != null) {
                    UserPreferencesManager.clearPlaybackState(applicationContext);
                }
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        instance = null;
        Log.d(TAG, "MusicPlayer released");
    }

    public void seekTo(int position) {
        if (isPrepared) {
            mediaPlayer.seekTo(position);
            if (playbackStatusListener != null) {
                playbackStatusListener.onPositionChanged(position);
            }
            savePlaybackState();
        }
    }

    public int getCurrentPosition() {
        if (isPrepared) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error getting current position", e);
                return 0;
            }
        }
        return 0;
    }
    public int getDuration() {
        if (isPrepared) {
            try {
                return mediaPlayer.getDuration();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error getting duration", e);
                return 0;
            }
        }
        return 0;
    }

    private void startProgressUpdate() {
        stopProgressUpdate();

        updateSeekBarTask = new Runnable() {
            @Override
            public void run() {
                if (isPrepared && isPlaying && playbackStatusListener != null) {
                    try {
                        int currentPosition = mediaPlayer.getCurrentPosition();
                        playbackStatusListener.onPositionChanged(currentPosition);
                        handler.postDelayed(this, 1000);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Error updating progress", e);
                    }
                }
            }
        };

        handler.post(updateSeekBarTask);
    }

    private void stopProgressUpdate() {
        if (handler != null && updateSeekBarTask != null) {
            handler.removeCallbacks(updateSeekBarTask);
        }
    }
    public List<Track> getPlaylist() {
        return new ArrayList<>(playlist);
    }
}

