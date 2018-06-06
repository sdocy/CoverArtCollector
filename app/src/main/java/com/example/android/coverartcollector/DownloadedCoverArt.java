package com.example.android.coverartcollector;

import android.graphics.Bitmap;

// holds info for cover art downloaded from the internet
public class DownloadedCoverArt {
    Bitmap coverArt;
    String coverArtURL;

    DownloadedCoverArt(Bitmap art, String url) {
        coverArt = art;
        coverArtURL = url;
    }
}
