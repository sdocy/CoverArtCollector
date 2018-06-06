package com.example.android.coverartcollector;

import android.graphics.Bitmap;

// class to hold cover art data
public class CoverArt {
    String artistName;
    String albumName;
    String deviceArtPath;       // string to cover art located on the device
    state artState;
    DownloadedCoverArt downloadArt;
    //Bitmap downloadedArt;       // cover art downloaded from the internet
    //String downLoadedArtURL;    // URL for cover art downloaded from the internet
    boolean useDownloadArt;     // user wants to use the downloaded art

    enum state {find, loading, loaded, noart, nointernet}       // list item states for each album

    CoverArt(String artist, String album) {
        artistName = artist;
        albumName = album;
        artState = CoverArt.state.find;
        downloadArt = null;
        useDownloadArt = false;
    }

    // was cover art for this album on the device?
    public boolean hasDeviceArt() {
        return (deviceArtPath != null);
    }

    // did we download cover art for this album from the internet?
    public boolean hasDownloadedArt() {
        return (downloadArt != null);
    }

    // is there no cover art for this album?
    public boolean hasNoArt() {
        return (hasDeviceArt() || hasDownloadedArt());
    }
}
