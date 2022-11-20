package ru.deewend.walkietalkie;

import android.app.Activity;
import android.app.Application;
import android.location.Location;
import android.location.LocationListener;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import java.io.DataOutputStream;

import ru.deewend.walkietalkie.thread.WalkieTalkieThread;

public class WalkieTalkie extends Application {
    public static class WTLocationListener implements LocationListener {
        public static final String TAG = "WTLocationListener";

        private static Location currentLocation;

        @Override
        public void onLocationChanged(Location currentLocation) {
            WTLocationListener.currentLocation = currentLocation;

            Activity activity = WalkieTalkie.getInstance().getWTThread().getCurrentActivity();
            if (activity instanceof TalkingActivity) {
                TalkingActivity talkingActivity = (TalkingActivity) activity;
                if (talkingActivity.getTalkingView().isRecording()) {
                    WalkieTalkieThread thread = WalkieTalkie.getInstance().getWTThread();
                    Thread senderThread = new Thread(() -> {
                        DataOutputStream stream = thread.getMainServerOutputStream();
                        try {
                            stream.write(0x01);
                            stream.writeUTF(thread.getUsername());
                            stream.writeDouble(currentLocation.getLatitude());
                            stream.writeDouble(currentLocation.getLongitude());
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
            }
        }

        public static Location getCurrentLocation() {
            return currentLocation;
        }
    }

    public static WalkieTalkie instance;

    private WalkieTalkieThread thread;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    public WalkieTalkieThread getWTThread() {
        return thread;
    }

    public void linkWTThread(WalkieTalkieThread thread) {
        this.thread = thread;
    }

    public static WalkieTalkie getInstance() {
        return instance;
    }
}
