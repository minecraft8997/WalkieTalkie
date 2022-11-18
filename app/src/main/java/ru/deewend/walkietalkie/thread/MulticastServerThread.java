package ru.deewend.walkietalkie.thread;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import ru.deewend.walkietalkie.Helper;

public class MulticastServerThread extends Thread {
    public static final String TAG = "MulticastServerThread";

    private final WalkieTalkieThread parent;

    public MulticastServerThread(WalkieTalkieThread parent) {
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        Throwable throwable = null;
        //noinspection ConstantConditions
        try (MulticastSocket multicastSocket =
                     (parent.multicastSocket.set(new MulticastSocket(Helper.MULTICAST_PORT)))
        ) {
            InetAddress address = InetAddress.getByName(Helper.MULTICAST_ADDRESS);
            multicastSocket.joinGroup(address);

            byte[] buffer = new byte[1];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastSocket.receive(packet);

                if (buffer[0] == 0x13) {
                    buffer[0] = 0x37; // OK
                } else {
                    buffer[0] = 0x01; // Protocol Error
                }
                try (DatagramSocket socket = new DatagramSocket()) {
                    DatagramPacket responsePacket = new DatagramPacket(
                            buffer, buffer.length, packet.getAddress(), Helper.MULTICAST_PORT);
                    socket.send(responsePacket);
                } catch (IOException e) {
                    Log.w(TAG, "An IOException occurred " +
                            "while sending a response to multicast packet", e);
                }
            }
        } catch (Throwable t) {
            throwable = t;
        } finally {
            parent.notifyComponentShutdown(this, throwable);
        }
    }
}
