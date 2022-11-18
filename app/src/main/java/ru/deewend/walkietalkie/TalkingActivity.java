package ru.deewend.walkietalkie;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Canvas;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.SurfaceHolder;

import java.io.FileDescriptor;
import java.io.IOException;

public class TalkingActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    private TalkingView talkingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource((FileDescriptor) null);
        } catch (IOException e) {
            WalkieTalkie.getInstance().getWTThread().interrupt();
        }
        talkingView = new TalkingView(this);
        setContentView(talkingView);
    }
}
