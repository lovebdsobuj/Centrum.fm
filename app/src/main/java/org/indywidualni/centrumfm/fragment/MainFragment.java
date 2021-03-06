package org.indywidualni.centrumfm.fragment;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import org.indywidualni.centrumfm.MyApplication;
import org.indywidualni.centrumfm.R;
import org.indywidualni.centrumfm.rest.RestClient;
import org.indywidualni.centrumfm.rest.adapter.NewsAdapter;
import org.indywidualni.centrumfm.rest.model.Channel;
import org.indywidualni.centrumfm.rest.model.RSS;
import org.indywidualni.centrumfm.util.Connectivity;
import org.indywidualni.centrumfm.util.database.DataSource;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainFragment extends TrackedFragment {

    private static final String TAG = MainFragment.class.getSimpleName();

    @Bind(R.id.recycler_view) RecyclerView mRecyclerView;
    @Bind(R.id.swipe_refresh) SwipeRefreshLayout mSwipeRefreshLayout;

    private static List<Channel.Item> rssItems;
    private Tracker tracker;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSwipeRefreshLayout.setColorSchemeResources(R.color.colorAccent);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (Connectivity.isConnected(getActivity()))
                    getRSS();
                else {
                    Snackbar.make(getActivity().findViewById(R.id.panel_main),
                            getString(R.string.no_network), Snackbar.LENGTH_LONG).show();
                    mSwipeRefreshLayout.setRefreshing(false);
                }
            }
        });

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        // set empty adapter first
        mRecyclerView.setAdapter(new NewsAdapter(new ArrayList<Channel.Item>(), null));

        // Google Analytics tracker
        tracker = ((MyApplication) getActivity().getApplication()).getDefaultTracker();

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... arg0) {
                // load offline data initially
                rssItems = DataSource.getInstance().getAllNews();
                return null;
            }
            @Override
            protected void onPostExecute(Void arg) {
                try {
                    // set the right adapter now
                    mRecyclerView.setAdapter(new NewsAdapter(rssItems, getActivity()));
    
                    // now go online and update items
                    if (Connectivity.isConnected(getActivity())) {
                        mSwipeRefreshLayout.post(new Runnable() {
                            @Override
                            public void run() {
                                mSwipeRefreshLayout.setRefreshing(true);
                            }
                        });
                        getRSS();
                    }
                } catch (NullPointerException e) {
                    // fragment was destroyed while AsyncTask was running
                    e.printStackTrace();
                }
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }

    private void getRSS() {
        Call<RSS> call = RestClient.getClientRSS().getRSS();
        call.enqueue(new Callback<RSS>() {
            @Override
            public void onResponse(Call<RSS> call, final Response<RSS> response) {
                Log.v(TAG, "getRSS: response " + response.code());

                if (response.isSuccess()) {  // tasks available
                    new AsyncTask<Void, Void, List<Channel.Item>>() {
                        @Override
                        protected List<Channel.Item> doInBackground(Void... arg0) {
                            DataSource.getInstance().insertNews(response.body()
                                    .getChannel().getItems());
                            return DataSource.getInstance().getAllNews();
                        }
                        @Override
                        protected void onPostExecute(List<Channel.Item> result) {
                            try {
                                rssItems.clear();
                                mRecyclerView.getAdapter().notifyDataSetChanged();
                                rssItems.addAll(result);
                                mRecyclerView.getAdapter().notifyDataSetChanged();
                                mSwipeRefreshLayout.setRefreshing(false);
                            } catch (NullPointerException e) {
                                // fragment was destroyed while AsyncTask was running
                                e.printStackTrace();
                            }
                        }
                    }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
                } else {
                    // error response, no access to resource?
                    if (mSwipeRefreshLayout != null)
                        mSwipeRefreshLayout.setRefreshing(false);

                    tracker.send(new HitBuilders.EventBuilder()
                            .setCategory("Error response")
                            .setAction("Get RSS")
                            .setLabel("error " + response.code())
                            .build());
                }
            }

            @Override
            public void onFailure(Call<RSS> call, Throwable t) {
                Log.e(TAG, "getRSS: " + t.getLocalizedMessage());
                if (mSwipeRefreshLayout != null)
                    mSwipeRefreshLayout.setRefreshing(false);
            }
        });
    }

}