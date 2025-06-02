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
    public void onItemClick(Track track) {
        
    }

    // Current search type
    private enum SearchType { SONGS, ARTISTS, ALBUMS }
    private SearchType currentSearchType = SearchType.SONGS;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
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
        TextView txtTitle = view.findViewById(R.id.txt_title);
        TextView txtUsername = view.findViewById(R.id.txt_username);
        TextView txtEmail = view.findViewById(R.id.txt_email);

        // Fix: Use profile_image instead of img_profile
        ImageView profileImage = view.findViewById(R.id.profile_image);

        // Initialize search panel elements
        setupSearchPanel(view);

        // Enhance the search functionality for the regular search box
        EditText editSearch = view.findViewById(R.id.edit_search);
        editSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        // Make the main search box clickable to show the search panel instead of doing a direct search
        editSearch.setFocusable(false);
        editSearch.setClickable(true);
        editSearch.setOnClickListener(v -> {
            showSearchPanel();
        });

        // Add click listener for the search card to show search panel
        cardSearch = view.findViewById(R.id.card_search);
        cardSearch.setOnClickListener(v -> {
            showSearchPanel();
        });

        // Add clear button for search field
        ImageView imgSearch = view.findViewById(R.id.img_search);
        imgSearch.setOnClickListener(v -> {
            editSearch.setText("");
            loadDefaultContent();
        });

        // Set user information
        txtUsername.setText(R.string.username_example);
        txtEmail.setText(R.string.email_example);

        // Set click listeners for profile navigation
        View.OnClickListener profileClickListener = v -> {
            Intent intent = new Intent(getActivity(), ProfileActivity.class);
            startActivity(intent);
        };

        // Apply the click listener to both the profile image and username
        // Fix: Use profileImage variable instead of imgProfile
        profileImage.setOnClickListener(profileClickListener);
        txtUsername.setOnClickListener(profileClickListener);

        // Setup Recently Played section
        setupRecentlyPlayed();

        // Setup RecyclerView untuk New Music
        setupNewMusic();

        // Load data from Deezer API and check API status
        checkApiStatus();

        // Set up theme toggle button
        ImageView imgThemeMode = view.findViewById(R.id.btn_theme_mode);
        updateThemeIcon(imgThemeMode);

        imgThemeMode.setOnClickListener(v -> {
            ThemeManager.toggleTheme(requireContext());
            updateThemeIcon(imgThemeMode);

            // Recreate the activity to apply theme changes
            requireActivity().recreate();
        });
    }

    private void setupSearchPanel(View view) {
        // Find search panel views from the included layout
        View panelInclude = view.findViewById(R.id.search_panel_include);
        searchPanel = panelInclude.findViewById(R.id.search_panel);
        searchPanelEditText = panelInclude.findViewById(R.id.edit_search_panel);
        searchCloseButton = panelInclude.findViewById(R.id.btn_search_close);
        recyclerSearchResults = panelInclude.findViewById(R.id.recycler_search_results);
        progressSearch = panelInclude.findViewById(R.id.progress_search);
        txtSearchResults = panelInclude.findViewById(R.id.txt_search_results);
        chipGroup = panelInclude.findViewById(R.id.chip_group);
        chipSongs = panelInclude.findViewById(R.id.chip_songs);
        chipArtists = panelInclude.findViewById(R.id.chip_artists);
        chipAlbums = panelInclude.findViewById(R.id.chip_albums);

        // Set up RecyclerView for search results
        recyclerSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
        searchResultAdapter = new SearchResultAdapter(this);
        recyclerSearchResults.setAdapter(searchResultAdapter);

        // Set up search text listener for the search panel
        searchPanelEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchPanelEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                final String query = s.toString().trim();
                searchRunnable = () -> {
                    if (query.isEmpty()) {
                        searchResultAdapter.updateResults(new ArrayList<>());
                        txtSearchResults.setText(R.string.enter_search_terms);
                        return;
                    }
                    performPanelSearch(query);
                };
                searchHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Set up close button listener
        searchCloseButton.setOnClickListener(v -> {
            hideSearchPanel();
        });

        // Set up chip group listener
        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_songs) {
                currentSearchType = SearchType.SONGS;
            } else if (checkedId == R.id.chip_artists) {
                currentSearchType = SearchType.ARTISTS;
            } else if (checkedId == R.id.chip_albums) {
                currentSearchType = SearchType.ALBUMS;
            }

            String query = searchPanelEditText.getText().toString().trim();
            if (!query.isEmpty()) {
                performPanelSearch(query);
            }
        });
    }

    private void performPanelSearch(String query) {
        progressSearch.setVisibility(View.VISIBLE);

        // Gunakan metode searchTracks yang tersedia di DeezerRepository
        // Karena tidak ada metode search dengan parameter SearchType
        repository.searchTracks(query, new DeezerRepository.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> data) {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        progressSearch.setVisibility(View.GONE);
                        updateSearchResults(data);
                    });
                }
            }

            @Override
            public void onError(String message) {
                handleSearchError(message);
            }
        });
    }

    private void updateSearchResults(List<Track> results) {
        if (results.isEmpty()) {
            txtSearchResults.setText(R.string.no_results);
        } else {
            txtSearchResults.setText(getString(R.string.results_found, results.size()));
        }
        searchResultAdapter.updateResults(results);
    }

    private void handleSearchError(String message) {
        if (getActivity() != null && isAdded()) {
            getActivity().runOnUiThread(() -> {
                progressSearch.setVisibility(View.GONE);
                Toast.makeText(getContext(), getString(R.string.search_error) + ": " + message, Toast.LENGTH_SHORT).show();
            });
        }
    }

    // Implement SearchResultAdapter.OnSearchItemClickListener methods
    @Override
    public void onSearchItemClick(Track track) {
        // Play the selected track
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).playTrack(track);
        }
        hideSearchPanel();
    }

    @Override
    public void onSearchItemPlayClick(Track track) {
        // Play the selected track immediately
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).playTrack(track);
        }
    }
}
