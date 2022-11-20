package ru.deewend.walkietalkie;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import ru.deewend.walkietalkie.thread.PlayerThread;
import ru.deewend.walkietalkie.thread.WalkieTalkieThread;

public class TalkingActivity extends AppCompatActivity {
    private WalkieTalkieThread thread;
    private TalkingView talkingView;
    private int audioBufferSize;
    private AudioTrack audioTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        thread = WalkieTalkie.getInstance().getWTThread();
        thread.setActivity(this);
        setTitle("Комната");

        setContentView(R.layout.activity_talking);
        talkingView = findViewById(R.id.talking_view);
        talkingView.initialize();

        audioBufferSize = AudioTrack.getMinBufferSize(
                TalkingView.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                TalkingView.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
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

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        throw new UnsupportedOperationException();
    }

    public void onNumberOfVerifiedConnectionsChanged(int onlineVerified) {
        setTitle("Главная (в сети: " + onlineVerified + ")");
    }

    public void onSpeakerMapUpdated() {
        List<String> speakersCopy = thread.getSpeakerList();
        ((TextView) findViewById(R.id.speaker_usernames)).setText(Helper.join(speakersCopy));

        List<String> distanceList = new ArrayList<>(speakersCopy.size());
        for (String speaker : speakersCopy) {
            distanceList.add(Helper.getDistance(thread.getSpeakerLocation(speaker)));
        }
        ((TextView) findViewById(R.id.distances)).setText(Helper.join(distanceList));
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("Вы уверены?");
        if (thread.hasChild()) {
            alertDialog.setMessage("Поскольку Вы являетесь " +
                    "владельцем этой комнаты, все её участники будут отключены");
        }
        alertDialog.setPositiveButton("Остаться", (dialog, which) -> dialog.cancel());
        alertDialog.setNegativeButton("Уйти", (dialog, which) -> {
            thread.notifyComponentShutdown(null, null);
            super.onBackPressed();

            dialog.dismiss();
        });
    }

    public TalkingView getTalkingView() {
        return talkingView;
    }

    public int getAudioBufferSize() {
        return audioBufferSize;
    }

    public AudioTrack getAudioTrack() {
        return audioTrack;
    }
}
