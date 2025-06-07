package com.example.melodix.fragment;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import com.bumptech.glide.Glide;
import com.example.melodix.activity.ProfileActivity;
import com.example.melodix.api.DeezerRepository;
import com.example.melodix.activity.MainActivity;
import com.example.melodix.adapter.NewMusicAdapter;
import com.example.melodix.R;
import com.example.melodix.adapter.RecentlyPlayedAdapter;
import com.example.melodix.adapter.SearchResultAdapter;
import com.example.melodix.listener.ThemeManager;
import com.example.melodix.model.Track;
import com.example.melodix.model.Artist;
import com.example.melodix.model.Album;
import com.example.melodix.listener.UserPreferencesManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements RecentlyPlayedAdapter.OnItemClickListener,
        NewMusicAdapter.OnItemClickListener, SearchResultAdapter.OnSearchItemClickListener {
    private static final String TAG = "HomeFragment";
    private RecyclerView recyclerNewMusic;
    private NewMusicAdapter newMusicAdapter;
    private DeezerRepository repository;
    private View searchPanel;
    private EditText searchPanelEditText;
    private ImageView searchCloseButton;
    private View cardSearch;
    private RecyclerView recyclerSearchResults;
    private SearchResultAdapter searchResultAdapter;
    private ProgressBar progressSearch;
    private TextView txtSearchResults;
    private com.google.android.material.chip.ChipGroup chipGroup;
    private com.google.android.material.chip.Chip chipSongs, chipArtists, chipAlbums;
    private static final long SEARCH_DELAY_MS = 500;
    private Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private List<Track> currentSearchResults = new ArrayList<>();
    private static List<Track> newMusicCache = new ArrayList<>();
    private boolean initialLoadDone = false;
    private ImageView btnThemeMode;
    private boolean isLoadingMore = false;
    private int currentPage = 1;
    private boolean recentlyPlayedSetupDone = false;
    private long lastTrackClickTime = 0;
    private static final long TRACK_CLICK_DEBOUNCE_MS = 1000; // 1 detik
    private com.google.android.material.button.MaterialButton btnRefresh;
    private TextView txtErrorMessage;
    private View errorContainer;
    private boolean isNetworkError = false;
    private String currentSearchQuery = "";
    private boolean isSearchCancelled = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: savedInstanceState=" + (savedInstanceState != null));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isThemeChange", true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = DeezerRepository.getInstance();
        recyclerNewMusic = view.findViewById(R.id.recycler_new_music);

        searchPanel = view.findViewById(R.id.search_panel_include);
        searchPanelEditText = view.findViewById(R.id.edit_search_panel);
        searchCloseButton = view.findViewById(R.id.btn_search_close);
        cardSearch = view.findViewById(R.id.card_search);
        recyclerSearchResults = view.findViewById(R.id.recycler_search_results);
        progressSearch = view.findViewById(R.id.progress_search);
        txtSearchResults = view.findViewById(R.id.txt_search_results);
        btnRefresh = view.findViewById(R.id.btn_refresh);
        txtErrorMessage = view.findViewById(R.id.txt_error_message);
        errorContainer = view.findViewById(R.id.error_container);


        chipGroup = view.findViewById(R.id.chip_group);
        chipSongs = view.findViewById(R.id.chip_songs);
        chipArtists = view.findViewById(R.id.chip_artists);
        chipAlbums = view.findViewById(R.id.chip_albums);

        // Setup refresh button listener
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                Log.d(TAG, "Refresh button clicked");
                retryLoadData();
            });
        }

        // Hide error container initially
        if (errorContainer != null) {
            errorContainer.setVisibility(View.GONE);
        }

        EditText mainSearchEditText = view.findViewById(R.id.edit_search);
        if (mainSearchEditText != null) {
            mainSearchEditText.setOnClickListener(v -> showSearchPanel());
            mainSearchEditText.setFocusable(false);
        }

        if (cardSearch != null) {
            cardSearch.setOnClickListener(v -> showSearchPanel());
        }

        if (searchCloseButton != null) {
            searchCloseButton.setOnClickListener(v -> hideSearchPanel());
        }
        setupSearchListeners();

        ImageView profileImage = view.findViewById(R.id.profile_image);
        TextView txtUsername = view.findViewById(R.id.txt_username);
        btnThemeMode = view.findViewById(R.id.btn_theme_mode);

        if (profileImage != null) {
            profileImage.setOnClickListener(v -> navigateToProfileActivity());
        }

        if (txtUsername != null) {
            txtUsername.setOnClickListener(v -> navigateToProfileActivity());
        }

        if (btnThemeMode != null) {
            btnThemeMode.setOnClickListener(v -> {
                ThemeManager.toggleTheme(requireActivity());
                updateThemeIcon();
            });
            updateThemeIcon();
        }

        if (searchPanel != null) {
            searchPanel.setVisibility(View.GONE);
        }

        searchResultAdapter = new SearchResultAdapter(this);
        if (recyclerSearchResults != null) {
            recyclerSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerSearchResults.setAdapter(searchResultAdapter);
        }
        setupRecentlyPlayed();
        setupNewMusic();
        loadUserData();
    }
    private void updateThemeIcon() {
        if (btnThemeMode != null && isAdded() && getActivity() != null) {
            int currentThemeSetting = ThemeManager.getThemeMode(requireActivity());
            if (currentThemeSetting == ThemeManager.DARK_MODE) {
                btnThemeMode.setImageResource(R.drawable.ic_light_mode);
            } else {
                btnThemeMode.setImageResource(R.drawable.ic_dark_mode);
            }
        }
    }
    private void retryLoadData() {
        Log.d(TAG, "=== RETRYING DATA LOAD ===");

        // Check network first
        if (!isNetworkAvailable()) {
            showNetworkError("No internet connection. Please check your network settings.");
            return;
        }

        // Hide error container
        hideErrorContainer();

        // Reset states
        isNetworkError = false;
        initialLoadDone = false;
        newMusicCache.clear();

        // Show loading
        ProgressBar progressNewMusic = getView() != null ? getView().findViewById(R.id.progress_circular) : null;
        if (progressNewMusic != null) {
            progressNewMusic.setVisibility(View.VISIBLE);
        }

        // Retry loading new music
        currentPage = 1;
        loadNewMusic(progressNewMusic);

        // Refresh recently played if needed
        refreshRecentlyPlayed();

        Toast.makeText(getContext(), "Refreshing data...", Toast.LENGTH_SHORT).show();
    }

    private void showNetworkError(String message) {
        Log.d(TAG, "Showing network error: " + message);

        isNetworkError = true;

        if (getView() == null) return;

        // Hide loading progress
        ProgressBar progressNewMusic = getView().findViewById(R.id.progress_circular);
        if (progressNewMusic != null) {
            progressNewMusic.setVisibility(View.GONE);
        }

        // Show error container
        if (errorContainer != null) {
            errorContainer.setVisibility(View.VISIBLE);
        }

        // Set error message
        if (txtErrorMessage != null) {
            txtErrorMessage.setText(message);
        }

        // Hide main content
        if (recyclerNewMusic != null) {
            recyclerNewMusic.setVisibility(View.GONE);
        }
    }

    private void hideErrorContainer() {
        if (errorContainer != null) {
            errorContainer.setVisibility(View.GONE);
        }

        // Show main content
        if (recyclerNewMusic != null) {
            recyclerNewMusic.setVisibility(View.VISIBLE);
        }

        isNetworkError = false;
    }

    private void setupSearchListeners() {
        searchPanelEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Cancel previous search
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                    isSearchCancelled = true; // Cancel ongoing search
                }

                final String query = s.toString().trim();
                currentSearchQuery = query;

                if (query.isEmpty()) {
                    txtSearchResults.setText("Enter search terms");
                    searchResultAdapter.updateResults(null);
                    progressSearch.setVisibility(View.GONE);
                    return;
                }

                // Reset cancellation flag
                isSearchCancelled = false;

                // Show immediate feedback
                progressSearch.setVisibility(View.VISIBLE);
                txtSearchResults.setText("Preparing search...");

                searchRunnable = () -> {
                    // Check if search was cancelled
                    if (isSearchCancelled || !query.equals(currentSearchQuery)) {
                        Log.d(TAG, "Search cancelled for query: " + query);
                        return;
                    }

                    Log.d(TAG, "=== STARTING SEARCH ===");
                    Log.d(TAG, "Query: " + query);
                    performSearch(query);
                };

                // ✅ OPTIMIZE: Reduce delay even more for faster response
                searchHandler.postDelayed(searchRunnable, 250); // From 300ms to 250ms
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        chipSongs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                currentSearchType = SearchType.SONGS;
                String query = searchPanelEditText.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                }
            }
        });

        chipArtists.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                currentSearchType = SearchType.ARTISTS;
                String query = searchPanelEditText.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                }
            }
        });

        chipAlbums.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                currentSearchType = SearchType.ALBUMS;
                String query = searchPanelEditText.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                }
            }
        });

        searchPanelEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = searchPanelEditText.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                }
                return true;
            }
            return false;
        });
    }
    private void performSearch(String query) {
        Log.d(TAG, "=== PERFORM SEARCH START ===");
        Log.d(TAG, "Available memory: " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + " MB");
        Log.d(TAG, "Query: '" + query + "', Type: " + currentSearchType);

        // ✅ ADD: Quick validation
        if (query.length() < 2) {
            txtSearchResults.setText("Enter at least 2 characters");
            progressSearch.setVisibility(View.GONE);
            return;
        }

        long performSearchStart = System.currentTimeMillis();

        progressSearch.setVisibility(View.VISIBLE);
        txtSearchResults.setText("Searching for \"" + query + "\"...");

        switch (currentSearchType) {
            case SONGS:
                searchSongs(query);
                break;
            case ARTISTS:
                searchArtists(query);
                break;
            case ALBUMS:
                searchAlbums(query);
                break;
        }

        long performSearchDuration = System.currentTimeMillis() - performSearchStart;
        Log.d(TAG, "performSearch method completed in: " + performSearchDuration + "ms");
    }
    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager =
                (android.net.ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.net.Network network = connectivityManager.getActiveNetwork();
                android.net.NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                return capabilities != null && (
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                );
            } else {
                android.net.NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.isConnected();
            }
        }
        return false;
    }

    private void searchSongs(String query) {
        if (!isNetworkAvailable()) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    progressSearch.setVisibility(View.GONE);
                    txtSearchResults.setText("No internet connection");
                    searchResultAdapter.updateResults(null);
                });
            }
            return;
        }

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                progressSearch.setVisibility(View.VISIBLE);
                txtSearchResults.setText("Searching for \"" + query + "\"...");
            });
        }

        long searchStartTime = System.currentTimeMillis();

        new Thread(() -> {
            repository.searchTracks(query, new DeezerRepository.DataCallback<List<Track>>() {
                @Override
                public void onSuccess(List<Track> data) {
                    if (getActivity() == null || !isAdded()) return;

                    long searchDuration = System.currentTimeMillis() - searchStartTime;
                    Log.d(TAG, "Search API completed in " + searchDuration + "ms");

                    // ✅ OPTIMIZE: Process data in background first
                    new Thread(() -> {
                        try {
                            if (data != null && !data.isEmpty()) {
                                currentSearchResults.clear();
                                currentSearchResults.addAll(data);

                                // Set context efficiently in background
                                for (Track track : currentSearchResults) {
                                    if (track != null) {
                                        track.setContext(requireContext());
                                    }
                                }

                                // ✅ OPTIMIZE: Update UI in smaller batches
                                updateSearchResultsOptimized(data, query);
                            } else {
                                // Handle empty results
                                getActivity().runOnUiThread(() -> {
                                    currentSearchResults.clear();
                                    progressSearch.setVisibility(View.GONE);
                                    searchResultAdapter.updateResults(null);
                                    txtSearchResults.setText("No songs found");
                                });
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing search results", e);
                        }
                    }).start();
                }

                @Override
                public void onError(String message) {
                    if (getActivity() == null || !isAdded()) return;

                    getActivity().runOnUiThread(() -> {
                        currentSearchResults.clear();
                        progressSearch.setVisibility(View.GONE);
                        txtSearchResults.setText(!isNetworkAvailable() ?
                                "No internet connection" : "Search error: " + message);
                        searchResultAdapter.updateResults(null);
                    });
                }
            });
        }).start();
    }
    private void updateSearchResultsOptimized(List<Track> data, String query) {
        if (getActivity() == null || !isAdded()) return;

        getActivity().runOnUiThread(() -> {
            try {
                progressSearch.setVisibility(View.GONE);

                if (data.size() <= 50) {
                    // Small/Medium result set - update all at once
                    searchResultAdapter.updateResults(data);
                    txtSearchResults.setText("Found " + data.size() + " songs");
                    Log.d(TAG, "✅ Search results updated: " + data.size() + " tracks");
                } else {
                    // Large result set - use manual progressive loading
                    progressiveLoadSearchResults(data);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating search results UI", e);
                txtSearchResults.setText("Error displaying results");
            }
        });
    }
    private void progressiveLoadSearchResults(List<Track> data) {
        try {
            // Show first batch immediately
            List<Track> firstBatch = data.subList(0, Math.min(30, data.size()));
            searchResultAdapter.updateResults(firstBatch);
            txtSearchResults.setText("Loading " + data.size() + " songs...");

            currentSearchResults.clear();
            currentSearchResults.addAll(data);

            // Load remaining data progressively with shorter delay
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                try {
                    // Check if search was cancelled
                    if (isSearchCancelled) {
                        Log.d(TAG, "Progressive loading cancelled");
                        return;
                    }

                    searchResultAdapter.updateResults(data);
                    txtSearchResults.setText("Found " + data.size() + " songs");
                    Log.d(TAG, "✅ Progressive search results completed: " + data.size() + " tracks");
                } catch (Exception e) {
                    Log.e(TAG, "Error in progressive load", e);
                }
            }, 100); // Reduced from 200ms to 100ms

        } catch (Exception e) {
            Log.e(TAG, "Error in progressive loading", e);
            // Fallback to normal loading
            searchResultAdapter.updateResults(data);
            txtSearchResults.setText("Found " + data.size() + " songs");
        }
    }


    private void searchArtists(String query) {
        getActivity().runOnUiThread(() -> {
            progressSearch.setVisibility(View.VISIBLE);
            txtSearchResults.setText("Searching for \"" + query + "\"...");
        });

        new Thread(() -> {
            repository.searchArtists(query, new DeezerRepository.DataCallback<List<Artist>>() {
                @Override
                public void onSuccess(List<Artist> data) {
                    if (getActivity() == null || !isAdded()) return;

                    final List<Track> artistTracks = new ArrayList<>();
                    if (data != null && !data.isEmpty()) {
                        for (Artist artist : data) {
                            Track artistTrack = new Track();
                            artistTrack.setId(artist.getId());
                            artistTrack.setTitle(artist.getName());
                            artistTrack.setType("artist");
                            if (artist.getPictureMedium() != null) {
                                Album album = new Album();
                                album.setCoverMedium(artist.getPictureMedium());
                                artistTrack.setAlbum(album);
                            }
                            artistTrack.setContext(requireContext());
                            artistTracks.add(artistTrack);
                        }
                    }

                    getActivity().runOnUiThread(() -> {
                        progressSearch.setVisibility(View.GONE);

                        if (!artistTracks.isEmpty()) {
                            currentSearchResults.clear();
                            currentSearchResults.addAll(artistTracks);
                            searchResultAdapter.updateResults(artistTracks);
                            txtSearchResults.setText("Found " + artistTracks.size() + " artists");
                        } else {
                            currentSearchResults.clear();
                            searchResultAdapter.updateResults(null);
                            txtSearchResults.setText("No artists found");
                        }
                    });
                }
                @Override
                public void onError(String message) {
                    if (getActivity() == null || !isAdded()) return;

                    getActivity().runOnUiThread(() -> {
                        currentSearchResults.clear();
                        progressSearch.setVisibility(View.GONE);
                        txtSearchResults.setText("Error: " + message);
                        searchResultAdapter.updateResults(null);
                        Log.e(TAG, "Artist search error: " + message);
                    });
                }
            });
        }).start();
    }
    private void searchAlbums(String query) {
        getActivity().runOnUiThread(() -> {
            progressSearch.setVisibility(View.VISIBLE);
            txtSearchResults.setText("Searching for \"" + query + "\"...");
        });

        new Thread(() -> {
            repository.searchAlbums(query, new DeezerRepository.DataCallback<List<Album>>() {
                @Override
                public void onSuccess(List<Album> data) {
                    if (getActivity() == null || !isAdded()) return;

                    final List<Track> albumTracks = new ArrayList<>();
                    if (data != null && !data.isEmpty()) {
                        for (Album album : data) {
                            Track albumTrack = new Track();
                            albumTrack.setId(album.getId());
                            albumTrack.setTitle(album.getTitle());
                            albumTrack.setType("album");

                            Album displayAlbum = new Album();
                            displayAlbum.setCoverMedium(album.getCoverMedium());
                            displayAlbum.setTitle(album.getTitle());
                            albumTrack.setAlbum(displayAlbum);

                            if (album.getArtist() != null) {
                                albumTrack.setArtist(album.getArtist());
                            }

                            albumTrack.setContext(requireContext());
                            albumTracks.add(albumTrack);
                        }
                    }

                    getActivity().runOnUiThread(() -> {
                        progressSearch.setVisibility(View.GONE);

                        if (!albumTracks.isEmpty()) {
                            currentSearchResults.clear();
                            currentSearchResults.addAll(albumTracks);

                            searchResultAdapter.updateResults(albumTracks);
                            txtSearchResults.setText("Found " + albumTracks.size() + " albums");
                        } else {
                            currentSearchResults.clear();
                            searchResultAdapter.updateResults(null);
                            txtSearchResults.setText("No albums found");
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    if (getActivity() == null || !isAdded()) return;

                    getActivity().runOnUiThread(() -> {
                        currentSearchResults.clear();
                        progressSearch.setVisibility(View.GONE);
                        txtSearchResults.setText("Error: " + message);
                        searchResultAdapter.updateResults(null);
                        Log.e(TAG, "Album search error: " + message);
                    });
                }
            });
        }).start();
    }

    @Override
    public void onItemClick(Track track) {
        if (track == null || getActivity() == null) return;

        // Add debounce protection
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTrackClickTime < TRACK_CLICK_DEBOUNCE_MS) {
            Log.d(TAG, "Click ignored - too soon after previous click");
            return;
        }
        lastTrackClickTime = currentTime;

        Log.d(TAG, "=== RECENTLY PLAYED TRACK CLICKED ===");
        Log.d(TAG, "Track: " + track.getTitle());
        Log.d(TAG, "Preview URL: " + (track.getPreviewUrl() != null ? track.getPreviewUrl() : "null"));

        if (track.getContext() == null) {
            track.setContext(requireContext());
        }

        if (track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) {
            Toast.makeText(requireContext(),
                    "Tidak bisa memutar lagu ini: URL tidak tersedia",
                    Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Cannot play track: Preview URL is null or empty");
            return;
        }

        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            Toast.makeText(requireContext(), "Memutar: " + track.getTitle(), Toast.LENGTH_SHORT).show();
            mainActivity.playTrack(track);

            Log.d(TAG, "✅ Playing selected track: " + track.getTitle());
            if (track.getArtist() != null) {
                Log.d(TAG, "Artist: " + track.getArtist().getName());
            }
        }
    }

    @Override
    public void onSearchItemClick(Track track) {
        if (track == null || getActivity() == null) return;

        // Add debounce protection
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTrackClickTime < TRACK_CLICK_DEBOUNCE_MS) {
            Log.d(TAG, "Search click ignored - too soon after previous click");
            return;
        }
        lastTrackClickTime = currentTime;

        Log.d(TAG, "=== SEARCH ITEM CLICKED ===");
        Log.d(TAG, "Selected track: " + track.getTitle());
        Log.d(TAG, "Current search results count: " + currentSearchResults.size());

        if (track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) {
            Toast.makeText(requireContext(),
                    "Cannot play this track: Preview not available",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (track.getContext() == null) {
            track.setContext(requireContext());
        }

        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();

            if (!currentSearchResults.isEmpty()) {
                Log.d(TAG, "Playing track with " + currentSearchResults.size() + " search results as playlist");
                mainActivity.playTrackFromSearch(track, new ArrayList<>(currentSearchResults));
            } else {
                Log.d(TAG, "No search results available, playing single track");
                mainActivity.playTrack(track);
            }

            String message = "Playing: " + track.getTitle();
            if (track.getArtist() != null) {
                message += " - " + track.getArtist().getName();
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();

            Log.d(TAG, "✅ Track playback initiated");
        }

        hideSearchPanel();
    }

    @Override
    public void onSearchItemPlayClick(Track track) {
        onSearchItemClick(track);
    }
    private void showSearchPanel() {
        if (searchPanel != null) {
            searchPanel.setVisibility(View.VISIBLE);
            searchPanelEditText.requestFocus();
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchPanelEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void hideSearchPanel() {
        if (searchPanel != null) {
            searchPanel.setVisibility(View.GONE);

            cancelOngoingSearch();

            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchPanelEditText.getWindowToken(), 0);
            }
            searchPanelEditText.setText("");

            // Clear current results to free memory
            currentSearchResults.clear();
            searchResultAdapter.updateResults(null);
        }
    }
    private void cancelOngoingSearch() {
        // Cancel search runnable
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
            isSearchCancelled = true;
            Log.d(TAG, "Cancelled ongoing search");
        }

        // Hide progress
        if (progressSearch != null) {
            progressSearch.setVisibility(View.GONE);
        }

        // Reset search state
        currentSearchQuery = "";
    }
    public List<Track> getAllFilteredTracks() {
        if (newMusicAdapter != null) {
            return newMusicAdapter.getAllFilteredTracks();
        }
        return new ArrayList<>();
    }
    private enum SearchType { SONGS, ARTISTS, ALBUMS }
    private SearchType currentSearchType = SearchType.SONGS;
    public HomeFragment() {
    }

    private void setupRecentlyPlayed() {
        if (getContext() == null || getView() == null) return;

        // Prevent multiple setup dalam waktu singkat
        if (recentlyPlayedSetupDone) {
            Log.d(TAG, "Recently played already setup, skipping");
            return;
        }

        Log.d(TAG, "=== SETTING UP RECENTLY PLAYED ===");

        RecyclerView recyclerRecentlyPlayed = getView().findViewById(R.id.recycler_recently_played);
        TextView txtNoRecentlyPlayed = getView().findViewById(R.id.txt_no_recently_played);

        if (recyclerRecentlyPlayed == null) {
            Log.e(TAG, "Recently played RecyclerView not found");
            return;
        }

        recyclerRecentlyPlayed.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(
                        getContext(), LinearLayoutManager.HORIZONTAL, false));

        // HANYA LOAD DARI LOCAL - JANGAN DARI CLOUD
        List<Track> recentTracks = UserPreferencesManager.getRecentTracks(requireContext());

        try {
            if (recentTracks == null) {
                recentTracks = new ArrayList<>();
            }

            for (Track track : recentTracks) {
                if (track != null) {
                    track.setContext(requireContext());
                }
            }

            List<Track> validTracks = new ArrayList<>();
            for (Track track : recentTracks) {
                if (track != null && track.getPreviewUrl() != null && !track.getPreviewUrl().isEmpty()) {
                    validTracks.add(track);
                }
            }

            Log.d(TAG, "Loaded " + recentTracks.size() + " recently played tracks FROM LOCAL ONLY");
            Log.d(TAG, "Valid tracks (with preview URL): " + validTracks.size());

            if (validTracks.isEmpty() && txtNoRecentlyPlayed != null) {
                recyclerRecentlyPlayed.setVisibility(View.GONE);
                txtNoRecentlyPlayed.setVisibility(View.VISIBLE);
                Log.d(TAG, "No recent tracks - showing empty message");
            } else {
                if (txtNoRecentlyPlayed != null) {
                    txtNoRecentlyPlayed.setVisibility(View.GONE);
                }
                recyclerRecentlyPlayed.setVisibility(View.VISIBLE);
                RecentlyPlayedAdapter adapter = new RecentlyPlayedAdapter(this);
                recyclerRecentlyPlayed.setAdapter(adapter);
                adapter.updateData(validTracks);
                Log.d(TAG, "✅ Recently played adapter updated with " + validTracks.size() + " tracks");
            }

            recentlyPlayedSetupDone = true;

        } catch (Exception e) {
            Log.e(TAG, "Error loading recently played tracks: " + e.getMessage(), e);
            if (txtNoRecentlyPlayed != null) {
                txtNoRecentlyPlayed.setVisibility(View.VISIBLE);
                txtNoRecentlyPlayed.setText("Error loading recently played tracks");
            }
        }
    }

    public void refreshRecentlyPlayed() {
        Log.d(TAG, "=== REFRESHING RECENTLY PLAYED ===");
        recentlyPlayedSetupDone = false; // Allow refresh
        setupRecentlyPlayed();
    }
    private void setupNewMusic() {
        if (getContext() == null || getView() == null) return;

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        recyclerNewMusic.setLayoutManager(layoutManager);

        recyclerNewMusic.setNestedScrollingEnabled(true);
        recyclerNewMusic.setHasFixedSize(false);

        recyclerNewMusic.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                if (!isLoadingMore && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0) {
                    loadMoreNewMusic();
                }
            }
        });
        newMusicAdapter = new NewMusicAdapter(this);
        recyclerNewMusic.setAdapter(newMusicAdapter);

        ProgressBar progressNewMusic = getView().findViewById(R.id.progress_circular);

        if (!newMusicCache.isEmpty()) {
            Log.d(TAG, "Using static cached data for Latest Music (items: " + newMusicCache.size() + ")");
            newMusicAdapter.updateData(newMusicCache);
            Log.d(TAG, "Displaying " + newMusicCache.size() + " latest music items");
            if (progressNewMusic != null) {
                progressNewMusic.setVisibility(View.GONE);
            }
            initialLoadDone = true;
            return;
        }

        if (initialLoadDone) {
            if (progressNewMusic != null) {
                progressNewMusic.setVisibility(View.GONE);
            }
            return;
        }
        if (progressNewMusic != null) {
            progressNewMusic.setVisibility(View.VISIBLE);
        }
        currentPage = 1;
        loadNewMusic(progressNewMusic);
    }
    private void loadNewMusic(ProgressBar progressBar) {
        // Check network before making API call
        if (!isNetworkAvailable()) {
            isLoadingMore = false;
            showNetworkError("No internet connection. Please check your network and tap refresh to try again.");
            return;
        }

        isLoadingMore = true;
        repository.getLatestTracks(new DeezerRepository.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> data) {
                if (!isAdded()) return;

                Log.d(TAG, "Retrieved " + (data != null ? data.size() : 0) + " latest tracks");

                // Hide error container on success
                hideErrorContainer();

                newMusicCache.clear();
                if (data != null) {
                    newMusicCache.addAll(data);
                }
                initialLoadDone = true;
                if (newMusicAdapter != null) {
                    newMusicAdapter.updateData(newMusicCache);
                    Log.d(TAG, "Updated adapter with " + newMusicCache.size() + " tracks");
                }
                currentPage++;
                isLoadingMore = false;

                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                Log.e(TAG, "Error fetching latest tracks: " + message);

                isLoadingMore = false;

                // Check if it's a network-related error
                if (message.toLowerCase().contains("network") ||
                        message.toLowerCase().contains("connection") ||
                        message.toLowerCase().contains("timeout") ||
                        !isNetworkAvailable()) {

                    showNetworkError("Failed to load music. Check your internet connection and tap refresh to try again.");
                } else {
                    showNetworkError("Error loading music: " + message + "\nTap refresh to try again.");
                }

                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
    }
    private void loadMoreNewMusic() {
        isLoadingMore = true;
        new Thread(() -> {
            repository.getLatestTracks(new DeezerRepository.DataCallback<List<Track>>() {
                @Override
                public void onSuccess(List<Track> data) {
                    if (!isAdded()) return;
                    final List<Track> uniqueNewTracks = new ArrayList<>();
                    if (data != null && !data.isEmpty()) {
                        final java.util.Set<Long> existingTrackIds = new java.util.HashSet<>();
                        for (Track track : newMusicCache) {
                            existingTrackIds.add(track.getId());
                        }
                        for (Track track : data) {
                            if (!existingTrackIds.contains(track.getId())) {
                                uniqueNewTracks.add(track);
                                existingTrackIds.add(track.getId());
                            }
                        }
                        Log.d(TAG, "Retrieved " + data.size() + " tracks, " + uniqueNewTracks.size() + " are unique");
                    }
                    getActivity().runOnUiThread(() -> {
                        if (!uniqueNewTracks.isEmpty()) {
                            newMusicCache.addAll(uniqueNewTracks);
                            if (newMusicAdapter != null) {
                                newMusicAdapter.appendData(uniqueNewTracks);
                                Log.d(TAG, "Added " + uniqueNewTracks.size() + " unique tracks. Total: " + newMusicCache.size());
                            }
                            currentPage++;
                        } else {
                            if (data != null && !data.isEmpty()) {
                                Toast.makeText(getContext(), "No new tracks to load", Toast.LENGTH_SHORT).show();
                                currentPage++;
                            } else {
                                Toast.makeText(getContext(), "No more music to load", Toast.LENGTH_SHORT).show();
                            }
                        }
                        isLoadingMore = false;
                    });
                }
                @Override
                public void onError(String message) {
                    if (!isAdded()) return;

                    getActivity().runOnUiThread(() -> {
                        Log.e(TAG, "Error fetching more latest tracks: " + message);
                        Toast.makeText(getContext(), "Error loading more music", Toast.LENGTH_SHORT).show();
                        isLoadingMore = false;
                    });
                }
            });
        }).start();
    }

    private void navigateToProfileActivity() {
        if (getActivity() != null) {
            android.content.Intent intent = new android.content.Intent(getActivity(), ProfileActivity.class);
            startActivity(intent);
        }
    }
    private void loadUserData() {
        TextView txtUsername = getView().findViewById(R.id.txt_username);
        TextView txtEmail = getView().findViewById(R.id.txt_email);
        ImageView profileImage = getView().findViewById(R.id.profile_image);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            String email = currentUser.getEmail();

            // Set username
            if (displayName != null && !displayName.isEmpty()) {
                txtUsername.setText(displayName);
            } else if (email != null) {
                String username = email.substring(0, email.indexOf('@'));
                txtUsername.setText(username);
            } else {
                txtUsername.setText("User");
            }

            // Set email
            if (email != null) {
                txtEmail.setText(email);
            } else {
                txtEmail.setText("");
            }

            // ✅ TAMBAHAN BARU: Load profile image menggunakan UserPreferencesManager
            if (profileImage != null) {
                loadProfileImage(profileImage);
            }

            Log.d(TAG, "User data loaded: " + (displayName != null ? displayName : email));
        } else {
            if (txtUsername != null) txtUsername.setText("Guest User");
            if (txtEmail != null) txtEmail.setText("Not logged in");
            if (profileImage != null) {
                profileImage.setImageResource(R.drawable.ic_launcher_background);
            }
            Log.d(TAG, "No user logged in, showing default data");
        }
    }
    private void loadProfileImage(ImageView profileImage) {
        Log.d(TAG, "=== Loading profile image ===");

        UserPreferencesManager.getProfileImageUrlAsync(requireContext(), new UserPreferencesManager.DataCallback<String>() {
            @Override
            public void onSuccess(String imageUrl) {
                if (!isAdded() || getActivity() == null) {
                    Log.w(TAG, "Fragment not added, skipping image load");
                    return;
                }

                Log.d(TAG, "Profile image URL received: " + (imageUrl != null ? "Yes" : "No"));

                if (imageUrl != null && !imageUrl.isEmpty()) {
                    if (imageUrl.startsWith("data:image")) {
                        Log.d(TAG, "Loading Base64 profile image");
                        loadBase64ProfileImage(imageUrl, profileImage);
                    } else {
                        Log.d(TAG, "Loading URL profile image");
                        Glide.with(requireContext())
                                .load(imageUrl)
                                .placeholder(R.drawable.ic_launcher_background)
                                .error(R.drawable.ic_launcher_background)
                                .circleCrop() // Make it circular
                                .into(profileImage);
                    }
                } else {
                    Log.d(TAG, "No profile image, using default");
                    profileImage.setImageResource(R.drawable.ic_launcher_background);
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || getActivity() == null) return;

                Log.e(TAG, "Error loading profile image: " + error);
                profileImage.setImageResource(R.drawable.ic_launcher_background);
            }
        });
    }
    private void loadBase64ProfileImage(String base64String, ImageView profileImage) {
        // Process Base64 in background thread to avoid UI blocking
        new Thread(() -> {
            try {
                String base64Image = base64String.substring(base64String.indexOf(",") + 1);
                byte[] decodedString = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT);
                android.graphics.Bitmap decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

                // Update UI on main thread
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (decodedByte != null) {
                            Log.d(TAG, "✅ Base64 image decoded successfully");

                            // Apply circular crop to the bitmap
                            android.graphics.Bitmap circularBitmap = createCircularBitmap(decodedByte);
                            profileImage.setImageBitmap(circularBitmap);
                        } else {
                            Log.e(TAG, "❌ Failed to decode Base64 image");
                            profileImage.setImageResource(R.drawable.ic_launcher_background);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing Base64 image", e);
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        profileImage.setImageResource(R.drawable.ic_launcher_background);
                    });
                }
            }
        }).start();
    }

    private android.graphics.Bitmap createCircularBitmap(android.graphics.Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = Math.min(width, height);

        android.graphics.Bitmap output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(output);

        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setAntiAlias(true);

        // Draw circle
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        // Apply source-in mode to clip bitmap to circle
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));

        // Draw bitmap
        android.graphics.Rect rect = new android.graphics.Rect(0, 0, size, size);
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }
    @Override
    public void onResume() {
        super.onResume();
        updateThemeIcon();
        loadUserData();

        // Hanya refresh recently played jika belum di-setup
        if (!recentlyPlayedSetupDone) {
            setupRecentlyPlayed();
        }
    }
    @Override
    public void onPause() {
        super.onPause();

        cancelOngoingSearch();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // ✅ IMPROVE: Better cleanup
        if (searchHandler != null) {
            searchHandler.removeCallbacksAndMessages(null);
            searchHandler = null;
        }

        // Clear all references
        currentSearchResults.clear();

        // Don't clear newMusicCache if you want to preserve it across fragment recreations
        // newMusicCache.clear();

        // Clear adapter data
        if (searchResultAdapter != null) {
            searchResultAdapter.clearResults();
        }

        // Reset states
        recentlyPlayedSetupDone = false;
        initialLoadDone = false;
        isSearchCancelled = true;
        currentSearchQuery = "";
    }
    private void safeUpdateSearchResults(List<Track> data) {
        try {
            if (searchResultAdapter != null) {
                searchResultAdapter.updateResults(data);
            } else {
                Log.w(TAG, "SearchResultAdapter is null, cannot update results");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating search results", e);
            // Recreate adapter if corrupted
            if (recyclerSearchResults != null && getContext() != null) {
                searchResultAdapter = new SearchResultAdapter(this);
                recyclerSearchResults.setAdapter(searchResultAdapter);
                searchResultAdapter.updateResults(data);
            }
        }
    }
}
