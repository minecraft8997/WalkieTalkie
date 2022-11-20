package ru.deewend.walkietalkie;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import ru.deewend.walkietalkie.thread.PlayerThread;

public class TalkingActivity extends AppCompatActivity {
    private AudioTrack audioTrack;
    private TalkingView talkingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WalkieTalkie.getInstance().getWTThread().setActivity(this);
        setTitle("Комната");

        setContentView(R.layout.activity_talking);
        talkingView = findViewById(R.id.talking_view);
        talkingView.initialize(this);

        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                TalkingView.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                talkingView.getAudioBufferSize(),
                AudioTrack.MODE_STREAM
        );
        audioTrack.setPlaybackRate(TalkingView.SAMPLE_RATE_HZ);
        audioTrack.play();
        int samplesToDraw = talkingView.getAudioBufferSize() / (16 / 8);
        audioTrack.setPositionNotificationPeriod(samplesToDraw);

        new PlayerThread(this).start();
    }

    public AudioTrack getAudioTrack() {
        return audioTrack;
    }

    public TalkingView getTalkingView() {
        return talkingView;
    }
}
