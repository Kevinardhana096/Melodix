package com.example.melodix;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DeezerRepository {
    private static final String TAG = "DeezerRepository";
    private static DeezerRepository instance;
    private DeezerApiService apiService;
    // Executors for background operations and UI thread callbacks
    private final Executor diskIO;
    private final Executor networkIO;
    private final Executor mainThread;

    private DeezerRepository() {
        apiService = DeezerApiClient.getApiService();

        // Initialize executors
        diskIO = Executors.newSingleThreadExecutor();
        networkIO = Executors.newFixedThreadPool(3);
        mainThread = new MainThreadExecutor();
    }

    public static synchronized DeezerRepository getInstance() {
        if (instance == null) {
            instance = new DeezerRepository();
        }
        return instance;
    }

    // Custom executor for Main Thread operations
    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            mainThreadHandler.post(command);
        }
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    // New method for executing operations on background thread
    public <T> void executeOnBackgroundThread(final BackgroundTask<T> task, final DataCallback<T> callback) {
        networkIO.execute(() -> {
            try {
                final T result = task.execute();
                mainThread.execute(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.e(TAG, "Background task error: " + e.getMessage(), e);
                mainThread.execute(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // Interface for background tasks
    public interface BackgroundTask<T> {
        T execute() throws Exception;
    }

    public void searchTracks(String query, final DataCallback<List<Track>> callback) {
        Log.d(TAG, "Searching tracks with query: " + query);
        apiService.searchTracks(query).enqueue(new Callback<SearchResponse>() {
            @Override
            public void onResponse(Call<SearchResponse> call, Response<SearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Track> tracks = response.body().getTracks();
                    int tracksWithDates = 0;

                    // Log details about release dates
                    if (tracks != null) {
                        for (Track track : tracks) {
                            if (track.getAlbum() != null && track.getAlbum().getReleaseDate() != null) {
                                tracksWithDates++;
                                Log.d(TAG, "Track: " + track.getTitle() +
                                         ", Release date: " + track.getAlbum().getReleaseDate());
                            }
                        }
                        Log.d(TAG, "Search successful. Found " + tracks.size() +
                                 " tracks, " + tracksWithDates + " with release dates");
                    } else {
                        Log.d(TAG, "Search successful but no tracks returned");
                    }

                    callback.onSuccess(response.body().getTracks());
                } else {
                    Log.e(TAG, "Search failed: " + response.code() + " - " + response.message());
                    callback.onError("Failed to search tracks: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<SearchResponse> call, Throwable t) {
                Log.e(TAG, "Search network error: " + t.getMessage(), t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    public void getTopTracks(final DataCallback<List<Track>> callback) {
        Log.d(TAG, "Getting top tracks");
        apiService.getTopTracks().enqueue(new Callback<SearchResponse>() {
            @Override
            public void onResponse(Call<SearchResponse> call, Response<SearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Track> tracks = response.body().getTracks();
                    int tracksWithDates = 0;

                    // Log details about release dates
                    if (tracks != null) {
                        for (Track track : tracks) {
                            if (track.getAlbum() != null && track.getAlbum().getReleaseDate() != null) {
                                tracksWithDates++;
                                Log.d(TAG, "Track: " + track.getTitle() +
                                         ", Release date: " + track.getAlbum().getReleaseDate());
                            }
                        }
                        Log.d(TAG, "Top tracks successful. Found " + tracks.size() +
                                 " tracks, " + tracksWithDates + " with release dates");
                    } else {
                        Log.d(TAG, "Top tracks successful but no tracks returned");
                    }

                    callback.onSuccess(response.body().getTracks());
                } else {
                    Log.e(TAG, "Top tracks fetch failed: " + response.code() + " - " + response.message());
                    callback.onError("Failed to get top tracks: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<SearchResponse> call, Throwable t) {
                Log.e(TAG, "Top tracks network error: " + t.getMessage(), t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    public void getChart(final DataCallback<ChartResponse> callback) {
        Log.d(TAG, "Getting chart data");
        apiService.getChart().enqueue(new Callback<ChartResponse>() {
            @Override
            public void onResponse(Call<ChartResponse> call, Response<ChartResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "Chart fetch successful: " +
                          (response.body().getTracks() != null ?
                           "Tracks: " + (response.body().getTracks().getItems() != null ?
                                       response.body().getTracks().getItems().size() : 0) : "No tracks") +
                          (response.body().getAlbums() != null ?
                           ", Albums: " + (response.body().getAlbums().getItems() != null ?
                                        response.body().getAlbums().getItems().size() : 0) : "") +
                          (response.body().getArtists() != null ?
                           ", Artists: " + (response.body().getArtists().getItems() != null ?
                                         response.body().getArtists().getItems().size() : 0) : ""));
                    callback.onSuccess(response.body());
                } else {
                    Log.e(TAG, "Chart fetch failed: " + response.code() + " - " + response.message());
                    callback.onError("Failed to get chart data: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ChartResponse> call, Throwable t) {
                Log.e(TAG, "Chart network error: " + t.getMessage(), t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Get the latest music releases, sorted by release date
     */
    public void getLatestReleases(final DataCallback<List<Track>> callback) {
        Log.d(TAG, "Getting latest releases");
        // Use "new" as query to get new releases and sort by release date
        apiService.getLatestReleases("new", "ALBUM_ASC").enqueue(new Callback<SearchResponse>() {
            @Override
            public void onResponse(Call<SearchResponse> call, Response<SearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Track> tracks = response.body().getTracks();
                    int tracksWithDates = 0;

                    // Log details about release dates
                    if (tracks != null) {
                        for (Track track : tracks) {
                            if (track.getAlbum() != null && track.getAlbum().getReleaseDate() != null) {
                                tracksWithDates++;
                                Log.d(TAG, "Latest release: " + track.getTitle() +
                                         ", Release date: " + track.getAlbum().getReleaseDate());
                            }
                        }
                        Log.d(TAG, "Latest releases fetch successful. Found " + tracks.size() +
                                 " tracks, " + tracksWithDates + " with release dates");
                    } else {
                        Log.d(TAG, "Latest releases fetch successful but no tracks returned");
                    }

                    // Even if we don't get tracks with release dates, let's try an alternative approach
                    if (tracksWithDates == 0 && tracks != null && !tracks.isEmpty()) {
                        fetchAlbumDetailsForTracks(tracks, callback);
                    } else {
                        callback.onSuccess(tracks);
                    }
                } else {
                    Log.e(TAG, "Latest releases fetch failed: " + response.code() + " - " + response.message());
                    callback.onError("Failed to get latest releases: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<SearchResponse> call, Throwable t) {
                Log.e(TAG, "Latest releases network error: " + t.getMessage(), t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * For tracks that don't have release dates, fetch the album details
     * to get the complete album information including release dates
     */
    private void fetchAlbumDetailsForTracks(List<Track> tracks, final DataCallback<List<Track>> callback) {
        final int[] processedCount = {0};
        final int totalTracks = tracks.size();

        for (int i = 0; i < tracks.size(); i++) {
            final Track track = tracks.get(i);

            if (track.getAlbum() != null && track.getAlbum().getId() > 0) {
                final int trackIndex = i;
                apiService.getAlbum(track.getAlbum().getId()).enqueue(new Callback<Album>() {
                    @Override
                    public void onResponse(Call<Album> call, Response<Album> response) {
                        synchronized (processedCount) {
                            processedCount[0]++;

                            if (response.isSuccessful() && response.body() != null) {
                                Album fullAlbum = response.body();

                                // Update the track's album with the full album details
                                track.setAlbum(fullAlbum);

                                if (fullAlbum.getReleaseDate() != null) {
                                    Log.d(TAG, "Updated track " + track.getTitle() +
                                            " with release date: " + fullAlbum.getReleaseDate());
                                }
                            }

                            // When all tracks have been processed, return the updated list
                            if (processedCount[0] >= totalTracks) {
                                callback.onSuccess(tracks);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Album> call, Throwable t) {
                        synchronized (processedCount) {
                            processedCount[0]++;

                            // Even if this album fetch fails, continue with others
                            Log.e(TAG, "Failed to fetch album details: " + t.getMessage());

                            // When all tracks have been processed, return the updated list
                            if (processedCount[0] >= totalTracks) {
                                callback.onSuccess(tracks);
                            }
                        }
                    }
                });
            } else {
                synchronized (processedCount) {
                    processedCount[0]++;

                    // When all tracks have been processed, return the updated list
                    if (processedCount[0] >= totalTracks) {
                        callback.onSuccess(tracks);
                    }
                }
            }
        }
    }

    // Example of a custom operation that needs background threading
    public void processTrackData(final List<Track> tracks, final DataCallback<List<Track>> callback) {
        executeOnBackgroundThread(() -> {
            // Simulate processing data
            Thread.sleep(1000);

            // Filter or transform tracks here
            return tracks;
        }, callback);
    }
}
