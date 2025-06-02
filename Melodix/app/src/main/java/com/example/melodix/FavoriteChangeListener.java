package com.example.melodix;

/**
 * Interface for listening to changes in favorite tracks
 */
public interface FavoriteChangeListener {
    /**
     * Called when a track's favorite status changes
     */
    void onFavoriteChanged();
}
