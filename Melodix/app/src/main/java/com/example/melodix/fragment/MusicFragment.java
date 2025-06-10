package com.example.melodix.fragment;

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
import com.example.melodix.api.DeezerRepository;
import com.example.melodix.activity.MainActivity;
import com.example.melodix.listener.MusicPlayer;
import com.example.melodix.R;
import com.example.melodix.database.DownloadedMusicDbHelper;
import com.example.melodix.database.DownloadedTrack;
import com.example.melodix.listener.MusicDownloader;
import com.example.melodix.model.Track;
import com.example.melodix.listener.TrackChangeListener;
import com.example.melodix.listener.UserPreferencesManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MusicFragment extends Fragment implements TrackChangeListener {
    private static final String TAG = "MusicFragment";
    private ImageView imgAlbumArt;
    private TextView tvTrackTitle, tvArtistName, tvCurrentTime, tvTotalTime;
    private SeekBar seekBarProgress;
    private FloatingActionButton btnPlayPause;
    private ImageButton btnPrevious, btnNext;
    private ImageButton btnFavoriteTop;
    private ImageButton btnDownload;
    private MusicPlayer musicPlayer;
    private Track currentTrack;
    private Handler uiUpdateHandler;
    private boolean isUiUpdateActive = false;
    private static final int PREVIEW_DURATION_SECONDS = 30;
    private static final int PREVIEW_DURATION_MS = PREVIEW_DURATION_SECONDS * 1000;

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

        musicPlayer = MusicPlayer.getInstance();
        uiUpdateHandler = new Handler(Looper.getMainLooper());

        updatePlayPauseButton(musicPlayer.isPlaying());

        btnPlayPause.setOnClickListener(v -> togglePlayback());
        btnNext.setOnClickListener(v -> skipToNextTrack());
        btnPrevious.setOnClickListener(v -> skipToPreviousTrack());

        btnFavoriteTop.setOnClickListener(v -> toggleFavoriteStatus());

        btnDownload.setOnClickListener(v -> downloadTrack());

        seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int limitedProgress = Math.min(progress, PREVIEW_DURATION_MS);
                    musicPlayer.seekTo(limitedProgress);

                    Log.d(TAG, "User seeked to: " + limitedProgress + "ms");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopUiUpdates();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                startUiUpdates();
            }
        });
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).addTrackChangeListener(this);
        }
        setupMusicPlayerListener();
        Track track = musicPlayer.getCurrentTrack();
        if (track != null) {
            updateTrackUI(track);
            currentTrack = track;
            startUiUpdates();
        } else {
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
        btnFavoriteTop = view.findViewById(R.id.btnFavoriteTop);
        btnDownload = view.findViewById(R.id.btnDownload);
    }

    private void loadLastPlayedTrack() {
        UserPreferencesManager.getRecentTracksAsync(requireContext(), new UserPreferencesManager.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> recentTracks) {
                if (recentTracks != null && !recentTracks.isEmpty()) {
                    Track lastTrack = recentTracks.get(0);

                    // ✅ TAMBAHAN: Validasi URL sebelum memutar
                    if (isTrackUrlValid(lastTrack)) {
                        currentTrack = lastTrack;
                        updateTrackUI(lastTrack);
                        Log.d(TAG, "Loaded last played track: " + lastTrack.getTitle());
                    } else {
                        Log.w(TAG, "Track URL expired, refreshing: " + lastTrack.getTitle());
                        refreshTrackUrl(lastTrack);
                    }
                }
            }
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading recent tracks: " + error);
                List<Track> localRecent = UserPreferencesManager.getRecentTracks(requireContext());
                if (localRecent != null && !localRecent.isEmpty()) {
                    Track lastTrack = localRecent.get(0);

                    // ✅ TAMBAHAN: Validasi URL untuk local tracks juga
                    if (isTrackUrlValid(lastTrack)) {
                        currentTrack = lastTrack;
                        updateTrackUI(lastTrack);
                        Log.d(TAG, "Loaded last played track from local: " + lastTrack.getTitle());
                    } else {
                        refreshTrackUrl(lastTrack);
                    }
                }
            }
        });
    }
    private boolean isTrackUrlValid(Track track) {
        if (track == null || track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) {
            return false;
        }

        // Cek apakah ini URL Deezer yang expired
        if (isDeezerUrl(track.getPreviewUrl())) {
            return !isDeezerUrlExpired(track.getPreviewUrl());
        }

        return true;
    }
    private boolean isDeezerUrl(String url) {
        return url != null && url.contains("dzcdn.net");
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
    private void refreshTrackUrl(Track track) {
        Log.d(TAG, "🔄 Refreshing expired URL for: " + track.getTitle());

        DeezerRepository repository = DeezerRepository.getInstance();
        String searchQuery = track.getTitle();
        if (track.getArtist() != null) {
            searchQuery += " " + track.getArtist().getName();
        }

        repository.searchTracks(searchQuery, new DeezerRepository.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> tracks) {
                Track refreshedTrack = findMatchingTrack(tracks, track);

                if (refreshedTrack != null && refreshedTrack.getPreviewUrl() != null) {
                    // Update track dengan URL fresh
                    track.setPreviewUrl(refreshedTrack.getPreviewUrl());
                    currentTrack = track;
                    updateTrackUI(track);

                    // Update di recent tracks storage
                    updateRecentTrackUrl(track);

                    Log.d(TAG, "✅ Successfully refreshed URL for: " + track.getTitle());
                } else {
                    Log.w(TAG, "❌ Could not refresh URL for: " + track.getTitle());
                    handleFailedTrackLoad();
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "❌ Error refreshing track URL: " + message);
                handleFailedTrackLoad();
            }
        });
    }
    private Track findMatchingTrack(List<Track> tracks, Track originalTrack) {
        if (tracks == null || tracks.isEmpty()) return null;

        // Cari berdasarkan ID dulu
        for (Track track : tracks) {
            if (track.getId() == originalTrack.getId()) {
                return track;
            }
        }

        // Kalau tidak ada, cari berdasarkan title dan artist
        for (Track track : tracks) {
            if (track.getTitle().equalsIgnoreCase(originalTrack.getTitle()) &&
                    track.getArtist() != null && originalTrack.getArtist() != null &&
                    track.getArtist().getName().equalsIgnoreCase(originalTrack.getArtist().getName())) {
                return track;
            }
        }

        return null;
    }
    private void updateRecentTrackUrl(Track track) {
        UserPreferencesManager.getRecentTracksAsync(requireContext(), new UserPreferencesManager.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> recentTracks) {
                boolean updated = false;
                for (Track recentTrack : recentTracks) {
                    if (recentTrack.getId() == track.getId()) {
                        recentTrack.setPreviewUrl(track.getPreviewUrl());
                        updated = true;
                        break;
                    }
                }

                if (updated) {
                    UserPreferencesManager.saveRecentTracksAsync(requireContext(), recentTracks, null);
                    Log.d(TAG, "Updated recent tracks with fresh URL");
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error updating recent tracks: " + error);
            }
        });
    }
    private void handleFailedTrackLoad() {
        Log.d(TAG, "Handling failed track load, trying to load alternative track");

        UserPreferencesManager.getRecentTracksAsync(requireContext(), new UserPreferencesManager.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> recentTracks) {
                if (recentTracks != null && recentTracks.size() > 1) {
                    // Coba track berikutnya di recent tracks
                    for (int i = 1; i < recentTracks.size(); i++) {
                        Track nextTrack = recentTracks.get(i);
                        if (isTrackUrlValid(nextTrack)) {
                            currentTrack = nextTrack;
                            updateTrackUI(nextTrack);
                            Log.d(TAG, "Loaded alternative track: " + nextTrack.getTitle());
                            return;
                        }
                    }
                }

                // Kalau semua recent tracks bermasalah, load track baru
                Toast.makeText(getContext(), "Recent tracks unavailable, loading new music...", Toast.LENGTH_SHORT).show();
                loadMoreTracksToPlaylist(true);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading alternative track: " + error);
                loadMoreTracksToPlaylist(true);
            }
        });
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
                        int previewDurationMs = PREVIEW_DURATION_MS;
                        seekBarProgress.setMax(previewDurationMs);
                        int minutes = previewDurationMs / 60000;
                        int seconds = (previewDurationMs % 60000) / 1000;
                        tvTotalTime.setText(String.format("%d:%02d", minutes, seconds));

                        Log.d(TAG, "Duration changed - Using preview duration: " + previewDurationMs + "ms instead of reported: " + duration + "ms");
                    });
                }
            }
            @Override
            public void onPositionChanged(int position) {
                if (isAdded() && seekBarProgress != null) {
                    requireActivity().runOnUiThread(() -> {
                        int limitedPosition = Math.min(position, PREVIEW_DURATION_MS);
                        seekBarProgress.setProgress(limitedPosition);
                        tvCurrentTime.setText(formatTime(limitedPosition));

                        Log.d(TAG, "Position updated: " + limitedPosition + "ms / " + PREVIEW_DURATION_MS + "ms");
                    });
                }
            }

            @Override
            public void onPrepareStart() {

            }

            @Override
            public void onPrepareComplete() {
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
                if (isAdded() && newTrack != null) {
                    Log.d(TAG, "onTrackChanged callback received for: " + newTrack.getTitle());
                    requireActivity().runOnUiThread(() -> {
                        currentTrack = newTrack;
                        updateTrackUI(newTrack);
                        updateFavoriteButtonState(newTrack);
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).addToRecentlyPlayed(newTrack);
                        }
                    });
                }
            }
        });
    }

    private void updateTrackUI(Track track) {
        if (track == null || !isAdded()) return;
        currentTrack = track;
        tvTrackTitle.setText(track.getTitle());
        if (track.getArtist() != null) {
            tvArtistName.setText(track.getArtist().getName());
        }

        if (track.getAlbum() != null && track.getAlbum().getCoverMedium() != null) {
            Glide.with(this)
                    .load(track.getAlbum().getCoverMedium())
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(imgAlbumArt);
        } else {
            imgAlbumArt.setImageResource(R.drawable.ic_launcher_background);
        }
        String titleText = track.getTitle();
        if (!track.isDownloaded()) {
            titleText += " (Preview)";
        }
        tvTrackTitle.setText(titleText);
        seekBarProgress.setProgress(0);
        tvCurrentTime.setText("0:00");

        int displayDuration = getDisplayDuration(track);
        int minutes = displayDuration / 60;
        int seconds = displayDuration % 60;
        tvTotalTime.setText(String.format("%d:%02d", minutes, seconds));

        updateFavoriteButtonState(track);
        updateDownloadButtonState();
    }
    private int getDisplayDuration(Track track) {
        if (track == null) return 0;

        if (track.isDownloaded() && track.getPreviewUrl() != null) {
            File localFile = new File(track.getPreviewUrl());
            if (localFile.exists()) {
                Log.d(TAG, "Track is downloaded, using preview duration: " + PREVIEW_DURATION_SECONDS + "s");
                return PREVIEW_DURATION_SECONDS;
            }
        }
        if (track.getPreviewUrl() != null && track.getPreviewUrl().contains("dzcdn.net")) {
            Log.d(TAG, "Track is streaming preview, using preview duration: " + PREVIEW_DURATION_SECONDS + "s");
            return PREVIEW_DURATION_SECONDS;
        }
        if (track.getDuration() > 0) {
            if (track.getDuration() > PREVIEW_DURATION_SECONDS) {
                Log.d(TAG, "Track duration (" + track.getDuration() + "s) is longer than preview, using preview duration");
                return PREVIEW_DURATION_SECONDS;
            }
            return track.getDuration();
        }
        return PREVIEW_DURATION_SECONDS;
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
                musicPlayer.play();
                updatePlayPauseButton(true);
                startUiUpdates();
            } else {
                musicPlayer.prepareFromUrl(requireContext(), currentTrack);
                updatePlayPauseButton(true);
            }
        }
    }
    private String formatTime(int timeMs) {
        int minutes = timeMs / 60000;
        int seconds = (timeMs % 60000) / 1000;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void skipToNextTrack() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) return;

        if (currentTrack != null && currentTrack.isDownloaded()) {
            mainActivity.setPlayingFromDownloaded(true);
            Track nextDownloadedTrack = mainActivity.getNextDownloadedTrack();

            if (nextDownloadedTrack != null) {
                mainActivity.playTrack(nextDownloadedTrack);
                return;
            }
        }

        if (musicPlayer.getPlaylist().isEmpty() || musicPlayer.getPlaylist().size() < 5) {
            Log.d(TAG, "Playlist is small or empty, loading more tracks...");
            loadMoreTracksToPlaylist(true);
            return;
        }

        boolean success = musicPlayer.playRandomTrack();
        if (success) {
            Log.d(TAG, "Playing random track from playlist");
            Track newTrack = musicPlayer.getCurrentTrack();
            if (newTrack != null) {
                currentTrack = newTrack;
                requireActivity().runOnUiThread(() -> {
                    updateTrackUI(newTrack);
                    updateFavoriteButtonState(newTrack);
                });

                if (mainActivity != null) {
                    mainActivity.addToRecentlyPlayed(newTrack);
                }
            }
        } else {
            Log.d(TAG, "Failed to play random track, loading more tracks...");
            loadMoreTracksToPlaylist(true);
        }
    }
    private void loadMoreTracksToPlaylist(boolean playNext) {
        DeezerRepository repository = DeezerRepository.getInstance();
        String[] searchQueries = {
                "pop music", "rock music", "jazz music", "electronic music",
                "indie music", "alternative", "classical", "hip hop",
                "latin music", "world music", "blues", "country music"
        };
        String randomQuery = searchQueries[(int) (Math.random() * searchQueries.length)];
        Log.d(TAG, "Loading tracks with query: " + randomQuery);
        repository.searchTracks(randomQuery, new DeezerRepository.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks != null && !tracks.isEmpty()) {
                    for (Track track : tracks) {
                        if (getContext() != null) {
                            track.setContext(getContext());
                        }
                    }
                    List<Track> shuffledTracks = new ArrayList<>(tracks);
                    java.util.Collections.shuffle(shuffledTracks);

                    List<Track> currentPlaylist = musicPlayer.getPlaylist();
                    shuffledTracks.addAll(0, currentPlaylist);

                    musicPlayer.setPlaylist(requireContext(), shuffledTracks, 0);

                    if (playNext && isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            boolean success = musicPlayer.playRandomTrack();
                            if (!success && !shuffledTracks.isEmpty()) {
                                musicPlayer.prepareFromUrl(requireContext(), shuffledTracks.get(0));
                            }
                        });
                    }
                } else {
                    loadTracksFromChart(playNext);
                }
            }
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error loading tracks: " + message);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        loadTracksFromChart(playNext);
                    });
                }
            }
        });
    }
    private void loadTracksFromChart(boolean playNext) {
        DeezerRepository repository = DeezerRepository.getInstance();
        repository.getTopTracks(new DeezerRepository.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (tracks != null && !tracks.isEmpty()) {
                    for (Track track : tracks) {
                        if (getContext() != null) {
                            track.setContext(getContext());
                        }
                    }

                    List<Track> shuffledTracks = new ArrayList<>(tracks);
                    java.util.Collections.shuffle(shuffledTracks);
                    List<Track> currentPlaylist = musicPlayer.getPlaylist();
                    shuffledTracks.addAll(0, currentPlaylist);

                    musicPlayer.setPlaylist(requireContext(), shuffledTracks, 0);

                    if (playNext && isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            boolean success = musicPlayer.playRandomTrack();
                            if (!success && !shuffledTracks.isEmpty()) {
                                musicPlayer.prepareFromUrl(requireContext(), shuffledTracks.get(0));
                            }
                        });
                    }
                } else {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "No more tracks available", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Error loading tracks: " + message, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void skipToPreviousTrack() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) return;

        if (currentTrack != null && currentTrack.isDownloaded()) {
            mainActivity.setPlayingFromDownloaded(true);
            Track previousDownloadedTrack = mainActivity.getPreviousDownloadedTrack();

            if (previousDownloadedTrack != null) {
                mainActivity.playTrack(previousDownloadedTrack);
                return;
            }
        }
        boolean success = musicPlayer.playPreviousTrack();
        if (success) {
            if (!musicPlayer.hasPreviousTrack()) {
                Log.d(TAG, "Restarting current track");
                Toast.makeText(getContext(), "Restarting track", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "Playing previous track");
                Track newTrack = musicPlayer.getCurrentTrack();
                if (newTrack != null) {
                    currentTrack = newTrack;
                    requireActivity().runOnUiThread(() -> {
                        updateTrackUI(newTrack);
                        updateFavoriteButtonState(newTrack);
                    });
                    if (mainActivity != null) {
                        mainActivity.addToRecentlyPlayed(newTrack);
                    }
                }
            }
        } else {
            Log.d(TAG, "No previous track available");
            Toast.makeText(getContext(), "No previous track available", Toast.LENGTH_SHORT).show();
        }
    }
    private void toggleFavoriteStatus() {
        if (currentTrack == null) return;
        final Track trackToToggle = currentTrack;

        UserPreferencesManager.getFavoriteTracksAsync(requireContext(), new UserPreferencesManager.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> favorites) {
                List<Track> mutableFavorites = new ArrayList<>(favorites);
                boolean isCurrentlyFavorite = false;
                for (Track favorite : mutableFavorites) {
                    if (favorite.getId() == trackToToggle.getId()) {
                        isCurrentlyFavorite = true;
                        break;
                    }
                }
                final boolean wasFavorite = isCurrentlyFavorite;
                if (wasFavorite) {
                    // Remove from favorites
                    mutableFavorites.removeIf(track -> track.getId() == trackToToggle.getId());
                    UserPreferencesManager.saveFavoriteTracksAsync(requireContext(), mutableFavorites, new UserPreferencesManager.DataCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean success) {
                            if (isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Removed from favorites", Toast.LENGTH_SHORT).show();
                                    // PERBAIKAN: Ganti ke icon kosong (outline) karena sudah di-remove
                                    btnFavoriteTop.setImageResource(R.drawable.ic_favorite);
                                    if (getActivity() instanceof MainActivity) {
                                        ((MainActivity) getActivity()).notifyFavoriteChanged();
                                    }
                                });
                            }
                        }
                        @Override
                        public void onError(String error) {
                            if (isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Error removing from favorites", Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "Error removing from favorites: " + error);
                                });
                            }
                        }
                    });

                } else {
                    // Add to favorites
                    mutableFavorites.add(trackToToggle);
                    UserPreferencesManager.saveFavoriteTracksAsync(requireContext(), mutableFavorites, new UserPreferencesManager.DataCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean success) {
                            if (isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Added to favorites", Toast.LENGTH_SHORT).show();
                                    // PERBAIKAN: Ganti ke icon filled karena sudah di-add
                                    btnFavoriteTop.setImageResource(R.drawable.ic_favorite_filled);
                                    if (getActivity() instanceof MainActivity) {
                                        ((MainActivity) getActivity()).notifyFavoriteChanged();
                                    }
                                });
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Error adding to favorites", Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "Error adding to favorites: " + error);
                                });
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Error loading favorites", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error loading favorites: " + error);
                    });
                }
            }
        });
    }
    private void updateFavoriteButtonState(Track track) {
        if (track == null || btnFavoriteTop == null) return;
        final Track finalTrack = track;

        UserPreferencesManager.getFavoriteTracksAsync(requireContext(), new UserPreferencesManager.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> favorites) {
                boolean isFavorite = false;

                for (Track favorite : favorites) {
                    if (favorite.getId() == finalTrack.getId()) {
                        isFavorite = true;
                        break;
                    }
                }
                final boolean trackIsFavorite = isFavorite;

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        // PERBAIKAN: Logika yang benar
                        // Jika favorite = tampilkan icon filled (ic_favorite)
                        // Jika tidak favorite = tampilkan icon outline (ic_favorite_filled)
                        btnFavoriteTop.setImageResource(trackIsFavorite ?
                                R.drawable.ic_favorite_filled : R.drawable.ic_favorite);
                    });
                }
            }
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error checking favorite status: " + error);
                List<Track> localFavorites = UserPreferencesManager.getFavoriteTracks(requireContext());
                boolean isFavorite = false;
                for (Track favorite : localFavorites) {
                    if (favorite.getId() == finalTrack.getId()) {
                        isFavorite = true;
                        break;
                    }
                }
                final boolean trackIsFavorite = isFavorite;

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        // PERBAIKAN: Logika yang sama untuk error case
                        btnFavoriteTop.setImageResource(trackIsFavorite ?
                                R.drawable.ic_favorite_filled : R.drawable.ic_favorite);
                    });
                }
            }
        });
    }
    public void onTrackSelectedFromSearch(Track selectedTrack, List<Track> searchResults) {
        Log.d(TAG, "Track selected from search: " + selectedTrack.getTitle());

        if (searchResults != null && searchResults.size() > 1) {
            int selectedIndex = -1;
            for (int i = 0; i < searchResults.size(); i++) {
                if (searchResults.get(i).getId() == selectedTrack.getId()) {
                    selectedIndex = i;
                    break;
                }
            }

            for (Track track : searchResults) {
                if (getContext() != null) {
                    track.setContext(getContext());
                }
            }

            List<Track> playlistTracks = new ArrayList<>(searchResults);
            java.util.Collections.shuffle(playlistTracks);

            if (selectedIndex >= 0) {
                playlistTracks.remove(selectedTrack);
                playlistTracks.add(0, selectedTrack);
            }

            musicPlayer.setPlaylist(requireContext(), playlistTracks, 0);
            currentTrack = selectedTrack;
            updateTrackUI(selectedTrack);

            Log.d(TAG, "Loaded " + playlistTracks.size() + " tracks into playlist from search");
            loadMoreTracksToPlaylist(false);

        } else {
            musicPlayer.prepareFromUrl(requireContext(), selectedTrack);
            currentTrack = selectedTrack;
            updateTrackUI(selectedTrack);

            loadMoreTracksToPlaylist(false);
        }
    }
    @Override
    public void onTrackChanged(Track track) {
        if (track == null || !isAdded()) {
            Log.w(TAG, "onTrackChanged called with null track or fragment not added");
            return;
        }

        Log.d(TAG, "=== TRACK CHANGED IN MUSIC FRAGMENT ===");
        Log.d(TAG, "New track: " + track.getTitle());

        currentTrack = track;
        if (musicPlayer.getPlaylist().size() < 3) {
            Log.d(TAG, "Playlist getting small, loading more tracks in background...");
            loadMoreTracksToPlaylist(false);
        }

        requireActivity().runOnUiThread(() -> {
            try {
                updateTrackUI(track);

                MusicPlayer musicPlayer = MusicPlayer.getInstance();
                Track playerTrack = musicPlayer.getCurrentTrack();
                if (playerTrack == null || playerTrack.getId() != track.getId()) {
                    Log.d(TAG, "Track not loaded in player, preparing...");
                    musicPlayer.prepareFromUrl(requireContext(), track);
                }

                boolean isPlaying = musicPlayer.isPlaying();
                updatePlayPauseButton(isPlaying);

                if (isPlaying) {
                    startUiUpdates();
                } else {
                    stopUiUpdates();
                }

                Log.d(TAG, "✅ Track UI updated successfully");

            } catch (Exception e) {
                Log.e(TAG, "❌ Error updating track UI: " + e.getMessage(), e);
            }
        });
    }
    private void startUiUpdates() {
        if (isUiUpdateActive) return;

        isUiUpdateActive = true;
        uiUpdateHandler.post(new Runnable() {
            @Override
            public void run() {
                if (musicPlayer != null && musicPlayer.isPlaying() && isAdded()) {
                    int currentPosition = musicPlayer.getCurrentPosition();

                    int maxPosition = PREVIEW_DURATION_MS;
                    if (currentPosition > maxPosition) {
                        currentPosition = maxPosition;
                    }

                    if (seekBarProgress != null) {
                        seekBarProgress.setProgress(currentPosition);
                    }
                    if (tvCurrentTime != null) {
                        tvCurrentTime.setText(formatTime(currentPosition));
                    }

                    if (currentPosition >= maxPosition) {
                        Log.d(TAG, "Preview completed, stopping playback");
                        musicPlayer.pause();
                        updatePlayPauseButton(false);
                        stopUiUpdates();
                        return;
                    }
                }
                if (isUiUpdateActive && isAdded() && isVisible()) {
                    uiUpdateHandler.postDelayed(this, 1000);
                }
            }
        });
    }

    private void stopUiUpdates() {
        isUiUpdateActive = false;
        uiUpdateHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroyView() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).removeTrackChangeListener(this);
        }

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
        if (musicPlayer.isPlaying()) {
            startUiUpdates();
        }
    }

    @Override
    public void onPause() {
        stopUiUpdates();
        super.onPause();
    }

    private void downloadTrack() {
        if (currentTrack == null) {
            Toast.makeText(getContext(), "No track selected", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentTrack.getPreviewUrl() == null || currentTrack.getPreviewUrl().isEmpty()) {
            Toast.makeText(getContext(), "No preview URL available for this track", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentTrack.isDownloaded()) {
            Toast.makeText(getContext(), "Track already downloaded", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(getContext(), "Starting download...", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Starting download for: " + currentTrack.getTitle());

        MusicDownloader.downloadTrack(requireContext(), currentTrack,
                new MusicDownloader.DownloadCallback() {
                    @Override
                    public void onDownloadStarted() {
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                btnDownload.setEnabled(false);
                                btnDownload.setAlpha(0.5f);
                                Log.d(TAG, "Download started for: " + currentTrack.getTitle());
                            });
                        }
                    }
                    @Override
                    public void onDownloadProgress(int progress) {
                        Log.d(TAG, "Download progress: " + progress + "%");
                    }

                    @Override
                    public void onDownloadComplete(File downloadedFile) {
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                btnDownload.setEnabled(true);
                                btnDownload.setAlpha(1.0f);

                                Log.d(TAG, "Download completed: " + downloadedFile.getAbsolutePath());

                                if (currentTrack != null) {
                                    if (!(currentTrack instanceof DownloadedTrack)) {
                                        Log.d(TAG, "Converting regular Track to DownloadedTrack");
                                    }
                                    Log.d(TAG, "Marking track as downloaded: " + currentTrack.getTitle());
                                }
                                UserPreferencesManager.addToDownloadHistoryAsync(
                                        requireContext(),
                                        currentTrack,
                                        downloadedFile.getAbsolutePath(),
                                        new UserPreferencesManager.DataCallback<Boolean>() {
                                            @Override
                                            public void onSuccess(Boolean success) {
                                                Log.d(TAG, "✅ Successfully synced download to cloud: " + currentTrack.getTitle());
                                                requireActivity().runOnUiThread(() -> {
                                                    updateDownloadButtonState();
                                                });
                                            }
                                            @Override
                                            public void onError(String error) {
                                                Log.w(TAG, "❌ Failed to sync download to cloud: " + error);
                                                requireActivity().runOnUiThread(() -> {
                                                    updateDownloadButtonState();
                                                });
                                            }
                                        }
                                );

                                Toast.makeText(getContext(),
                                        "Download complete: " + currentTrack.getTitle(),
                                        Toast.LENGTH_SHORT).show();

                                if (getActivity() instanceof MainActivity) {
                                    MainActivity mainActivity = (MainActivity) getActivity();
                                    mainActivity.refreshDownloadedMusic();
                                    mainActivity.notifyDownloadCompleted(currentTrack);
                                }
                                updateDownloadButtonState();
                            });
                        }
                    }
                    @Override
                    public void onDownloadError(String message) {
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                btnDownload.setEnabled(true);
                                btnDownload.setAlpha(1.0f);

                                Log.e(TAG, "Download failed: " + message);
                                Toast.makeText(getContext(),
                                        "Download failed: " + message,
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                });
    }
    private void updateDownloadButtonState() {
        if (currentTrack == null || btnDownload == null) return;
        Log.d(TAG, "Updating download button state for: " + currentTrack.getTitle());
        boolean isDownloaded = checkIfTrackIsDownloaded(currentTrack);
        Log.d(TAG, "Track " + currentTrack.getTitle() + " is downloaded: " + isDownloaded);
        if (isDownloaded) {
            btnDownload.setImageResource(R.drawable.ic_downloaded);
            btnDownload.setAlpha(0.6f);
            btnDownload.setEnabled(false);
        } else {
            btnDownload.setImageResource(R.drawable.ic_download);
            btnDownload.setAlpha(1.0f);
            btnDownload.setEnabled(true);
        }
    }

    private boolean checkIfTrackIsDownloaded(Track track) {
        if (track == null) return false;
        if (track instanceof DownloadedTrack) {
            return track.isDownloaded();
        }
        try {
            DownloadedMusicDbHelper dbHelper =
                    new DownloadedMusicDbHelper(requireContext());
            java.util.List<Track> localTracks =
                    com.example.melodix.fragment.DownloadedMusicFragment.getDownloadedTracksFromDb(dbHelper);

            for (Track localTrack : localTracks) {
                if (localTrack.getId() == track.getId()) {
                    Log.d(TAG, "Track found in local database: " + track.getTitle());
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking local database: " + e.getMessage());
        }

        return false;
    }
}



