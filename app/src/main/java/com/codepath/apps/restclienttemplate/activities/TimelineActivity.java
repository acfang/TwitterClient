package com.codepath.apps.restclienttemplate.activities;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.codepath.apps.restclienttemplate.EndlessRecyclerViewScrollListener;
import com.codepath.apps.restclienttemplate.R;
import com.codepath.apps.restclienttemplate.TwitterApp;
import com.codepath.apps.restclienttemplate.TwitterClient;
import com.codepath.apps.restclienttemplate.adapters.TweetsAdapter;
import com.codepath.apps.restclienttemplate.databinding.ActivityTimelineBinding;
import com.codepath.apps.restclienttemplate.models.Tweet;
import com.codepath.apps.restclienttemplate.models.TweetDao;
import com.codepath.apps.restclienttemplate.models.TweetWithUser;
import com.codepath.apps.restclienttemplate.models.User;
import com.codepath.asynchttpclient.callback.JsonHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.parceler.Parcels;

import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Headers;

public class TimelineActivity extends AppCompatActivity {

    public static final String KEY_USER_NAME = "user_name";
    public final int MAX_TWEET_LENGTH = 140;
    public final String TAG = "TimelineActivity";
    private final int REQUEST_CODE = 20;

    private TwitterClient client;
    private RecyclerView rvTweets;
    private List<Tweet> tweets;
    private TweetsAdapter adapter;
    private SwipeRefreshLayout swipeContainer;
    private EndlessRecyclerViewScrollListener scrollListener;
    private LinearLayoutManager layoutManager;
    private TweetDao tweetDao;
    private List<Tweet> tweetsFromNetwork;

    private MenuItem miActionProgressItem;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityTimelineBinding activityTimelineBinding =  ActivityTimelineBinding.inflate(getLayoutInflater());
        setContentView(activityTimelineBinding.getRoot());

        setUpToolbar(activityTimelineBinding);

        // Compose new tweet when reply icon is clicked
        TweetsAdapter.OnClickListener replyOnClickListener= new TweetsAdapter.OnClickListener() {
            @Override
            public void onClickListener(int position) {
                Intent intent = new Intent(TimelineActivity.this, ComposeActivity.class);
                intent.putExtra(KEY_USER_NAME, tweets.get(position).user.userName);
                startActivity(intent);
            }
        };

