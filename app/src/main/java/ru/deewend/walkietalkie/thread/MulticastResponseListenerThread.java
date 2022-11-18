package ru.deewend.walkietalkie.thread;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import ru.deewend.walkietalkie.Helper;

public class MulticastResponseListenerThread extends Thread {
    public static final String TAG = "MulticastResponseLis...";

    private final WalkieTalkieThread parent;

    private volatile boolean receivedMagic;
    private volatile InetAddress from;

    public MulticastResponseListenerThread(WalkieTalkieThread parent) {
        this.parent = parent;
    }

    @Override
    public void run() {
        Throwable throwable = null;
        try (DatagramSocket datagramSocket = instantiateSocket()) {
            byte[] buffer = new byte[1];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    datagramSocket.receive(packet);
                } catch (IOException e) {
                    Log.w(TAG, "DatagramPacket receive failed", e);

                    break;
                }
                if (buffer[0] == 0x37) {
                    from = packet.getAddress();
                    receivedMagic = true;
                    synchronized (parent) {
                        parent.notifyAll();
                    }

                    break;
                }
            }
        } catch (Throwable t) {
            throwable = t;
        } finally {
            parent.notifyComponentShutdown(this, throwable);
        }
    }

    private DatagramSocket instantiateSocket() throws SocketException, InterruptedException {
        WalkieTalkieThread global;
        if (parent.parent != null) {
            global = parent.parent;
        } else {
            global = parent;
        }

        //noinspection ConstantConditions
        return global.multicastResponseListener.set(new DatagramSocket(Helper.MULTICAST_PORT));
    }

    public boolean receivedMagic() {
        return receivedMagic;
    }

    public InetAddress getInetAddressFrom() {
        return from;
    }
}
