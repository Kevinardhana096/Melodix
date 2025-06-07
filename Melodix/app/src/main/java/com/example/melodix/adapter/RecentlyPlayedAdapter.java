package com.example.melodix.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.melodix.R;
import com.example.melodix.model.Track;

import java.util.ArrayList;
import java.util.List;

public class RecentlyPlayedAdapter extends RecyclerView.Adapter<RecentlyPlayedAdapter.ViewHolder> {
    private List<Track> tracks;
    private OnItemClickListener listener;
    public interface OnItemClickListener {
        void onItemClick(Track track);
    }
    public RecentlyPlayedAdapter(OnItemClickListener listener) {
        this.tracks = new ArrayList<>();
        this.listener = listener;
    }
    public void updateData(List<Track> newTracks) {
        this.tracks.clear();
        this.tracks.addAll(newTracks);
        notifyDataSetChanged();
    }
    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView albumArt;
        TextView songName;
        TextView artistName;
        ProgressBar progressBarImage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            albumArt = itemView.findViewById(R.id.img_album);
            songName = itemView.findViewById(R.id.txt_song_title);
            artistName = itemView.findViewById(R.id.txt_artist);
            progressBarImage = itemView.findViewById(R.id.progressBarImage);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(tracks.get(position));
                }
            });
        }
        public void showImageLoading(boolean isLoading) {
            progressBarImage.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            albumArt.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recently_played, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Track track = tracks.get(position);
        holder.showImageLoading(true);
        holder.songName.setText(track.getTitle());
        if (track.getArtist() != null) {
            holder.artistName.setText(track.getArtist().getName());
        }
        if (track.getAlbum() != null && track.getAlbum().getCoverMedium() != null) {
            Glide.with(holder.itemView.getContext())
                    .load(track.getAlbum().getCoverMedium())
                    .placeholder(R.drawable.ic_launcher_background)
                    .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            holder.showImageLoading(false);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            holder.showImageLoading(false);
                            return false;
                        }
                    })
                    .into(holder.albumArt);
        } else {
            holder.albumArt.setImageResource(R.drawable.ic_launcher_background);
            holder.showImageLoading(false);
        }
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }
}
