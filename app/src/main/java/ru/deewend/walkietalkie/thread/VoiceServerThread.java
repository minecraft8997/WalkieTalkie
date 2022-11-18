package ru.deewend.walkietalkie.thread;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ru.deewend.walkietalkie.Helper;

public class VoiceServerThread extends Thread implements IServerThread {
    public static class ClientThread extends Thread implements IClientThread {
        public static final String TAG = "VoiceServerThread/CT";

        private final VoiceServerThread parent;
        private final Socket socket;
        private volatile String username;
        private volatile DataOutputStream outputStream;
        private volatile boolean isLoggedIn;

        public ClientThread(VoiceServerThread parent, Socket socket) {
            this.parent = parent;
            this.socket = socket;
        }

        @Override
        public void run() {
            boolean shouldNotReport = false;
            try (Socket socket = this.socket) {
                socket.setTcpNoDelay(true);

                // no need to close these streams manually because
                // they are wrappers over Socket's streams which will be closed
                // automatically on socket close (by try-catch-resources)
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

                // handshaking
                if (inputStream.readInt() != 0xB01DFACE) {
                    throw new ProtocolException("Illegal handshake magic");
                }
                outputStream.writeInt(0x0DDBA11);
                outputStream.writeInt(0xCAFEBABE);
                outputStream.flush();

                // logging in
                String username = inputStream.readUTF();
                if (Helper.checkUsername(username) != Helper.USERNAME_OK) {
                    outputStream.writeBoolean(false);
                    outputStream.flush();

                    return;
                }
                byte[] password = new byte[16];
                if (inputStream.read(password) != 16) {
                    throw new EOFException("Failed to read password");
                }

                // checking received credentials
                WalkieTalkieThread globalParent = this.parent.parent;
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (globalParent) {
                    //noinspection ConstantConditions
                    if (!globalParent.voiceServerPasswordMap.containsKey(username)) {
                        outputStream.writeBoolean(false);
                        outputStream.flush();

                        return;
                    }
                    if (!Arrays.equals(password,
                            globalParent.voiceServerPasswordMap.get(username))
                    ) {
                        outputStream.writeBoolean(false);
                        outputStream.flush();

                        return;
                    }

                    globalParent.voiceServerPasswordMap.remove(username);
                    globalParent.notifyAll();
                }

                // completing logging in
                this.username = username;
                this.outputStream = outputStream;
                this.isLoggedIn = true;
                outputStream.writeBoolean(true);
                outputStream.flush();

                // voice packet handler loop
                Helper.broadcastLoop(parent, this, inputStream, TAG);
            } catch (Throwable t) {
                shouldNotReport =
                        (t instanceof SocketException && Thread.currentThread().isInterrupted());
                if (!shouldNotReport) {
                    Log.w(TAG, "A fatal (for this connection) " +
                            "IOException occurred while handling a client", t);
                }
            } finally {
                if (!shouldNotReport) {
                    parent.handleDisconnect(this);
                } else {
                    synchronized (parent) {
                        parent.activeConnections.remove(this);
                    }
                }
            }
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public DataOutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public boolean isLoggedIn() {
            return isLoggedIn;
        }

        @Override
        public void interrupt() {
            super.interrupt();

            try {
                socket.close();
            } catch (IOException e) {
                Log.w(TAG, "An IOException occurred while closing socket", e);
            }
        }
    }

    public static final String TAG = "VoiceServerThread";

    private final WalkieTalkieThread parent;
    /* package-private */ final List<IClientThread> activeConnections;

    public VoiceServerThread(WalkieTalkieThread parent) {
        this.parent = parent;
        this.activeConnections = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        Throwable throwable = null;
        //noinspection ConstantConditions
        try (ServerSocket serverSocket =
                     (parent.voiceServerSocket.set(new ServerSocket(Helper.VOICE_SERVER_PORT)))
        ) {
            while (true) {
                Socket socket = serverSocket.accept();
                Log.i(TAG, "A new connection from " + socket.getInetAddress());

                ClientThread clientThread = new ClientThread(this, socket);
                synchronized (this) {
                    activeConnections.add(clientThread);
                }
                clientThread.start();
            }
        } catch (Throwable t) {
            throwable = t;
        } finally {
            parent.notifyComponentShutdown(this, throwable);
        }
    }

    @Override
    public List<IClientThread> getActiveConnections() {
        return activeConnections;
    }

    @SuppressWarnings("ConstantConditions")
    private void handleDisconnect(VoiceServerThread.ClientThread clientThread) {
        synchronized (this) {
            activeConnections.remove(clientThread);
        }
        String username = clientThread.username;
        if (username == null) return;
        synchronized (parent) {
            for (IClientThread element : parent.activeConnections) {
                if (username.equals(element.getUsername())) {
                    ((WalkieTalkieThread.ClientThread) element).interrupt();

                    break;
                }
            }
        }
    }
}
