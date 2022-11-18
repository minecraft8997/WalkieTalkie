package ru.deewend.walkietalkie;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

@SuppressLint("ViewConstructor")
public class TalkingView extends SurfaceView implements SurfaceHolder.Callback {
    private final TalkingActivity activity;
    private final Paint paint;
    private boolean canUseSurfaceHolder;
    private int width;
    private int height;

    public TalkingView(TalkingActivity activity) {
        super(activity);

        this.activity = activity;
        this.paint = new Paint();

        getHolder().addCallback(this);
    }

    public void drawScreen(Canvas canvas) {
        canvas.drawColor(Color.BLACK);

        int width = getWidth();
        int height = getHeight();

        // drawing nickname of speaking user
        paint.setColor(Color.WHITE);
        paint.setTextSize(((float) width / "deewend".length()) * 0.96f);
        canvas.drawText("deewend", width * 0.072f, height * 0.036f, paint);

        // drawing talking button
        float buttonX = width / 2.0f;
        float buttonRadius = Math.min(width, height) / 3.0f;
        float buttonY = (height - buttonRadius) - (height * 0.064f);
        float shadeCircleY = buttonY * 1.024f;
        paint.setColor(Color.DKGRAY);
        canvas.drawCircle(buttonX, shadeCircleY, buttonRadius, paint);
        paint.setColor(Color.GRAY);
        canvas.drawCircle(buttonX, buttonY, buttonRadius, paint);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        canUseSurfaceHolder = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        canUseSurfaceHolder = false;
    }

    public boolean canUseSurfaceHolder() {
        return canUseSurfaceHolder;
    }
}
