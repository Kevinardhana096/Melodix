package com.example.melodix.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.melodix.MainActivity;
import com.example.melodix.MusicPlayer;
import com.example.melodix.R;
import com.example.melodix.Track;
import com.example.melodix.TrackChangeListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MusicFragment extends Fragment implements TrackChangeListener {
    private static final String TAG = "MusicFragment";

    // UI elements
    private ImageView imgAlbumArt;
    private TextView tvTrackTitle, tvArtistName, tvCurrentTime, tvTotalTime, tvSongTitle;
    private SeekBar seekBarProgress;
    private FloatingActionButton btnPlayPause;
    private ImageButton btnPrevious, btnNext;
    private ImageButton btnFavoriteTop; // Add favorite button
    private MusicPlayer musicPlayer;
    private Track currentTrack;
    private Handler uiUpdateHandler;
    private boolean isUiUpdateActive = false;

    // Constants for SharedPreferences
    private static final String PREFS_NAME = "MelodixPrefs";
    private static final String FAVORITES_KEY = "favorites";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_music, container, false);
        initializeViews(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get the music player instance
        musicPlayer = MusicPlayer.getInstance();
        uiUpdateHandler = new Handler(Looper.getMainLooper());

        // Set initial button state based on player state
        updatePlayPauseButton(musicPlayer.isPlaying());

        // Set up listeners for playback controls
        btnPlayPause.setOnClickListener(v -> togglePlayback());
        btnNext.setOnClickListener(v -> skipToNextTrack());
        btnPrevious.setOnClickListener(v -> skipToPreviousTrack());

        // Set up favorite button listener
        btnFavoriteTop.setOnClickListener(v -> toggleFavoriteStatus());

        // Set up seek bar listener for manual seeking
        seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    musicPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Stop UI updates during manual seeking
                stopUiUpdates();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Resume UI updates after manual seeking
                startUiUpdates();
            }
        });

        // Register this fragment to receive track change events
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).addTrackChangeListener(this);
        }

        // Set up the music player listener
        setupMusicPlayerListener();

        // If there's a track already loaded in the player, update the UI
        Track track = musicPlayer.getCurrentTrack();
        if (track != null) {
            updateTrackUI(track);
            currentTrack = track; // Make sure current track is set
            startUiUpdates(); // Start updating UI for ongoing playback
        } else {
            // No track in player, try to load the last played track from SharedPreferences
            loadLastPlayedTrack();
        }
    }

    private void initializeViews(View view) {
        imgAlbumArt = view.findViewById(R.id.imgAlbumArt);
        tvTrackTitle = view.findViewById(R.id.tvTrackTitle);
        tvArtistName = view.findViewById(R.id.tvArtistName);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvTotalTime = view.findViewById(R.id.tvTotalTime);
        seekBarProgress = view.findViewById(R.id.seekBarProgress);
        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        btnPrevious = view.findViewById(R.id.btnPrevious);
        btnNext = view.findViewById(R.id.btnNext);
        tvSongTitle = view.findViewById(R.id.tvSongTitle);
        btnFavoriteTop = view.findViewById(R.id.btnFavoriteTop); // Initialize favorite button
    }

    private void loadLastPlayedTrack() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString("recentTracks", null);

        if (json != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Track>>() {}.getType();
            List<Track> recentTracks = gson.fromJson(json, type);

            if (recentTracks != null && !recentTracks.isEmpty()) {
                // Get the most recently played track (first in the list)
                Track lastTrack = recentTracks.get(0);

                // Update the UI with this track
                currentTrack = lastTrack;
                updateTrackUI(lastTrack);

                // Don't automatically start playback, just show the track info
                Log.d(TAG, "Loaded last played track: " + lastTrack.getTitle());
            }
        }
    }

    private void setupMusicPlayerListener() {
        musicPlayer.setPlaybackStatusListener(new MusicPlayer.OnPlaybackStatusListener() {
            @Override
            public void onPlaybackStarted() {
                if (isAdded() && btnPlayPause != null) {
                    requireActivity().runOnUiThread(() -> {
                        updatePlayPauseButton(true);
                        startUiUpdates();
                    });
                }
            }

            @Override
            public void onPlaybackPaused() {
                if (isAdded() && btnPlayPause != null) {
                    requireActivity().runOnUiThread(() -> {
                        updatePlayPauseButton(false);
                        stopUiUpdates();
                    });
                }
            }

            @Override
            public void onPlaybackStopped() {
                if (isAdded() && btnPlayPause != null) {
                    requireActivity().runOnUiThread(() -> {
                        updatePlayPauseButton(false);
                        stopUiUpdates();
                    });
                }
            }

            @Override
            public void onPlaybackCompleted() {
                if (isAdded() && btnPlayPause != null) {
                    requireActivity().runOnUiThread(() -> {
                        updatePlayPauseButton(false);
                        stopUiUpdates();
                    });
                }
            }

            @Override
            public void onDurationChanged(int duration) {
                if (isAdded() && seekBarProgress != null) {
                    requireActivity().runOnUiThread(() -> {
                        seekBarProgress.setMax(duration);
                        int minutes = duration / 60000;
                        int seconds = (duration % 60000) / 1000;
                        tvTotalTime.setText(String.format("%d:%02d", minutes, seconds));
                    });
                }
            }

            @Override
            public void onPositionChanged(int position) {
                if (isAdded() && seekBarProgress != null) {
                    requireActivity().runOnUiThread(() -> {
                        seekBarProgress.setProgress(position);
                        int minutes = position / 60000;
                        int seconds = (position % 60000) / 1000;
                        tvCurrentTime.setText(String.format("%d:%02d", minutes, seconds));
                    });
                }
            }

            @Override
            public void onPrepareStart() {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Loading track...", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onPrepareComplete() {
                // Ready to play
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                        updatePlayPauseButton(true));
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Error: " + message, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Playback error: " + message);
                    });
                }
            }

            @Override
            public void onTrackChanged(Track newTrack) {
                // This is crucial for updating UI when tracks are changed via next/previous buttons
                if (isAdded() && newTrack != null) {
                    Log.d(TAG, "onTrackChanged callback: " + newTrack.getTitle());
                    requireActivity().runOnUiThread(() -> {
                        updateTrackUI(newTrack);
                    });
                }
            }
        });
    }

    private void updateTrackUI(Track track) {
        if (track == null || !isAdded()) return;

        currentTrack = track;

        // Update track information
        tvTrackTitle.setText(track.getTitle());
        tvSongTitle.setText(track.getTitle());
        if (track.getArtist() != null) {
            tvArtistName.setText(track.getArtist().getName());
        }

        // Load album art
        if (track.getAlbum() != null && track.getAlbum().getCoverMedium() != null) {
            Glide.with(this)
                    .load(track.getAlbum().getCoverMedium())
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(imgAlbumArt);
        } else {
            imgAlbumArt.setImageResource(R.drawable.ic_launcher_background);
        }

        // Reset the seekbar if track has changed
        seekBarProgress.setProgress(0);
        tvCurrentTime.setText("0:00");

        // Set total duration if available
        if (track.getDuration() > 0) {
            int minutes = track.getDuration() / 60;
            int seconds = track.getDuration() % 60;
            tvTotalTime.setText(String.format("%d:%02d", minutes, seconds));
        } else {
            tvTotalTime.setText("0:00");
        }

        // Update favorite button state
        updateFavoriteButtonState(track);
    }

    private void togglePlayback() {
        if (currentTrack == null) {
            Toast.makeText(getContext(), "No track selected", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isCurrentlyPlaying = musicPlayer.isPlaying();
        Log.d(TAG, "togglePlayback - current state: " + (isCurrentlyPlaying ? "playing" : "paused"));

        if (isCurrentlyPlaying) {
            musicPlayer.pause();
            updatePlayPauseButton(false);
            stopUiUpdates();
        } else {
            if (musicPlayer.getCurrentTrack() != null &&
                musicPlayer.getCurrentTrack().getId() == currentTrack.getId()) {
                // Resume the current track
                musicPlayer.play();
                updatePlayPauseButton(true);
                startUiUpdates();
            } else {
                // Load and play the selected track
                musicPlayer.prepareFromUrl(requireContext(), currentTrack);
                updatePlayPauseButton(true);
                // UI updates will start from onPlaybackStarted callback
            }
        }
    }

    private void skipToNextTrack() {
        if (musicPlayer.hasNextTrack()) {
            boolean success = musicPlayer.playNextTrack();
            if (success) {
                // UI will be updated via onTrackChanged callback
                Log.d(TAG, "Playing next track");
            } else {
                Log.d(TAG, "Failed to play next track");
                Toast.makeText(getContext(), "Could not play next track", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d(TAG, "No next track available");
            Toast.makeText(getContext(), "No next track available", Toast.LENGTH_SHORT).show();
        }
    }

    private void skipToPreviousTrack() {
        boolean success = musicPlayer.playPreviousTrack();
        if (success) {
            // If success is true but there was no previous track, it means the current track was restarted
            if (!musicPlayer.hasPreviousTrack()) {
                Log.d(TAG, "Restarting current track");
                Toast.makeText(getContext(), "Restarting track", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "Playing previous track");
            }
            // UI will be updated via onTrackChanged callback
        } else {
            Log.d(TAG, "No previous track available");
            Toast.makeText(getContext(), "No previous track available", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleFavoriteStatus() {
        if (currentTrack == null) return;

        List<Track> favorites = getFavoriteTracks();
        boolean isCurrentlyFavorite = false;

        // Check if track is currently in favorites
        for (Track favorite : favorites) {
            if (favorite.getId() == currentTrack.getId()) {
                isCurrentlyFavorite = true;
                break;
            }
        }

        // Toggle favorite status
        if (isCurrentlyFavorite) {
            // Remove from favorites
            favorites.removeIf(track -> track.getId() == currentTrack.getId());
            Toast.makeText(getContext(), "Removed from favorites", Toast.LENGTH_SHORT).show();
            btnFavoriteTop.setImageResource(R.drawable.ic_favorite_filled);
        } else {
            // Add to favorites
            favorites.add(currentTrack);
            Toast.makeText(getContext(), "Added to favorites", Toast.LENGTH_SHORT).show();
            btnFavoriteTop.setImageResource(R.drawable.ic_favorite);
        }

        // Save updated favorites list
        saveFavorites(favorites);

        // Notify MainActivity that favorites have changed
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).notifyFavoriteChanged();
        }
    }

    private List<Track> getFavoriteTracks() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(FAVORITES_KEY, null);

        if (json == null) {
            return new ArrayList<>();
        }

        Gson gson = new Gson();
        Type type = new TypeToken<ArrayList<Track>>() {}.getType();
        return gson.fromJson(json, type);
    }

    private void saveFavorites(List<Track> favorites) {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        Gson gson = new Gson();
        String json = gson.toJson(favorites);
        editor.putString(FAVORITES_KEY, json);
        editor.apply();
    }

    private void updateFavoriteButtonState(Track track) {
        if (track == null || btnFavoriteTop == null) return;

        List<Track> favorites = getFavoriteTracks();
        boolean isFavorite = false;

        for (Track favorite : favorites) {
            if (favorite.getId() == track.getId()) {
                isFavorite = true;
                break;
            }
        }

        btnFavoriteTop.setImageResource(isFavorite ?
                R.drawable.ic_favorite : R.drawable.ic_favorite_filled);
    }

    @Override
    public void onTrackChanged(Track track) {
        if (track == null || !isAdded()) return;

        Log.d(TAG, "Track changed to: " + track.getTitle());

        // Check if this is the same track that's already playing
        boolean isSameTrack = false;
        if (currentTrack != null && track.getId() == currentTrack.getId()) {
            isSameTrack = true;
        }

        // Set the current track and update UI
        currentTrack = track;
        updateTrackUI(track);

        // Only prepare and start from beginning if it's a new track
        if (!isSameTrack) {
            musicPlayer.prepareFromUrl(requireContext(), track);
            updatePlayPauseButton(true);
        } else {
            // For the same track, just update the UI to match current playback state
            updatePlayPauseButton(musicPlayer.isPlaying());
            if (musicPlayer.isPlaying()) {
                startUiUpdates();
            }
        }
    }

    // Start regular UI updates for progress
    private void startUiUpdates() {
        if (isUiUpdateActive) return;

        isUiUpdateActive = true;
        uiUpdateHandler.post(new Runnable() {
            @Override
            public void run() {
                if (musicPlayer != null && musicPlayer.isPlaying() && isAdded()) {
                    int currentPosition = musicPlayer.getCurrentPosition();
                    if (seekBarProgress != null) {
                        seekBarProgress.setProgress(currentPosition);
                    }
                    if (tvCurrentTime != null) {
                        int minutes = currentPosition / 60000;
                        int seconds = (currentPosition % 60000) / 1000;
                        tvCurrentTime.setText(String.format("%d:%02d", minutes, seconds));
                    }
                }

                // Continue if still active and fragment is visible
                if (isUiUpdateActive && isAdded() && isVisible()) {
                    uiUpdateHandler.postDelayed(this, 1000);
                }
            }
        });
    }

    // Stop UI updates
    private void stopUiUpdates() {
        isUiUpdateActive = false;
        uiUpdateHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroyView() {
        // Unregister this fragment from track change events
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).removeTrackChangeListener(this);
        }

        // Stop UI updates
        stopUiUpdates();

        super.onDestroyView();
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        if (btnPlayPause != null) {
            btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // When fragment resumes, start UI updates if a track is playing
        if (musicPlayer.isPlaying()) {
            startUiUpdates();
        }
    }

    @Override
    public void onPause() {
        // When fragment pauses, stop UI updates
        stopUiUpdates();
        super.onPause();
    }
}
