package de.scheckmedia.fdc;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Tobias Scheck on 09.07.17.
 */
public class FlickrApi {
    final String baseURL = "https://api.flickr.com/services/rest/"
                        +  "?method=%s&api_key=%s&format=json&nojsoncallback=1";
    private String flickrApiKey;

    public FlickrApi(String apiKey) {
        this.flickrApiKey = apiKey;
    }

    public void search(String query, boolean onlyTags, boolean onlyCC,
                       String sort, String quality, int page, int perPage,
                       FlickrApiEvents callback) {
        String url = buildURL("flickr.photos.search");

        try {
            query = URLEncoder.encode(query, "UTF-8");

            if(onlyTags) {
                url += "&tags=" + query;
            } else {
                url += "&text=" + query;
            }

            if(onlyCC)
                url += "&licence=1";

            url +=  "&extras=url_q," + quality + //String.join(",", qualities) +
                    "&page=" + page +
                    "&per_page=" + perPage +
                    "&sort=" + sort;

            sendRequest(url, callback);
        } catch (UnsupportedEncodingException e) {
            callback.onError(e);
        }
    }

    private String buildURL(String method) {
        return String.format(this.baseURL, method, this.flickrApiKey);
    }

    private void sendRequest(String url, FlickrApiEvents callback) {
        System.out.println("send request to ur: " + url);
        new Thread(() -> {
            callback.onRequestStart();
            try(CloseableHttpClient client = HttpClientBuilder.create().build()) {
                HttpGet request = new HttpGet(url);
                HttpResponse result = client.execute(request);
                String json = EntityUtils.toString(result.getEntity(), "UTF-8");
                callback.onRequestEnd(json);
            }  catch (IOException ex) {
                callback.onError(ex);
            }
        }).start();


    }
}
