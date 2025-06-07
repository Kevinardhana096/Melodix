package com.example.melodix.listener;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.melodix.database.DownloadedAlbum;
import com.example.melodix.database.DownloadedArtist;
import com.example.melodix.database.DownloadedMusicDbHelper;
import com.example.melodix.database.DownloadedTrack;
import com.example.melodix.fragment.DownloadedMusicFragment;
import com.example.melodix.model.Album;
import com.example.melodix.model.Artist;
import com.example.melodix.model.Track;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;

public class UserPreferencesManager {
    private static final String TAG = "UserPrefsManager";
    private static final String PREF_NAME_PREFIX = "MelodixPrefs_";
    private static final String PREF_ANONYMOUS = "MelodixPrefs_anonymous";
    private static final String KEY_CURRENT_TRACK = "currentTrack";
    private static final String KEY_PLAYBACK_POSITION = "playbackPosition";
    private static final String KEY_PLAYBACK_QUEUE = "playbackQueue";
    private static final String KEY_PROFILE_IMAGE_URL = "profileImageUrl";
    private static final String USERS_COLLECTION = "users";
    private static final String FAVORITES_FIELD = "favorites";
    private static final String RECENT_TRACKS_FIELD = "recentTracks";
    private static final String PROFILE_IMAGE_FIELD = "profileImageUrl";
    private static final String DISPLAY_NAME_FIELD = "displayName";
    private static final String EMAIL_FIELD = "email";
    private static final String CREATED_AT_FIELD = "createdAt";
    private static final String UPDATED_AT_FIELD = "updatedAt";

    private static FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    // User Profile Management Methods
    public static void getUserProfileAsync(Context context, DataCallback<Map<String, Object>> callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not logged in");
            return;
        }

        String userId = currentUser.getUid();
        DocumentReference userDoc = db.collection(USERS_COLLECTION).document(userId);

