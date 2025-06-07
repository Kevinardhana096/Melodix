package com.example.melodix.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.melodix.listener.FavoriteChangeListener;
import com.example.melodix.listener.MusicPlayer;
import com.example.melodix.R;
import com.example.melodix.listener.ThemeManager;
import com.example.melodix.listener.TrackChangeListener;
import com.example.melodix.listener.UserPreferencesManager;
import com.example.melodix.api.DeezerRepository;
import com.example.melodix.database.DownloadedMusicDbHelper;
import com.example.melodix.fragment.DownloadedMusicFragment;
import com.example.melodix.fragment.FavoriteFragment;

import java.util.ArrayList;
import java.util.List;

import com.example.melodix.fragment.HomeFragment;
import com.example.melodix.fragment.MusicFragment;
import com.example.melodix.model.Track;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import android.app.AlertDialog;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private BottomNavigationView bottomNavigation;
    private DeezerRepository repository;
    private Track currentTrack;
    private HomeFragment homeFragment;
    private MusicFragment musicFragment;
    private FavoriteFragment favoriteFragment;
    private DownloadedMusicFragment downloadedMusicFragment;
    private Fragment currentFragment;
    private MusicPlayer musicPlayer;
    private List<TrackChangeListener> trackChangeListeners = new ArrayList<>();
    private List<FavoriteChangeListener> favoriteChangeListeners = new ArrayList<>();
    private List<Track> sessionTracks = new ArrayList<>();
    private boolean playingFromDownloaded = false;
    private List<Track> downloadedTracks = new ArrayList<>();
    private int currentDownloadedIndex = -1;
    private String lastAddedTrackId = "";
    private long lastAddedTime = 0;
    private static final long ADD_RECENT_DEBOUNCE_MS = 2000; // 2 detik

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.initializeTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupThemeToggleButton();
        repository = DeezerRepository.getInstance();
        musicPlayer = MusicPlayer.getInstance();
        setupMusicPlayer();

        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            musicFragment = new MusicFragment();
            favoriteFragment = new FavoriteFragment();
            downloadedMusicFragment = new DownloadedMusicFragment();

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, homeFragment, "home")
                    .hide(homeFragment)
                    .add(R.id.fragment_container, musicFragment, "music")
                    .hide(musicFragment)
                    .add(R.id.fragment_container, favoriteFragment, "favorite")
                    .hide(favoriteFragment)
                    .add(R.id.fragment_container, downloadedMusicFragment, "downloaded")
                    .hide(downloadedMusicFragment)
                    .commitNow();
        } else {
            homeFragment = (HomeFragment) getSupportFragmentManager().findFragmentByTag("home");
            musicFragment = (MusicFragment) getSupportFragmentManager().findFragmentByTag("music");
            favoriteFragment = (FavoriteFragment) getSupportFragmentManager().findFragmentByTag("favorite");
            downloadedMusicFragment = (DownloadedMusicFragment) getSupportFragmentManager().findFragmentByTag("downloaded");
        }

        bottomNavigation = findViewById(R.id.bottom_navigation);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                showFragment(homeFragment);
            } else if (itemId == R.id.nav_music) {
                showFragment(musicFragment);
            } else if (itemId == R.id.nav_favorite) {
                showFragment(favoriteFragment);
            } else if (itemId == R.id.nav_downloaded) {
                showFragment(downloadedMusicFragment);
            }
            return true;
        });

        if (savedInstanceState == null) {
            showFragment(homeFragment);
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        }

        String currentUserId = getCurrentUserId(); // Method untuk get current user ID
        String prefKey = "is_first_login_" + currentUserId;

        boolean isFirstLoginOnDevice = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean(prefKey, true);

        if (isFirstLoginOnDevice) {
            Log.d(TAG, "First login for user: " + currentUserId + " - clearing recent tracks");
            clearRecentlyPlayedOnNewDevice();
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean(prefKey, false)
                    .apply();
        }
    }
    private String getCurrentUserId() {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : "anonymous_user";
    }
    public void addTrackChangeListener(TrackChangeListener listener) {
        if (!trackChangeListeners.contains(listener)) {
            trackChangeListeners.add(listener);
        }
    }
    public BottomNavigationView getBottomNavigation() {
        return bottomNavigation;
    }
    public void removeTrackChangeListener(TrackChangeListener listener) {
        trackChangeListeners.remove(listener);
    }
    public void addFavoriteChangeListener(FavoriteChangeListener listener) {
        if (!favoriteChangeListeners.contains(listener)) {
            favoriteChangeListeners.add(listener);
        }
    }
    public void removeFavoriteChangeListener(FavoriteChangeListener listener) {
        favoriteChangeListeners.remove(listener);
    }
    public void notifyFavoriteChanged() {
        for (FavoriteChangeListener listener : favoriteChangeListeners) {
            listener.onFavoriteChanged();
        }
    }
    public void notifyDownloadCompleted(Track track) {
        Log.d("MainActivity", "=== DOWNLOAD COMPLETED NOTIFICATION ===");
        Log.d("MainActivity", "Track: " + track.getTitle());

        refreshDownloadedMusic();

        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof DownloadedMusicFragment && fragment.isAdded()) {
                ((DownloadedMusicFragment) fragment).onDownloadCompleted(track);
                break;
            }
        }
    }

    public void refreshDownloadedMusic() {
        Log.d("MainActivity", "=== REFRESH DOWNLOADED MUSIC REQUESTED ===");

        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof DownloadedMusicFragment && fragment.isAdded()) {
                Log.d("MainActivity", "Found DownloadedMusicFragment - forcing refresh");
                ((DownloadedMusicFragment) fragment).forceRefresh();
                return;
            }
        }

        Fragment downloadFragment = getSupportFragmentManager().findFragmentByTag("DownloadedMusicFragment");
        if (downloadFragment instanceof DownloadedMusicFragment) {
            Log.d("MainActivity", "Found DownloadedMusicFragment by tag - forcing refresh");
            ((DownloadedMusicFragment) downloadFragment).forceRefresh();
            return;
        }
        Log.d("MainActivity", "DownloadedMusicFragment not found or not visible");
    }

    public void loadAllDownloadedTracks() {
        if (downloadedMusicFragment != null) {
            downloadedTracks = downloadedMusicFragment.getAllDownloadedTracks();
        } else {
            DownloadedMusicDbHelper dbHelper = new DownloadedMusicDbHelper(this);
            downloadedTracks = DownloadedMusicFragment.getDownloadedTracksFromDb(dbHelper);
        }
    }

    public Track getNextDownloadedTrack() {
        if (downloadedTracks == null || downloadedTracks.isEmpty()) {
            loadAllDownloadedTracks();
        }
        if (downloadedTracks == null || downloadedTracks.isEmpty()) {
            return null;
        }
        if (currentDownloadedIndex == -1 && currentTrack != null) {
            for (int i = 0; i < downloadedTracks.size(); i++) {
                if (downloadedTracks.get(i).getId() == currentTrack.getId()) {
                    currentDownloadedIndex = i;
                    break;
                }
            }
        }
        if (currentDownloadedIndex != -1) {
            int nextIndex = (currentDownloadedIndex + 1) % downloadedTracks.size();
            currentDownloadedIndex = nextIndex;
            return downloadedTracks.get(nextIndex);
        }
        currentDownloadedIndex = 0;
        return downloadedTracks.get(0);
    }

    public Track getPreviousDownloadedTrack() {
        if (downloadedTracks == null || downloadedTracks.isEmpty()) {
            loadAllDownloadedTracks();
        }

        if (downloadedTracks == null || downloadedTracks.isEmpty()) {
            return null;
        }

        if (currentDownloadedIndex == -1 && currentTrack != null) {
            for (int i = 0; i < downloadedTracks.size(); i++) {
                if (downloadedTracks.get(i).getId() == currentTrack.getId()) {
                    currentDownloadedIndex = i;
                    break;
                }
            }
        }

        if (currentDownloadedIndex != -1) {
            int prevIndex = (currentDownloadedIndex - 1 + downloadedTracks.size()) % downloadedTracks.size();
            currentDownloadedIndex = prevIndex;
            return downloadedTracks.get(prevIndex);
        }

        currentDownloadedIndex = downloadedTracks.size() - 1;
        return downloadedTracks.get(currentDownloadedIndex);
    }

    public void setPlayingFromDownloaded(boolean playingFromDownloaded) {
        this.playingFromDownloaded = playingFromDownloaded;
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setTitle("Keluar Aplikasi")
            .setMessage("Apakah Anda yakin ingin keluar?")
            .setPositiveButton("Ya", (dialog, which) -> {
                super.onBackPressed();
            })
            .setNegativeButton("Tidak", null)
            .show();
    }

    private void setupMusicPlayer() {
        Log.d(TAG, "Setting up MusicPlayer listeners");

        musicPlayer.setPlaybackStatusListener(new MusicPlayer.OnPlaybackStatusListener() {
            @Override
            public void onPlaybackStarted() {
                Log.d(TAG, "MusicPlayer: Playback started");

            }

            @Override
            public void onPlaybackPaused() {
                Log.d(TAG, "MusicPlayer: Playback paused");
            }

            @Override
            public void onPlaybackStopped() {
                Log.d(TAG, "MusicPlayer: Playback stopped");
            }

            @Override
            public void onPlaybackCompleted() {
                Log.d(TAG, "MusicPlayer: Playback completed");
            }

            @Override
            public void onDurationChanged(int duration) {
            }

            @Override
            public void onPositionChanged(int position) {
            }

            @Override
            public void onPrepareStart() {
                Log.d(TAG, "MusicPlayer: Prepare started");
            }

            @Override
            public void onPrepareComplete() {
                Log.d(TAG, "MusicPlayer: Prepare completed");
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "MusicPlayer error: " + message);
                Toast.makeText(MainActivity.this, "Playback error: " + message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTrackChanged(Track newTrack) {
                Log.d(TAG, "=== MUSIC PLAYER TRACK CHANGED ===");
                Log.d(TAG, "New track: " + (newTrack != null ? newTrack.getTitle() : "null"));

                currentTrack = newTrack;

                runOnUiThread(() -> {
                    notifyTrackChanged(newTrack);
                });
            }
        });
    }

    private void showFragment(Fragment fragment) {
        if (fragment == null) {
            Log.e(TAG, "Fragment is null, cannot show");
            return;
        }

        Log.d(TAG, "=== SHOWING FRAGMENT ===");
        Log.d(TAG, "Fragment: " + fragment.getClass().getSimpleName());
        Log.d(TAG, "Current track: " + (currentTrack != null ? currentTrack.getTitle() : "null"));

        try {
            currentFragment = fragment;

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);

            if (homeFragment != null && homeFragment.isAdded()) {
                transaction.hide(homeFragment);
            }
            if (musicFragment != null && musicFragment.isAdded()) {
                transaction.hide(musicFragment);
            }
            if (favoriteFragment != null && favoriteFragment.isAdded()) {
                transaction.hide(favoriteFragment);
            }
            if (downloadedMusicFragment != null && downloadedMusicFragment.isAdded()) {
                transaction.hide(downloadedMusicFragment);
            }

            transaction.show(fragment);
            transaction.commitAllowingStateLoss();

            if (fragment == musicFragment && currentTrack != null) {
                Log.d(TAG, "Showing MusicFragment with current track: " + currentTrack.getTitle());

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    notifyTrackChanged(currentTrack);
                }, 100);
            }

            if (fragment == homeFragment) {
                homeFragment.refreshRecentlyPlayed();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error showing fragment: " + e.getMessage(), e);
            Toast.makeText(this, "Navigation error. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private void switchToMusicFragment() {
        showFragment(musicFragment);
        bottomNavigation.setSelectedItemId(R.id.nav_music);
    }

    public void notifyTrackChanged(Track track) {
        Log.d(TAG, "=== NOTIFYING TRACK CHANGED ===");
        Log.d(TAG, "Track: " + (track != null ? track.getTitle() : "null"));
        Log.d(TAG, "Number of listeners: " + trackChangeListeners.size());

        for (int i = 0; i < trackChangeListeners.size(); i++) {
            TrackChangeListener listener = trackChangeListeners.get(i);
            if (listener != null) {
                try {
                    Log.d(TAG, "Notifying listener " + i + ": " + listener.getClass().getSimpleName());
                    listener.onTrackChanged(track);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying listener " + i + ": " + e.getMessage(), e);
                }
            }
        }
    }

    public void playTrack(Track trackToPlay) {
        if (trackToPlay == null) {
            Toast.makeText(this, "Invalid track", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "playTrack called with null track");
            return;
        }

        Log.d(TAG, "=== MAIN ACTIVITY PLAY TRACK ===");
        Log.d(TAG, "Track: " + trackToPlay.getTitle());
        Log.d(TAG, "Track ID: " + trackToPlay.getId());
        Log.d(TAG, "Preview URL: " + trackToPlay.getPreviewUrl());
        Log.d(TAG, "Current Fragment: " + (currentFragment != null ? currentFragment.getClass().getSimpleName() : "null"));

        if (trackToPlay.getPreviewUrl() == null || trackToPlay.getPreviewUrl().isEmpty()) {
            Toast.makeText(this, "Track has no preview URL for playback.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Track '" + trackToPlay.getTitle() + "' has no preview URL.");
            return;
        }

        this.currentTrack = trackToPlay;

        try {
            Log.d(TAG, "Stopping current playback and preparing new track");

            if (musicPlayer.isPlaying()) {
                musicPlayer.stop();
            }

            List<Track> fullPlaylist = buildPlaylistForTrack(trackToPlay);

            this.sessionTracks.clear();
            this.sessionTracks.addAll(fullPlaylist);

            Log.d(TAG, "Setting playlist with " + fullPlaylist.size() + " tracks");
            musicPlayer.setPlaylist(this, this.sessionTracks, 0);

            runOnUiThread(() -> {
                Log.d(TAG, "Notifying track change to all listeners");
                notifyTrackChanged(trackToPlay);
            });

            addToRecentlyPlayedAsync(trackToPlay);

            runOnUiThread(() -> {
                switchToMusicFragment();
            });

            Log.d(TAG, "✅ Track preparation completed successfully");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in playTrack: " + e.getMessage(), e);
            Toast.makeText(this, "Error playing track: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void setupThemeToggleButton() {
        View themeButton = findViewById(R.id.btn_theme_mode);

        if (themeButton != null) {
            updateThemeButtonIcon();

            themeButton.setOnClickListener(v -> {
                String message = ThemeManager.toggleThemeWithEmoji(this);

                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

                updateThemeButtonIcon();

                Log.d(TAG, "Theme toggle: " + message);
            });
        }
    }
    private void updateThemeButtonIcon() {
        View themeButton = findViewById(R.id.btn_theme_mode);

        if (themeButton instanceof android.widget.ImageButton) {
            android.widget.ImageButton imageButton = (android.widget.ImageButton) themeButton;

            int iconResource = ThemeManager.isDarkMode(this) ?
                    R.drawable.ic_light_mode :
                    R.drawable.ic_dark_mode;

            imageButton.setImageResource(iconResource);

            String description = ThemeManager.isDarkMode(this) ?
                    "Switch to light mode" : "Switch to dark mode";
            imageButton.setContentDescription(description);
        }
    }
    public void playTrackFromSearch(Track track, List<Track> searchResults) {
        if (track == null) {
            Toast.makeText(this, "Invalid track", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "=== PLAY TRACK FROM SEARCH ===");
        Log.d(TAG, "Selected track: " + track.getTitle());
        Log.d(TAG, "Search results count: " + (searchResults != null ? searchResults.size() : 0));

        showFragment(musicFragment);
        bottomNavigation.setSelectedItemId(R.id.nav_music);

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (musicFragment != null && searchResults != null && !searchResults.isEmpty()) {
                musicFragment.onTrackSelectedFromSearch(track, searchResults);
            } else {
                playTrack(track);
            }
        }, 100);
        addToRecentlyPlayed(track);
        Log.d(TAG, "✅ Added search track to recent played: " + track.getTitle());
    }

    private List<Track> buildPlaylistForTrack(Track trackToPlay) {
        List<Track> fullPlaylist = new ArrayList<>();
        List<Track> sourceTracks = new ArrayList<>();

        fullPlaylist.add(trackToPlay);

        if (currentFragment instanceof FavoriteFragment) {
            sourceTracks.addAll(getFavoriteTracks());
            Log.d(TAG, "Building playlist from favorites: " + sourceTracks.size() + " tracks");
        } else if (currentFragment instanceof DownloadedMusicFragment) {
            sourceTracks.addAll(getDownloadedTracks());
            Log.d(TAG, "Building playlist from downloaded: " + sourceTracks.size() + " tracks");
        } else if (homeFragment != null && homeFragment.isVisible()) {
            List<Track> homeTracks = homeFragment.getAllFilteredTracks();
            if (homeTracks != null) {
                sourceTracks.addAll(homeTracks);
            }
            Log.d(TAG, "Building playlist from home: " + sourceTracks.size() + " tracks");
        }

        for (Track sourceTrack : sourceTracks) {
            if (sourceTrack.getId() != trackToPlay.getId()) {
                fullPlaylist.add(sourceTrack);
            }
        }

        return fullPlaylist;
    }
    private List<Track> getDownloadedTracks() {
        if (downloadedTracks == null || downloadedTracks.isEmpty()) {
            loadAllDownloadedTracks();
        }
        return downloadedTracks != null ? downloadedTracks : new ArrayList<>();
    }

    private List<Track> getFavoriteTracks() {
        return UserPreferencesManager.getFavoriteTracks(this);
    }

    public void addToRecentlyPlayedAsync(Track track) {
        // GANTI DENGAN LOCAL-ONLY
        addToRecentlyPlayed(track);
    }
    public void addToRecentlyPlayed(Track track) {
        if (track == null) {
            Log.e(TAG, "Cannot add null track to recently played");
            return;
        }

        // Tambah debounce untuk prevent duplikasi
        String trackId = String.valueOf(track.getId());
        long currentTime = System.currentTimeMillis();

        if (trackId.equals(lastAddedTrackId) &&
                (currentTime - lastAddedTime) < ADD_RECENT_DEBOUNCE_MS) {
            Log.d(TAG, "Skipping duplicate recent track add: " + track.getTitle() +
                    " (debounced: " + (currentTime - lastAddedTime) + "ms)");
            return;
        }

        lastAddedTrackId = trackId;
        lastAddedTime = currentTime;

        try {
            List<Track> recentTracks = UserPreferencesManager.getRecentTracks(this);

            // Remove existing track dengan ID yang sama
            recentTracks.removeIf(t -> t != null && t.getId() == track.getId());

            // Add to beginning
            recentTracks.add(0, track);

            // Limit to 10 items
            if (recentTracks.size() > 10) {
                recentTracks = new ArrayList<>(recentTracks.subList(0, 10));
            }

            Log.d(TAG, "Adding track to recently played LOCAL ONLY: " + track.getTitle() +
                    " (total: " + recentTracks.size() + ")");

            // HANYA SIMPAN LOCAL - JANGAN SYNC KE CLOUD
            UserPreferencesManager.saveRecentTracks(this, recentTracks);

            // Refresh UI
            if (currentFragment instanceof HomeFragment && homeFragment != null) {
                runOnUiThread(() -> homeFragment.refreshRecentlyPlayed());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating recently played tracks: " + e.getMessage(), e);
        }
    }

    public void clearRecentlyPlayedOnNewDevice() {
        Log.d(TAG, "Clearing recently played for new device");

        try {
            // Clear local recent tracks
            UserPreferencesManager.saveRecentTracks(this, new ArrayList<>());
            Log.d(TAG, "✅ Local recent tracks cleared");

            // Clear any cached data yang mungkin ada
            getSharedPreferences("recent_tracks", MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();

            // Refresh UI jika homeFragment sudah ada dan visible
            if (currentFragment instanceof HomeFragment && homeFragment != null) {
                runOnUiThread(() -> {
                    homeFragment.refreshRecentlyPlayed();
                    Log.d(TAG, "✅ UI refreshed after clearing recent tracks");
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error clearing recently played: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (musicPlayer != null) {
            musicPlayer.release();
        }
    }
}
