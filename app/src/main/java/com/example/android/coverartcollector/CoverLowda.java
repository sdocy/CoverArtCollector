package com.example.android.coverartcollector;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.util.Log;


public class CoverLowda extends AsyncTaskLoader<DownloadedCoverArt> {

    private String musicBrainzFirstURL;

    CoverLowda(Context context, String requestUrl) {
        super(context);

        musicBrainzFirstURL = requestUrl;
    }

    @Override
    protected void onStartLoading() {
        forceLoad();
    }

    @Override
    public DownloadedCoverArt loadInBackground() {
        Log.d("NETWORK", "retrieving " + musicBrainzFirstURL);

        return NetworkWork.retrieveAlbumInfo(musicBrainzFirstURL);
    }
}
