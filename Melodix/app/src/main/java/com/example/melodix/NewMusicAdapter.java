package com.example.melodix;

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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NewMusicAdapter extends RecyclerView.Adapter<NewMusicAdapter.ViewHolder> {

    private List<Track> tracks;
    private List<Track> filteredTracks;
    private OnItemClickListener listener;
    private SimpleDateFormat inputDateFormat;
    private SimpleDateFormat outputDateFormat;
    private int mostRecentYear = 0; // Variable to store most recent year

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

        // Find the most recent year first
        findMostRecentYear();

        // Filter tracks for most recent year only
        filterTracksForMostRecentYear();

        // Sort tracks by release date (newest first)
        sortTracksByReleaseDate();

        notifyDataSetChanged();
    }

    private void findMostRecentYear() {
        mostRecentYear = 0; // Reset

        // Create a map to count tracks per year
        Map<Integer, Integer> yearCount = new HashMap<>();

        for (Track track : tracks) {
            if (track.getAlbum() != null && track.getAlbum().getReleaseDate() != null) {
                try {
                    Date releaseDate = inputDateFormat.parse(track.getAlbum().getReleaseDate());
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(releaseDate);
                    int releaseYear = calendar.get(Calendar.YEAR);

                    // Count tracks per year
                    yearCount.put(releaseYear, yearCount.getOrDefault(releaseYear, 0) + 1);

                    // Update most recent year
                    if (releaseYear > mostRecentYear) {
                        mostRecentYear = releaseYear;
                    }
                } catch (ParseException e) {
                    // Ignore tracks with unparseable dates
                }
            }
        }
    }

    private void filterTracksForMostRecentYear() {
        filteredTracks.clear();

        // If we couldn't determine a most recent year, show all tracks
        if (mostRecentYear == 0) {
            filteredTracks.addAll(tracks);
            return;
        }

        for (Track track : tracks) {
            if (track.getAlbum() != null && track.getAlbum().getReleaseDate() != null) {
                try {
                    Date releaseDate = inputDateFormat.parse(track.getAlbum().getReleaseDate());
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(releaseDate);
                    int releaseYear = calendar.get(Calendar.YEAR);

                    if (releaseYear == mostRecentYear) {
                        filteredTracks.add(track);
                    }
                } catch (ParseException e) {
                    // Skip tracks with unparseable dates
                }
            }
        }

        // If no tracks from most recent year found, use all tracks
        if (filteredTracks.isEmpty() && !tracks.isEmpty()) {
            filteredTracks.addAll(tracks);
        }
    }

    private void sortTracksByReleaseDate() {
        Collections.sort(filteredTracks, new Comparator<Track>() {
            @Override
            public int compare(Track track1, Track track2) {
                String date1 = track1.getAlbum() != null ? track1.getAlbum().getReleaseDate() : null;
                String date2 = track2.getAlbum() != null ? track2.getAlbum().getReleaseDate() : null;

                // Handle null cases
                if (date1 == null && date2 == null) return 0;
                if (date1 == null) return 1;
                if (date2 == null) return -1;

                try {
                    // Parse dates and compare them (newest first)
                    Date releaseDate1 = inputDateFormat.parse(date1);
                    Date releaseDate2 = inputDateFormat.parse(date2);
                    return releaseDate2.compareTo(releaseDate1);
                } catch (ParseException e) {
                    // If parsing fails, compare strings
                    return date2.compareTo(date1);
                }
            }
        });
    }

    /**
     * Get all currently filtered tracks for creating a playlist
     * @return List of filtered tracks
     */
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

        // Show loading state for text content
        public void showTextLoading(boolean isLoading) {
            // We'll leave this empty for now as we removed the progress bars
        }

        // Show loading state for image content
        public void showImageLoading(boolean isLoading) {
            // We'll leave this empty for now as we removed the progress bars
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

        // Set text data
        holder.songName.setText(track.getTitle());

        if (track.getArtist() != null) {
            holder.artistName.setText(track.getArtist().getName());
        }

        // Display release date
        if (track.getAlbum() != null && track.getAlbum().getReleaseDate() != null) {
            try {
                Date date = inputDateFormat.parse(track.getAlbum().getReleaseDate());
                String formattedDate = "Released: " + outputDateFormat.format(date);
                holder.releaseDate.setText(formattedDate);
            } catch (ParseException e) {
                // Fallback if date parsing fails
                holder.releaseDate.setText("Released: " + track.getAlbum().getReleaseDate());
            }
        } else {
            holder.releaseDate.setText("Release date not available");
        }

        // Load album art using Glide
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