        // Like current tweet when like icon is clicked
        TweetsAdapter.OnClickListener likeOnClickListener= new TweetsAdapter.OnClickListener() {
            @Override
            public void onClickListener(int position) {
                final Long tweetId = tweets.get(position).id;
                client.likeTweet(new JsonHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Headers headers, JSON json) {
                        Toast.makeText(getApplicationContext(), "Liked!", Toast.LENGTH_SHORT).show();
                        Log.i(TAG, "onSuccess to like");
                    }

                    @Override
                    public void onFailure(int statusCode, Headers headers, String response, Throwable throwable) {
                        client.unlikeTweet(new JsonHttpResponseHandler() {
                            @Override
                            public void onSuccess(int statusCode, Headers headers, JSON json) {
                                Toast.makeText(getApplicationContext(), "Unliked!", Toast.LENGTH_SHORT).show();
                                Log.i(TAG, "onSuccess to unlike");
                            }

                            @Override
                            public void onFailure(int statusCode, Headers headers, String response, Throwable throwable) {
                                Log.i(TAG, "onFailure to unlike");
                            }
                        }, tweetId);
                    }
                }, tweetId);
            }
        };

        // Retweet or unretweet current tweet when retweet icon is clicked
        TweetsAdapter.OnClickListener retweetOnClickListener= new TweetsAdapter.OnClickListener() {
            @Override
            public void onClickListener(int position) {
                final Long tweetId = tweets.get(position).id;
                client.retweetTweet(new JsonHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Headers headers, JSON json) {
                        Toast.makeText(getApplicationContext(), "Retweeted!", Toast.LENGTH_SHORT).show();
                        Log.i(TAG, "onSuccess to retweet");
                    }

                    @Override
                    public void onFailure(int statusCode, Headers headers, String response, Throwable throwable) {
                        client.unRetweetTweet(new JsonHttpResponseHandler() {
                            @Override
                            public void onSuccess(int statusCode, Headers headers, JSON json) {
                                Toast.makeText(getApplicationContext(), "Unretweeted!", Toast.LENGTH_SHORT).show();
                                Log.i(TAG, "onSuccess to untweet");
                            }

                            @Override
                            public void onFailure(int statusCode, Headers headers, String response, Throwable throwable) {
                                Log.i(TAG, "onFailure to untweet");
                            }
                        }, tweetId);
                    }
                }, tweetId);
            }
        };

        tweetDao = ((TwitterApp) getApplicationContext()).getMyDatabase().tweetDao();
        client = TwitterApp.getRestClient(this);
        tweets = new ArrayList<>();
        adapter = new TweetsAdapter(this, tweets, replyOnClickListener, retweetOnClickListener, likeOnClickListener);
        layoutManager = new LinearLayoutManager(this);

        // Load more data as users scroll
        scrollListener = new EndlessRecyclerViewScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                Log.i(TAG, "onLoadMore: " + page);
                loadMoreData();
            }
        };
        rvTweets = activityTimelineBinding.rvTweets;
        rvTweets.addOnScrollListener(scrollListener);
        rvTweets.setLayoutManager(layoutManager);
        rvTweets.setAdapter(adapter);

        setUpSwipeContainer(activityTimelineBinding);

        showInformationFromDataBase();
        populateHomeTimeline();
    }

    private void setUpSwipeContainer(com.codepath.apps.restclienttemplate.databinding.ActivityTimelineBinding activityTimelineBinding) {
        swipeContainer = activityTimelineBinding.swipeContainer;
        swipeContainer.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.i(TAG, "Fetching new data");
                Toast.makeText(getApplicationContext(), "Timeline updated!", Toast.LENGTH_SHORT).show();
                populateHomeTimeline();
            }
        });
    }

    private void setUpToolbar(com.codepath.apps.restclienttemplate.databinding.ActivityTimelineBinding activityTimelineBinding) {
        Toolbar toolbar = activityTimelineBinding.toolbar;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }

    private void showInformationFromDataBase() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Showing information from DB");
                List<TweetWithUser> tweetWithUsers = tweetDao.recentItems();
                List<Tweet> tweetsFromDB = TweetWithUser.getTweetlist(tweetWithUsers);
                adapter.clear();
                adapter.addAll(tweetsFromDB);
            }
        });
    }

    private void loadMoreData() {
        client.getNextPageOfTweets(new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Headers headers, JSON json) {
                showProgressBar();
                Log.i(TAG, "Load more data onSuccess! " + json.toString());
                JSONArray jsonArray = json.jsonArray;
                try {
                    adapter.addAll(Tweet.fromJsonArray(jsonArray));
                    adapter.notifyDataSetChanged();
                } catch (JSONException e) {
                    Log.e(TAG, "Json exception", e);
                }
                hideProgressBar();
            }

            @Override
            public void onFailure(int statusCode, Headers headers, String response, Throwable throwable) {
                Log.i(TAG, "onFailure! " + response, throwable);

            }
        }, tweets.get(tweets.size() - 1).id);
    }

    private void populateHomeTimeline() {
        client.getHomeTimeline(new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Headers headers, JSON json) {
                Log.i(TAG, "onSuccess when populate HomeTimeline! " + json.toString());
                JSONArray jsonArray = json.jsonArray;
                try {
                    tweetsFromNetwork = Tweet.fromJsonArray(jsonArray);
                    adapter.clear();
                    adapter.addAll(tweetsFromNetwork);
                    adapter.notifyDataSetChanged();
                    swipeContainer.setRefreshing(false);
                    insertTweetsToDatabase();
                } catch (JSONException e) {
                    Log.e(TAG, "Json exception", e);
                }
            }

            @Override
            public void onFailure(int statusCode, Headers headers, String response, Throwable throwable) {
                Log.i(TAG, "onFailure! " + response, throwable);
            }
        });
    }

    private void insertTweetsToDatabase() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Saving information into DB");
                List<User> usersFromNetwork = User.fromJsonTweetArray(tweetsFromNetwork);
                tweetDao.insertModel(usersFromNetwork.toArray(new User[0]));
                tweetDao.insertModel(tweetsFromNetwork.toArray(new Tweet[0]));
            }
        });
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem compose = menu.findItem(R.id.miCompose);

        compose.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                Intent intent = new Intent(TimelineActivity.this, ComposeActivity.class);
                startActivityForResult(intent, REQUEST_CODE);
                return false;
            }
        });

        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        miActionProgressItem = menu.findItem(R.id.miActionProgress);
        return super.onPrepareOptionsMenu(menu);
    }

    public void showProgressBar() {
        miActionProgressItem.setVisible(true);
    }

    public void hideProgressBar() {
        miActionProgressItem.setVisible(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            Tweet tweet = Parcels.unwrap(data.getParcelableExtra("tweet"));
            tweets.add(0, tweet);
            adapter.notifyItemInserted(0);
            rvTweets.smoothScrollToPosition(0);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}