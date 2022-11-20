package ru.deewend.walkietalkie;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

import ru.deewend.walkietalkie.thread.IClientThread;
import ru.deewend.walkietalkie.thread.IServerThread;

public class Helper {
    public static final int MULTICAST_PORT =    30924;
    public static final int MAIN_SERVER_PORT =  30925;
    public static final int VOICE_SERVER_PORT = 30926;
    public static final String MULTICAST_ADDRESS = "230.91.22.43";

    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 16;
    public static final int USERNAME_OK = -1;
    public static final int USERNAME_LENGTH_INVALID = -2;

    private Helper() {
    }

    public static void checkOnUIThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException("The method requires to be executed from UI thread!");
        }
    }

    public static void scheduleUITask(Runnable task) {
        Objects.requireNonNull(task);

        Looper mainLooper = Looper.getMainLooper();
        if (Looper.myLooper() != mainLooper) (new Handler(mainLooper)).post(task);
        else                                 task.run();
    }

    public static boolean isInsideCircle(
            float x, float y, float circleX, float circleY, float radius
    ) {
        float dX = (x - circleX);
        float dY = (y - circleY);

        return Math.pow(dX, 2) + Math.pow(dY, 2) <= Math.pow(radius, 2);
    }

    public static void startActivity(Activity from, Class<? extends Activity> clazz) {
        Intent intent = new Intent(from, clazz);
        from.startActivity(intent);
        //from.overridePendingTransition(0, 0);
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public static void broadcastLoop(
            IServerThread parent,
            IClientThread currentThread,
            DataInputStream inputStream,
            String logTag
    ) throws IOException {
        while (true) {
            byte first = inputStream.readByte();
            int available = inputStream.available();
            byte[] packet = new byte[1 + available];
            packet[0] = first;
            //noinspection ResultOfMethodCallIgnored
            inputStream.read(packet, 1, available);
            Log.w("Some server", "Received packet for broadcasting!");

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (parent) {
                for (IClientThread clientThread : parent.getActiveConnections()) {
                    if (clientThread == currentThread || !clientThread.isLoggedIn()) continue;

                    try {
                        clientThread.getOutputStream().write(packet);
                        clientThread.getOutputStream().flush();
                    } catch (IOException e) {
                        Log.w(logTag, "An IOException " +
                                "occurred while sending a voice packet", e);
                    }
                }
            }
        }
    }

    /*
     * Returns USERNAME_OK (-1) if provided String could be
     * considered as a valid user username. If not, it returns either
     * USERNAME_LENGTH_INVALID (-2) or an index of first illegal character the method has noticed.
     *
     * Acceptable symbols are a-z, A-Z, 0-9, '_' and '-' characters.
     */
    public static int checkUsername(String username) {
        if (username.length() < USERNAME_MIN_LENGTH || username.length() > USERNAME_MAX_LENGTH) {
            return USERNAME_LENGTH_INVALID;
        }

        for (int i = 0; i < username.length(); i++) {
            char currentChar = username.charAt(i);

            boolean isLatinLower = (currentChar >= 'a' && currentChar <= 'z');
            boolean isLatinUpper = (currentChar >= 'A' && currentChar <= 'Z');
            boolean isDigit = (currentChar >= '0' & currentChar <= '9');
            boolean isAllowedMisc = (currentChar == '_' || currentChar == '-');

            if (isLatinLower || isLatinUpper || isDigit || isAllowedMisc) continue;

            return i;
        }

        return USERNAME_OK;
    }
}
