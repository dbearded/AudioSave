package com.bearded.derek.audiosave;

import android.content.ClipData;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class SaveFile extends AppCompatActivity implements CopyFileAsyncTask.CompletionListener {

    private static final String LOG_TAG = "AudioSaveTest";

    private static final String MIME_TYPE = "video/3gpp";

    private File frontFile, backFile;

    TextView frontContent, backContent;
    CardView frontCard, backCard;

    private AppCompatButton mPlayButton;

    private MediaPlayer mPlayer = null;

    private boolean cardSelected;

    boolean mStartPlaying = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_save_file);
        frontCard = findViewById(R.id.front_card);
        backCard = findViewById(R.id.back_card);
        frontContent = findViewById(R.id.front_content);
        backContent = findViewById(R.id.back_content);
        mPlayButton = findViewById(R.id.play_button);

        Intent intent = getIntent();
        if (intent != null) {
            ClipData clipData = intent.getClipData();
            if (clipData != null) {
                if (clipData.getDescription().hasMimeType(Intent.normalizeMimeType(MIME_TYPE))) {
                    ClipData.Item front = clipData.getItemAt(0);
                    ClipData.Item back = clipData.getItemAt(1);
                    Uri[] uris = new Uri[]{front.getUri(), back.getUri()};
                    CopyFileAsyncTask copyFileAsyncTask = new CopyFileAsyncTask(this, this);
                    copyFileAsyncTask.execute(uris);
                }
            }

//            mFileName =  new File(intent.getData().getPath()).getAbsolutePath();
//            CopyFileAsyncTask copyFileAsyncTask = new CopyFileAsyncTask(this, this);
//            copyFileAsyncTask.execute(intent.getData());
        }

        frontCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cardSelected = true;
                setCardColor(cardSelected);
            }
        });

        backCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cardSelected = false;
                setCardColor(cardSelected);
            }
        });
    }

    private void setCardColor(boolean cardSelected) {
        if (cardSelected) {
            frontCard.setCardBackgroundColor(getResources().getColor(R.color.cardSelected));
            backCard.setCardBackgroundColor(getResources().getColor(R.color.cardNotSelected));
        } else {
            backCard.setCardBackgroundColor(getResources().getColor(R.color.cardSelected));
            frontCard.setCardBackgroundColor(getResources().getColor(R.color.cardNotSelected));
        }
    }

    private String getMediaPath() {
        if (cardSelected) {
            return frontFile.getAbsolutePath();
        } else {
            return backFile.getAbsolutePath();
        }
    }

    private void onPlay(boolean start) {
        if (start) {
            startPlaying();
        } else {
            stopPlaying();
        }
    }

    private void startPlaying() {
        mPlayer = new MediaPlayer();
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                stopPlaying();
                mStartPlaying = true;
                mPlayButton.setText("Play");
            }
        });
        try {
            mPlayer.setDataSource(getMediaPath());
            mPlayer.prepare();
            mPlayer.start();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }
    }

    private void stopPlaying() {
        mPlayer.release();
        mPlayer = null;
    }


    @Override
    public void onComplete(List<String> filePaths) {
        frontFile = new File(filePaths.get(0));
        backFile = new File(filePaths.get(1));
        frontContent.setText(frontFile.getName());
        backContent.setText(backFile.getName());
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlay(mStartPlaying);
                if (mStartPlaying) {
                    mPlayButton.setText("Stop");
                } else {
                    mPlayButton.setText("Play");
                }
                mStartPlaying = !mStartPlaying;
            }
        });
    }
}
