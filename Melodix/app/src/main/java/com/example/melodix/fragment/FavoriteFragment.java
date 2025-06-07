package com.example.melodix.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.melodix.api.DeezerRepository;
import com.example.melodix.adapter.FavoriteAdapter;
import com.example.melodix.listener.FavoriteChangeListener;
import com.example.melodix.activity.MainActivity;
import com.example.melodix.R;
import com.example.melodix.model.Track;
import com.example.melodix.listener.UserPreferencesManager;

import java.util.ArrayList;
import java.util.List;

public class FavoriteFragment extends Fragment implements FavoriteAdapter.OnFavoriteClickListener,
        FavoriteChangeListener {
    private static final String TAG = "FavoriteFragment";
    private RecyclerView favoritesRecyclerView;
    private FavoriteAdapter adapter;
    private ConstraintLayout emptyStateContainer;
    private TextView favoriteCountText;
    private List<Track> currentFavorites = new ArrayList<>();
    public FavoriteFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorite, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        favoritesRecyclerView = view.findViewById(R.id.favoritesRecyclerView);
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer);
        favoriteCountText = view.findViewById(R.id.favoriteCountText);

        favoritesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FavoriteAdapter(requireContext(), this);
        favoritesRecyclerView.setAdapter(adapter);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).addFavoriteChangeListener(this);
        }
        loadFavorites();
    }

    @Override
    public void onDestroyView() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).removeFavoriteChangeListener(this);
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void loadFavorites() {
        Log.d(TAG, "=== LOADING FAVORITES ===");
        Log.d(TAG, "User: mobilesaperhenibus");
        Log.d(TAG, "Timestamp: 2025-06-08 17:09:10 UTC");

        UserPreferencesManager.getFavoriteTracksAsync(requireContext(), new UserPreferencesManager.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> favorites) {
                if (!isAdded()) return;

                Log.d(TAG, "✅ Successfully loaded " + favorites.size() + " favorites from cloud");

                int validTracks = 0;
                List<Track> tracksToRefresh = new ArrayList<>();

                for (Track track : favorites) {
                    if (isTrackPlayable(track)) {
                        validTracks++;
                    } else {
                        Log.w(TAG, "⚠️ Track not playable: " + track.getTitle() +
                                " (Preview URL: " + track.getPreviewUrl() + ")");
                        tracksToRefresh.add(track);
                    }
                }
                Log.d(TAG, "📊 Playable tracks: " + validTracks + "/" + favorites.size());
                currentFavorites = new ArrayList<>(favorites);

                requireActivity().runOnUiThread(() -> {
                    updateFavoritesUI(favorites);
                });

                if (!tracksToRefresh.isEmpty()) {
                    Log.d(TAG, "🔄 Refreshing " + tracksToRefresh.size() + " tracks with expired URLs");
                    refreshTrackUrls(tracksToRefresh);
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;

                Log.e(TAG, "❌ Error loading favorites from cloud: " + error);

                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Loading offline favorites...", Toast.LENGTH_SHORT).show();

                    List<Track> localFavorites = UserPreferencesManager.getFavoriteTracks(requireContext());
                    Log.d(TAG, "📱 Loaded " + localFavorites.size() + " favorites from local storage");

                    currentFavorites = new ArrayList<>(localFavorites);
                    updateFavoritesUI(localFavorites);
                });
            }
        });
    }

    private void refreshTrackUrls(List<Track> tracksToRefresh) {
        if (tracksToRefresh == null || tracksToRefresh.isEmpty()) {
            return;
        }

        DeezerRepository repository = DeezerRepository.getInstance();
        final int[] refreshedCount = {0};
        final int totalToRefresh = tracksToRefresh.size();

        Log.d(TAG, "🔄 Starting URL refresh for " + totalToRefresh + " tracks");

        for (Track track : tracksToRefresh) {
            String searchQuery = track.getTitle();
            if (track.getArtist() != null) {
                searchQuery += " " + track.getArtist().getName();
            }

            final Track trackToUpdate = track;

            repository.searchTracks(searchQuery, new DeezerRepository.DataCallback<List<Track>>() {
                @Override
                public void onSuccess(List<Track> searchResults) {
                    Track freshTrack = findMatchingTrack(searchResults, trackToUpdate);

                    if (freshTrack != null && freshTrack.getPreviewUrl() != null && !freshTrack.getPreviewUrl().isEmpty()) {
                        trackToUpdate.setPreviewUrl(freshTrack.getPreviewUrl());
                        refreshedCount[0]++;

                        Log.d(TAG, "✅ Refreshed URL for: " + trackToUpdate.getTitle() +
                               " (" + refreshedCount[0] + "/" + totalToRefresh + ")");

                        if (refreshedCount[0] == totalToRefresh) {
                            saveRefreshedFavorites();
                        }
                    } else {
                        Log.w(TAG, "❌ Could not find fresh URL for: " + trackToUpdate.getTitle());

                        if (refreshedCount[0] + 1 == totalToRefresh) {
                            saveRefreshedFavorites();
                        }
                    }
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG, "❌ Error refreshing track " + trackToUpdate.getTitle() + ": " + message);

                    if (refreshedCount[0] + 1 == totalToRefresh) {
                        saveRefreshedFavorites();
                    }
                }
            });
        }
    }

    private void saveRefreshedFavorites() {
        Log.d(TAG, "💾 Saving favorites with refreshed URLs");

        UserPreferencesManager.saveFavoriteTracksAsync(requireContext(), currentFavorites,
            new UserPreferencesManager.DataCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean success) {
                    if (!isAdded()) return;

                    Log.d(TAG, "✅ Successfully saved favorites with refreshed URLs");
                    requireActivity().runOnUiThread(() -> {
                        updateFavoritesUI(currentFavorites);
                        Toast.makeText(getContext(), "Track data refreshed", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "❌ Failed to save refreshed favorites: " + error);
                }
            }
        );
    }

    private Track findMatchingTrack(List<Track> tracks, Track originalTrack) {
        if (tracks == null || tracks.isEmpty()) return null;

        for (Track track : tracks) {
            if (track.getId() == originalTrack.getId()) {
                Log.d(TAG, "✅ Found exact ID match for: " + originalTrack.getTitle());
                return track;
            }
        }

        for (Track track : tracks) {
            if (isTrackExactMatch(track, originalTrack)) {
                Log.d(TAG, "✅ Found exact match for: " + originalTrack.getTitle());
                return track;
            }
        }

        for (Track track : tracks) {
            if (track.getTitle().equalsIgnoreCase(originalTrack.getTitle()) &&
                    Math.abs(track.getDuration() - originalTrack.getDuration()) <= 5) { // Allow 5 sec difference
                Log.d(TAG, "⚠️ Using title match for: " + originalTrack.getTitle());
                return track;
            }
        }

        Log.w(TAG, "❌ No suitable match found for: " + originalTrack.getTitle());
        return null;
    }
    private boolean isTrackExactMatch(Track track1, Track track2) {
        if (track1 == null || track2 == null) return false;

        if (!track1.getTitle().equalsIgnoreCase(track2.getTitle())) return false;


        if (track1.getArtist() != null && track2.getArtist() != null) {
            if (!track1.getArtist().getName().equalsIgnoreCase(track2.getArtist().getName())) {
                return false;
            }
        }

        int durationDiff = Math.abs(track1.getDuration() - track2.getDuration());
        if (durationDiff > 10) {
            return false;
        }
        return true;
    }

    private boolean isTrackPlayable(Track track) {
        if (track == null) return false;
        if (track.getTitle() == null || track.getTitle().isEmpty()) return false;
        if (track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) return false;
        return track.getId() > 0;
    }

    private void updateFavoritesUI(List<Track> favorites) {

        if (favorites.isEmpty()) {
            emptyStateContainer.setVisibility(View.VISIBLE);
            favoritesRecyclerView.setVisibility(View.GONE);
            favoriteCountText.setText("0 tracks");
            Log.d(TAG, "📭 Showing empty state - no favorites");
        } else {
            emptyStateContainer.setVisibility(View.GONE);
            favoritesRecyclerView.setVisibility(View.VISIBLE);

            int playableCount = 0;
            for (Track track : favorites) {
                if (isTrackPlayable(track)) {
                    playableCount++;
                }
            }

            favoriteCountText.setText(favorites.size() + " tracks (" + playableCount + " playable)");
            adapter.updateData(favorites);

            Log.d(TAG, "🎵 Updated UI with " + favorites.size() + " favorites (" + playableCount + " playable)");
        }
    }
    private void removeFromFavorites(Track track) {
        currentFavorites.removeIf(favorite -> favorite.getId() == track.getId());
        saveFavorites(currentFavorites, new UserPreferencesManager.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean success) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Removed from favorites", Toast.LENGTH_SHORT).show();
                        updateFavoritesUI(currentFavorites);
                    });
                }
            }
            @Override
            public void onError(String error) {
                if (!currentFavorites.contains(track)) {
                    currentFavorites.add(track);
                }

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Error removing from favorites", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error removing from favorites: " + error);
                        updateFavoritesUI(currentFavorites);
                    });
                }
            }
        });

        updateFavoritesUI(currentFavorites);
    }
    private void saveFavorites(List<Track> favorites, UserPreferencesManager.DataCallback<Boolean> callback) {
        UserPreferencesManager.saveFavoriteTracksAsync(requireContext(), favorites, new UserPreferencesManager.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean success) {
                Log.d(TAG, "Favorites saved successfully to Firestore");
                if (callback != null) callback.onSuccess(success);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error saving favorites to Firestore: " + error);
                if (callback != null) callback.onError(error);
            }
        });
    }

    @Override
    public void onPlayClick(Track track) {
        Log.d(TAG, "=== PLAY CLICK FROM FAVORITES ===");
        Log.d(TAG, "Track: " + (track != null ? track.getTitle() : "null"));
        Log.d(TAG, "Track ID: " + (track != null ? track.getId() : "null"));

        if (!isTrackPlayable(track)) {
            Log.w(TAG, "⚠️ Track not immediately playable, attempting refresh");
            refreshTrackAndPlay(track);
            return;
        }

        if (isDeezerUrlExpired(track.getPreviewUrl())) {
            Log.w(TAG, "⚠️ URL expired, refreshing");
            refreshTrackAndPlay(track);
            return;
        }

        Toast.makeText(getContext(), "Playing: " + track.getTitle(), Toast.LENGTH_SHORT).show();
        playTrackSafely(track);
    }

    private boolean isDeezerUrlExpired(String url) {
        if (url == null || !url.contains("dzcdn.net")) {
            return false;
        }

        try {
            if (url.contains("exp=")) {
                String[] parts = url.split("exp=");
                if (parts.length > 1) {
                    String expPart = parts[1].split("~")[0];
                    long expTime = Long.parseLong(expPart);
                    long currentTime = System.currentTimeMillis() / 1000;
                    long bufferTime = 3600;
                    boolean isExpired = (currentTime + bufferTime) > expTime;

                    Log.d(TAG, "URL expiration check - Current: " + currentTime +
                            ", Expires: " + expTime + ", Expired: " + isExpired);

                    return isExpired;
                }
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error checking URL expiration: " + e.getMessage());
            return true;
        }
    }

    private void refreshTrackAndPlay(Track outdatedTrack) {
        if (outdatedTrack == null || getContext() == null) return;

        Log.d(TAG, "🔄 Refreshing track: " + outdatedTrack.getTitle() + " (ID: " + outdatedTrack.getId() + ")");

        String searchQuery = createSearchQuery(outdatedTrack);

        DeezerRepository repository = DeezerRepository.getInstance();
        repository.searchTracks(searchQuery, new DeezerRepository.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> searchResults) {
                if (!isAdded()) return;

                Track refreshedTrack = findMatchingTrack(searchResults, outdatedTrack);

                if (refreshedTrack != null && isTrackPlayable(refreshedTrack)) {
                    Log.d(TAG, "✅ Successfully refreshed: " + outdatedTrack.getTitle());
                    Log.d(TAG, "   Old ID: " + outdatedTrack.getId() + " -> New ID: " + refreshedTrack.getId());
                    Log.d(TAG, "   Old URL: " + outdatedTrack.getPreviewUrl());
                    Log.d(TAG, "   New URL: " + refreshedTrack.getPreviewUrl());

                    Track playableTrack = createPlayableTrackFromFavorite(outdatedTrack, refreshedTrack);

                    updateTrackInFavorites(playableTrack);

                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Playing refreshed track", Toast.LENGTH_SHORT).show();
                        playTrackSafely(playableTrack);
                    });

                } else {
                    Log.e(TAG, "❌ Could not refresh track or find playable version");
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(),
                                "This track is no longer available",
                                Toast.LENGTH_LONG).show();
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "❌ Error refreshing track: " + message);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(),
                                "Unable to refresh track data",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    private String createSearchQuery(Track track) {
        StringBuilder query = new StringBuilder();

        if (track.getTitle() != null) {
            query.append(track.getTitle());
        }

        if (track.getArtist() != null && track.getArtist().getName() != null) {
            query.append(" ").append(track.getArtist().getName());
        }

        if (track.getAlbum() != null && track.getAlbum().getTitle() != null) {
            query.append(" ").append(track.getAlbum().getTitle());
        }
        return query.toString().trim();
    }
    private Track createPlayableTrackFromFavorite(Track favoriteTrack, Track freshTrack) {
        Track playableTrack = freshTrack;
        if (favoriteTrack.getContext() != null) {
            playableTrack.setContext(favoriteTrack.getContext());
        }
        return playableTrack;
    }
    private void updateTrackInFavorites(Track updatedTrack) {
        UserPreferencesManager.getFavoriteTracksAsync(requireContext(), new UserPreferencesManager.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> favorites) {
                List<Track> updatedFavorites = new ArrayList<>(favorites);
                boolean trackFound = false;
                for (int i = 0; i < updatedFavorites.size(); i++) {
                    Track favorite = updatedFavorites.get(i);
                    if (favorite.getId() == updatedTrack.getId()) {
                        updatedFavorites.set(i, updatedTrack);
                        trackFound = true;
                        break;
                    }
                }

                if (trackFound) {
                    UserPreferencesManager.saveFavoriteTracksAsync(requireContext(), updatedFavorites,
                            new UserPreferencesManager.DataCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean success) {
                            Log.d(TAG, "✅ Updated favorite track with fresh URL");
                            currentFavorites = new ArrayList<>(updatedFavorites);
                        }
                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "❌ Error updating favorite track: " + error);
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Error getting favorites to update track: " + error);
            }
        });
    }
    private void playTrackSafely(Track track) {
        if (track == null || !(getActivity() instanceof MainActivity)) {
            Log.e(TAG, "❌ Cannot play track - invalid state");
            return;
        }
        if (!isTrackPlayable(track)) {
            Log.e(TAG, "❌ Track is not playable after refresh");
            Toast.makeText(getContext(), "Track cannot be played", Toast.LENGTH_SHORT).show();
            return;
        }
        if (track.getContext() == null) {
            track.setContext(getContext());
        }

        MainActivity mainActivity = (MainActivity) getActivity();
        mainActivity.playTrack(track);

        Log.d(TAG, "✅ Track playback initiated safely");
    }

    @Override
    public void onRemoveFromFavoritesClick(Track track, int position) {
        adapter.removeItem(position);
        removeFromFavorites(track);
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
                for (int i = 0; i < currentFavorites.size(); i++) {
                    if (currentFavorites.get(i).getId() == track.getId()) {
                        position = i;
                        break;
                    }
                }
                if (position != -1) {
                    onRemoveFromFavoritesClick(track, position);
                }
                return true;
            } else if (itemId == R.id.action_share) {
                shareTrack(track);
                return true;
            }
            return false;
        });

        popup.show();
    }
    private void shareTrack(Track track) {
        String shareText = "Check out this song: " + track.getTitle();

        if (track.getArtist() != null) {
            shareText += " by " + track.getArtist().getName();
        }

        shareText += "\n\nShared via Melodix";

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

