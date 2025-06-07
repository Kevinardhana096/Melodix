package com.example.melodix.fragment;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.melodix.database.DownloadedAlbum;
import com.example.melodix.database.DownloadedArtist;
import com.example.melodix.database.DownloadedMusicContract;
import com.example.melodix.database.DownloadedMusicDbHelper;
import com.example.melodix.database.DownloadedTrack;
import com.example.melodix.activity.MainActivity;
import com.example.melodix.R;
import com.example.melodix.listener.MusicDownloader;
import com.example.melodix.model.Track;
import com.example.melodix.listener.UserPreferencesManager;
import com.example.melodix.api.DeezerRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadedMusicFragment extends Fragment {
    private static final String TAG = "DownloadedMusicFragment";
    private RecyclerView recyclerView;
    private ConstraintLayout emptyStateContainer;
    private ProgressBar loadingProgress;
    private SearchView searchView;
    private TextView downloadedCount;
    private MaterialButton sortButton;
    private List<Track> downloadedTracks = new ArrayList<>();
    private DownloadedMusicAdapter adapter;
    private boolean isFragmentVisible = false;
    private boolean needsRefresh = false;

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        isFragmentVisible = isVisibleToUser;

        if (isFragmentVisible && needsRefresh) {
            Log.d(TAG, "Fragment became visible with pending refresh - refreshing now");
            loadDownloadedTracks();
            needsRefresh = false;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_downloaded_music, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupRecyclerView();
        setupSearchView();
        setupSortButton();

        loadDownloadedTracks();
    }

    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.downloads_recycler_view);
        emptyStateContainer = view.findViewById(R.id.empty_state_container);
        loadingProgress = view.findViewById(R.id.loading_progress);
        searchView = view.findViewById(R.id.search_view);
        downloadedCount = view.findViewById(R.id.downloaded_count);
        sortButton = view.findViewById(R.id.sort_button);

        MaterialButton exploreButton = view.findViewById(R.id.explore_button);
        exploreButton.setOnClickListener(v -> navigateToHome());
    }

    private void setupRecyclerView() {
        adapter = new DownloadedMusicAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(
                requireContext(), DividerItemDecoration.VERTICAL));
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterDownloads(query);
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                filterDownloads(newText);
                return true;
            }
        });
    }
    private void setupSortButton() {
        sortButton.setOnClickListener(v -> {
            showSortOptionsMenu();
        });
    }

    private void showSortOptionsMenu() {
        Toast.makeText(getContext(), "Sort options not implemented yet", Toast.LENGTH_SHORT).show();
    }

    public void loadDownloadedTracks() {
        Log.d(TAG, "=== LOADING DOWNLOADED TRACKS ===");

        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }
        loadLocalTracksFirst();
    }
    private void loadLocalTracksFirst() {
        Log.d(TAG, "Loading local tracks first...");

        try {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            String userId = currentUser != null ? currentUser.getUid() : "anonymous";

            DownloadedMusicDbHelper dbHelper = new DownloadedMusicDbHelper(requireContext());
            List<Track> localTracks = getDownloadedTracksFromDb(dbHelper, userId);

            Log.d(TAG, "Found " + localTracks.size() + " local tracks");
            requireActivity().runOnUiThread(() -> {
                if (localTracks.size() > 0) {
                    downloadedTracks = localTracks;
                    if (adapter != null) {
                        adapter.setTracks(downloadedTracks);
                        updateDownloadCount(downloadedTracks.size());
                        showEmptyState(false);
                    }
                    Log.d(TAG, "UI updated with local tracks: " + localTracks.size());
                }
                loadCloudTracksSecond();
            });

        } catch (Exception e) {
            Log.e(TAG, "Error loading local tracks: " + e.getMessage());
            loadCloudTracksSecond();
        }
    }
    private void loadCloudTracksSecond() {
        Log.d(TAG, "Loading cloud tracks for sync...");

        UserPreferencesManager.getDownloadHistoryAsync(requireContext(), new UserPreferencesManager.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> cloudHistory) {
                Log.d(TAG, "Successfully loaded " + cloudHistory.size() + " tracks from cloud");

                requireActivity().runOnUiThread(() -> {
                    if (loadingProgress != null) {
                        loadingProgress.setVisibility(View.GONE);
                    }

                    List<Track> mergedTracks = mergeLocalAndCloudTracks(downloadedTracks, cloudHistory);
                    downloadedTracks = mergedTracks;

                    if (adapter != null) {
                        adapter.setTracks(downloadedTracks);
                        updateDownloadCount(downloadedTracks.size());
                        showEmptyState(downloadedTracks.isEmpty());
                        Log.d(TAG, "UI updated with merged tracks: " + downloadedTracks.size());
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading cloud download history: " + error);

                requireActivity().runOnUiThread(() -> {
                    if (loadingProgress != null) {
                        loadingProgress.setVisibility(View.GONE);
                    }
                    if (downloadedTracks.isEmpty()) {
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        String userId = currentUser != null ? currentUser.getUid() : "anonymous";

                        DownloadedMusicDbHelper dbHelper = new DownloadedMusicDbHelper(requireContext());
                        downloadedTracks = getDownloadedTracksFromDb(dbHelper, userId);

                        if (adapter != null) {
                            adapter.setTracks(downloadedTracks);
                            updateDownloadCount(downloadedTracks.size());
                            showEmptyState(downloadedTracks.isEmpty());
                        }
                    }

                    Toast.makeText(getContext(), "Showing local downloads (offline)", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private List<Track> mergeLocalAndCloudTracks(List<Track> localTracks, List<Track> cloudTracks) {
        Log.d(TAG, "Merging " + localTracks.size() + " local tracks with " + cloudTracks.size() + " cloud tracks");
        List<Track> mergedTracks = new ArrayList<>();
        for (Track localTrack : localTracks) {
            mergedTracks.add(localTrack);
        }
        for (Track cloudTrack : cloudTracks) {
            boolean existsLocally = false;
            for (Track localTrack : localTracks) {
                if (localTrack.getId() == cloudTrack.getId()) {
                    existsLocally = true;
                    break;
                }
            }
            if (!existsLocally) {
                Log.d(TAG, "Adding cloud-only track: " + cloudTrack.getTitle());
                mergedTracks.add(cloudTrack);
            }
        }
        Log.d(TAG, "Merged result: " + mergedTracks.size() + " total tracks");
        return mergedTracks;
    }

    public void forceRefresh() {
        Log.d(TAG, "=== FORCE REFRESH REQUESTED ===");

        if (isFragmentVisible && isAdded()) {
            Log.d(TAG, "Fragment is visible - refreshing immediately");
            loadDownloadedTracks();
        } else {
            Log.d(TAG, "Fragment not visible - marking for refresh when visible");
            needsRefresh = true;
        }
    }
    public void onDownloadCompleted(Track completedTrack) {
        Log.d(TAG, "Download completed for: " + completedTrack.getTitle());

        if (isFragmentVisible && isAdded()) {
            requireActivity().runOnUiThread(() -> {
                boolean trackExists = false;
                for (Track track : downloadedTracks) {
                    if (track.getId() == completedTrack.getId()) {
                        trackExists = true;
                        if (track instanceof DownloadedTrack) {
                            ((DownloadedTrack) track).setDownloaded(true);
                        }
                        break;
                    }
                }
                if (!trackExists) {
                    downloadedTracks.add(0, completedTrack);
                }
                if (adapter != null) {
                    adapter.setTracks(downloadedTracks);
                    updateDownloadCount(downloadedTracks.size());
                    showEmptyState(downloadedTracks.isEmpty());
                }

                Log.d(TAG, "UI updated after download completion");
            });
        } else {
            needsRefresh = true;
        }
    }
    private void redownloadTrack(Track track) {
        if (track.isDownloaded()) {
            Toast.makeText(getContext(), "Track already downloaded", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "=== RE-DOWNLOADING TRACK ===");
        Log.d(TAG, "Track: " + track.getTitle());
        Log.d(TAG, "Original URL: " + track.getPreviewUrl());

        if (isUrlExpiredOrInvalid(track.getPreviewUrl())) {
            Log.w(TAG, "URL appears expired, refreshing before download");
            Toast.makeText(getContext(), "Refreshing track data...", Toast.LENGTH_SHORT).show();
            refreshTrackForRedownload(track);
            return;
        }
        Toast.makeText(getContext(), "Re-downloading: " + track.getTitle(), Toast.LENGTH_SHORT).show();
        performActualDownload(track);
    }
    private boolean isUrlExpiredOrInvalid(String url) {
        if (url == null || url.isEmpty()) {
            return true;
        }
        if (url.contains("dzcdn.net") && url.contains("exp=")) {
            try {
                String[] parts = url.split("exp=");
                if (parts.length > 1) {
                    String expPart = parts[1].split("~")[0];
                    long expTime = Long.parseLong(expPart);
                    long currentTime = System.currentTimeMillis() / 1000;

                    boolean isExpired = (currentTime + 3600) > expTime;
                    Log.d(TAG, "URL expiration check - Current: " + currentTime +
                            ", Expires: " + expTime + ", Is expired: " + isExpired);
                    return isExpired;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking URL expiration: " + e.getMessage());
                return true;
            }
        }
        return url.contains("dzcdn.net") || url.startsWith("http");
    }
    private void refreshTrackForRedownload(Track outdatedTrack) {
        Log.d(TAG, "Refreshing track for re-download: " + outdatedTrack.getTitle());

        String searchQuery = outdatedTrack.getTitle();
        if (outdatedTrack.getArtist() != null) {
            searchQuery += " " + outdatedTrack.getArtist().getName();
        }
        DeezerRepository repository = DeezerRepository.getInstance();
        repository.searchTracks(searchQuery, new DeezerRepository.DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> searchResults) {
                if (!isAdded()) return;
                Track refreshedTrack = findBestMatch(searchResults, outdatedTrack);

                if (refreshedTrack != null && isTrackDownloadable(refreshedTrack)) {
                    Log.d(TAG, "✅ Successfully refreshed URL for re-download");
                    Log.d(TAG, "New URL: " + refreshedTrack.getPreviewUrl());
                    updateTrackForRedownload(outdatedTrack, refreshedTrack);

                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Track data refreshed, starting download", Toast.LENGTH_SHORT).show();
                        performActualDownload(outdatedTrack);
                    });

                } else {
                    Log.e(TAG, "❌ Could not refresh track for re-download");
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(),
                                "This track is no longer available for download",
                                Toast.LENGTH_LONG).show();
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "❌ Error refreshing track for re-download: " + message);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(),
                                "Unable to refresh track data: " + message,
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    private Track findBestMatch(List<Track> tracks, Track originalTrack) {
        if (tracks == null || tracks.isEmpty()) return null;
        for (Track track : tracks) {
            if (track.getId() == originalTrack.getId()) {
                Log.d(TAG, "Found exact ID match for re-download");
                return track;
            }
        }
        for (Track track : tracks) {
            if (isExactMatch(track, originalTrack)) {
                Log.d(TAG, "Found exact match for re-download");
                return track;
            }
        }
        for (Track track : tracks) {
            if (track.getTitle().equalsIgnoreCase(originalTrack.getTitle())) {
                int durationDiff = Math.abs(track.getDuration() - originalTrack.getDuration());
                if (durationDiff <= 10) {
                    Log.d(TAG, "Found title match with similar duration for re-download");
                    return track;
                }
            }
        }
        Log.w(TAG, "No suitable match found for re-download");
        return null;
    }
    private boolean isExactMatch(Track track1, Track track2) {
        if (!track1.getTitle().equalsIgnoreCase(track2.getTitle())) {
            return false;
        }
        if (track1.getArtist() != null && track2.getArtist() != null) {
            if (!track1.getArtist().getName().equalsIgnoreCase(track2.getArtist().getName())) {
                return false;
            }
        }
        int durationDiff = Math.abs(track1.getDuration() - track2.getDuration());
        return durationDiff <= 5;
    }
    private boolean isTrackDownloadable(Track track) {
        if (track == null) return false;
        if (track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) return false;
        if (track.getTitle() == null || track.getTitle().isEmpty()) return false;
        return track.getId() > 0;
    }
    private void updateTrackForRedownload(Track outdatedTrack, Track freshTrack) {
        Log.d(TAG, "Updating track with fresh data for re-download");
        outdatedTrack.setPreviewUrl(freshTrack.getPreviewUrl());
        if (outdatedTrack.getAlbum() == null && freshTrack.getAlbum() != null) {
            outdatedTrack.setAlbum(freshTrack.getAlbum());
        }
        if (outdatedTrack.getArtist() == null && freshTrack.getArtist() != null) {
            outdatedTrack.setArtist(freshTrack.getArtist());
        }
        if (outdatedTrack.getContext() == null && getContext() != null) {
            outdatedTrack.setContext(getContext());
        }
        Log.d(TAG, "Track updated - New URL: " + outdatedTrack.getPreviewUrl());
    }
    private void performActualDownload(Track track) {
        Log.d(TAG, "Starting actual download for: " + track.getTitle());
        Log.d(TAG, "Download URL: " + track.getPreviewUrl());

        MusicDownloader.downloadTrack(requireContext(), track,
                new MusicDownloader.DownloadCallback() {
                    @Override
                    public void onDownloadStarted() {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Download started...", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Download started for: " + track.getTitle());
                        });
                    }

                    @Override
                    public void onDownloadProgress(int progress) {
                        Log.d(TAG, "Download progress: " + progress + "%");
                    }

                    @Override
                    public void onDownloadComplete(File downloadedFile) {
                        requireActivity().runOnUiThread(() -> {
                            Log.d(TAG, "✅ Download completed: " + downloadedFile.getAbsolutePath());

                            if (track instanceof DownloadedTrack) {
                                ((DownloadedTrack) track).setDownloaded(true);
                            }
                            UserPreferencesManager.addToDownloadHistoryAsync(
                                    requireContext(),
                                    track,
                                    downloadedFile.getAbsolutePath(),
                                    new UserPreferencesManager.DataCallback<Boolean>() {
                                        @Override
                                        public void onSuccess(Boolean success) {
                                            Log.d(TAG, "✅ Added to cloud download history");
                                        }

                                        @Override
                                        public void onError(String error) {
                                            Log.w(TAG, "❌ Failed to add to cloud: " + error);
                                        }
                                    }
                            );
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                            Toast.makeText(getContext(), "Download complete: " + track.getTitle(), Toast.LENGTH_SHORT).show();
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).refreshDownloadedMusic();
                                ((MainActivity) getActivity()).notifyDownloadCompleted(track);
                            }
                        });
                    }

                    @Override
                    public void onDownloadError(String message) {
                        requireActivity().runOnUiThread(() -> {
                            Log.e(TAG, "❌ Download failed: " + message);

                            String userMessage;
                            if (message.contains("403")) {
                                userMessage = "Download failed: Access denied. The track may no longer be available.";
                            } else if (message.contains("404")) {
                                userMessage = "Download failed: Track not found.";
                            } else if (message.contains("network")) {
                                userMessage = "Download failed: Network error. Please check your connection.";
                            } else {
                                userMessage = "Download failed: " + message;
                            }

                            Toast.makeText(getContext(), userMessage, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void filterDownloads(String query) {
        if (query == null || query.isEmpty()) {
            adapter.setTracks(downloadedTracks);
            updateDownloadCount(downloadedTracks.size());
            return;
        }

        List<Track> filteredTracks = new ArrayList<>();
        String lowercaseQuery = query.toLowerCase();

        for (Track track : downloadedTracks) {
            if (track.getTitle().toLowerCase().contains(lowercaseQuery) ||
                    (track.getArtist() != null && track.getArtist().getName().toLowerCase().contains(lowercaseQuery))) {
                filteredTracks.add(track);
            }
        }

        adapter.setTracks(filteredTracks);
        updateDownloadCount(filteredTracks.size());
    }

    private void navigateToHome() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).getBottomNavigation().setSelectedItemId(R.id.nav_home);
        }
    }

    private void showEmptyState(boolean show) {
        emptyStateContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateDownloadCount(int count) {
        downloadedCount.setText(count + " " + (count == 1 ? "item" : "items"));
    }
    private String formatFileSize(long sizeBytes) {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        } else if (sizeBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeBytes / 1024.0);
        } else {
            return String.format("%.1f MB", sizeBytes / (1024.0 * 1024));
        }
    }
    private void showTrackOptions(Track track) {
        if (track == null || getContext() == null) return;
        List<String> optionsList = new ArrayList<>();
        optionsList.add("Play");

        if (track.isDownloaded()) {
            boolean fileExists = false;
            if (track.getPreviewUrl() != null) {
                File file = new File(track.getPreviewUrl());
                fileExists = file.exists();
            }

            if (fileExists) {
                optionsList.add("Delete from device");
                optionsList.add("Share");
            } else {
                optionsList.add("Re-download (file missing)");
                optionsList.add("Remove from history");
            }
        } else {
            optionsList.add("Download");
            optionsList.add("Remove from history");
        }

        String[] options = optionsList.toArray(new String[0]);

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(getContext());
        builder.setTitle(track.getTitle())
                .setItems(options, (dialog, which) -> {
                    String selectedOption = options[which];
                    handleTrackOption(track, selectedOption);
                })
                .setNegativeButton("Cancel", (dialog, id) -> dialog.dismiss());
        builder.create().show();
    }
    private void handleTrackOption(Track track, String option) {
        Log.d(TAG, "Selected option: " + option + " for track: " + track.getTitle());

        switch (option) {
            case "Play":
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).playTrack(track);
                }
                break;
            case "Delete from device":
                confirmDeleteTrack(track);
                break;
            case "Share":
                shareTrack(track);
                break;
            case "Download":
            case "Re-download (file missing)":
                redownloadTrack(track);
                break;
            case "Remove from history":
                confirmRemoveFromHistory(track);
                break;
        }
    }

    private void confirmRemoveFromHistory(Track track) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(getContext());
        builder.setTitle("Remove from History")
                .setMessage("Remove '" + track.getTitle() + "' from your download history?\n\nThis won't delete any local files.")
                .setPositiveButton("Remove", (dialog, id) -> {
                    removeFromHistoryOnly(track);
                })
                .setNegativeButton("Cancel", (dialog, id) -> dialog.dismiss());
        builder.create().show();
    }
    private void removeFromHistoryOnly(Track track) {
        UserPreferencesManager.removeFromDownloadHistoryAsync(getContext(), track,
                new UserPreferencesManager.DataCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean success) {
                        requireActivity().runOnUiThread(() -> {
                            updateUIAfterDeletion(track);
                            Toast.makeText(getContext(), "Removed from history", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Failed to remove from history", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }
    private void confirmDeleteTrack(Track track) {
        if (track == null || getContext() == null) return;

        String message = "Are you sure you want to delete '" + track.getTitle() + "'?";
        if (track.getPreviewUrl() != null) {
            File file = new File(track.getPreviewUrl());
            if (file.exists()) {
                long fileSize = file.length();
                message += "\n\nThis will free up " + formatFileSize(fileSize) + " of storage.";
            }
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(getContext());
        builder.setTitle("Delete Download")
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, id) -> {
                    Log.d(TAG, "User confirmed deletion of: " + track.getTitle());
                    deleteTrack(track);
                })
                .setNegativeButton("Cancel", (dialog, id) -> {
                    Log.d(TAG, "User cancelled deletion");
                    dialog.dismiss();
                });

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
        try {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(getContext().getColor(android.R.color.holo_red_dark));
        } catch (Exception e) {
            Log.w(TAG, "Could not style delete button: " + e.getMessage());
        }
    }
    private void deleteTrack(Track track) {
        Log.d(TAG, "=== DELETING TRACK ===");
        Log.d(TAG, "Track: " + track.getTitle());
        Log.d(TAG, "Track ID: " + track.getId());
        Log.d(TAG, "File path: " + track.getPreviewUrl());

        if (track == null) {
            Toast.makeText(getContext(), "Invalid track", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Track is null");
            return;
        }

        if (track.getPreviewUrl() == null || track.getPreviewUrl().isEmpty()) {
            Log.w(TAG, "No file path available, proceeding with database cleanup only");
            deleteFromDatabaseAndCloud(track);
            return;
        }

        File file = new File(track.getPreviewUrl());
        Log.d(TAG, "File exists: " + file.exists());
        Log.d(TAG, "File size: " + (file.exists() ? file.length() : "N/A") + " bytes");

        boolean fileDeleted = false;
        if (file.exists()) {
            try {
                fileDeleted = file.delete();
                Log.d(TAG, "File deletion result: " + fileDeleted);

                if (!fileDeleted) {
                    Log.w(TAG, "Failed to delete file, but continuing with database cleanup");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception deleting file: " + e.getMessage());
                Toast.makeText(getContext(), "Permission denied deleting file", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            Log.w(TAG, "File doesn't exist, proceeding with database cleanup");
        }
        deleteFromDatabaseAndCloud(track);
    }
    private void deleteFromDatabaseAndCloud(Track track) {
        Log.d(TAG, "=== DATABASE AND CLOUD CLEANUP ===");

        DownloadedMusicDbHelper dbHelper = new DownloadedMusicDbHelper(getContext());
        SQLiteDatabase db = null;

        try {
            db = dbHelper.getWritableDatabase();

            String selection = DownloadedMusicContract.TrackEntry.COLUMN_TRACK_ID + " = ? AND " +
                    DownloadedMusicContract.TrackEntry.COLUMN_USER_ID + " = ?";

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            String userId = currentUser != null ? currentUser.getUid() : "anonymous";

            String[] selectionArgs = {String.valueOf(track.getId()), userId};

            Log.d(TAG, "Deleting from database with trackId: " + track.getId() + ", userId: " + userId);

            int deletedRows = db.delete(
                    DownloadedMusicContract.TrackEntry.TABLE_NAME,
                    selection,
                    selectionArgs
            );

            Log.d(TAG, "Database deletion result: " + deletedRows + " rows deleted");

            if (deletedRows > 0) {
                UserPreferencesManager.removeFromDownloadHistoryAsync(getContext(), track,
                        new UserPreferencesManager.DataCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean success) {
                                Log.d(TAG, "✅ Successfully removed from cloud: " + track.getTitle());
                            }
                            @Override
                            public void onError(String error) {
                                Log.w(TAG, "❌ Failed to remove from cloud: " + error);
                            }
                        });
                requireActivity().runOnUiThread(() -> {
                    updateUIAfterDeletion(track);
                });

            } else {
                Log.w(TAG, "No rows deleted from database - track may not exist");
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Track not found in database", Toast.LENGTH_SHORT).show();
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error deleting from database: " + e.getMessage(), e);
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), "Error deleting track: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }
    private void updateUIAfterDeletion(Track deletedTrack) {
        Log.d(TAG, "=== UPDATING UI AFTER DELETION ===");
        Log.d(TAG, "Removing track: " + deletedTrack.getTitle());
        Log.d(TAG, "Current list size: " + downloadedTracks.size());

        boolean removed = downloadedTracks.removeIf(t -> t.getId() == deletedTrack.getId());
        Log.d(TAG, "Track removed from list: " + removed);
        Log.d(TAG, "New list size: " + downloadedTracks.size());

        if (adapter != null) {
            adapter.setTracks(downloadedTracks);
            Log.d(TAG, "Adapter updated with " + downloadedTracks.size() + " tracks");
        }

        updateDownloadCount(downloadedTracks.size());

        if (downloadedTracks.isEmpty()) {
            Log.d(TAG, "No more tracks - showing empty state");
            showEmptyState(true);
        }

        Toast.makeText(getContext(), "Track deleted: " + deletedTrack.getTitle(), Toast.LENGTH_SHORT).show();
    }

    private void shareTrack(Track track) {
        if (track.getPreviewUrl() != null && getContext() != null) {
            File file = new File(track.getPreviewUrl());
            if (file.exists()) {
                try {
                    android.content.Intent shareIntent = new android.content.Intent(Intent.ACTION_SEND);
                    shareIntent.setType("audio/*");
                    android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                        getContext(),
                        getContext().getPackageName() + ".provider",
                        file);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, track.getTitle());
                    shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out this track: " + track.getTitle() +
                        (track.getArtist() != null ? " by " + track.getArtist().getName() : ""));
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, "Share track via"));
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Error sharing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error sharing track", e);
                }
            } else {
                Toast.makeText(getContext(), "File doesn't exist", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public List<Track> getAllDownloadedTracks() {
        return new ArrayList<>(downloadedTracks);
    }

    public static List<Track> getDownloadedTracksFromDb(DownloadedMusicDbHelper dbHelper) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String userId = currentUser != null ? currentUser.getUid() : "anonymous";
        return getDownloadedTracksFromDb(dbHelper, userId);
    }

    public static List<Track> getDownloadedTracksFromDb(DownloadedMusicDbHelper dbHelper, String userId) {
        List<Track> tracks = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String selection = DownloadedMusicContract.TrackEntry.COLUMN_USER_ID + " = ?";
        String[] selectionArgs = { userId };

        String[] projection = {
                DownloadedMusicContract.TrackEntry._ID,
                DownloadedMusicContract.TrackEntry.COLUMN_TRACK_ID,
                DownloadedMusicContract.TrackEntry.COLUMN_TITLE,
                DownloadedMusicContract.TrackEntry.COLUMN_ARTIST,
                DownloadedMusicContract.TrackEntry.COLUMN_FILE_PATH,
                DownloadedMusicContract.TrackEntry.COLUMN_DURATION,
                DownloadedMusicContract.TrackEntry.COLUMN_ALBUM_ART,
                DownloadedMusicContract.TrackEntry.COLUMN_DOWNLOAD_DATE
        };

        try (android.database.Cursor cursor = db.query(
                DownloadedMusicContract.TrackEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                DownloadedMusicContract.TrackEntry.COLUMN_DOWNLOAD_DATE + " DESC")) {

            while (cursor != null && cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadedMusicContract.TrackEntry.COLUMN_TRACK_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadedMusicContract.TrackEntry.COLUMN_TITLE));
                String artistName = cursor.getString(cursor.getColumnIndexOrThrow(DownloadedMusicContract.TrackEntry.COLUMN_ARTIST));
                String filePath = cursor.getString(cursor.getColumnIndexOrThrow(DownloadedMusicContract.TrackEntry.COLUMN_FILE_PATH));
                int duration = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadedMusicContract.TrackEntry.COLUMN_DURATION));
                String albumArtUrl = cursor.getString(cursor.getColumnIndexOrThrow(DownloadedMusicContract.TrackEntry.COLUMN_ALBUM_ART));

                DownloadedTrack track = new DownloadedTrack();
                track.setId(id);
                track.setTitle(title);
                track.setDuration(duration);
                track.setPreviewUrl(filePath);

                DownloadedArtist artist = new DownloadedArtist();
                artist.setName(artistName);
                track.setArtist(artist);

                if (albumArtUrl != null && !albumArtUrl.isEmpty()) {
                    DownloadedAlbum album = new DownloadedAlbum();
                    album.setCoverMedium(albumArtUrl);
                    track.setAlbum(album);
                }

                tracks.add(track);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading downloaded tracks", e);
        } finally {
            db.close();
        }

        return tracks;
    }

    private class DownloadedMusicAdapter extends RecyclerView.Adapter<DownloadedMusicAdapter.ViewHolder> {
        private List<Track> tracks = new ArrayList<>();

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_downloaded_music, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Track track = tracks.get(position);
            holder.bind(track);
        }

        @Override
        public int getItemCount() {
            return tracks.size();
        }

        public void setTracks(List<Track> tracks) {
            this.tracks = tracks;
            notifyDataSetChanged();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView artist;
            TextView duration;
            TextView fileSize;
            TextView downloadDate;
            ImageView artwork;
            ImageButton optionsMenu;
            MaterialButton redownloadButton;
            TextView downloadStatus;

            ViewHolder(@NonNull View itemView) {
                super(itemView);

                title = itemView.findViewById(R.id.tv_title);
                artist = itemView.findViewById(R.id.tv_artist);
                duration = itemView.findViewById(R.id.tv_duration);
                fileSize = itemView.findViewById(R.id.tv_file_size);
                downloadDate = itemView.findViewById(R.id.tv_date);
                artwork = itemView.findViewById(R.id.iv_artwork);
                optionsMenu = itemView.findViewById(R.id.options_menu);

                redownloadButton = itemView.findViewById(R.id.redownload_button);
                downloadStatus = itemView.findViewById(R.id.tv_download_status);

                itemView.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        Track clickedTrack = tracks.get(position);

                        if (clickedTrack.isDownloaded()) {
                            // Play locally
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).playTrack(clickedTrack);
                            }
                        } else {
                            // Show re-download option
                            redownloadTrack(clickedTrack);
                        }
                    }
                });

                optionsMenu.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        showTrackOptions(tracks.get(position));
                    }
                });

                if (redownloadButton != null) {
                    redownloadButton.setOnClickListener(v -> {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            redownloadTrack(tracks.get(position));
                        }
                    });
                }
            }

            void bind(Track track) {

                title.setText(track.getTitle());

                if (track.getArtist() != null) {
                    artist.setText(track.getArtist().getName());
                } else {
                    artist.setText("Unknown Artist");
                }

                int mins = track.getDuration() / 60;
                int secs = track.getDuration() % 60;
                duration.setText(String.format("%d:%02d", mins, secs));

                if (track.isDownloaded()) {
                    if (track.getPreviewUrl() != null) {
                        File file = new File(track.getPreviewUrl());
                        if (file.exists()) {
                            long fileSizeBytes = file.length();
                            fileSize.setText(formatFileSize(fileSizeBytes));
                            fileSize.setVisibility(View.VISIBLE);

                            downloadStatus.setText("✅ Downloaded");
                            downloadStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_green_dark));
                            downloadStatus.setVisibility(View.VISIBLE);
                        } else {
                            fileSize.setText("---");
                            fileSize.setVisibility(View.VISIBLE);

                            downloadStatus.setText("❌ File missing");
                            downloadStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_red_dark));
                            downloadStatus.setVisibility(View.VISIBLE);
                        }
                    }

                    redownloadButton.setVisibility(View.GONE);

                } else {
                    fileSize.setText("---");
                    fileSize.setVisibility(View.VISIBLE);

                    downloadStatus.setText("☁️ Available");
                    downloadStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_blue_dark));
                    downloadStatus.setVisibility(View.VISIBLE);

                    redownloadButton.setVisibility(View.VISIBLE);
                    redownloadButton.setText("Download");
                }

                if (track.getAlbum() != null && track.getAlbum().getCoverMedium() != null) {
                    com.bumptech.glide.Glide.with(itemView.getContext())
                            .load(track.getAlbum().getCoverMedium())
                            .placeholder(R.drawable.ic_downloaded_music)
                            .error(R.drawable.ic_downloaded_music)
                            .centerCrop()
                            .into(artwork);
                } else {
                    artwork.setImageResource(R.drawable.ic_downloaded_music);
                }
            }
        }
    }
    @Override
    public void onPause() {
        super.onPause();
        isFragmentVisible = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        isFragmentVisible = true;
        Log.d(TAG, "Fragment resumed, refreshing data");
        debugDatabaseState();
        loadDownloadedTracks();
    }
    private void debugDatabaseState() {
        try {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            String userId = currentUser != null ? currentUser.getUid() : "anonymous";

            DownloadedMusicDbHelper dbHelper = new DownloadedMusicDbHelper(requireContext());
            List<Track> dbTracks = getDownloadedTracksFromDb(dbHelper, userId);

            Log.d(TAG, "=== DATABASE DEBUG ===");
            Log.d(TAG, "User ID: " + userId);
            Log.d(TAG, "Tracks in database: " + dbTracks.size());
            for (Track track : dbTracks) {
                Log.d(TAG, "- " + track.getId() + ": " + track.getTitle());
            }
            Log.d(TAG, "=== END DATABASE DEBUG ===");

        } catch (Exception e) {
            Log.e(TAG, "Error debugging database: " + e.getMessage());
        }
    }
}



