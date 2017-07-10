package de.scheckmedia.fdc;

/**
 * Created by Tobias Scheck on 09.07.17.
 */
public interface FlickrApiEvents {
    void onRequestStart();
    void onRequestEnd(Object data);
    void onError(Exception ex);
    // void onError();
}
