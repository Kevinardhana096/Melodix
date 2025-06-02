package com.example.melodix;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.ViewHolder> {

    private List<Track> tracks;
    private OnFavoriteClickListener listener;
    private Context context;

    public interface OnFavoriteClickListener {
        void onPlayClick(Track track);
        void onRemoveFromFavoritesClick(Track track, int position);
        void onOptionsClick(Track track, View view);
    }

    public FavoriteAdapter(Context context, OnFavoriteClickListener listener) {
        this.context = context;
        this.tracks = new ArrayList<>();
        this.listener = listener;
    }

    public void updateData(List<Track> newTracks) {
        this.tracks.clear();
        if (newTracks != null) {
            this.tracks.addAll(newTracks);
        }
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < tracks.size()) {
            tracks.remove(position);
            notifyItemRemoved(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Track track = tracks.get(position);

        // Set track information
        holder.tvTrackTitle.setText(track.getTitle());

        if (track.getArtist() != null) {
            holder.tvArtistName.setText(track.getArtist().getName());
        } else {
            holder.tvArtistName.setText("Unknown Artist");
        }

        // Format duration (converting seconds to MM:SS format)
        int durationInSeconds = track.getDuration() / 1000;
        int minutes = durationInSeconds / 60;
        int seconds = durationInSeconds % 60;
        holder.tvDuration.setText(String.format("%d:%02d", minutes, seconds));

        // Load album art using Glide
        if (track.getAlbum() != null && track.getAlbum().getCoverMedium() != null) {
            Glide.with(context)
                    .load(track.getAlbum().getCoverMedium())
                    .placeholder(R.drawable.ic_music)
                    .into(holder.imgAlbumArt);
        } else {
            holder.imgAlbumArt.setImageResource(R.drawable.ic_music);
        }

        // Set click listeners
        holder.btnPlay.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlayClick(track);
            }
        });

        holder.btnFavorite.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemoveFromFavoritesClick(track, holder.getAdapterPosition());
            }
        });

        holder.btnOptions.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOptionsClick(track, v);
            }
        });

        // Make the whole item clickable to play the track
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlayClick(track);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAlbumArt;
        TextView tvTrackTitle, tvArtistName, tvDuration;
        ImageButton btnFavorite, btnOptions, btnPlay;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAlbumArt = itemView.findViewById(R.id.imgAlbumArt);
            tvTrackTitle = itemView.findViewById(R.id.tvTrackTitle);
            tvArtistName = itemView.findViewById(R.id.tvArtistName);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
            btnOptions = itemView.findViewById(R.id.btnOptions);
            btnPlay = itemView.findViewById(R.id.btnPlay);
        }
    }
}
