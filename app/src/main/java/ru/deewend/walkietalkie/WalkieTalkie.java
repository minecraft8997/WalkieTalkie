package ru.deewend.walkietalkie;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import ru.deewend.walkietalkie.thread.WalkieTalkieThread;

public class WalkieTalkie extends Application {
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
