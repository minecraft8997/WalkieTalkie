package ru.deewend.walkietalkie;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import ru.deewend.walkietalkie.thread.RecorderThread;

public class TalkingView extends View {
    public static final int SAMPLE_RATE_HZ = 11025;

    private TalkingActivity activity;
    private AudioRecord audioRecord;
    private int audioBufferSize;
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
    public void initialize(TalkingActivity activity) {
        this.activity = activity;
        this.audioBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        this.audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,
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
        System.out.println("Starting recording...");
        //audioRecord.startRecording();
        System.out.println("OK...");
        recording = true;
        invalidate();
    }

    private void stopRecording() {
        recording = false;
        //audioRecord.stop();
        invalidate();
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
        float shadeCircleY = buttonY * 1.056f;
        if (recording) buttonY *= 1.024f;
        paint.setColor(Color.DKGRAY);
        canvas.drawCircle(buttonX, shadeCircleY, buttonRadius, paint);
        paint.setColor(Color.GRAY);
        canvas.drawCircle(buttonX, buttonY, buttonRadius, paint);
    }

    public TalkingActivity getActivity() {
        return activity;
    }

    public AudioRecord getAudioRecord() {
        return audioRecord;
    }

    public int getAudioBufferSize() {
        return audioBufferSize;
    }

    public boolean isRecording() {
        return recording;
    }
}
