package com.example.melodix.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.melodix.model.Album;
import com.example.melodix.model.AlbumSearchResponse;
import com.example.melodix.model.Artist;
import com.example.melodix.model.ArtistSearchResponse;
import com.example.melodix.model.SearchResponse;
import com.example.melodix.model.Track;

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
    private final Executor diskIO;
    private final Executor networkIO;
    private final Executor mainThread;

    private DeezerRepository() {
        apiService = DeezerApiClient.getApiService();

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

    public void searchTracks(String query, final DataCallback<List<Track>> callback) {
        Log.d(TAG, "Searching tracks with query: " + query);
        apiService.searchTracks(query).enqueue(new Callback<SearchResponse>() {
            @Override
            public void onResponse(Call<SearchResponse> call, Response<SearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Track> tracks = response.body().getTracks();
                    int tracksWithDates = 0;
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
    private void fetchAlbumDetailsForTracks(List<Track> tracks, final DataCallback<List<Track>> callback) {
        final int[] processedCount = {0};
        final int totalTracks = tracks.size();

        for (int i = 0; i < tracks.size(); i++) {
            final Track track = tracks.get(i);

            if ((track.getAlbum() == null || track.getAlbum().getReleaseDate() == null) &&
                track.getAlbum() != null && track.getAlbum().getId() > 0) {

                apiService.getAlbum(track.getAlbum().getId()).enqueue(new Callback<Album>() {
                    @Override
                    public void onResponse(Call<Album> call, Response<Album> response) {
                        synchronized (processedCount) {
                            processedCount[0]++;

                            if (response.isSuccessful() && response.body() != null) {
                                Album fullAlbum = response.body();
                                if (fullAlbum.getReleaseDate() != null) {
                                    String existingCover = null;
                                    if (track.getAlbum() != null && track.getAlbum().getCoverMedium() != null) {
                                        existingCover = track.getAlbum().getCoverMedium();
                                    }

                                    track.setAlbum(fullAlbum);

                                    if (existingCover != null && track.getAlbum().getCoverMedium() == null) {
                                        track.getAlbum().setCoverMedium(existingCover);
                                    }

                                    Log.d(TAG, "Updated track: " + track.getTitle() +
                                          " with release date: " + fullAlbum.getReleaseDate());
                                }
                            }
                            if (processedCount[0] >= totalTracks) {
                                Log.d(TAG, "Completed fetching album details for " + totalTracks + " tracks");
                                callback.onSuccess(tracks);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Album> call, Throwable t) {
                        synchronized (processedCount) {
                            processedCount[0]++;
                            Log.e(TAG, "Failed to fetch album details: " + t.getMessage());
                            if (processedCount[0] >= totalTracks) {
                                callback.onSuccess(tracks);
                            }
                        }
                    }
                });
            } else {
                synchronized (processedCount) {
                    processedCount[0]++;

                    if (processedCount[0] >= totalTracks) {
                        callback.onSuccess(tracks);
                    }
                }
            }
        }
    }

    public void getLatestTracks(final DataCallback<List<Track>> callback) {
        Log.d(TAG, "Getting latest music tracks");
        apiService.searchTracks("new releases 2025").enqueue(new Callback<SearchResponse>() {
            @Override
            public void onResponse(Call<SearchResponse> call, Response<SearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Track> tracks = response.body().getTracks();
                    Log.d(TAG, "Latest tracks fetch successful. Found " +
                          (tracks != null ? tracks.size() : 0) + " tracks");

                    if (tracks != null && !tracks.isEmpty()) {
                        int missingReleaseDates = 0;
                        for (Track track : tracks) {
                            if (track.getAlbum() == null || track.getAlbum().getReleaseDate() == null) {
                                missingReleaseDates++;
                            }
                        }
                        if (missingReleaseDates > 0) {
                            Log.d(TAG, missingReleaseDates + " out of " + tracks.size() + " tracks are missing release dates. Fetching album details...");
                            fetchAlbumDetailsForTracks(tracks, callback);
                        } else {
                            callback.onSuccess(tracks);
                        }
                    } else {
                        callback.onSuccess(tracks);
                    }
                } else {
                    Log.e(TAG, "Latest tracks fetch failed: " + response.code() + " - " + response.message());
                    callback.onError("Failed to get latest tracks: " + response.code());
                }
            }
            @Override
            public void onFailure(Call<SearchResponse> call, Throwable t) {
                Log.e(TAG, "Latest tracks network error: " + t.getMessage(), t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    public void searchArtists(String query, final DataCallback<List<Artist>> callback) {
        Log.d(TAG, "Searching artists with query: " + query);
        apiService.searchArtists(query).enqueue(new Callback<ArtistSearchResponse>() {
            @Override
            public void onResponse(Call<ArtistSearchResponse> call, Response<ArtistSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Artist> artists = response.body().getArtists();
                    Log.d(TAG, "Artist search successful. Found " +
                            (artists != null ? artists.size() : 0) + " artists");
                    callback.onSuccess(artists);
                } else {
                    Log.e(TAG, "Artist search failed: " + response.code() + " - " + response.message());
                    callback.onError("Failed to search artists: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ArtistSearchResponse> call, Throwable t) {
                Log.e(TAG, "Artist search network error: " + t.getMessage(), t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    public void searchAlbums(String query, final DataCallback<List<Album>> callback) {
        Log.d(TAG, "Searching albums with query: " + query);
        apiService.searchAlbums(query).enqueue(new Callback<AlbumSearchResponse>() {
            @Override
            public void onResponse(Call<AlbumSearchResponse> call, Response<AlbumSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Album> albums = response.body().getAlbums();
                    Log.d(TAG, "Album search successful. Found " +
                            (albums != null ? albums.size() : 0) + " albums");
                    callback.onSuccess(albums);
                } else {
                    Log.e(TAG, "Album search failed: " + response.code() + " - " + response.message());
                    callback.onError("Failed to search albums: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<AlbumSearchResponse> call, Throwable t) {
                Log.e(TAG, "Album search network error: " + t.getMessage(), t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
}
