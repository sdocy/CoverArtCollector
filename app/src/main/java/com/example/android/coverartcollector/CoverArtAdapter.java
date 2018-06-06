package com.example.android.coverartcollector;

import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

// view adapter for the list of albums
public class CoverArtAdapter extends ArrayAdapter<CoverArt> implements LoaderManager.LoaderCallbacks<DownloadedCoverArt> {

    private Context myContext;
    private List<CoverArt> albums;
    private NetworkWork netWork;            // worker class for network tasks
    private LoaderManager lowdaMgr;         // async loader to perfrom network tasks
    private CoverArtCollector myParent;     // reference to main activity

    private int numDownloaded = 0;          // number of covers downloaded

    private CoverArt albumToRetrieve;       // current album we are retrieving art for
    private String musicBrainzReleaseGroupRequest;  // holds URL for a cover art request

    private boolean loadingNow = false;             // is there a lowda currently running, it must finish before
                                                    // we start a new one

    // find all         findingAll=true, findingUnknown=false
    // find unknown     findingAll=true, findingUnknown=true
    private boolean findingAll = false;
    private boolean findingUnknown = false;
    private int findingAllIndex;            // index of the album we are findin art for when finding ALL

    private GeneralTools myTools;

    // viewholder for listview item
    private static class ViewHolder {
        private TextView artistName;
        private TextView albumName;
        private TextView findArt;
        private TextView noArt;
        private TextView noNet;
        private ProgressBar progress;
        private ImageView downloadArt;
        private ImageView deviceArt;
        private ImageView deviceArtCheck;
        private ImageView downloadArtCheck;
    }