        userDoc.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    Map<String, Object> userData = document.getData();
                    // Cache profile image URL locally
                    if (userData != null && userData.containsKey(PROFILE_IMAGE_FIELD)) {
                        saveProfileImageUrlLocal(context, (String) userData.get(PROFILE_IMAGE_FIELD));
                    }
                    callback.onSuccess(userData);
                    Log.d(TAG, "User profile loaded from Firestore");
                } else {
                    // Create user document if it doesn't exist
                    createUserDocument(currentUser, callback);
                }
            } else {
                Log.e(TAG, "Error getting user profile from Firestore", task.getException());
                callback.onError("Failed to load user profile: " + task.getException().getMessage());
            }
        });
    }

    public static void saveUserProfileAsync(Context context, Map<String, Object> profileData, DataCallback<Boolean> callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not logged in");
            return;
        }

        String userId = currentUser.getUid();
        DocumentReference userDoc = db.collection(USERS_COLLECTION).document(userId);

        // Add timestamp
        profileData.put(UPDATED_AT_FIELD, FieldValue.serverTimestamp());

        userDoc.set(profileData, SetOptions.merge()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "User profile saved to Firestore successfully");

                // Cache profile image URL locally if provided
                if (profileData.containsKey(PROFILE_IMAGE_FIELD)) {
                    saveProfileImageUrlLocal(context, (String) profileData.get(PROFILE_IMAGE_FIELD));
                }

                callback.onSuccess(true);
            } else {
                Log.e(TAG, "Error saving user profile to Firestore", task.getException());
                callback.onError("Failed to save profile: " + task.getException().getMessage());
            }
        });
    }

    public static void updateProfileImageAsync(Context context, String imageUrl, DataCallback<Boolean> callback) {
        Log.d(TAG, "=== updateProfileImageAsync called ===");
        Log.d(TAG, "Image URL: " + (imageUrl != null ? imageUrl.substring(0, Math.min(50, imageUrl.length())) + "..." : "null"));

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "❌ User not logged in");
            callback.onError("User not logged in");
            return;
        }

        String userId = currentUser.getUid();
        Log.d(TAG, "User ID: " + userId);

        DocumentReference userDoc = db.collection(USERS_COLLECTION).document(userId);

        Map<String, Object> updates = new HashMap<>();
        updates.put(PROFILE_IMAGE_FIELD, imageUrl);
        updates.put(UPDATED_AT_FIELD, FieldValue.serverTimestamp());

        Log.d(TAG, "Updating Firestore document...");
        userDoc.update(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "✅ Profile image URL updated in Firestore successfully");
                saveProfileImageUrlLocal(context, imageUrl);
                callback.onSuccess(true);
            } else {
                Log.w(TAG, "⚠️ Failed to update, trying to create document");
                Log.e(TAG, "Update error: " + task.getException().getMessage());

                // If document doesn't exist, create it
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("uid", user.getUid());
                    userData.put(EMAIL_FIELD, user.getEmail());
                    userData.put(DISPLAY_NAME_FIELD, user.getDisplayName());
                    userData.put(PROFILE_IMAGE_FIELD, imageUrl);
                    userData.put(CREATED_AT_FIELD, FieldValue.serverTimestamp());
                    userData.put(UPDATED_AT_FIELD, FieldValue.serverTimestamp());

                    Log.d(TAG, "Creating new user document with profile image...");
                    saveUserProfileAsync(context, userData, callback);
                } else {
                    Log.e(TAG, "❌ User is null, cannot create document");
                    callback.onError("Failed to update profile image");
                }
            }
        });
    }

    public static void getProfileImageUrlAsync(Context context, DataCallback<String> callback) {
        Log.d(TAG, "=== getProfileImageUrlAsync called ===");

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "⚠️ User not logged in, trying local cache");
            String localUrl = getProfileImageUrlLocal(context);
            Log.d(TAG, "Local URL: " + (localUrl != null ? "Found" : "Not found"));
            callback.onSuccess(localUrl);
            return;
        }

        String userId = currentUser.getUid();
        Log.d(TAG, "User ID: " + userId);

        DocumentReference userDoc = db.collection(USERS_COLLECTION).document(userId);

        Log.d(TAG, "Fetching profile image from Firestore...");
        userDoc.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists() && document.contains(PROFILE_IMAGE_FIELD)) {
                    String imageUrl = document.getString(PROFILE_IMAGE_FIELD);
                    Log.d(TAG, "✅ Profile image found in Firestore: " + (imageUrl != null ? "Yes" : "No"));
                    saveProfileImageUrlLocal(context, imageUrl);
                    callback.onSuccess(imageUrl);
                } else {
                    Log.w(TAG, "⚠️ Document doesn't exist or no profile image field");
                    // Fallback to local cache or Firebase Auth
                    String localUrl = getProfileImageUrlLocal(context);
                    if (localUrl == null && currentUser.getPhotoUrl() != null) {
                        localUrl = currentUser.getPhotoUrl().toString();
                        Log.d(TAG, "Using Firebase Auth photo URL");
                    } else {
                        Log.d(TAG, "Using local cache: " + (localUrl != null ? "Found" : "Not found"));
                    }
                    callback.onSuccess(localUrl);
                }
            } else {
                Log.e(TAG, "❌ Error getting profile image from Firestore", task.getException());
                String localUrl = getProfileImageUrlLocal(context);
                Log.d(TAG, "Fallback to local cache: " + (localUrl != null ? "Found" : "Not found"));
                callback.onSuccess(localUrl);
            }
        });
    }

    private static void createUserDocument(FirebaseUser user, DataCallback<Map<String, Object>> callback) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", user.getUid());
        userData.put(EMAIL_FIELD, user.getEmail());
        userData.put(DISPLAY_NAME_FIELD, user.getDisplayName());
        userData.put(PROFILE_IMAGE_FIELD, user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
        userData.put(CREATED_AT_FIELD, FieldValue.serverTimestamp());
        userData.put(UPDATED_AT_FIELD, FieldValue.serverTimestamp());

        db.collection(USERS_COLLECTION).document(user.getUid())
                .set(userData)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User document created successfully");
                        callback.onSuccess(userData);
                    } else {
                        Log.e(TAG, "Failed to create user document", task.getException());
                        callback.onError("Failed to create user profile");
                    }
                });
    }

    private static void saveProfileImageUrlLocal(Context context, String imageUrl) {
        Log.d(TAG, "💾 Saving profile image URL locally: " + (imageUrl != null ? "Yes" : "No"));
        SharedPreferences prefs = getUserPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_PROFILE_IMAGE_URL, imageUrl);
        editor.apply();
        Log.d(TAG, "✅ Profile image URL saved to local cache");
    }

    private static String getProfileImageUrlLocal(Context context) {
        SharedPreferences prefs = getUserPreferences(context);
        String url = prefs.getString(KEY_PROFILE_IMAGE_URL, null);
        Log.d(TAG, "📱 Getting local profile image URL: " + (url != null ? "Found" : "Not found"));
        return url;
    }

    private static Gson getGson() {
        JsonSerializer<Track> trackSerializer = new JsonSerializer<Track>() {
            @Override
            public JsonElement serialize(Track track, Type typeOfSrc, JsonSerializationContext context) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("_class", track.getClass().getName());
                jsonObject.addProperty("id", track.getId());
                jsonObject.addProperty("title", track.getTitle());
                jsonObject.addProperty("duration", track.getDuration());
                jsonObject.addProperty("previewUrl", track.getPreviewUrl());
                jsonObject.addProperty("isDownloaded", track.isDownloaded());
                if (track.getArtist() != null) {
                    jsonObject.add("artist", context.serialize(track.getArtist()));
                }
                if (track.getAlbum() != null) {
                    jsonObject.add("album", context.serialize(track.getAlbum()));
                }
                return jsonObject;
            }
        };
        JsonDeserializer<Track> trackDeserializer = new JsonDeserializer<Track>() {
            @Override
            public Track deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                JsonObject jsonObject = json.getAsJsonObject();
                Track track;
                if (jsonObject.has("_class")) {
                    String className = jsonObject.get("_class").getAsString();
                    if (className.equals(DownloadedTrack.class.getName())) {
                        track = new DownloadedTrack();
                    } else {
                        track = new Track();
                    }
                } else {
                    track = new Track();
                }
                if (jsonObject.has("id")) track.setId(jsonObject.get("id").getAsLong());
                if (jsonObject.has("title")) track.setTitle(jsonObject.get("title").getAsString());
                if (jsonObject.has("duration")) track.setDuration(jsonObject.get("duration").getAsInt());
                if (jsonObject.has("previewUrl")) track.setPreviewUrl(jsonObject.get("previewUrl").getAsString());

                if (jsonObject.has("artist")) {
                    Artist artist = context.deserialize(jsonObject.get("artist"), Artist.class);
                    track.setArtist(artist);
                }

                if (jsonObject.has("album")) {
                    Album album = context.deserialize(jsonObject.get("album"), Album.class);
                    track.setAlbum(album);
                }
                return track;
            }
        };
        return new GsonBuilder()
                .registerTypeAdapter(Track.class, trackSerializer)
                .registerTypeAdapter(Track.class, trackDeserializer)
                .create();
    }

    public static SharedPreferences getUserPreferences(Context context) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String prefName;
        if (currentUser != null) {
            prefName = PREF_NAME_PREFIX + currentUser.getUid();
        } else {
            prefName = PREF_ANONYMOUS;
        }
        return context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
    }

    // Rest of your existing methods remain unchanged...
    public static void getFavoriteTracksAsync(Context context, DataCallback<List<Track>> callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            List<Track> localFavorites = getFavoriteTracksLocal(context);
            callback.onSuccess(localFavorites);
            return;
        }
        String userId = currentUser.getUid();
        DocumentReference userDoc = db.collection(USERS_COLLECTION).document(userId);
        userDoc.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists() && document.contains(FAVORITES_FIELD)) {
                    try {
                        List<Map<String, Object>> favoritesData = (List<Map<String, Object>>) document.get(FAVORITES_FIELD);
                        List<Track> favorites = convertMapListToTracks(favoritesData);
                        saveFavoriteTracksLocal(context, favorites);
                        callback.onSuccess(favorites);
                        Log.d(TAG, "Loaded " + favorites.size() + " favorites from Firestore");
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing favorites from Firestore", e);
                        List<Track> localFavorites = getFavoriteTracksLocal(context);
                        callback.onSuccess(localFavorites);
                    }
                } else {
                    List<Track> localFavorites = getFavoriteTracksLocal(context);
                    if (!localFavorites.isEmpty()) {
                        saveFavoriteTracksAsync(context, localFavorites, new DataCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean success) {
                                Log.d(TAG, "Migrated local favorites to Firestore");
                            }
                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Failed to migrate favorites: " + error);
                            }
                        });
                    }
                    callback.onSuccess(localFavorites);
                }
            } else {
                Log.e(TAG, "Error getting favorites from Firestore", task.getException());
                List<Track> localFavorites = getFavoriteTracksLocal(context);
                callback.onSuccess(localFavorites);
            }
        });
    }

    // Continue with all your existing methods...
    // (I'm keeping the rest of the code the same to maintain functionality)

    public static void saveFavoriteTracksAsync(Context context, List<Track> favorites, DataCallback<Boolean> callback) {
        saveFavoriteTracksLocal(context, favorites);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "User not logged in, favorites saved locally only");
            if (callback != null) callback.onSuccess(true);
            return;
        }
        String userId = currentUser.getUid();
        DocumentReference userDoc = db.collection(USERS_COLLECTION).document(userId);
        List<Map<String, Object>> favoritesData = convertTracksToMapList(favorites);

        Map<String, Object> data = new HashMap<>();
        data.put(FAVORITES_FIELD, favoritesData);

        userDoc.set(data, SetOptions.merge()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Favorites saved to Firestore successfully");
                if (callback != null) callback.onSuccess(true);
            } else {
                Log.e(TAG, "Error saving favorites to Firestore", task.getException());
                if (callback != null) callback.onError(task.getException().getMessage());
            }
        });
    }

    public static void getRecentTracksAsync(Context context, DataCallback<List<Track>> callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            List<Track> localRecent = getRecentTracksLocal(context);
            callback.onSuccess(localRecent);
            return;
        }

        String userId = currentUser.getUid();
        DocumentReference userDoc = db.collection(USERS_COLLECTION).document(userId);

        userDoc.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists() && document.contains(RECENT_TRACKS_FIELD)) {
                    try {
                        List<Map<String, Object>> recentData = (List<Map<String, Object>>) document.get(RECENT_TRACKS_FIELD);
                        List<Track> recentTracks = convertMapListToTracks(recentData);
                        saveRecentTracksLocal(context, recentTracks);
                        callback.onSuccess(recentTracks);
                        Log.d(TAG, "Loaded " + recentTracks.size() + " recent tracks from Firestore");
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing recent tracks from Firestore", e);
                        List<Track> localRecent = getRecentTracksLocal(context);
                        callback.onSuccess(localRecent);
                    }
                } else {
                    List<Track> localRecent = getRecentTracksLocal(context);
                    callback.onSuccess(localRecent);
                }
            } else {
                Log.e(TAG, "Error getting recent tracks from Firestore", task.getException());
                List<Track> localRecent = getRecentTracksLocal(context);
                callback.onSuccess(localRecent);
            }
        });
    }

    public static void saveRecentTracksAsync(Context context, List<Track> recentTracks, DataCallback<Boolean> callback) {
        saveRecentTracksLocal(context, recentTracks);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "User not logged in, recent tracks saved locally only");
            if (callback != null) callback.onSuccess(true);
            return;
        }
        String userId = currentUser.getUid();
        DocumentReference userDoc = db.collection(USERS_COLLECTION).document(userId);
        List<Map<String, Object>> recentData = convertTracksToMapList(recentTracks);

        Map<String, Object> data = new HashMap<>();
        data.put(RECENT_TRACKS_FIELD, recentData);

        userDoc.set(data, SetOptions.merge()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Recent tracks saved to Firestore successfully");
                if (callback != null) callback.onSuccess(true);
            } else {
                Log.e(TAG, "Error saving recent tracks to Firestore", task.getException());
                if (callback != null) callback.onError(task.getException().getMessage());
            }
        });
    }

    public static List<Track> getFavoriteTracks(Context context) {
        return getFavoriteTracksLocal(context);
    }

    public static List<Track> getRecentTracks(Context context) {
        return getRecentTracksLocal(context);
    }

    public static void saveRecentTracks(Context context, List<Track> tracks) {
        saveRecentTracksLocal(context, tracks);
    }

    private static List<Track> getFavoriteTracksLocal(Context context) {
        SharedPreferences prefs = getUserPreferences(context);
        String json = prefs.getString("favorites", null);

        if (json == null) {
            return new ArrayList<>();
        }

        try {
            Gson gson = getGson();
            Type type = new TypeToken<ArrayList<Track>>() {}.getType();
            List<Track> tracks = gson.fromJson(json, type);
            return tracks != null ? tracks : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Error deserializing favorite tracks: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private static void saveFavoriteTracksLocal(Context context, List<Track> tracks) {
        SharedPreferences prefs = getUserPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        try {
            Gson gson = getGson();
            String json = gson.toJson(tracks);
            editor.putString("favorites", json);
            editor.apply();
            Log.d(TAG, "Saved " + tracks.size() + " favorites locally");
        } catch (Exception e) {
            Log.e(TAG, "Error serializing favorite tracks: " + e.getMessage(), e);
        }
    }

    private static List<Track> getRecentTracksLocal(Context context) {
        SharedPreferences prefs = getUserPreferences(context);
        String json = prefs.getString("recentTracks", null);

        if (json == null) {
            return new ArrayList<>();
        }

        try {
            Gson gson = getGson();
            Type type = new TypeToken<ArrayList<Track>>() {}.getType();
            List<Track> tracks = gson.fromJson(json, type);
            return tracks != null ? tracks : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Error deserializing recent tracks: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private static void saveRecentTracksLocal(Context context, List<Track> tracks) {
        SharedPreferences prefs = getUserPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        try {
            Gson gson = getGson();
            String json = gson.toJson(tracks);
            editor.putString("recentTracks", json);
            editor.apply();
            Log.d(TAG, "Saved " + tracks.size() + " recent tracks locally");
        } catch (Exception e) {
            Log.e(TAG, "Error serializing recent tracks: " + e.getMessage(), e);
        }
    }

    public static void getDownloadHistoryAsync(Context context, DataCallback<List<Track>> callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            // Fallback to local downloads only
            List<Track> localDownloads = getLocalDownloads(context);
            callback.onSuccess(localDownloads);
            return;
        }

        String userId = currentUser.getUid();
        db.collection(USERS_COLLECTION)
                .document(userId)
                .collection("downloadHistory")
                .orderBy("downloadedAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Track> downloadHistory = new ArrayList<>();

                        for (DocumentSnapshot doc : task.getResult()) {
                            Track track = convertDocumentToTrack(doc);
                            if (track != null) {
                                boolean isLocal = isTrackAvailableLocally(context, track);
                                setTrackDownloadedStatus(track, isLocal);

                                downloadHistory.add(track);
                            }
                        }
                        callback.onSuccess(downloadHistory);
                        Log.d(TAG, "Loaded " + downloadHistory.size() + " download history items");
                    } else {
                        Log.e(TAG, "Error getting download history", task.getException());
                        // Fallback to local downloads
                        List<Track> localDownloads = getLocalDownloads(context);
                        callback.onSuccess(localDownloads);
                    }
                });
    }

    public static void addToRecentTracksAsync(Context context, Track track, DataCallback<Boolean> callback) {
        if (track == null) {
            if (callback != null) callback.onError("Track is null");
            return;
        }
        getRecentTracksAsync(context, new DataCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> recentTracks) {
                recentTracks.removeIf(t -> t != null && t.getId() == track.getId());
                recentTracks.add(0, track);
                if (recentTracks.size() > 10) {
                    recentTracks = new ArrayList<>(recentTracks.subList(0, 10));
                }
                saveRecentTracksAsync(context, recentTracks, callback);
                Log.d(TAG, "Added track to recent: " + track.getTitle());
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error getting recent tracks for add: " + error);
                try {
                    List<Track> localRecent = getRecentTracksLocal(context);
                    localRecent.removeIf(t -> t != null && t.getId() == track.getId());
                    localRecent.add(0, track);

                    if (localRecent.size() > 10) {
                        localRecent = new ArrayList<>(localRecent.subList(0, 10));
                    }

                    saveRecentTracksLocal(context, localRecent);
                    if (callback != null) callback.onSuccess(true);
                } catch (Exception e) {
                    if (callback != null) callback.onError("Failed to add to recent tracks: " + e.getMessage());
                }
            }
        });
    }

    private static void setTrackDownloadedStatus(Track track, boolean isDownloaded) {
        try {
            if (track instanceof DownloadedTrack) {
                Field downloadedField = DownloadedTrack.class.getDeclaredField("isDownloaded");
                downloadedField.setAccessible(true);
                downloadedField.set(track, isDownloaded);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not set downloaded status for track: " + track.getTitle(), e);
        }
    }

    public static void addToDownloadHistoryAsync(Context context, Track track, String localFilePath, DataCallback<Boolean> callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            if (callback != null) callback.onError("User not logged in");
            return;
        }

        String userId = currentUser.getUid();

        Map<String, Object> downloadData = new HashMap<>();
        downloadData.put("trackId", track.getId());
        downloadData.put("title", track.getTitle());
        downloadData.put("artist", track.getArtist() != null ? track.getArtist().getName() : "Unknown");
        downloadData.put("albumArt", track.getAlbum() != null ? track.getAlbum().getCoverMedium() : null);
        downloadData.put("previewUrl", track.getPreviewUrl());
        downloadData.put("duration", track.getDuration());
        downloadData.put("downloadedAt", FieldValue.serverTimestamp());
        downloadData.put("localFilePath", localFilePath);

        if (localFilePath != null) {
            File file = new File(localFilePath);
            if (file.exists()) {
                downloadData.put("fileSize", file.length());
            }
        }

        db.collection(USERS_COLLECTION)
                .document(userId)
                .collection("downloadHistory")
                .document(String.valueOf(track.getId()))
                .set(downloadData, SetOptions.merge())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Download history saved for: " + track.getTitle());
                        if (callback != null) callback.onSuccess(true);
                    } else {
                        Log.e(TAG, "Error saving download history", task.getException());
                        if (callback != null) callback.onError(task.getException().getMessage());
                    }
                });
    }

    public static void removeFromDownloadHistoryAsync(Context context, Track track, DataCallback<Boolean> callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            if (callback != null) callback.onError("User not logged in");
            return;
        }

        String userId = currentUser.getUid();

        db.collection(USERS_COLLECTION)
                .document(userId)
                .collection("downloadHistory")
                .document(String.valueOf(track.getId()))
                .delete()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Removed from download history: " + track.getTitle());
                        if (callback != null) callback.onSuccess(true);
                    } else {
                        Log.e(TAG, "Error removing from download history", task.getException());
                        if (callback != null) callback.onError(task.getException().getMessage());
                    }
                });
    }

    private static List<Track> getLocalDownloads(Context context) {
        DownloadedMusicDbHelper dbHelper = new DownloadedMusicDbHelper(context);
        return DownloadedMusicFragment.getDownloadedTracksFromDb(dbHelper);
    }

    private static boolean isTrackAvailableLocally(Context context, Track track) {
        List<Track> localTracks = getLocalDownloads(context);
        return localTracks.stream().anyMatch(local -> local.getId() == track.getId());
    }

    private static Track convertDocumentToTrack(DocumentSnapshot doc) {
        try {
            boolean isLocallyAvailable = false;
            DownloadedTrack track = new DownloadedTrack() {
                private boolean downloaded = false;

                @Override
                public boolean isDownloaded() {
                    return downloaded;
                }

                public void setDownloadedStatus(boolean status) {
                    this.downloaded = status;
                }
            };

            track.setId(doc.getLong("trackId"));
            track.setTitle(doc.getString("title"));
            track.setDuration(doc.getLong("duration").intValue());
            track.setPreviewUrl(doc.getString("previewUrl"));

            String artistName = doc.getString("artist");
            if (artistName != null) {
                DownloadedArtist artist = new DownloadedArtist();
                artist.setName(artistName);
                track.setArtist(artist);
            }

            String albumArt = doc.getString("albumArt");
            if (albumArt != null) {
                DownloadedAlbum album = new DownloadedAlbum();
                album.setCoverMedium(albumArt);
                track.setAlbum(album);
            }

            return track;
        } catch (Exception e) {
            Log.e(TAG, "Error converting document to track", e);
            return null;
        }
    }

    private static List<Map<String, Object>> convertTracksToMapList(List<Track> tracks) {
        List<Map<String, Object>> mapList = new ArrayList<>();
        Gson gson = getGson();

        for (Track track : tracks) {
            try {
                String json = gson.toJson(track);
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> trackMap = gson.fromJson(json, type);
                mapList.add(trackMap);
            } catch (Exception e) {
                Log.e(TAG, "Error converting track to map: " + track.getTitle(), e);
            }
        }
        return mapList;
    }

    private static List<Track> convertMapListToTracks(List<Map<String, Object>> mapList) {
        List<Track> tracks = new ArrayList<>();
        Gson gson = getGson();

        if (mapList == null) {
            return tracks;
        }

        for (Map<String, Object> trackMap : mapList) {
            try {

                String json = gson.toJson(trackMap);
                Track track = gson.fromJson(json, Track.class);
                if (track != null) {
                    tracks.add(track);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error converting map to track", e);
            }
        }

        return tracks;
    }

    public static void saveCurrentTrack(Context context, Track track) {
        if (track == null) return;

        SharedPreferences prefs = getUserPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        try {
            Gson gson = getGson();
            String json = gson.toJson(track);
            editor.putString(KEY_CURRENT_TRACK, json);
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving current track: " + e.getMessage(), e);
        }
    }

    public static void savePlaybackPosition(Context context, int position) {
        SharedPreferences prefs = getUserPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_PLAYBACK_POSITION, position);
        editor.apply();
    }

    public static void savePlaybackQueue(Context context, List<Track> queue) {
        if (queue == null) return;

        SharedPreferences prefs = getUserPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        try {
            Gson gson = getGson();
            String json = gson.toJson(queue);
            editor.putString(KEY_PLAYBACK_QUEUE, json);
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving playback queue: " + e.getMessage(), e);
        }
    }

    public static Track getCurrentTrack(Context context) {
        SharedPreferences prefs = getUserPreferences(context);
        String json = prefs.getString(KEY_CURRENT_TRACK, null);
        if (json == null) {
            return null;
        }
        try {
            Gson gson = getGson();
            return gson.fromJson(json, Track.class);
        } catch (Exception e) {
            Log.e(TAG, "Error loading current track: " + e.getMessage(), e);
            return null;
        }
    }

    public static List<Track> getPlaybackQueue(Context context) {
        SharedPreferences prefs = getUserPreferences(context);
        String json = prefs.getString(KEY_PLAYBACK_QUEUE, null);
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            Gson gson = getGson();
            Type type = new TypeToken<ArrayList<Track>>() {}.getType();
            return gson.fromJson(json, type);
        } catch (Exception e) {
            Log.e(TAG, "Error loading playback queue: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public static void clearPlaybackState(Context context) {
        if (context == null) return;

        SharedPreferences prefs = getUserPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_CURRENT_TRACK);
        editor.remove(KEY_PLAYBACK_POSITION);
        editor.remove(KEY_PLAYBACK_QUEUE);
        editor.apply();
    }

    public static void clearUserPreferences(Context context) {
        if (context == null) return;

        SharedPreferences prefs = getUserPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
    }
}