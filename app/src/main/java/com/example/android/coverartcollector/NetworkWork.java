package com.example.android.coverartcollector;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;

// network request worker class
public class NetworkWork {

    private Context myContext;

    NetworkWork(Context context) {
        myContext = context;
    }

    public boolean checkConnectivity() {
        ConnectivityManager cm =
                (ConnectivityManager) myContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return (activeNetwork != null && activeNetwork.isConnectedOrConnecting());
        }

        return false;
    }

    // format a album.getinfo request for Last FM
    public String formatMusicBrainzAlbumGetinfoRequest(CoverArt album) {
        StringBuilder stringBuilder = new StringBuilder("http://ws.audioscrobbler.com/2.0/");

        try {
            stringBuilder.append("?method=album.getinfo");
            stringBuilder.append("&api_key=");
            stringBuilder.append("56b070a62a0ab4aebd6f86f982f7b2cc");
            stringBuilder.append("&artist=");
            stringBuilder.append(URLEncoder.encode(album.artistName, "UTF-8"));
            stringBuilder.append("&album=");
            stringBuilder.append(URLEncoder.encode(album.albumName, "UTF-8"));
            stringBuilder.append("&format=json");
        } catch (UnsupportedEncodingException e) {
            Log.e("ERROR", "NetworkWork:formatMusicBrainzReleaseGroupRequest(): Problem formatting URL.", e);
            return null;
        }

        return stringBuilder.toString();
    }

    // send a JSON network request
    public static DownloadedCoverArt retrieveAlbumInfo(String requestURL) {
        // Create URL object
        URL url = createUrl(requestURL);

        // Perform HTTP request to the URL and receive a JSON response back
        String jsonResponse = null;
        try {
            jsonResponse = makeHttpRequest(url);
        } catch (IOException e) {
            Log.e("ERROR", "NetworkWork:retrieveReleaseGroup(): Error closing input stream", e);
        }

        // raw json response
        Log.d("JSON", jsonResponse);

        String downloadArtPath = extractArtPathFromJson(jsonResponse);

        return getBitmapFromURL(downloadArtPath);
    }

     // Returns new URL object from the given string URL.
    private static URL createUrl(String stringUrl) {
        URL url = null;
        try {
            url = new URL(stringUrl);
        } catch (MalformedURLException e) {
            Log.e("ERROR", "NetworkWork:createUrl(): Error with creating URL ", e);
        }
        return url;
    }

     // Make an HTTP request to the given URL and return a String as the response.
    private static String makeHttpRequest(URL url) throws IOException {
        String jsonResponse = "";

        // If the URL is null, then return early.
        if (url == null) {
            return jsonResponse;
        }

        HttpURLConnection urlConnection = null;
        InputStream inputStream = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setReadTimeout(10000 /* milliseconds */);
            urlConnection.setConnectTimeout(15000 /* milliseconds */);
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            // If the request was successful (response code 200),
            // then read the input stream and parse the response.
            if (urlConnection.getResponseCode() == 200) {
                inputStream = urlConnection.getInputStream();
                jsonResponse = readFromStream(inputStream);
            } else {
                Log.e("ERROR", "NetworkWork:makeHttpRequest(): Error response code: " + urlConnection.getResponseCode());
            }
        } catch (IOException e) {
            Log.e("ERROR", "NetworkWork:makeHttpRequest(): Problem retrieving JSON results.", e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return jsonResponse;
    }

     // Convert the InputStream into a String which contains the
     // whole JSON response from the server.
    private static String readFromStream(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charset.forName("UTF-8"));
            BufferedReader reader = new BufferedReader(inputStreamReader);
            String line = reader.readLine();
            while (line != null) {
                output.append(line);
                line = reader.readLine();
            }
        }
        return output.toString();
    }

    // extract image info from album.getinfo return data
    private static String extractArtPathFromJson(String jsonData) {
        JSONObject root;

        // get jsonObject from raw json data
        try {
            root = new JSONObject(jsonData);
        } catch (JSONException e) {
            Log.e("ERROR", "NetworkWork:extractArtPathFromJson(): Problem retrieving JSON results.", e);
            return null;
        }

        // check for error field
        boolean errorRet = true;
        try {
            root.getInt("error");
        } catch (JSONException e) {
            // hopefully we get an exception when looking for error
            errorRet = false;
        }

        if (errorRet) {
            return null;
        }

        // get medium image
        try {
            JSONObject jsonAlbum  = root.getJSONObject("album");
            JSONArray jsonImages = jsonAlbum.getJSONArray("image");
            for (int i = 0; i < jsonImages.length(); i++) {
                String size = jsonImages.getJSONObject(i).getString("size");
                if (!size.equals("medium")) {
                    continue;
                }

                String imageURL = jsonImages.getJSONObject(i).getString("#text");

                if (imageURL.isEmpty()) {
                    return null;
                } else {
                    Log.d("NETWORK", "FOUND ART " + imageURL);
                    return imageURL;
                }
            }

            return null;
        } catch (JSONException e) {
            Log.e("ERROR", "NetworkWork:extractArtPathFromJson(): Problem parsing JSON results.");
            return null;
        }
    }

    // got this code from https://stackoverflow.com/questions/8992964/android-load-from-url-to-bitmap
    // creates a bitmap from an image URL
    private static DownloadedCoverArt getBitmapFromURL(String src) {
        if ((src == null) || src.isEmpty()) {
            return null;
        }

        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();

            InputStream input = connection.getInputStream();
            return new DownloadedCoverArt(BitmapFactory.decodeStream(input), src);
        } catch (IOException e) {
            Log.e("ERROR", "NetworkWork:getBitmapFromURL(): Problem downloading and creating bitmap.");
            return null;
        }
    }
}
