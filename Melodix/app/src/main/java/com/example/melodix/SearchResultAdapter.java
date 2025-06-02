package com.example.melodix;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SearchViewHolder> {

    private List<Track> searchResults;
    private OnSearchItemClickListener listener;

    public SearchResultAdapter(OnSearchItemClickListener listener) {
        this.searchResults = new ArrayList<>();
        this.listener = listener;
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
        Track track = searchResults.get(position);
        holder.bind(track);
    }

    @Override
    public int getItemCount() {
        return searchResults.size();
    }

    public void updateResults(List<Track> newResults) {
        this.searchResults.clear();
        if (newResults != null) {
            this.searchResults.addAll(newResults);
        }
        notifyDataSetChanged();
    }

    public class SearchViewHolder extends RecyclerView.ViewHolder {
        private ImageView imgAlbumArt;
        private TextView txtSongTitle;
        private TextView txtArtistName;
        private ImageView imgPlay;

        public SearchViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAlbumArt = itemView.findViewById(R.id.img_album_art);
            txtSongTitle = itemView.findViewById(R.id.txt_song_title);
            txtArtistName = itemView.findViewById(R.id.txt_artist_name);
            imgPlay = itemView.findViewById(R.id.img_play);

            // Set click listener for the item
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSearchItemClick(searchResults.get(position));
                }
            });

            // Set click listener for the play button
            imgPlay.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSearchItemPlayClick(searchResults.get(position));
                }
            });
        }

        public void bind(Track track) {
            txtSongTitle.setText(track.getTitle());

            if (track.getArtist() != null) {
                txtArtistName.setText(track.getArtist().getName());
            } else {
                txtArtistName.setText("Unknown Artist");
            }

            // Load album art using Glide
            if (track.getAlbum() != null && track.getAlbum().getCoverMedium() != null) {
                Glide.with(imgAlbumArt.getContext())
                        .load(track.getAlbum().getCoverMedium())
                        .placeholder(R.drawable.ic_launcher_background)
                        .into(imgAlbumArt);
            } else {
                imgAlbumArt.setImageResource(R.drawable.ic_launcher_background);
            }
        }
    }

    public interface OnSearchItemClickListener {
        void onSearchItemClick(Track track);
        void onSearchItemPlayClick(Track track);
    }
}
