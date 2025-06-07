package com.example.melodix.listener;

/**
 * Interface for listening to changes in favorite tracks
 */
public interface FavoriteChangeListener {
    /**
     * Called when a track's favorite status changes
     */
    void onFavoriteChanged();
}
