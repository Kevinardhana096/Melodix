package com.example.melodix.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.example.melodix.R;
import com.example.melodix.model.Track;

import java.util.ArrayList;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SearchViewHolder> {
    private static final String TAG = "SearchResultAdapter";
    private List<Track> searchResults;
    private OnSearchItemClickListener listener;

    private static final RequestOptions GLIDE_OPTIONS = new RequestOptions()
            .placeholder(R.drawable.ic_launcher_background)
            .error(R.drawable.ic_launcher_background)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop();

    public SearchResultAdapter(OnSearchItemClickListener listener) {
        this.searchResults = new ArrayList<>();
        this.listener = listener;

        setHasStableIds(true);
    }

    @NonNull
    @Override
    public SearchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new SearchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchViewHolder holder, int position) {
        if (position < searchResults.size()) {
            Track track = searchResults.get(position);
            holder.bind(track);
        }
    }

    @Override
    public int getItemCount() {
        return searchResults.size();
    }

    @Override
    public long getItemId(int position) {
        if (position < searchResults.size()) {
            return searchResults.get(position).getId();
        }
        return RecyclerView.NO_ID;
    }

    public void updateResults(List<Track> newResults) {
        Log.d(TAG, "=== UPDATING SEARCH RESULTS ===");
        Log.d(TAG, "Previous size: " + searchResults.size());
        Log.d(TAG, "New size: " + (newResults != null ? newResults.size() : 0));

        if (newResults == null) {
            int previousSize = searchResults.size();
            searchResults.clear();
            if (previousSize > 0) {
                notifyItemRangeRemoved(0, previousSize);
            }
            Log.d(TAG, "✅ Cleared all search results");
            return;
        }

        // For small datasets, use simple update
        if (newResults.size() <= 50 || searchResults.isEmpty()) {
            updateResultsSimple(newResults);
        } else {
            // For large datasets, use DiffUtil
            updateResultsWithDiffUtil(newResults);
        }
    }

    // ✅ OPTIMIZE: Simple update for small datasets
    private void updateResultsSimple(List<Track> newResults) {
        try {
            int oldSize = searchResults.size();
            searchResults.clear();
            searchResults.addAll(newResults);

            if (oldSize == 0) {
                notifyItemRangeInserted(0, newResults.size());
            } else {
                notifyDataSetChanged(); // Fallback for mixed operations
            }

            Log.d(TAG, "✅ Simple update completed: " + newResults.size() + " items");
        } catch (Exception e) {
            Log.e(TAG, "Error in simple update", e);
            // Fallback
            searchResults.clear();
            searchResults.addAll(newResults);
            notifyDataSetChanged();
        }
    }

    // ✅ OPTIMIZE: DiffUtil for large datasets
    private void updateResultsWithDiffUtil(List<Track> newResults) {
        try {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return searchResults.size();
                }

                @Override
                public int getNewListSize() {
                    return newResults.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return searchResults.get(oldItemPosition).getId() ==
                            newResults.get(newItemPosition).getId();
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    Track oldTrack = searchResults.get(oldItemPosition);
                    Track newTrack = newResults.get(newItemPosition);

                    return oldTrack.getTitle().equals(newTrack.getTitle()) &&
                            (oldTrack.getArtist() != null && newTrack.getArtist() != null ?
                                    oldTrack.getArtist().getName().equals(newTrack.getArtist().getName()) :
                                    oldTrack.getArtist() == newTrack.getArtist());
                }
            }, false); // detectMoves = false for better performance

            searchResults.clear();
            searchResults.addAll(newResults);
            diffResult.dispatchUpdatesTo(this);

            Log.d(TAG, "✅ DiffUtil update completed: " + newResults.size() + " items");
        } catch (Exception e) {
            Log.e(TAG, "Error in DiffUtil update, falling back to simple update", e);
            updateResultsSimple(newResults);
        }
    }

    // ✅ OPTIMIZE: Add progressive update method
    public void updateResultsProgressive(List<Track> newResults, int batchSize) {
        Log.d(TAG, "=== PROGRESSIVE UPDATE ===");
        Log.d(TAG, "Total items: " + newResults.size() + ", Batch size: " + batchSize);

        searchResults.clear();
        notifyDataSetChanged();

        // Add items in batches
        for (int i = 0; i < newResults.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, newResults.size());
            List<Track> batch = newResults.subList(i, endIndex);

            int startPosition = searchResults.size();
            searchResults.addAll(batch);
            notifyItemRangeInserted(startPosition, batch.size());

            Log.d(TAG, "Added batch: " + batch.size() + " items, Total: " + searchResults.size());

            // Small delay between batches to prevent UI blocking
            if (endIndex < newResults.size()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        Log.d(TAG, "✅ Progressive update completed");
    }

    public class SearchViewHolder extends RecyclerView.ViewHolder {
        private ImageView imgAlbumArt;
        private TextView txtSongTitle;
        private TextView txtArtistName;
        private ImageView imgPlay;
        private TextView txtContentType;

        // ✅ OPTIMIZE: Track current bound item to prevent unnecessary updates
        private long currentTrackId = -1;

        public SearchViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAlbumArt = itemView.findViewById(R.id.img_album_art);
            txtSongTitle = itemView.findViewById(R.id.txt_song_title);
            txtArtistName = itemView.findViewById(R.id.txt_artist_name);
            imgPlay = itemView.findViewById(R.id.img_play);
            txtContentType = itemView.findViewById(R.id.txt_content_type);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION &&
                        position < searchResults.size() &&
                        listener != null) {
                    listener.onSearchItemClick(searchResults.get(position));
                }
            });

            imgPlay.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION &&
                        position < searchResults.size() &&
                        listener != null) {
                    listener.onSearchItemPlayClick(searchResults.get(position));
                }
            });
        }

        public void bind(Track track) {
            if (track == null) {
                Log.w(TAG, "Attempting to bind null track");
                return;
            }

            // ✅ OPTIMIZE: Skip binding if same track
            if (currentTrackId == track.getId()) {
                return;
            }
            currentTrackId = track.getId();

            try {
                bindTrackInfo(track);
                bindAlbumArt(track);
            } catch (Exception e) {
                Log.e(TAG, "Error binding track: " + track.getTitle(), e);
                bindFallbackData();
            }
        }

        private void bindTrackInfo(Track track) {
            String contentType = track.getType();

            if (contentType != null) {
                switch (contentType) {
                    case "artist":
                        txtSongTitle.setText(track.getTitle());
                        txtArtistName.setText("Artist");
                        setContentType("ARTIST");
                        break;

                    case "album":
                        txtSongTitle.setText(track.getTitle());
                        txtArtistName.setText(track.getArtist() != null ?
                                track.getArtist().getName() : "Various Artists");
                        setContentType("ALBUM");
                        break;

                    default:
                        setContentType(null);
                        txtSongTitle.setText(track.getTitle());
                        txtArtistName.setText(track.getArtist() != null ?
                                track.getArtist().getName() : "Unknown Artist");
                        break;
                }
            } else {
                setContentType(null);
                txtSongTitle.setText(track.getTitle());
                txtArtistName.setText(track.getArtist() != null ?
                        track.getArtist().getName() : "Unknown Artist");
            }
        }

        private void setContentType(String type) {
            if (txtContentType != null) {
                if (type != null) {
                    txtContentType.setVisibility(View.VISIBLE);
                    txtContentType.setText(type);
                } else {
                    txtContentType.setVisibility(View.GONE);
                }
            }
        }

        // ✅ OPTIMIZE: Improved image loading with better error handling
        private void bindAlbumArt(Track track) {
            if (imgAlbumArt == null) return;

            String imageUrl = null;
            if (track.getAlbum() != null) {
                imageUrl = track.getAlbum().getCoverMedium();
            }

            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(imgAlbumArt.getContext())
                        .load(imageUrl)
                        .apply(GLIDE_OPTIONS)
                        .into(imgAlbumArt);
            } else {
                // Clear previous image and set placeholder
                Glide.with(imgAlbumArt.getContext()).clear(imgAlbumArt);
                imgAlbumArt.setImageResource(R.drawable.ic_launcher_background);
            }
        }

        private void bindFallbackData() {
            txtSongTitle.setText("Unknown Track");
            txtArtistName.setText("Unknown Artist");
            setContentType(null);
            imgAlbumArt.setImageResource(R.drawable.ic_launcher_background);
        }
    }

    // ✅ OPTIMIZE: Add method to clear resources
    public void clearResults() {
        int size = searchResults.size();
        searchResults.clear();
        if (size > 0) {
            notifyItemRangeRemoved(0, size);
        }
        Log.d(TAG, "✅ Cleared " + size + " search results");
    }

    public interface OnSearchItemClickListener {
        void onSearchItemClick(Track track);
        void onSearchItemPlayClick(Track track);
    }
}