    CoverArtAdapter(@NonNull Context context, @NonNull List<CoverArt> objs, LoaderManager lowdaManager,
                    CoverArtCollector p) {
        super(context, 0, objs);

        myContext = context;
        albums = objs;
        myParent = p;

        netWork = new NetworkWork(myContext);

        lowdaMgr = lowdaManager;

        myTools = new GeneralTools(myContext);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        View listItem = convertView;
        if(listItem == null) {
            // new view, init a new ViewHolder for it
            listItem = LayoutInflater.from(myContext).inflate(R.layout.cover_art_layout, parent, false);

            holder = new ViewHolder();
            holder.artistName = listItem.findViewById(R.id.artist_name);
            holder.albumName = listItem.findViewById(R.id.album_name);
            holder.findArt = listItem.findViewById(R.id.find_art);
            holder.noArt = listItem.findViewById(R.id.no_art);
            holder.noNet = listItem.findViewById(R.id.no_internet);
            holder.progress = listItem.findViewById(R.id.progress_bar);
            holder.deviceArt = listItem.findViewById(R.id.device_art);
            holder.downloadArt = listItem.findViewById(R.id.download_art);
            holder.deviceArtCheck = listItem.findViewById(R.id.device_art_checkmark);
            holder.downloadArtCheck = listItem.findViewById(R.id.download_art_checkmark);

            listItem.setTag(holder);
        } else {
            // recycled view, get existing ViewHolder
            holder = (ViewHolder) listItem.getTag();
        }

        final CoverArt currentInfo = getItem(position);
        if (currentInfo == null) {
            Log.e("ERROR", "infoViewAdapter:getView() could not get currentInfo");
            return listItem;
        }

        holder.artistName.setText(currentInfo.artistName);
        holder.albumName.setText(currentInfo.albumName);

        // show number downloaded iff we have downloaded some art
        if (numDownloaded > 0) {
            myParent.numDownloadedView.setText(myContext.getString(R.string.coversDownloaded, numDownloaded));
        } else {
            myParent.numDownloadedView.setText("");
        }

        // show device art or "unknown" if there is no device art
        if (currentInfo.hasDeviceArt()) {
            Bitmap bm = BitmapFactory.decodeFile(currentInfo.deviceArtPath);
            holder.deviceArt.setImageBitmap(bm);
        } else {
            // this is used for unknown album art
            holder.deviceArt.setImageResource(R.drawable.unknown);
        }

        // show downloaded art if there is one
        if (currentInfo.hasDownloadedArt()) {
            holder.downloadArt.setImageBitmap(currentInfo.downloadArt.coverArt);
        }

        // got this trick for having clickable elements inside a listview from
        // https://stackoverflow.com/questions/20541821/get-listview-item-position-on-button-click
        holder.deviceArt.setTag(position);
        holder.deviceArt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                int pos = (Integer) arg0.getTag();

                CoverArt currElem = getItem(pos);
                if (currElem == null) {
                    return;
                }

                myTools.vibrate(GeneralTools.touchVibDelay);

                if (!currElem.useDownloadArt) {
                    // second click will open a google search for the artist / album
                    String googleSearch = "https://www.google.com/search?tbm=isch&q=";
                    try {
                        googleSearch += URLEncoder.encode(currElem.artistName, "UTF-8");
                        googleSearch += "+" + URLEncoder.encode(currElem.albumName, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        Log.e("ERROR", "CoverArtAdapter:turnOnLoaded:onClick(): Problem formatting URL.", e);
                        return;
                    }

                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(googleSearch));
                    myContext.startActivity(browserIntent);
                } else {
                    // first click switches chosen art
                    currElem.useDownloadArt = false;
                    notifyDataSetChanged();
                }
            }
        });

        // show / hide certain views based on item state
        switch (currentInfo.artState) {
            case find :         turnOnFind(position, holder);
                                break;
            case loading :      turnOnLoading(holder);
                                break;
            case loaded :       turnOnLoaded(position, holder);
                                break;
            case noart :        turnOnNoArt(holder);
                                break;
            case nointernet :   turnOnNoNet(holder);
                                break;
        }

        return listItem;
    }

    // user has not tried to find art for this album yet, find art button is visible
    private void turnOnFind(int pos, ViewHolder holder) {
        holder.findArt.setVisibility(View.VISIBLE);
        holder.noArt.setVisibility(View.INVISIBLE);
        holder.noNet.setVisibility(View.INVISIBLE);
        holder.progress.setVisibility(View.INVISIBLE);
        holder.downloadArt.setVisibility(View.INVISIBLE);

        // got this trick for having clickable elements inside a listview from
        // https://stackoverflow.com/questions/20541821/get-listview-item-position-on-button-click
        holder.findArt.setTag(pos);
        holder.findArt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (loadingNow) {
                    // can only do one load at a time
                    return;
                }

                myTools.vibrate(GeneralTools.touchVibDelay);

                int position = (Integer) arg0.getTag();
                loadArt(position);
            }
        });
    }

    // we are in the process of downloading art for this item, progressbar is visible
    private void turnOnLoading(ViewHolder holder) {
        holder.findArt.setVisibility(View.INVISIBLE);
        holder.noArt.setVisibility(View.INVISIBLE);
        holder.noNet.setVisibility(View.INVISIBLE);
        holder.progress.setVisibility(View.VISIBLE);
        holder.downloadArt.setVisibility(View.INVISIBLE);
    }

    // we have successfully downloaded cover art for this item, downloaded art image is visible
    private void turnOnLoaded(int pos, ViewHolder holder) {
        holder.findArt.setVisibility(View.INVISIBLE);
        holder.noArt.setVisibility(View.INVISIBLE);
        holder.noNet.setVisibility(View.INVISIBLE);
        holder.progress.setVisibility(View.INVISIBLE);
        holder.downloadArt.setVisibility(View.VISIBLE);

        CoverArt currElem = getItem(pos);
        if (currElem == null) {
            return;
        }
        if (currElem.useDownloadArt) {
            // downloaded art is chosen
            holder.deviceArtCheck.setVisibility(View.INVISIBLE);
            holder.downloadArtCheck.setVisibility(View.VISIBLE);
            holder.deviceArt.setColorFilter(myContext.getResources().getColor(R.color.filterPlayed));
            holder.downloadArt.setColorFilter(myContext.getResources().getColor(R.color.filterNotPlayed));
        } else {
            // device art is chosen
            holder.deviceArtCheck.setVisibility(View.VISIBLE);
            holder.downloadArtCheck.setVisibility(View.INVISIBLE);
            holder.deviceArt.setColorFilter(myContext.getResources().getColor(R.color.filterNotPlayed));
            holder.downloadArt.setColorFilter(myContext.getResources().getColor(R.color.filterPlayed));
        }

        // got this trick for having clickable elements inside a listview from
        // https://stackoverflow.com/questions/20541821/get-listview-item-position-on-button-click
        holder.downloadArt.setTag(pos);
        holder.downloadArt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                int position = (Integer) arg0.getTag();

                CoverArt currElem = getItem(position);
                if (currElem == null) {
                    return;
                }

                myTools.vibrate(GeneralTools.touchVibDelay);

                if (currElem.useDownloadArt) {
                    // user already chose download art, we open image URL on second click
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(currElem.downloadArt.coverArtURL));
                    myContext.startActivity(browserIntent);
                } else {
                    // first click switches chosen art
                    currElem.useDownloadArt = true;
                    notifyDataSetChanged();
                }
            }
        });
    }

    // we did not find any cover art for this item, "no art" message is visible
    private void turnOnNoArt(ViewHolder holder) {
        holder.findArt.setVisibility(View.INVISIBLE);
        holder.noArt.setVisibility(View.VISIBLE);
        holder.noNet.setVisibility(View.INVISIBLE);
        holder.progress.setVisibility(View.INVISIBLE);
        holder.downloadArt.setVisibility(View.INVISIBLE);
    }

    // we have no internet connect, "no internet" message is visible
    private void turnOnNoNet(ViewHolder holder) {
        holder.findArt.setVisibility(View.INVISIBLE);
        holder.noArt.setVisibility(View.INVISIBLE);
        holder.noNet.setVisibility(View.VISIBLE);
        holder.progress.setVisibility(View.INVISIBLE);
        holder.downloadArt.setVisibility(View.INVISIBLE);
    }

    // find cover art for the specified item
    private void loadArt(int position) {
        if ((position < 0) || (position > getCount())) {
            return;
        }

        CoverArt currElem = getItem(position);
        if (currElem == null) {
            return;
        }

        // check for internet connection
        if (!netWork.checkConnectivity()) {
            currElem.artState = CoverArt.state.nointernet;
            notifyDataSetChanged();

            return;
        } else {
            currElem.artState = CoverArt.state.loading;
            notifyDataSetChanged();
        }

        // form album.getinfo URL query
        musicBrainzReleaseGroupRequest = netWork.formatMusicBrainzAlbumGetinfoRequest(currElem);
        if (musicBrainzReleaseGroupRequest == null) {
            return;
        }

        // save ref to the album we are getting art for so we can update it in onLoadFinished()
        albumToRetrieve = currElem;

        // disable other loads until this one is done
        loadingNow = true;

        lowdaMgr.restartLoader(1, null, CoverArtAdapter.this);
    }

    @Override
    // create a lowda for downloading cover art
    public Loader<DownloadedCoverArt> onCreateLoader(int id, Bundle args) {
        return new CoverLowda(myContext, musicBrainzReleaseGroupRequest);
    }

    @Override
    // loading of art has completed
    public void onLoadFinished(Loader<DownloadedCoverArt> lowda, DownloadedCoverArt data) {
        if (data == null) {
            albumToRetrieve.artState = CoverArt.state.noart;
        } else {
            albumToRetrieve.artState = CoverArt.state.loaded;
            albumToRetrieve.downloadArt = data;
            numDownloaded++;
        }

        // we are done loading, user may start a new load
        loadingNow = false;

        notifyDataSetChanged();

        // if we are doing auto-find, get the next album
        if (findingAll) {
            int toFind = findAlbumToFind(findingAllIndex + 1, findingUnknown);
            if (toFind >= 0) {
                loadArt(toFind);
            } else {
                // we are done
                findingAll = false;
                myParent.enableAutoFind();
            }
        }
    }

    @Override
    // lowda has been reset
    public void onLoaderReset(Loader<DownloadedCoverArt> lowda) {

    }

    // kick off an auto-find by finding the first album to find art for
    public void findAllArt(boolean unknownOnly) {
        myTools.vibrate(GeneralTools.touchVibDelay);

        findingAll = true;
        findingUnknown = unknownOnly;

        int toFind = findAlbumToFind(0, findingUnknown);
        if (toFind >= 0) {
            loadArt(toFind);
        } else {
            findingAll = false;
            myParent.enableAutoFind();
        }
    }

    // find an album that we need to get cover art for, return the album's list position
    private int findAlbumToFind(int startingPos, boolean unknownOnly) {
        // look for an album without device art
        for (findingAllIndex = startingPos; findingAllIndex < getCount(); findingAllIndex++) {
            CoverArt alb = albums.get(findingAllIndex);

            // have we downloaded art for this album yet?
            if (!alb.hasDownloadedArt()) {
                // is there device art for this album?
                if ((!unknownOnly) || (!alb.hasDeviceArt())) {
                    break;
                }
            }
        }

        if (findingAllIndex < getCount()) {
            return findingAllIndex;
        } else {
            return -1;
        }
    }
}
