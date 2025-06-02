package com.example.melodix.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.melodix.FavoriteAdapter;
import com.example.melodix.FavoriteChangeListener;
import com.example.melodix.MainActivity;
import com.example.melodix.MusicPlayer;
import com.example.melodix.R;
import com.example.melodix.Track;
import com.example.melodix.TrackChangeListener;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FavoriteFragment extends Fragment implements FavoriteAdapter.OnFavoriteClickListener,
        FavoriteChangeListener {

    private RecyclerView favoritesRecyclerView;
    private FavoriteAdapter adapter;
    private ConstraintLayout emptyStateContainer;
    private TextView favoriteCountText;

    private static final String PREFS_NAME = "MelodixPrefs";
    private static final String FAVORITES_KEY = "favorites";

    public FavoriteFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorite, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        favoritesRecyclerView = view.findViewById(R.id.favoritesRecyclerView);
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer);
        favoriteCountText = view.findViewById(R.id.favoriteCountText);

        // Setup RecyclerView
        favoritesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FavoriteAdapter(requireContext(), this);
        favoritesRecyclerView.setAdapter(adapter);

        // Register as a favorite change listener
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).addFavoriteChangeListener(this);
        }

        // Load favorite tracks
        loadFavorites();
    }

    @Override
    public void onDestroyView() {
        // Unregister from favorite change events
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).removeFavoriteChangeListener(this);
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh favorites data when returning to this fragment
        loadFavorites();
    }

    // Load favorites from SharedPreferences
    private void loadFavorites() {
        List<Track> favorites = getFavoriteTracks();

        // Update UI based on whether there are favorite tracks
        if (favorites.isEmpty()) {
            emptyStateContainer.setVisibility(View.VISIBLE);
            favoritesRecyclerView.setVisibility(View.GONE);
            favoriteCountText.setText("0 tracks");
        } else {
            emptyStateContainer.setVisibility(View.GONE);
            favoritesRecyclerView.setVisibility(View.VISIBLE);
            favoriteCountText.setText(favorites.size() + " tracks");
            adapter.updateData(favorites);
        }
    }

    // Get favorite tracks from SharedPreferences
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

    // Add a track to favorites
    public void addToFavorites(Track track) {
        List<Track> favorites = getFavoriteTracks();

        // Check if the track is already in favorites
        boolean exists = false;
        for (Track favorite : favorites) {
            if (favorite.getId() == track.getId()) {
                exists = true;
                break;
            }
        }

        // If not already in favorites, add it
        if (!exists) {
            favorites.add(track);
            saveFavorites(favorites);
            loadFavorites(); // Refresh UI
            Toast.makeText(getContext(), "Added to favorites", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Already in favorites", Toast.LENGTH_SHORT).show();
        }
    }

    // Remove a track from favorites
    private void removeFromFavorites(Track track) {
        List<Track> favorites = getFavoriteTracks();

        // Find and remove the track
        favorites.removeIf(favorite -> favorite.getId() == track.getId());

        saveFavorites(favorites);
        loadFavorites(); // Refresh UI
    }

    // Save the updated favorites list to SharedPreferences
    private void saveFavorites(List<Track> favorites) {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        Gson gson = new Gson();
        String json = gson.toJson(favorites);
        editor.putString(FAVORITES_KEY, json);
        editor.apply();
    }

    // Adapter callbacks
    @Override
    public void onPlayClick(Track track) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).playTrack(track);
        }
    }

    @Override
    public void onRemoveFromFavoritesClick(Track track, int position) {
        adapter.removeItem(position);
        removeFromFavorites(track);

        // Update counter
        List<Track> favorites = getFavoriteTracks();
        favoriteCountText.setText(favorites.size() + " tracks");

        // Show empty state if no more favorites
        if (favorites.isEmpty()) {
            emptyStateContainer.setVisibility(View.VISIBLE);
            favoritesRecyclerView.setVisibility(View.GONE);
        }

        Toast.makeText(getContext(), "Removed from favorites", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onOptionsClick(Track track, View view) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(R.menu.favorite_track_options, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.action_play) {
                onPlayClick(track);
                return true;
            } else if (itemId == R.id.action_remove_favorite) {
                int position = -1;
                List<Track> favorites = getFavoriteTracks();
                for (int i = 0; i < favorites.size(); i++) {
                    if (favorites.get(i).getId() == track.getId()) {
                        position = i;
                        break;
                    }
                }
                if (position != -1) {
                    onRemoveFromFavoritesClick(track, position);
                }
                return true;
            } else if (itemId == R.id.action_share) {
                // Implement share functionality
                shareTrack(track);
                return true;
            }
            return false;
        });

        popup.show();
    }

    // Share track information
    // In FavoriteFragment.java, modify the shareTrack method
    private void shareTrack(Track track) {
        String shareText = "Check out this song: " + track.getTitle();

        if (track.getArtist() != null) {
            shareText += " by " + track.getArtist().getName();
        }

        // Remove or replace the link reference since Track doesn't have getLink()
        // Instead, we'll use a default sharing message without a link
        shareText += "\n\nShared via Melodix";

        // Create and start the share intent
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
        startActivity(android.content.Intent.createChooser(shareIntent, "Share via"));
    }

    @Override
    public void onFavoriteChanged() {
        if (isAdded()) {
            requireActivity().runOnUiThread(this::loadFavorites);
        }
    }
}
