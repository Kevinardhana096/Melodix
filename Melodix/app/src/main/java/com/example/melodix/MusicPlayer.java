package com.example.melodix;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.SeekBar;

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

    // New fields for playlist functionality
    private List<Track> playlist = new ArrayList<>();
    private int currentTrackIndex = -1;
    private boolean autoPlayNext = true;  // Auto-play next track when current one completes

    // Menyimpan daftar indeks lagu yang sudah diputar
    private List<Integer> playedTrackIndices = new ArrayList<>();

    // Interface for callbacks
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

        // New callback methods for track navigation
        default void onTrackChanged(Track newTrack) {}
        default void onPlaylistChanged(List<Track> playlist, int currentIndex) {}
    }

    private MusicPlayer() {
        mediaPlayer = new MediaPlayer();
        handler = new Handler();

        // Set up media player listeners
        mediaPlayer.setOnCompletionListener(mp -> {
            isPlaying = false;
            if (playbackStatusListener != null) {
                playbackStatusListener.onPlaybackCompleted();
            }

            // Auto-play next track if enabled
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
            return false;
        });

        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            if (playbackStatusListener != null) {
                playbackStatusListener.onPrepareComplete();
                playbackStatusListener.onDurationChanged(mp.getDuration());
            }
            // Auto-play when prepared
            play();
        });
    }

    // Singleton pattern
    public static synchronized MusicPlayer getInstance() {
        if (instance == null) {
            instance = new MusicPlayer();
        }
        return instance;
    }

    public void setPlaybackStatusListener(OnPlaybackStatusListener listener) {
        this.playbackStatusListener = listener;
    }

    public Track getCurrentTrack() {
        return currentTrack;
    }

    /**
     * Set the current playlist and start playing from the specified track
     *
     * @param tracks List of tracks to use as playlist
     * @param startTrackIndex Index of the track to start with
     * @param context Context needed for media player
     */
    public void setPlaylist(Context context, List<Track> tracks, int startTrackIndex) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }

        // Store the playlist
        playlist.clear();
        playlist.addAll(tracks);

        // Reset played track history to allow fresh random selections
        playedTrackIndices.clear();

        // Ensure index is valid
        if (startTrackIndex < 0) startTrackIndex = 0;
        if (startTrackIndex >= tracks.size()) startTrackIndex = tracks.size() - 1;

        currentTrackIndex = startTrackIndex;

        // Store current index in played tracks to prevent immediate repeat
        playedTrackIndices.add(currentTrackIndex);

        // Store context in each track for future use
        for (Track track : playlist) {
            track.setContext(context);
        }

        // Prepare and play the selected track
        prepareFromUrl(context, playlist.get(currentTrackIndex));

        // Notify listeners about the new playlist
        if (playbackStatusListener != null) {
            playbackStatusListener.onPlaylistChanged(playlist, currentTrackIndex);
        }
    }

    /**
     * Add a single track to the current playlist
     *
     * @param track Track to add
     */
    public void addToPlaylist(Track track) {
        if (track != null) {
            playlist.add(track);

            // If this is the first track, set the current index
            if (currentTrackIndex == -1) {
                currentTrackIndex = 0;
            }

            // Notify listeners about playlist change
            if (playbackStatusListener != null) {
                playbackStatusListener.onPlaylistChanged(playlist, currentTrackIndex);
            }
        }
    }

    /**
     * Play a random track from the playlist, ensuring no repetition
     *
     * @return true if successfully switched to random track, false otherwise
     */
    public boolean playRandomTrack() {
        // Check if we have a valid playlist
        if (playlist.isEmpty()) {
            Log.e(TAG, "Cannot play random track: playlist is empty");
            return false;
        }

        // If we only have one track, just replay it
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

        // Jika semua lagu dalam playlist sudah dimainkan, reset daftar lagu yang sudah diputar
        if (playedTrackIndices.size() >= playlist.size() - 1) {
            Log.d(TAG, "All tracks have been played, resetting play history");
            playedTrackIndices.clear();
            // Tetap simpan lagu yang sedang diputar dalam daftar lagu yang sudah dimainkan
            if (currentTrackIndex >= 0 && currentTrackIndex < playlist.size()) {
                playedTrackIndices.add(currentTrackIndex);
            }
        }

        // Pilih indeks secara acak yang belum pernah diputar
        int randomIndex;
        int maxAttempts = 10; // Batasi jumlah percobaan untuk menghindari loop tak terbatas
        int attempts = 0;

        do {
            randomIndex = (int) (Math.random() * playlist.size());
            attempts++;
            // Lanjutkan loop jika lagu yang dipilih sudah dimainkan sebelumnya atau sama dengan lagu saat ini
            if (attempts > maxAttempts) {
                // Jika terlalu banyak percobaan, pilih indeks pertama yang belum dimainkan
                boolean foundUnplayed = false;
                for (int i = 0; i < playlist.size(); i++) {
                    if (!playedTrackIndices.contains(i)) {
                        randomIndex = i;
                        foundUnplayed = true;
                        break;
                    }
                }

                // Jika semua lagu sudah dimainkan, pilih lagu yang pertama kali dimainkan
                if (!foundUnplayed && !playlist.isEmpty()) {
                    randomIndex = 0;
                    playedTrackIndices.clear(); // Reset played history
                }
                break;
            }
        } while (playedTrackIndices.contains(randomIndex));

        // Simpan indeks lagu yang akan diputar
        playedTrackIndices.add(randomIndex);
        currentTrackIndex = randomIndex;

        if (currentTrackIndex < 0 || currentTrackIndex >= playlist.size()) {
            Log.e(TAG, "Invalid track index: " + currentTrackIndex + ", playlist size: " + playlist.size());
            return false;
        }

        Log.d(TAG, "Playing track index " + randomIndex + ", played history size: " + playedTrackIndices.size());

        // Get a valid context
        Context context = getValidContext();

        // Prepare and play the random track
        if (context != null) {
            Track randomTrack = playlist.get(currentTrackIndex);
            Log.d(TAG, "Playing random track: " + randomTrack.getTitle());

            // Explicitly notify listeners about track change first
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

    /**
     * Helper method to get a valid context using multiple fallback strategies
     */
    private Context getValidContext() {
        Context context = null;

        // Try to get context from application first
        try {
            context = MelodixApplication.getAppContext();
        } catch (Exception e) {
            Log.w(TAG, "Could not get application context: " + e.getMessage());
        }

        // Try to get context from current track as fallback
        if (context == null && currentTrack != null) {
            context = currentTrack.getContext();
        }

        // Try to get context from any track in the playlist as last resort
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

    /**
     * Play the next track in the playlist if available
     * Changed to play a random track instead of the sequential next one
     *
     * @return true if successfully switched to next track, false otherwise
     */
    public boolean playNextTrack() {
        // Play a random track instead of sequential next
        return playRandomTrack();
    }

    /**
     * Play the previous track in the playlist if available
     *
     * @return true if successfully switched to previous track, false otherwise
     */
    public boolean playPreviousTrack() {
        // Check if we have a valid playlist
        if (playlist.isEmpty()) {
            Log.e(TAG, "Cannot play previous track: playlist is empty");
            return false;
        }

        // If current position is past 3 seconds, restart the current track
        if (getCurrentPosition() > 3000) {
            // If we're more than 3 seconds in, restart current track
            seekTo(0);
            return true;
        }

        // Check if we're already at the first track
        if (currentTrackIndex <= 0) {
            // If at first track, just restart it
            seekTo(0);
            return true;
        }

        // Move to previous track
        currentTrackIndex--;

        // Ensure index is valid (defensive programming)
        if (currentTrackIndex < 0) {
            currentTrackIndex = 0;
        }

        // Get a valid context using our helper method
        Context context = getValidContext();

        // Prepare and play the previous track
        if (context != null) {
            Track prevTrack = playlist.get(currentTrackIndex);
            Log.d(TAG, "Playing previous track: " + prevTrack.getTitle());

            // Explicitly notify listeners about track change first
            if (playbackStatusListener != null) {
                playbackStatusListener.onTrackChanged(prevTrack);
            }

            prepareFromUrl(context, prevTrack);
            return true;
        } else {
            Log.e(TAG, "Context is null, can't play previous track");
            return false;
        }
    }

    /**
     * Check if there is a next track available
     *
     * @return true if there is a next track, false otherwise
     */
    public boolean hasNextTrack() {
        return !playlist.isEmpty() && currentTrackIndex < playlist.size() - 1;
    }

    /**
     * Check if there is a previous track available
     *
     * @return true if there is a previous track, false otherwise
     */
    public boolean hasPreviousTrack() {
        return !playlist.isEmpty() && currentTrackIndex > 0;
    }

    public void prepareFromUrl(Context context, Track track) {
        if (track == null || track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) {
            if (playbackStatusListener != null) {
                playbackStatusListener.onError("Invalid track or preview URL");
            }
            return;
        }

        if (context == null) {
            if (playbackStatusListener != null) {
                playbackStatusListener.onError("Context is null, cannot prepare media player");
            }
            return;
        }

        currentTrack = track;
        // Store context in track for later use
        track.setContext(context);

        try {
            // Reset media player to clear previous state
            reset();

            if (playbackStatusListener != null) {
                playbackStatusListener.onPrepareStart();
                playbackStatusListener.onTrackChanged(track);
            }

            // Set up the audio attributes
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build();
            mediaPlayer.setAudioAttributes(audioAttributes);

            // Set the data source and prepare asynchronously
            mediaPlayer.setDataSource(context, Uri.parse(track.getPreviewUrl()));
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Log.e(TAG, "Error setting data source", e);
            if (playbackStatusListener != null) {
                playbackStatusListener.onError("Error preparing media: " + e.getMessage());
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "MediaPlayer in illegal state", e);
            if (playbackStatusListener != null) {
                playbackStatusListener.onError("MediaPlayer error: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in media player", e);
            if (playbackStatusListener != null) {
                playbackStatusListener.onError("Unexpected error: " + e.getMessage());
            }
        }
    }

    public void play() {
        if (!isPrepared) {
            Log.w(TAG, "Cannot play: MediaPlayer not prepared");
            return;
        }

        try {
            mediaPlayer.start();
            isPlaying = true;
            if (playbackStatusListener != null) {
                playbackStatusListener.onPlaybackStarted();
            }
            startProgressUpdate();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error playing media", e);
            if (playbackStatusListener != null) {
                playbackStatusListener.onError("Error playing: " + e.getMessage());
            }
        }
    }

    public void pause() {
        if (isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            if (playbackStatusListener != null) {
                playbackStatusListener.onPlaybackPaused();
            }
            stopProgressUpdate();
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            isPlaying = false;
            isPrepared = false;
            if (playbackStatusListener != null) {
                playbackStatusListener.onPlaybackStopped();
            }
            stopProgressUpdate();
        }
    }

    public void reset() {
        stop();
        if (mediaPlayer != null) {
            mediaPlayer.reset();
        }
    }

    public void release() {
        stop();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        instance = null;
    }

    public boolean isPlaying() {
        return isPlaying;
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

    public void seekTo(int position) {
        if (isPrepared) {
            try {
                mediaPlayer.seekTo(position);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error seeking to position", e);
            }
        }
    }

    public void setupSeekBar(SeekBar seekBar) {
        if (seekBar != null && isPrepared) {
            seekBar.setMax(mediaPlayer.getDuration());
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        seekTo(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // Pause updates while user is dragging
                    stopProgressUpdate();
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // Resume updates when user stops dragging
                    if (isPlaying) {
                        startProgressUpdate();
                    }
                }
            });
        }
    }

    private void startProgressUpdate() {
        stopProgressUpdate(); // Stop any existing updates

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
}
