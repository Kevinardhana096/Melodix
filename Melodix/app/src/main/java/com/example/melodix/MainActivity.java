package com.example.melodix;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.melodix.fragment.FavoriteFragment;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import android.content.SharedPreferences;
import com.bumptech.glide.Glide;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.example.melodix.fragment.HomeFragment;
import com.example.melodix.fragment.MusicFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private BottomNavigationView bottomNavigation;
    private DeezerRepository repository;
    private Track currentTrack;
    private HomeFragment homeFragment;
    private MusicFragment musicFragment;
    private FavoriteFragment favoriteFragment;
    private MusicPlayer musicPlayer;
    private List<TrackChangeListener> trackChangeListeners = new ArrayList<>();
    private List<FavoriteChangeListener> favoriteChangeListeners = new ArrayList<>();
    private List<Track> sessionTracks = new ArrayList<>();

    public void addTrackChangeListener(TrackChangeListener listener) {
        if (!trackChangeListeners.contains(listener)) {
            trackChangeListeners.add(listener);
        }
    }

    // Remove a listener
    public void removeTrackChangeListener(TrackChangeListener listener) {
        trackChangeListeners.remove(listener);
    }

    // Add a favorite change listener
    public void addFavoriteChangeListener(FavoriteChangeListener listener) {
        if (!favoriteChangeListeners.contains(listener)) {
            favoriteChangeListeners.add(listener);
        }
    }

    // Remove a favorite change listener
    public void removeFavoriteChangeListener(FavoriteChangeListener listener) {
        favoriteChangeListeners.remove(listener);
    }

    // Notify all favorite change listeners
    public void notifyFavoriteChanged() {
        for (FavoriteChangeListener listener : favoriteChangeListeners) {
            listener.onFavoriteChanged();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before setting content view
        int themeMode = ThemeManager.getThemeMode(this);
        ThemeManager.setTheme(this, themeMode);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize repository
        repository = DeezerRepository.getInstance();

        // Initialize music player
        musicPlayer = MusicPlayer.getInstance();
        setupMusicPlayer();

        // Initialize fragments
        if (savedInstanceState == null) {
            // Only create new fragment instances if this is a fresh start
            homeFragment = new HomeFragment();
            musicFragment = new MusicFragment();
            favoriteFragment = new FavoriteFragment();

            // Initial setup - add fragments but keep them hidden
            getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, homeFragment, "home")
                .hide(homeFragment)
                .add(R.id.fragment_container, musicFragment, "music")
                .hide(musicFragment)
                .add(R.id.fragment_container, favoriteFragment, "favorite")
                .hide(favoriteFragment)
                .commitNow(); // Use commitNow to ensure fragments are added immediately
        } else {
            // If we're being restored from a saved state, retrieve existing fragments
            homeFragment = (HomeFragment) getSupportFragmentManager().findFragmentByTag("home");
            musicFragment = (MusicFragment) getSupportFragmentManager().findFragmentByTag("music");
            favoriteFragment = (FavoriteFragment) getSupportFragmentManager().findFragmentByTag("favorite");
        }

        // Initialize bottom navigation
        bottomNavigation = findViewById(R.id.bottom_navigation);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                showFragment(homeFragment);
            } else if (itemId == R.id.nav_music) {
                showFragment(musicFragment);
            } else if (itemId == R.id.nav_favorite) {
                showFragment(favoriteFragment);
            }
            return true;
        });

        // Set home as default selected fragment if it's a fresh start
        if (savedInstanceState == null) {
            showFragment(homeFragment);
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        }
    }

    private void setupMusicPlayer() {
        musicPlayer.setPlaybackStatusListener(new MusicPlayer.OnPlaybackStatusListener() {
            @Override
            public void onPlaybackStarted() {
                // Update UI to show playing state
            }

            @Override
            public void onPlaybackPaused() {
                // Update UI to show paused state
            }

            @Override
            public void onPlaybackStopped() {
                // Update UI to show stopped state
            }

            @Override
            public void onPlaybackCompleted() {
                // Handle playback completion
            }

            @Override
            public void onDurationChanged(int duration) {
                // Update duration display
            }

            @Override
            public void onPositionChanged(int position) {
                // Update progress display
            }

            @Override
            public void onPrepareStart() {
                // Show loading indicator
            }

            @Override
            public void onPrepareComplete() {
                // Hide loading indicator
            }

            @Override
            public void onError(String message) {
                // Show error message
                Toast.makeText(MainActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Updated method that just shows/hides fragments rather than adding them repeatedly
     */
    private void showFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // Add smooth animations
        transaction.setCustomAnimations(
                R.anim.fade_in,
                R.anim.fade_out
        );

        // Hide all fragments
        if (homeFragment != null && homeFragment.isAdded()) {
            transaction.hide(homeFragment);
        }
        if (musicFragment != null && musicFragment.isAdded()) {
            transaction.hide(musicFragment);
        }
        if (favoriteFragment != null && favoriteFragment.isAdded()) {
            transaction.hide(favoriteFragment);
        }

        // Show the selected fragment
        transaction.show(fragment);
        transaction.commitAllowingStateLoss();

        // If showing music fragment and no track is playing, check if it's a first login
        if (fragment == musicFragment && musicPlayer.getCurrentTrack() == null) {
            SharedPreferences prefs = getSharedPreferences("MelodixPrefs", MODE_PRIVATE);
            boolean isFirstMusicVisit = prefs.getBoolean("isFirstMusicVisit", true);

            if (isFirstMusicVisit) {
                // Mark that first visit has occurred
                prefs.edit().putBoolean("isFirstMusicVisit", false).apply();

                // Load a random track
                loadRandomTrack();
            }
        }

        if (fragment == homeFragment) {
            homeFragment.refreshRecentlyPlayed();
        }

        if (fragment == musicFragment && currentTrack != null) {
            // If we have a current track, make sure MusicFragment shows it
            for (TrackChangeListener listener : trackChangeListeners) {
                listener.onTrackChanged(currentTrack);
            }
        }
    }

    /**
     * Switch to the music fragment and select it in the bottom navigation
     */
    private void switchToMusicFragment() {
        // Show the music fragment
        showFragment(musicFragment);

        // Update the selected item in the bottom navigation
        bottomNavigation.setSelectedItemId(R.id.nav_music);
    }

    /**
     * Set playlist with all available tracks and play the selected one
     */
    public void playTrack(Track track) {
        if (track == null) {
            Toast.makeText(this, "Invalid track", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Playing track: " + track.getTitle());

        // Store the current track
        currentTrack = track;

        // Add all filteredTracks to session if available through HomeFragment
        if (homeFragment != null && homeFragment.isVisible()) {
            List<Track> allTracks = homeFragment.getAllFilteredTracks();
            if (allTracks != null && !allTracks.isEmpty()) {
                // Clear previous session tracks and add all filtered tracks
                sessionTracks.clear();
                sessionTracks.addAll(allTracks);
                Log.d(TAG, "Added " + sessionTracks.size() + " tracks to playlist from HomeFragment");
            }
        }

        // If we still have no tracks in session (shouldn't happen), add at least the current track
        if (sessionTracks.isEmpty()) {
            sessionTracks.add(track);
            Log.d(TAG, "Added only the current track to playlist");
        }

        // Find the index of the current track in the playlist
        int trackIndex = -1;
        for (int i = 0; i < sessionTracks.size(); i++) {
            if (sessionTracks.get(i).getId() == track.getId()) {
                trackIndex = i;
                break;
            }
        }

        // If track is somehow not in the playlist (shouldn't happen), add it and play
        if (trackIndex == -1) {
            sessionTracks.add(track);
            trackIndex = sessionTracks.size() - 1;
            Log.d(TAG, "Track wasn't in playlist, added it at the end");
        }

        // Setup playlist in MusicPlayer with proper context
        Log.d(TAG, "Setting playlist with " + sessionTracks.size() + " tracks, playing track at index " + trackIndex);
        musicPlayer.setPlaylist(this, sessionTracks, trackIndex);

        // Add to recently played tracks
        addToRecentlyPlayed(track);

        // Notify all track change listeners
        for (TrackChangeListener listener : trackChangeListeners) {
            listener.onTrackChanged(track);
        }

        // Switch to the Music fragment
        switchToMusicFragment();
    }

    private void loadRandomTrack() {
        // Common search terms for popular music
        String[] searchTerms = {"pop", "rock", "hip hop", "jazz", "dance"};
        String randomTerm = searchTerms[(int) (Math.random() * searchTerms.length)];

        Toast.makeText(this, "Loading some music for you...", Toast.LENGTH_SHORT).show();

        // Use the DeezerRepository to fetch tracks
        repository.searchTracks(randomTerm, new DeezerRepository.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> data) {
                if (data != null && !data.isEmpty()) {
                    // Select a random track from results
                    int randomIndex = (int) (Math.random() * Math.min(data.size(), 10));
                    Track randomTrack = data.get(randomIndex);

                    // Play the random track
                    runOnUiThread(() -> playTrack(randomTrack));
                }
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Couldn't load music: " + message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void addToRecentlyPlayed(Track track) {
        // Get current list of recently played tracks
        SharedPreferences prefs = getSharedPreferences("MelodixPrefs", MODE_PRIVATE);
        Gson gson = new Gson();
        String json = prefs.getString("recentTracks", null);
        List<Track> recentTracks;

        if (json == null) {
            recentTracks = new ArrayList<>();
        } else {
            Type type = new TypeToken<ArrayList<Track>>() {}.getType();
            recentTracks = gson.fromJson(json, type);
        }

        // Remove track if it already exists (to avoid duplicates)
        recentTracks.removeIf(t -> t.getId() == track.getId());

        // Add track to the beginning of the list
        recentTracks.add(0, track);

        // Keep only the most recent tracks (e.g., 10)
        if (recentTracks.size() > 10) {
            recentTracks = recentTracks.subList(0, 10);
        }

        // Save the updated list
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("recentTracks", gson.toJson(recentTracks));
        editor.apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (musicPlayer != null) {
            musicPlayer.release();
        }
    }
}

