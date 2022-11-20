package ru.deewend.walkietalkie.thread;

import android.media.AudioTrack;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;

import ru.deewend.walkietalkie.TalkingActivity;
import ru.deewend.walkietalkie.WalkieTalkie;

public class PlayerThread extends Thread {
    //public static final String TAG = "PlayerThread";

    private final TalkingActivity talkingActivity;
    private final WalkieTalkieThread thread;

    public PlayerThread(TalkingActivity talkingActivity) {
        this.talkingActivity = talkingActivity;
        this.thread = WalkieTalkie.getInstance().getWTThread();
    }

    private void doLogic() throws IOException {
        AudioTrack audioTrack = talkingActivity.getAudioTrack();
        int bufferSizeShorts = talkingActivity.getTalkingView().getAudioBufferSize() / 2;
        short[] audioBuffer = new short[bufferSizeShorts];

        DataInputStream inputStream = thread.getVoiceServerInputStream();
        while (true) {
            for (int i = 0; i < bufferSizeShorts; i++) {
                audioBuffer[i] = inputStream.readShort();
            }
            audioTrack.write(audioBuffer, 0, bufferSizeShorts);
            audioTrack.flush();
        }
    }

    @Override
    public void run() {
        Throwable throwable = null;
        try {
            doLogic();
        } catch (Throwable t) {
            throwable = t;
        } finally {
            thread.notifyComponentShutdown(this, throwable);
        }
    }
}
