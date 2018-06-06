package com.example.android.coverartcollector;

import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CoverArtCollector extends AppCompatActivity {

    List<CoverArt> albumList;
    private ListView songListView;
    private TextView emptyListView;
    private CoverArtAdapter adapter = null;

    private TextView findAllArtView;
    private TextView findUnknownArtView;
    private boolean findingArt = false;

    // this are updated directly by our CoverArtAdapter
    TextView numDownloadedView;

    // for sorting the gridList
    //sort by artist name -> album name -> track number
    public class AlbumComparator implements Comparator<CoverArt>
    {
        public int compare(CoverArt left, CoverArt right) {
            if (left.artistName.equals(right.artistName)) {
                return left.albumName.compareTo(right.albumName);
            } else {
                return left.artistName.compareTo(right.artistName);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cover_art_collector);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        initViews();

        getAlbumList();

        initAdapters();
    }

    private void initViews() {
        songListView = findViewById(R.id.songList);

        emptyListView = findViewById(R.id.empty_list);
        songListView.setEmptyView(emptyListView);

        findAllArtView = findViewById(R.id.find_all_art);
        findUnknownArtView = findViewById(R.id.find_unknown_art);
        numDownloadedView = findViewById(R.id.num_downloaded);
    }

    // initialize music list
    private void getAlbumList() {
        albumList = getMusicList();

        // sort the songList
        Collections.sort(albumList, new AlbumComparator());

        // requires list to be sorted by artist and album
        removeAlbumDuplicates();
    }

    private void initAdapters() {
        adapter = new CoverArtAdapter(this, albumList, getLoaderManager(), this);

        songListView.setAdapter(adapter);
    }

    // intiate auto-find for all albums
    public void findAllArt(View v) {
        if (findingArt) {
            // let current auto-find complete
            return;
        }

        findingArt = true;
        findAllArtView.setTextColor(getResources().getColor(R.color.filterPlayed));
        findUnknownArtView.setTextColor(getResources().getColor(R.color.filterPlayed));

        if (adapter != null) {
            adapter.findAllArt(false);
        }
    }

    // intiate auto-find for albums with no cover art on this device
    public void findUnknownArt(View v) {
        if (findingArt) {
            // let current auto-find complete
            return;
        }

        findingArt = true;
        findAllArtView.setTextColor(getResources().getColor(R.color.filterPlayed));
        findUnknownArtView.setTextColor(getResources().getColor(R.color.filterPlayed));

        if (adapter != null) {
            adapter.findAllArt(true);
        }
    }

    // current auto-find is complete, re-enable find buttons
    public void enableAutoFind() {
        findingArt = false;

        findAllArtView.setTextColor(getResources().getColor(R.color.buttonTextColor));
        findUnknownArtView.setTextColor(getResources().getColor(R.color.buttonTextColor));
    }

    // remove songs that are on the same album, requires list to be sorted based on artist
    // and album so that duplicates will be adjacent
    private void removeAlbumDuplicates() {
        List<CoverArt> toRemove = new ArrayList<>();

        for (int i = 0; i < albumList.size() - 1; i++) {
            CoverArt s1 = albumList.get(i);
            CoverArt s2 = albumList.get(i + 1);

            if (dupeAlbum(s1, s2)) {
                toRemove.add(s1);
            }
        }

        for (CoverArt s : toRemove) {
            albumList.remove(s);
        }
    }

    // are two songs on the same album...identical artist name and album name?
    private boolean dupeAlbum(CoverArt a, CoverArt b) {
        if (!a.artistName.equals(b.artistName)) {
            return false;
        }

        return (a.albumName.equals(b.albumName));
    }

    // this code retrieved from https://gist.github.com/novoda/374533
    // it uses MediaStore to find all music files and related cover art on this device
    private List<CoverArt> getMusicList() {
        Cursor cursor;
        List<CoverArt> songs = new ArrayList<>();

        //Retrieve a list of Music files currently listed in the Media store DB via URI.

        //Some audio may be explicitly marked as not being music
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        String[] projection = {
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.TRACK
        };

        // deprecated, should use CursorLoader
        cursor = this.managedQuery(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                selection, null, null);

        while (cursor.moveToNext()) {
            //Log.e("GET_SONGS", cursor.getString(0) + "||" + cursor.getString(1) + "||" + cursor.getString(2)
            //        + "||" +  cursor.getString(3) + "||" + cursor.getString(4) + "||" + cursor.getString(5));

            songs.add(new CoverArt(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)),
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))));
            CoverArt s = songs.get(songs.size() - 1);
            // songName, artistName, albumName, filePath, trackNumber
            /*s.addSong(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)),
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)),
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)),
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)),
                    cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)));*/

            Cursor cursorArt = getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    new String[] {MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART},
                    MediaStore.Audio.Albums._ID+ "=?",
                    new String[] {cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))}, null);
            if (cursorArt != null) {
                if (cursorArt.moveToFirst()) {
                    String path = cursorArt.getString(cursorArt.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
                    if (path != null) {
                        //Log.e("GET_ART", path);
                        s.deviceArtPath = path;
                    }
                }

                cursorArt.close();
            }
        }

        return songs;
    }

    // this is only for the purpose of allowing the project to be reviewed on phones w/o music
    private List<CoverArt> fakeMusicList() {
        List<CoverArt> songs = new ArrayList<>();

        songs.add(new CoverArt("Genesis", "Selling England By The Pound"));
        songs.add(new CoverArt("2Pac & Outlawz", "Gang Related - Wild Wild West"));
        songs.add(new CoverArt("David Bowie", "Space Oddity"));
        songs.add(new CoverArt("Pink Floyd", "Dark Side Of The Moon"));
        songs.add(new CoverArt("Eminem", "Recovery (Deluxe Edition)"));
        songs.add(new CoverArt("Eric Clapton", "Unplugged"));
        songs.add(new CoverArt("Kansas", "Point Of Know Return"));
        songs.add(new CoverArt("Led Zeppelin", "Led Zeppelin IV"));
        songs.add(new CoverArt("Prince", "Purple Rain (Soundtrack)"));
        songs.add(new CoverArt("Rush", "Chronicles [Disc 2]"));
        songs.add(new CoverArt("The Beatles", "Abbey Road"));
        songs.add(new CoverArt("The Police", "Synchronicity"));
        return songs;
    }
}
