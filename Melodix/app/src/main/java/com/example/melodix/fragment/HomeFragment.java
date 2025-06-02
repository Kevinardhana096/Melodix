package com.example.melodix.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import com.bumptech.glide.Glide;
import com.example.melodix.DeezerRepository;
import com.example.melodix.MainActivity;
import com.example.melodix.NewMusicAdapter;
import com.example.melodix.ProfileActivity;
import com.example.melodix.R;
import com.example.melodix.RecentlyPlayedAdapter;
import com.example.melodix.SearchResultAdapter;
import com.example.melodix.ThemeManager;
import com.example.melodix.Track;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements RecentlyPlayedAdapter.OnItemClickListener,
        NewMusicAdapter.OnItemClickListener, SearchResultAdapter.OnSearchItemClickListener {

    private static final String TAG = "HomeFragment";
    private RecyclerView recyclerNewMusic;
    private NewMusicAdapter newMusicAdapter;
    private DeezerRepository repository;

    // Search panel elements
    private View searchPanel;
    private EditText searchPanelEditText;
    private ImageView searchCloseButton;
    private View cardSearch; // Reference to the search card in main layout
    private RecyclerView recyclerSearchResults;
    private SearchResultAdapter searchResultAdapter;
    private ProgressBar progressSearch;
    private TextView txtSearchResults;
    private com.google.android.material.chip.ChipGroup chipGroup;
    private com.google.android.material.chip.Chip chipSongs, chipArtists, chipAlbums;

    // For handling delayed search as user types
    private static final long SEARCH_DELAY_MS = 500;
    private Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize repository
        repository = DeezerRepository.getInstance();

        // Initialize views
        recyclerNewMusic = view.findViewById(R.id.recycler_new_music);

        // Initialize search panel elements
        searchPanel = view.findViewById(R.id.search_panel_include);
        searchPanelEditText = view.findViewById(R.id.edit_search_panel);
        searchCloseButton = view.findViewById(R.id.btn_search_close);  // Fix this line
        cardSearch = view.findViewById(R.id.card_search);
        recyclerSearchResults = view.findViewById(R.id.recycler_search_results);
        progressSearch = view.findViewById(R.id.progress_search);
        txtSearchResults = view.findViewById(R.id.txt_search_results);

        // Initialize chip group for search filtering
        chipGroup = view.findViewById(R.id.chip_group);
        chipSongs = view.findViewById(R.id.chip_songs);
        chipArtists = view.findViewById(R.id.chip_artists);
        chipAlbums = view.findViewById(R.id.chip_albums);

        // Connect the main search edit text to show search panel
        EditText mainSearchEditText = view.findViewById(R.id.edit_search);
        if (mainSearchEditText != null) {
            mainSearchEditText.setOnClickListener(v -> showSearchPanel());
            mainSearchEditText.setFocusable(false); // Make it just trigger the search panel
        }

        // Setup click listeners for search
        if (cardSearch != null) {
            cardSearch.setOnClickListener(v -> showSearchPanel());
        }

        if (searchCloseButton != null) {
            searchCloseButton.setOnClickListener(v -> hideSearchPanel());
        }

        // Add this line to set up all search listeners
        setupSearchListeners();

        // Initialize profile elements
        ImageView profileImage = view.findViewById(R.id.profile_image);
        ImageView btnThemeMode = view.findViewById(R.id.btn_theme_mode);

        // Set theme toggle click listener
        if (btnThemeMode != null) {
            btnThemeMode.setOnClickListener(v -> {
                ThemeManager.toggleTheme(requireActivity());
            });
        }

        // Hide search panel initially
        if (searchPanel != null) {
            searchPanel.setVisibility(View.GONE);
        }

        // Initialize search results adapter
        searchResultAdapter = new SearchResultAdapter(this);
        if (recyclerSearchResults != null) {
            recyclerSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerSearchResults.setAdapter(searchResultAdapter);
        }

        // Load content
        setupRecentlyPlayed();
        setupNewMusic();
    }

    private void setupSearchListeners() {
        // Set up search text change listener
        searchPanelEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Cancel any pending searches
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                final String query = s.toString().trim();

                // Clear results if query is empty
                if (query.isEmpty()) {
                    txtSearchResults.setText("Enter search terms");
                    searchResultAdapter.updateResults(null);
                    return;
                }

                // Show loading indicator
                progressSearch.setVisibility(View.VISIBLE);
                txtSearchResults.setText("Searching...");

                // Create new search with delay
                searchRunnable = () -> performSearch(query);
                searchHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Set up search type filter listeners
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

        // Setup keyboard search action
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
        // Show loading state
        progressSearch.setVisibility(View.VISIBLE);
        txtSearchResults.setText("Searching for \"" + query + "\"...");

        // Select the appropriate search method based on filter
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
    }

    private void searchSongs(String query) {
        repository.searchTracks(query, new DeezerRepository.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> data) {
                if (getActivity() == null || !isAdded()) return;

                getActivity().runOnUiThread(() -> {
                    progressSearch.setVisibility(View.GONE);

                    if (data != null && !data.isEmpty()) {
                        searchResultAdapter.updateResults(data);
                        txtSearchResults.setText("Found " + data.size() + " songs");
                    } else {
                        searchResultAdapter.updateResults(null);
                        txtSearchResults.setText("No songs found");
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (getActivity() == null || !isAdded()) return;

                getActivity().runOnUiThread(() -> {
                    progressSearch.setVisibility(View.GONE);
                    txtSearchResults.setText("Error: " + message);
                    searchResultAdapter.updateResults(null);
                    Log.e(TAG, "Search error: " + message);
                });
            }
        });
    }

    private void searchArtists(String query) {
        // Similar to searchSongs but using repository.searchArtists
        // Replace with the appropriate method call to your repository
        txtSearchResults.setText("Artist search not implemented yet");
        progressSearch.setVisibility(View.GONE);
    }

    private void searchAlbums(String query) {
        // Similar to searchSongs but using repository.searchAlbums
        // Replace with the appropriate method call to your repository
        txtSearchResults.setText("Album search not implemented yet");
        progressSearch.setVisibility(View.GONE);
    }
    @Override
    public void onItemClick(Track track) {
        if (track == null || getActivity() == null) return;

        // Play the clicked track
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.playTrack(track);
            Log.d(TAG, "Playing track: " + track.getTitle());
        }
    }

    @Override
    public void onSearchItemClick(Track track) {
        // Same behavior as regular item click
        onItemClick(track);

        // Hide the search panel after clicking a search result
        hideSearchPanel();
    }
    @Override
    public void onSearchItemPlayClick(Track track) {
        // Play the clicked track immediately
        onItemClick(track);
    }
    private void showSearchPanel() {
        if (searchPanel != null) {
            searchPanel.setVisibility(View.VISIBLE);
            searchPanelEditText.requestFocus();

            // Show keyboard
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchPanelEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void hideSearchPanel() {
        if (searchPanel != null) {
            searchPanel.setVisibility(View.GONE);

            // Hide keyboard
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchPanelEditText.getWindowToken(), 0);
            }

            // Clear search field
            searchPanelEditText.setText("");
        }
    }

    private void loadDefaultContent() {
        // Reload recently played and new music sections
        setupRecentlyPlayed();
        setupNewMusic();
    }

    public List<Track> getAllFilteredTracks() {
        // Return all tracks from new music section
        if (newMusicAdapter != null) {
            return newMusicAdapter.getAllFilteredTracks(); // Changed from getAllTracks to getAllFilteredTracks
        }
        return new ArrayList<>();
    }

    // Current search type
    private enum SearchType { SONGS, ARTISTS, ALBUMS }
    private SearchType currentSearchType = SearchType.SONGS;

    public HomeFragment() {
        // Required empty public constructor
    }

    private void setupRecentlyPlayed() {
        if (getContext() == null || getView() == null) return;

        // Find the HorizontalScrollView and its child LinearLayout
        HorizontalScrollView scrollRecentlyPlayed = getView().findViewById(R.id.scroll_recently_played);
        if (scrollRecentlyPlayed == null) {
            Log.e(TAG, "Recently played ScrollView not found");
            return;
        }

        // Find the LinearLayout inside the HorizontalScrollView
        LinearLayout containerRecentlyPlayed = (LinearLayout) scrollRecentlyPlayed.getChildAt(0);
        if (containerRecentlyPlayed == null) {
            Log.e(TAG, "Recently played container not found");
            return;
        }

        // Load recently played tracks from SharedPreferences
        SharedPreferences prefs = getContext().getSharedPreferences("MelodixPrefs", MODE_PRIVATE);
        Gson gson = new Gson();
        String json = prefs.getString("recentTracks", null);
        List<Track> recentTracks = new ArrayList<>();

        try {
            if (json != null) {
                Type type = new TypeToken<ArrayList<Track>>() {}.getType();
                List<Track> loadedTracks = gson.fromJson(json, type);

                if (loadedTracks != null) {
                    recentTracks.addAll(loadedTracks);
                    Log.d(TAG, "Loaded " + recentTracks.size() + " recently played tracks");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading recently played tracks: " + e.getMessage(), e);
        }

        // Clear existing views
        containerRecentlyPlayed.removeAllViews();

        // Show or hide "No Recent Tracks" message
        if (recentTracks.isEmpty()) {
            // Add a text view to show "No recently played tracks"
            TextView txtNoRecentlyPlayed = new TextView(getContext());
            txtNoRecentlyPlayed.setText("No recently played tracks");
            txtNoRecentlyPlayed.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            txtNoRecentlyPlayed.setPadding(16, 16, 16, 16);
            containerRecentlyPlayed.addView(txtNoRecentlyPlayed);
        } else {
            // Populate with recently played tracks
            LayoutInflater inflater = LayoutInflater.from(getContext());

            for (Track track : recentTracks) {
                // Inflate the card layout for a recently played track
                View trackView = inflater.inflate(R.layout.item_recently_played, containerRecentlyPlayed, false);

                // Find views
                ImageView imgAlbum = trackView.findViewById(R.id.img_album);
                TextView txtSongName = trackView.findViewById(R.id.txt_song_title);
                TextView txtArtistName = trackView.findViewById(R.id.txt_artist);

                // Set data
                txtSongName.setText(track.getTitle());
                if (track.getArtist() != null) {
                    txtArtistName.setText(track.getArtist().getName());
                }

                // Load album art
                if (track.getAlbum() != null && track.getAlbum().getCoverMedium() != null) {
                    Glide.with(this)
                            .load(track.getAlbum().getCoverMedium())
                            .placeholder(R.drawable.ic_launcher_background)
                            .into(imgAlbum);
                }

                // Set click listener
                trackView.setOnClickListener(v -> onItemClick(track));

                // Add to container
                containerRecentlyPlayed.addView(trackView);

                // Add layout parameters with margins
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) trackView.getLayoutParams();
                params.width = getResources().getDimensionPixelSize(R.dimen.recently_played_item_width);
                params.height = getResources().getDimensionPixelSize(R.dimen.recently_played_item_height);
                params.setMarginEnd(16);
                trackView.setLayoutParams(params);
            }
        }
    }
    /**
     * Refreshes the recently played tracks section
     * Called from MainActivity when returning to the HomeFragment
     */
    public void refreshRecentlyPlayed() {
        // Simply reuse the existing setup method to refresh the data
        setupRecentlyPlayed();
    }

    private void setupNewMusic() {
        if (getContext() == null || getView() == null) return;

        // Initialize RecyclerView for New Music tracks
        recyclerNewMusic.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        newMusicAdapter = new NewMusicAdapter(this); // Fixed constructor call
        recyclerNewMusic.setAdapter(newMusicAdapter);

        // Show loading indicator
        ProgressBar progressNewMusic = getView().findViewById(R.id.progress_circular); // Changed ID to match layout
        if (progressNewMusic != null) {
            progressNewMusic.setVisibility(View.VISIBLE);
        }

        // Fetch new releases or charts from Deezer API
        repository.getLatestReleases(new DeezerRepository.DataCallback<List<Track>>() { // Changed method name to match repository
            @Override
            public void onSuccess(List<Track> data) {
                if (getActivity() == null || !isAdded()) return;

                getActivity().runOnUiThread(() -> {
                    // Hide loading indicator
                    if (progressNewMusic != null) {
                        progressNewMusic.setVisibility(View.GONE);
                    }

                    if (data != null && !data.isEmpty()) {
                        // Store context in tracks for later use
                        for (Track track : data) {
                            track.setContext(getContext());
                        }

                        // Update adapter with new data
                        newMusicAdapter.updateData(data); // Changed from updateTracks to updateData
                        Log.d(TAG, "Loaded " + data.size() + " new music tracks");

                        // Create "No Music" message programmatically
                        TextView txtNoNewMusic = new TextView(getContext());
                        txtNoNewMusic.setText("No new music available");
                        txtNoNewMusic.setId(View.generateViewId());

                        txtNoNewMusic.setVisibility(View.GONE);
                        recyclerNewMusic.setVisibility(View.VISIBLE);
                    } else {
                        // Show "No New Music" message programmatically
                        TextView txtNoNewMusic = new TextView(getContext());
                        txtNoNewMusic.setText("No new music available");
                        ViewGroup container = getView().findViewById(R.id.container_new_music);
                        if (container != null) {
                            container.addView(txtNoNewMusic);
                        }
                        recyclerNewMusic.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (getActivity() == null || !isAdded()) return;

                getActivity().runOnUiThread(() -> {
                    // Hide loading indicator
                    if (progressNewMusic != null) {
                        progressNewMusic.setVisibility(View.GONE);
                    }

                    // Show error message programmatically
                    TextView txtNoNewMusic = new TextView(getContext());
                    txtNoNewMusic.setText("Error loading new music");
                    ViewGroup container = getView().findViewById(R.id.container_new_music);
                    if (container != null) {
                        container.addView(txtNoNewMusic);
                    }
                    recyclerNewMusic.setVisibility(View.GONE);

                    // Log error
                    Log.e(TAG, "Error loading new music: " + message);
                });
            }
        });
    }
}
