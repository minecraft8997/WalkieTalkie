package ru.deewend.walkietalkie;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.DataOutputStream;

import ru.deewend.walkietalkie.thread.RecorderThread;
import ru.deewend.walkietalkie.thread.WalkieTalkieThread;

public class TalkingView extends View {
    public static final String TAG = "TalkingView";
    public static final int SAMPLE_RATE_HZ = 8000;

    private int audioBufferSize;
    private AudioRecord audioRecord;
    private Paint paint;
    private volatile boolean recording;
    private float buttonX;
    private float buttonY;
    private float buttonRadius;

    public TalkingView(Context context) {
        super(context);
    }

    public TalkingView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TalkingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressLint("MissingPermission") // already checked permissions in MainActivity
    public void initialize() {
        this.audioBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        this.audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufferSize
        );
        this.paint = new Paint();

        setOnTouchListener((v, event) -> {
            float x = event.getX();
            float y = event.getY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (Helper.isInsideCircle(x, y, buttonX, buttonY, buttonRadius)) {
                        startRecording();
                    }

                    break;
                case MotionEvent.ACTION_UP:
                    if (recording) {
                        stopRecording();
                    }
                    v.performClick();

                    break;
                default:
                    break;
            }

            return true;
        });

        audioRecord.startRecording();
        new RecorderThread(this).start();
    }

    private void startRecording() {
        recording = true;
        invalidate();

        double latitude;
        double longitude;
        Location currentLocation = WalkieTalkie.WTLocationListener.getCurrentLocation();
        if (currentLocation != null) {
            latitude = currentLocation.getLatitude();
            longitude = currentLocation.getLongitude();
        } else {
            latitude = 0.0D;
            longitude = 0.0D;
        }

        WalkieTalkieThread thread = WalkieTalkie.getInstance().getWTThread();
        Thread senderThread = new Thread(() -> {
            DataOutputStream stream = thread.getMainServerOutputStream();
            try {
                stream.write(0x00);
                stream.writeUTF(thread.getUsername());
                stream.writeDouble(latitude);
                stream.writeDouble(longitude);
                stream.flush();
            } catch (Exception e) {
                Log.w(TAG, "An exception occurred while sending location data", e);
            }
        });
        senderThread.start();
        while (senderThread.isAlive()) {
            try {
                senderThread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void stopRecording() {
        recording = false;
        invalidate();

        WalkieTalkieThread thread = WalkieTalkie.getInstance().getWTThread();
        Thread senderThread = new Thread(() -> {
            DataOutputStream stream = thread.getMainServerOutputStream();
            try {
                stream.write(0x02);
                stream.writeUTF(thread.getUsername());
                stream.flush();
            } catch (Exception e) {
                Log.w(TAG, "An exception occurred while sending recording finish packet", e);
            }
        });
        senderThread.start();
        while (senderThread.isAlive()) {
            try {
                senderThread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);

        int width = getWidth();
        int height = getHeight();

        // drawing talking button
        buttonX = width / 2.0f;
        buttonRadius = Math.min(width, height) / 3.0f;
        buttonY = (height - buttonRadius) - (height * 0.16f);
        float shadeCircleY = buttonY * 1.04f;
        if (recording) buttonY *= 1.024f;
        paint.setColor(Color.DKGRAY);
        canvas.drawCircle(buttonX, shadeCircleY, buttonRadius, paint);
        paint.setColor(Color.GRAY);
        canvas.drawCircle(buttonX, buttonY, buttonRadius, paint);
    }

    public int getAudioBufferSize() {
        return audioBufferSize;
    }

    public AudioRecord getAudioRecord() {
        return audioRecord;
    }

    public boolean isRecording() {
        return recording;
    }
}
