package ru.deewend.walkietalkie.thread;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.deewend.walkietalkie.Helper;
import ru.deewend.walkietalkie.InterruptibleResourceHolder;
import ru.deewend.walkietalkie.MainActivity;
import ru.deewend.walkietalkie.TalkingActivity;

public class WalkieTalkieThread extends Thread implements IServerThread {
    public static class ClientThread extends Thread implements IClientThread {
        public static final String TAG = "WalkieTalkieThread/CT";

        private final WalkieTalkieThread parent;
        private final Socket socket;
        private volatile String username;
        private volatile DataOutputStream outputStream;
        private volatile boolean isLoggedIn;

        public ClientThread(WalkieTalkieThread parent, Socket socket) {
            this.parent = parent;
            this.socket = socket;
        }

        @Override
        public void run() {
            boolean shouldNotReport = false;
            try (Socket socket = this.socket) {
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                DataOutputStream outputStream =
                        (this.outputStream = new DataOutputStream(socket.getOutputStream()));

                // handshaking
                if (inputStream.readInt() != 0xCA11AB1E) {
                    throw new ProtocolException("Illegal handshake magic");
                }
                outputStream.writeInt(0x0DDBA11);
                outputStream.flush();

                // logging in
                String username = inputStream.readUTF();
                if (Helper.checkUsername(username) != Helper.USERNAME_OK) {
                    outputStream.writeBoolean(false);
                    outputStream.flush();

                    return;
                }
                synchronized (parent) {
                    //noinspection ConstantConditions
                    for (IClientThread clientThread : parent.activeConnections) {
                        if (username.equalsIgnoreCase(clientThread.getUsername())) {
                            outputStream.writeBoolean(false);
                            outputStream.flush();

                            return;
                        }
                    }

                    this.username = username;
                }
                byte[] voiceServerPassword = new byte[16];
                new SecureRandom().nextBytes(voiceServerPassword);
                synchronized (parent) {
                    //noinspection ConstantConditions
                    parent.voiceServerPasswordMap.put(username, voiceServerPassword);
                }
                outputStream.writeBoolean(true);
                outputStream.write(voiceServerPassword);
                outputStream.flush();

                synchronized (parent) {
                    long end = System.currentTimeMillis() + 15000L;
                    while (true) {
                        parent.wait(Math.max(end - System.currentTimeMillis(), 0L));

                        boolean containsKey = parent.voiceServerPasswordMap.containsKey(username);
                        if (!containsKey) break;
                        if (end - System.currentTimeMillis() <= 0) {
                            parent.voiceServerPasswordMap.remove(username);

                            return;
                        }
                    }
                }
                isLoggedIn = true;
                outputStream.writeBoolean(true);
                outputStream.flush();

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
                        //noinspection ConstantConditions
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

    public static final String TAG = "WalkieTalkieThread";

    public static final byte MODE_CONNECT = 0;
    public static final byte MODE_HOST_AND_CONNECT = 1;

    private final byte mode;
    private final String username;
    private volatile Activity currentActivity;
    private volatile boolean shutdownInitiated;

    /* package-private */ final WalkieTalkieThread parent;

    //================================================================================
    // HOST_AND_CONNECT
    //================================================================================

    private final InterruptibleResourceHolder<ServerSocket> mainServerSocket;
    /* package-private */ final InterruptibleResourceHolder<MulticastSocket> multicastSocket;
    private volatile VoiceServerThread voiceServerThread;
    /* package-private */ final InterruptibleResourceHolder<ServerSocket> voiceServerSocket;
    /* package-private */ final List<IClientThread> activeConnections;
    /* package-private */ final Map<String, byte[]> voiceServerPasswordMap;

    //================================================================================
    // MODE_CONNECT
    //================================================================================

    /* package-private */ final
    InterruptibleResourceHolder<DatagramSocket> multicastResponseListener;
    private final InterruptibleResourceHolder<Socket> mainServerConnectionSocket;
    private final InterruptibleResourceHolder<Socket> voiceServerConnectionSocket;

    private volatile DataOutputStream mainServerOutputStream;
    private volatile DataOutputStream voiceServerOutputStream;
    private volatile boolean isReady;

    public WalkieTalkieThread(byte mode, String username, Activity currentActivity) {
        this.mode = mode;
        this.username = username;
        this.currentActivity = currentActivity;
        this.parent = null;

        if (mode == MODE_HOST_AND_CONNECT) {
            this.activeConnections = new ArrayList<>();
            this.voiceServerPasswordMap = new HashMap<>();
            this.mainServerSocket = new InterruptibleResourceHolder<>();
            this.multicastSocket = new InterruptibleResourceHolder<>();
            this.voiceServerSocket = new InterruptibleResourceHolder<>();
        } else {
            this.activeConnections = null;
            this.voiceServerPasswordMap = null;
            this.mainServerSocket = null;
            this.multicastSocket = null;
            this.voiceServerSocket = null;
        }
        this.multicastResponseListener = new InterruptibleResourceHolder<>();
        this.mainServerConnectionSocket = new InterruptibleResourceHolder<>();
        this.voiceServerConnectionSocket = new InterruptibleResourceHolder<>();
    }

    public WalkieTalkieThread(WalkieTalkieThread parent) {
        this.mode = MODE_CONNECT;
        this.username = parent.username;
        this.parent = parent;
        this.multicastResponseListener = null;
        this.mainServerConnectionSocket = null;
        this.voiceServerConnectionSocket = null;

        this.activeConnections = null;
        this.voiceServerPasswordMap = null;
        this.mainServerSocket = null;
        this.multicastSocket = null;
        this.voiceServerSocket = null;
    }

    private void connect() throws Exception {
        InetAddress hostAddress = findHostAddress();
        if (hostAddress == null) {
            throw new IOException("Не удалось найти комнату в этой сети");
        }

        try (
                Socket mainServerConnectionSocket = ((parent != null ? parent : this)
                        .mainServerConnectionSocket.set(new Socket()));
                Socket voiceServerConnectionSocket = ((parent != null ? parent : this)
                        .voiceServerConnectionSocket.set(new Socket()))
        ) {
            // connecting to main server
            mainServerConnectionSocket.setTcpNoDelay(true);
            mainServerConnectionSocket.connect(
                    new InetSocketAddress(hostAddress, Helper.MAIN_SERVER_PORT));

            // setting up streams from main server connection socket
            DataInputStream mainServerInputStream =
                    new DataInputStream(mainServerConnectionSocket.getInputStream());
            DataOutputStream mainServerOutputStream = (this.mainServerOutputStream =
                    new DataOutputStream(mainServerConnectionSocket.getOutputStream()));

            // handshaking
            mainServerOutputStream.writeInt(0xCA11AB1E);
            mainServerOutputStream.flush();
            if (mainServerInputStream.readInt() != 0x0DDBA11) {
                throw new ProtocolException("Illegal handshake magic (main server)");
            }

            // logging in
            mainServerOutputStream.writeUTF(username);
            mainServerOutputStream.flush();
            boolean loginOK = mainServerInputStream.readBoolean();
            if (!loginOK) {
                throw new RuntimeException("The main server has rejected our login request");
            }

            // receiving a password for further connecting to voice server
            byte[] voiceServerPassword = new byte[16];
            if (mainServerInputStream.read(voiceServerPassword) != 16) {
                throw new EOFException("Failed to read voice server password");
            }

            // connecting to voice server
            voiceServerConnectionSocket.setTcpNoDelay(true);
            voiceServerConnectionSocket.connect(
                    new InetSocketAddress(hostAddress, Helper.VOICE_SERVER_PORT));

            DataInputStream voiceServerInputStream =
                    new DataInputStream(voiceServerConnectionSocket.getInputStream());
            DataOutputStream voiceServerOutputStream =
                    new DataOutputStream(voiceServerConnectionSocket.getOutputStream());

            // handshaking
            voiceServerOutputStream.writeInt(0xB01DFACE);
            voiceServerOutputStream.flush();
            int i1 = voiceServerInputStream.readInt();
            int i2 = voiceServerInputStream.readInt();
            if (i1 != 0x0DDBA11 || i2 != 0xCAFEBABE) {
                throw new ProtocolException("Illegal handshake magic (voice server)");
            }

            // logging in
            voiceServerOutputStream.writeUTF(username);
            voiceServerOutputStream.write(voiceServerPassword);
            voiceServerOutputStream.flush();
            boolean voiceServerLoginOK = voiceServerInputStream.readBoolean();
            if (!voiceServerLoginOK) {
                throw new RuntimeException("The voice server has rejected our login request");
            }
            boolean isEverythingOk = mainServerInputStream.readBoolean();
            if (!isEverythingOk) {
                throw new RuntimeException("Something went wrong while completing logging in");
            }

            // we are now fully logged in! :)
            isReady = true;
            Helper.scheduleUITask(() -> {
                MainActivity activity = (MainActivity) getCurrentActivity();
                activity.changeStateRecursive(true);
                Helper.startActivity(activity, TalkingActivity.class);
                Toast.makeText(getCurrentActivity(),
                        "Всё готово, приятного общения!", Toast.LENGTH_LONG).show();
            });

            while (true) {
                Thread.sleep(10L);
            }
        }
    }

    private InetAddress findHostAddress() throws UnknownHostException {
        if (parent != null) return InetAddress.getByName("localhost");

        MulticastResponseListenerThread responseListenerThread = null;
        try {
            responseListenerThread = new MulticastResponseListenerThread(this);
            responseListenerThread.start();

            Helper.scheduleUITask(() -> Toast.makeText(getCurrentActivity(),
                    "Ищем адрес хост-смартфона...", Toast.LENGTH_SHORT).show());
            byte[] buffer = { 0x13 };
            for (int i = 0; i < 6; i++) {
                if (!responseListenerThread.isAlive()) return null;

                int attemptNumber = i + 1;
                Log.i(TAG, "Finding Host address... Attempt #" + attemptNumber + ".");
                Helper.scheduleUITask(() -> Toast.makeText(getCurrentActivity(),
                        "Попытка #" + attemptNumber, Toast.LENGTH_SHORT).show());

                boolean isSucceeded = sendMulticastPacket(buffer);
                try {
                    if (isSucceeded) {
                        synchronized (this) {
                            long end = System.currentTimeMillis() + 5000L;
                            do {
                                wait(Math.max(end - System.currentTimeMillis(), 0L));

                                if (responseListenerThread.receivedMagic()) {
                                    Log.i(TAG, "Found Host address!");

                                    return responseListenerThread.getInetAddressFrom();
                                }
                            } while (System.currentTimeMillis() < end);
                        }
                    } else {
                        Thread.sleep(5000L);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();

                    Log.w(TAG, "WalkieTalkie client thread " +
                            "(child=false) was requested to shutdown", e);

                    return null;
                }
            }

            return null;
        } finally {
            if (responseListenerThread != null) {
                responseListenerThread.interrupt();
            }
        }
    }

    private boolean sendMulticastPacket(byte[] buffer) {
        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length,
                    InetAddress.getByName(Helper.MULTICAST_ADDRESS), Helper.MULTICAST_PORT);
            socket.send(responsePacket);

            return true;
        } catch (IOException e) {
            Log.w(TAG, "An IOException occurred while sending a multicast packet", e);

            return false;
        }
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void hostAndConnect() throws Exception {
        //noinspection ConstantConditions
        try (ServerSocket serverSocket =
                     mainServerSocket.set(new ServerSocket(Helper.MAIN_SERVER_PORT))
        ) {
            voiceServerThread = new VoiceServerThread(this);
            voiceServerThread.start();
            new MulticastServerThread(this).start();
            new WalkieTalkieThread(this).start();

            while (true) {
                Socket socket = serverSocket.accept();
                Log.i(TAG, "A new connection from " + socket.getInetAddress());

                ClientThread clientThread = new ClientThread(this, socket);
                //noinspection ConstantConditions
                this.activeConnections.add(clientThread);
                clientThread.start();
            }
        }
    }

    @SuppressWarnings({"ConstantConditions", "SynchronizeOnNonFinalField"})
    private void handleDisconnect(WalkieTalkieThread.ClientThread clientThread) {
        String username = clientThread.username;
        synchronized (this) {
            activeConnections.remove(clientThread);
            if (username != null) {
                voiceServerPasswordMap.remove(username);
            }
        }
        if (username == null) return;
        synchronized (voiceServerThread) {
            for (IClientThread element : voiceServerThread.activeConnections) {
                if (username.equals(element.getUsername())) {
                    ((VoiceServerThread.ClientThread) element).interrupt();

                    break;
                }
            }
        }
    }

    /* package-private */ void notifyComponentShutdown(Thread thread, Throwable cause) {
        if (parent != null) {
            parent.notifyComponentShutdown(thread, cause);

            return;
        }

        String componentName;
        if (thread == null) {
            componentName = "UI";
        } else {
            String canonicalName = thread.getClass().getCanonicalName();
            if (canonicalName == null) {
                componentName = "Unknown (local or anonymous class)";
            } else {
                componentName =
                        canonicalName.replace("ru.deewend.walkietalkie.", "");
            }
        }
        if (isSeriousException(thread, cause)) {
            Log.w(TAG, "Component \"" +
                    componentName + "\" reported a fatal exception/error", cause);

            Helper.scheduleUITask(() -> {
                Toast.makeText(currentActivity, "Компонент \"" + componentName +
                        "\" сообщил о фатальной ошибке:", Toast.LENGTH_LONG).show();
                Toast.makeText(currentActivity, cause.getClass() +
                        ": " + cause.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            });
        }
        synchronized (this) {
            if (shutdownInitiated) return;

            shutdownInitiated = true;
        }

        List<Throwable> throwableList = new ArrayList<>();
        if (mode == MODE_HOST_AND_CONNECT) {
            interruptResource(multicastSocket, throwableList);
            if (interruptResource(voiceServerSocket, throwableList)) {
                voiceServerThread.closeConnectionsAsync();
            }
            if (interruptResource(mainServerSocket, throwableList)) {
                closeConnectionsAsync();
            }
        }
        closeClientResourcesAsync(throwableList);

        if (!throwableList.isEmpty()) {
            Helper.scheduleUITask(() ->
                    Toast.makeText(currentActivity, "Во время высвобождения ресурсов " +
                            "возникло " + throwableList.size() + " ошибок", Toast.LENGTH_LONG).show()
            );
            for (Throwable element : throwableList) {
                Log.w(TAG, "An exception/error occurred " +
                        "while closing required resource (mode=" + mode + ")", element);
            }
        }
        Helper.scheduleUITask(() -> {
            Activity activity = getCurrentActivity();
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).changeStateRecursive(true);
            } else {
                Helper.startActivity(activity, MainActivity.class);
            }
        });
    }

    private Activity getCurrentActivity() {
        if (parent != null) return parent.currentActivity;

        return currentActivity;
    }

    private void closeClientResourcesAsync(List<Throwable> throwableList) {
        if (interruptResource(multicastResponseListener, throwableList)) {
            if (interruptResource(mainServerConnectionSocket, throwableList)) {
                interruptResource(voiceServerConnectionSocket, throwableList);
            }
        }
    }

    private boolean interruptResource(
            InterruptibleResourceHolder<?> resourceHolder, List<Throwable> throwableList
    ) {
        boolean wasInitialized = true;
        try {
            wasInitialized = resourceHolder.interrupt();
        } catch (Exception e) {
            throwableList.add(e);
        }

        return wasInitialized;
    }

    private boolean isSeriousException(Thread from, Throwable t) {
        if (t == null) return false;
        if (from == null) return true /* because t != null */;

        return !(t instanceof SocketException) || !shutdownInitiated;
    }

    @Override
    public void run() {
        Throwable throwable = null;
        try {
            if (mode == MODE_CONNECT) connect();
            else hostAndConnect();
        } catch (Throwable t) {
            throwable = t;
        } finally {
            notifyComponentShutdown(this, throwable);
        }
    }

    @Override
    public List<IClientThread> getActiveConnections() {
        return activeConnections;
    }
}
