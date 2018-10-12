package com.bearded.derek.audiosave;

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static android.content.ContentValues.TAG;
import static android.content.Context.DOWNLOAD_SERVICE;

public class CopyFileAsyncTask extends AsyncTask<Uri, Void, List<String>> {
    private static final String TYPE_FILE = "file";
    private static final String TYPE_CONTENT = "content";
    private static final int DEFAULT_BUFFER_SIZE = 1024/* bytes */* 1024/* kilobytes */* 10/* megabytes */;

    public interface CompletionListener {
        void onComplete(List<String> filePaths);
    }

    WeakReference<Context> contextWeakReference;
    WeakReference<CompletionListener> completionListenerWeakReference;

    private DownloadManager downloadManager;
    private boolean isDownloading = false;
    private long enqueue;
    private final BroadcastReceiver downloadCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                isDownloading = false;
                context.unregisterReceiver(this);
            }
        }
    };
    private Uri uri;

    public CopyFileAsyncTask(Context context, CompletionListener listener) {
        contextWeakReference = new WeakReference<>(context);
        completionListenerWeakReference = new WeakReference<>(listener);
    }

    @Override
    protected List<String> doInBackground(Uri... uris) {
        List<String> filePaths = new ArrayList<>();
        for (Uri uri :
                uris) {
            filePaths.add(copyFile(uri));
        }
        return filePaths;
    }

    @Override
    protected void onPostExecute(List<String> filePaths) {
        super.onPostExecute(filePaths);
        CompletionListener listener = completionListenerWeakReference.get();
        if (listener != null) {
            listener.onComplete(filePaths);
        }
    }

    private String copyFile(Uri uri) {
        String path = getPath(uri);

        this.uri = uri;

        if (TextUtils.isEmpty(path)) {
            Log.e(TAG, "Can't read path.");
            return null;
        }

        Log.d(TAG, "Copying from " + path);
        String fileName = Uri.parse(path).getLastPathSegment(); // in case of TYPE_CONTENT

        return copyFile(path, fileName);
    }

    private String getPath(Uri uri) {
        String scheme = uri.getScheme();

//        if (TYPE_FILE.equalsIgnoreCase(scheme)) return uri.getPath();
//        else if (TYPE_CONTENT.equalsIgnoreCase(scheme)) return getFilePathFromMedia(uri);
        return uri.getPath();

//        return "";
    }

    private String getFilePathFromMedia(Uri uri) {
        String data = "_data";
        String[] projection = { data };
        Cursor cursor = contextWeakReference.get().getContentResolver().query(uri, projection, null, null, null);
        int column_index = cursor.getColumnIndex(data);
        if (cursor.moveToFirst()) {
            String filePath = cursor.getString(column_index);
            cursor.close();
            return filePath;
        }
        return "";
    }

    private String copyFile(String oldFileName, String newFileName) {
        if (Patterns.WEB_URL.matcher(oldFileName).matches()) {
            if (isNetworkAvailable()) {
                downloadFileFromUrl(oldFileName);
                return  null;
            }
            else {
                Log.e(TAG, "No internet connection. File neither downloaded nor copied.");
                return null;
            }
        }
        else {
            File oldFile = new File(oldFileName);
            File newFile = new File(contextWeakReference.get().getFilesDir(), newFileName);
            return copyFile(oldFile, newFile);
        }
    }

    public boolean isNetworkAvailable() {
        return ((ConnectivityManager) contextWeakReference.get().getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() !=
            null;
    }

    private void downloadFileFromUrl(String fileUrl) {
        registerDownloadReceiver();
        downloadManager = (DownloadManager) contextWeakReference.get().getSystemService(DOWNLOAD_SERVICE);
        Request request = new Request(Uri.parse(fileUrl));
        enqueue = downloadManager.enqueue(request);
        isDownloading = true;
    }

    private void registerDownloadReceiver() {
        contextWeakReference.get().registerReceiver(downloadCompleteReceiver, new IntentFilter(DownloadManager
            .ACTION_DOWNLOAD_COMPLETE));
    }

    private String getFileFromDownloadManager(long enqueue) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(enqueue);
        Cursor c = downloadManager.query(query);
        if (c.moveToFirst()) {
            int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                String uriString = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                c.close();
                return uriString;
            }
        }
        Log.e(TAG, "Downloading file not successful. Malformed Url or no internet connection");
        return null;
    }

    private String copyFile(File oldFile, File newFile) {
        InputStream in = null;
        OutputStream out = null;
        int copiedBytesSize;

        try {
            try {
//                in = new BufferedInputStream(new FileInputStream(oldFile));
                in = new BufferedInputStream(contextWeakReference.get().getContentResolver().openInputStream(uri));
//                contextWeakReference.get().getContentResolver().openFileDescriptor(uri, )
                out = new BufferedOutputStream(new FileOutputStream(newFile));
                copiedBytesSize = copyStreams(in, out);

                Log.d(TAG, "Copied bytes: " + copiedBytesSize);
            }
            finally {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            }
        }
        catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        return newFile.getAbsolutePath();
    }

    private int copyStreams(InputStream in, OutputStream out) throws IOException {
        byte[] bytes = new byte[DEFAULT_BUFFER_SIZE];
        int copiedBytesSize = 0;
        int currentByte = 0;

        while (-1 != (currentByte = in.read(bytes))) {
            out.write(bytes, 0, currentByte);
            copiedBytesSize += currentByte;
        }

        return copiedBytesSize;
    }
}