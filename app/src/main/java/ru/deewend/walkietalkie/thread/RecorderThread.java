package ru.deewend.walkietalkie.thread;

import android.media.AudioRecord;

import java.io.DataOutputStream;
import java.io.IOException;

import ru.deewend.walkietalkie.TalkingView;
import ru.deewend.walkietalkie.WalkieTalkie;

public class RecorderThread extends Thread {
    public static final String TAG = "RecorderThread";

    private final TalkingView talkingView;
    private final WalkieTalkieThread thread;

    public RecorderThread(TalkingView talkingView) {
        this.talkingView = talkingView;
        this.thread = WalkieTalkie.getInstance().getWTThread();
    }

    private void doLogic() throws IOException {
        AudioRecord audioRecord = talkingView.getAudioRecord();
        int bufferSizeShorts = talkingView.getAudioBufferSize() / 2;
        short[] buffer = new short[bufferSizeShorts];

        int read;
        DataOutputStream outputStream = thread.getVoiceServerOutputStream();
        while ((read = audioRecord.read(buffer, 0, bufferSizeShorts)) >= 0) {
            if (!talkingView.isRecording()) continue;
            System.out.println("read=" + read + ", buffSizeShorts=" + bufferSizeShorts);

            for (int i = 0; i < read; i++) {
                outputStream.writeShort(buffer[i]);
            }
            outputStream.flush();
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
            talkingView.getAudioRecord().release();
            thread.notifyComponentShutdown(this, throwable);
        }
    }
}
