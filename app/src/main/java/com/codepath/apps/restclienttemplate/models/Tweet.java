package com.codepath.apps.restclienttemplate.models;

import android.text.format.DateUtils;
import android.util.Log;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.parceler.Parcel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Parcel
@Entity(foreignKeys = @ForeignKey(entity=User.class, parentColumns="id", childColumns="userId"))
public class Tweet {

    private static final String TAG = "Tweet Debug";

    @ColumnInfo
    @PrimaryKey
    public long id;

    @ColumnInfo
    public String body;

    @ColumnInfo
    public String createdAt;

    @Ignore
    public User user;

    @ColumnInfo
    public Long userId;

    @ColumnInfo
    public String imgUrl;

    @ColumnInfo
    public Long numRetweet;

    @ColumnInfo
    public Long numLike;

    @ColumnInfo
    public boolean liked;

    @ColumnInfo
    public boolean retweeded;

    // Empty constructor needed for parcel library
    public Tweet() {}

    // Extract a tweet's information from the given JsonObject
    public static Tweet fromJson(JSONObject jsonObject) throws JSONException {
        Tweet tweet = new Tweet();
        tweet.body = jsonObject.getString("text");
        tweet.createdAt = getRelativeTimeAgo(jsonObject.getString("created_at"));
        tweet.user = User.fromJson(jsonObject.getJSONObject("user"));
        tweet.id = jsonObject.getLong("id");
        tweet.numLike = jsonObject.getLong("favorite_count");
        tweet.numRetweet = jsonObject.getLong("retweet_count");
        tweet.userId = tweet.user.id;
        tweet.liked = false;
        tweet.retweeded = false;
        try {
            JSONArray medias = jsonObject.getJSONObject("entities").getJSONArray("media");
            for (int i = 0; i < medias.length(); ++i) {
                JSONObject media = medias.getJSONObject(0);
                if (media.getString("type").equalsIgnoreCase("photo")) {
                    tweet.imgUrl = media.getString("media_url_https");
                    break;
                } else {
                    tweet.imgUrl = null;
                }
            }
        } catch (JSONException e) {
            tweet.imgUrl = null;
            Log.d(TAG, "Doesn't have image");
        }
        return tweet;
    }

    // Convert a JsonArray to a list of tweets
    public static List<Tweet> fromJsonArray(JSONArray jsonArray) throws JSONException {
        List<Tweet> tweets = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); ++i) {
            tweets.add(fromJson(jsonArray.getJSONObject(i)));
        }
        return tweets;
    }

    public static String getRelativeTimeAgo(String rawJsonDate) {
        String twitterFormat = "EEE MMM dd HH:mm:ss ZZZZZ yyyy";
        SimpleDateFormat sf = new SimpleDateFormat(twitterFormat, Locale.ENGLISH);
        sf.setLenient(true);

        String relativeDate = "";
        try {
            long dateMillis = sf.parse(rawJsonDate).getTime();
            relativeDate = DateUtils.getRelativeTimeSpanString(dateMillis,
                    System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS).toString();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return relativeDate;
    }
}
