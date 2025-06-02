package com.example.melodix;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface DeezerApiService {
    @GET("search")
    Call<SearchResponse> searchTracks(@Query("q") String query);

    @GET("chart")
    Call<ChartResponse> getChart();

    @GET("chart/0/tracks")
    Call<SearchResponse> getTopTracks();

    @GET("search")
    Call<SearchResponse> getLatestReleases(@Query("q") String query, @Query("order") String order);

    @GET("track/{id}")
    Call<Track> getTrack(@Path("id") long trackId);

    @GET("album/{id}")
    Call<Album> getAlbum(@Path("id") long albumId);

    @GET("artist/{id}")
    Call<Artist> getArtist(@Path("id") long artistId);
}
