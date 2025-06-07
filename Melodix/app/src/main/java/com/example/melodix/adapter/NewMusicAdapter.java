package com.example.melodix.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NewMusicAdapter extends RecyclerView.Adapter<NewMusicAdapter.ViewHolder> {
    private List<Track> tracks;
    private List<Track> filteredTracks;
    private OnItemClickListener listener;
    private SimpleDateFormat inputDateFormat;
    private SimpleDateFormat outputDateFormat;
    public interface OnItemClickListener {
        void onItemClick(Track track);
    }
    public NewMusicAdapter(OnItemClickListener listener) {
        this.tracks = new ArrayList<>();
        this.filteredTracks = new ArrayList<>();
        this.listener = listener;
        this.inputDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        this.outputDateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    }
    public void updateData(List<Track> newTracks) {
        this.tracks.clear();
        this.tracks.addAll(newTracks);
        filteredTracks.clear();
        int currentYear = 2025;
        for (Track track : tracks) {
            if (track.getAlbum() != null && track.getAlbum().getReleaseDate() != null) {
                try {
                    Date date = inputDateFormat.parse(track.getAlbum().getReleaseDate());
                    java.util.Calendar calendar = java.util.Calendar.getInstance();
                    calendar.setTime(date);
                    int trackYear = calendar.get(java.util.Calendar.YEAR);

                    if (trackYear == currentYear) {
                        filteredTracks.add(track);
                    }
                } catch (ParseException e) {

                }
            }
        }
        android.util.Log.d("NewMusicAdapter", "Found " + filteredTracks.size() +
                           " tracks from " + currentYear + " out of " + tracks.size() + " total tracks");
        sortTracksByPopularity();
        notifyDataSetChanged();
    }
    public void appendData(List<Track> newTracks) {
        if (newTracks != null && !newTracks.isEmpty()) {
            this.tracks.addAll(newTracks);
            int currentYear = 2025;
            List<Track> filtered2025Tracks = new ArrayList<>();
            for (Track track : newTracks) {
                if (track.getAlbum() != null && track.getAlbum().getReleaseDate() != null) {
                    try {
                        Date date = inputDateFormat.parse(track.getAlbum().getReleaseDate());
                        java.util.Calendar calendar = java.util.Calendar.getInstance();
                        calendar.setTime(date);
                        int trackYear = calendar.get(java.util.Calendar.YEAR);
                        if (trackYear == currentYear) {
                            filtered2025Tracks.add(track);
                        }
                    } catch (ParseException e) {
                    }
                }
            }
            int startPosition = filteredTracks.size();
            if (!filtered2025Tracks.isEmpty()) {
                this.filteredTracks.addAll(filtered2025Tracks);
                sortTracksByPopularity();
                notifyItemRangeInserted(startPosition, filtered2025Tracks.size());
                android.util.Log.d("NewMusicAdapter", "Added " + filtered2025Tracks.size() +
                                  " new tracks from 2025 (from " + newTracks.size() + " total new tracks)");
            } else {
                android.util.Log.d("NewMusicAdapter", "No new 2025 tracks to add from the " + newTracks.size() + " tracks received");
            }
        }
    }
    private void sortTracksByPopularity() {
        Collections.sort(filteredTracks, new Comparator<Track>() {
            @Override
            public int compare(Track track1, Track track2) {
                if (track1.getAlbum() != null && track2.getAlbum() != null &&
                    track1.getAlbum().getReleaseDate() != null && track2.getAlbum().getReleaseDate() != null) {
                    try {
                        Date date1 = inputDateFormat.parse(track1.getAlbum().getReleaseDate());
                        Date date2 = inputDateFormat.parse(track2.getAlbum().getReleaseDate());
                        return date2.compareTo(date1);
                    } catch (ParseException e) {
                        return track1.getTitle().compareTo(track2.getTitle());
                    }
                } else if (track1.getAlbum() != null && track1.getAlbum().getReleaseDate() != null) {
                    return -1;
                } else if (track2.getAlbum() != null && track2.getAlbum().getReleaseDate() != null) {
                    return 1;
                } else {
                    return track1.getTitle().compareTo(track2.getTitle());
                }
            }
        });
    }
    public List<Track> getAllFilteredTracks() {
        return new ArrayList<>(filteredTracks);
    }
    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView albumArt;
        TextView songName;
        TextView artistName;
        TextView releaseDate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            albumArt = itemView.findViewById(R.id.img_album);
            songName = itemView.findViewById(R.id.txt_song_title);
            artistName = itemView.findViewById(R.id.txt_artist);
            releaseDate = itemView.findViewById(R.id.txt_release_date);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        listener.onItemClick(filteredTracks.get(position));
                    }
                }
            });
        }
    }
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_new_music, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Track track = filteredTracks.get(position);
        holder.songName.setText(track.getTitle());
        if (track.getArtist() != null) {
            holder.artistName.setText(track.getArtist().getName());
        }
        if (track.getAlbum() != null && track.getAlbum().getReleaseDate() != null) {
            try {
                Date date = inputDateFormat.parse(track.getAlbum().getReleaseDate());
                String formattedDate = "Released: " + outputDateFormat.format(date);
                holder.releaseDate.setText(formattedDate);
            } catch (ParseException e) {
                holder.releaseDate.setText("Released: " + track.getAlbum().getReleaseDate());
            }
        } else {
            holder.releaseDate.setText("Release date not available");
        }
        if (track.getAlbum() != null && track.getAlbum().getCoverMedium() != null) {
            Glide.with(holder.itemView.getContext())
                    .load(track.getAlbum().getCoverMedium())
                    .placeholder(R.drawable.ic_launcher_background)
                    .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            return false;
                        }
                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(holder.albumArt);
        } else {
            holder.albumArt.setImageResource(R.drawable.ic_launcher_background);
        }
    }

    @Override
    public int getItemCount() {
        return filteredTracks.size();
    }
}
