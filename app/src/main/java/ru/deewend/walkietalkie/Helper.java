package ru.deewend.walkietalkie;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
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
        from.overridePendingTransition(0, 0);
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public static void broadcastLoop(
            DataInputStream inputStream,
            String logTag
    ) throws IOException {
        IClientThread currentThread = (IClientThread) Thread.currentThread();
        while (true) {
            byte first = inputStream.readByte();
            int available = inputStream.available();
            byte[] packet = new byte[1 + available];
            packet[0] = first;
            //noinspection ResultOfMethodCallIgnored
            inputStream.read(packet, 1, available);

            broadcastPacket(currentThread, true, packet, logTag);
        }
    }

    public static void broadcastPacket(
            IClientThread currentThread, boolean exclude, byte[] packet, String logTag
    ) {
        IServerThread parent = currentThread.getParent();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (parent) {
            for (IClientThread clientThread : parent.getActiveConnections()) {
                if ((exclude && clientThread == currentThread) || !clientThread.isLoggedIn()) {
                    continue;
                }
                try {
                    clientThread.getOutputStream().write(packet);
                    clientThread.getOutputStream().flush();
                } catch (IOException e) {
                    Log.w(logTag, "An IOException occurred while sending a packet", e);
                }
            }
        }
    }

    /*
     * It's highly recommended (however not required) to call this method
     * in UI thread as field "WalkieTalkie.WTLocationListener.currentLocation"
     * it uses is not volatile (for optimization purposes) so the method can return
     * outdated information.
     */
    public static String getDistance(Location to) {
        if (to == null) return "NaN";
        if (to.getLatitude() == 0 && to.getLongitude() == 0) return "NaN";
        Location currentLocation = WalkieTalkie.WTLocationListener.getCurrentLocation();
        if (currentLocation == null) return "NaN";
        if (currentLocation.getLatitude() == 0 && currentLocation.getLongitude() == 0) return "NaN";

        return String.valueOf((int) currentLocation.distanceTo(to));
    }

    public static String join(List<String> list) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            builder.append(list.get(i)).append((i < list.size() - 1) ? ", " : "");
        }

        return builder.toString();
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
