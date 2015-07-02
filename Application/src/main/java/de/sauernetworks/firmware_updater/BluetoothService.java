package de.sauernetworks.firmware_updater;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import de.sauernetworks.stm32bootloader.Commands;
import de.sauernetworks.stm32bootloader.Protocol;
import de.sauernetworks.tools.Logger;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothService {
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    // Debugging
    private static final String TAG = "STM32_FW_UpdaterBTS";
    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "STM32_FW_UpdaterSecure";
    private static final String NAME_INSECURE = "STM32_FW_UpdaterInsecure";
    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private final Context mContext;
    private final Commands mCommands;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private Logger mLog;

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BluetoothService(Context context, Handler handler, Commands cmds, Logger logger) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        mContext = context;
        mCommands = cmds;
        mLog = logger;
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        mLog.Log("setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        mLog.Log("start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        mLog.Log("connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        mLog.Log("connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        mLog.Log("stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothService.this.start();
    }

    /**
     * Sends a byte
     *
     * @param b A byte to send.
     */
    private void sendByte(byte b) {
        // Check that we're actually connected before trying anything
        /*if (getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this.getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }*/
        // Get the message bytes and tell the BluetoothChatService to write
        byte[] send = new byte[1];
        send[0] = b;
        write(send);
    }

    public void sendGetCmd() {
        mCommands.setGet_in_progress(true);
    }

    public void sendGvrpCmd() {
        mCommands.setGvrp_in_progress(true);
    }

    public void sendGIDCmd() {
        mCommands.setGid_in_progress(true);
    }

    public void sendGoCmd() {
        mCommands.setGo_in_progress(true);
    }

    public void sendEraseCmd() {
        mCommands.setErase_in_progress(true);
    }

    public void sendInit() {
        mCommands.setInit_in_progress(true);
    }

    public void readMemory() {
        mCommands.setRead_in_progress(true);
    }

    public void getVersion() {
        mCommands.setVersion_in_progress(true);
    }

    public void writeMemory() {
        mCommands.setWrite_in_progress(true);
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                            MY_UUID_SECURE);
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            NAME_INSECURE, MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            mLog.Log("Socket Type: " + mSocketType +
                    "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice(),
                                        mSocketType);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            mLog.Log("Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(
                            MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(
                            MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    public void send_ml_packet(int adr, String msg) {
        byte[] serialCommandBytes;
        //adr = (byte) (0xFF) & adr;
        byte stx = 0x02; // Start Text Zeichen
        int bcc = 0x00; // Leeres BCC
        bcc = bcc ^ stx; // EOR auf STX
        int len = msg.length() + 1; // Adress Byte hinzurechnen
        byte[] m = msg.getBytes();
        bcc = bcc ^ len; // EOR auf LEN
        bcc = bcc ^ adr; // EOR auf ADR
        int idx = 0;
        while (idx <= len - 2) {
            bcc = bcc ^ m[idx];
            idx++;
        }
        serialCommandBytes = new byte[len + 4];
        serialCommandBytes[0] = (byte) ((0xFF) & stx);
        serialCommandBytes[1] = (byte) ((0xFF) & len);
        serialCommandBytes[2] = (byte) ((0xFF) & adr);
        for (int i = 3; i < (len + 2); i++) {
            serialCommandBytes[i] = (byte) ((0xFF) & m[i - 3]);
        }
        serialCommandBytes[len + 2] = (byte) ((0xFF) & bcc);
        serialCommandBytes[len + 3] = (byte) ((0xFF) & 0x0D);
        write(serialCommandBytes);
    }

    public boolean writeToFile(byte[] array, boolean overwrite) {
        if (mCommands.getVer_major() > 0) {
            String path = mContext.getFilesDir().toString() + "/" + Constants.FIRMWARE_FILENAME;
            String filepath = path + String.format("_%d_%d_build%d", mCommands.getVer_major(), mCommands.getVer_minor(), mCommands.getVer_build()) + Constants.FIRMWARE_EXTENSION;
            //LogTextView.d(TAG, path);
            FileOutputStream stream;
            try {
                stream = new FileOutputStream(filepath, overwrite);
                try {
                    stream.write(array);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return true;
        }
        else {
            mLog.Log("Version Tag is missing!");
            return false;
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            mLog.Log("create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public int readTimeout(byte[] b, int timeoutMillis) throws IOException  {
            int bufferOffset = 0;
            long maxTimeMillis = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < maxTimeMillis && bufferOffset < b.length) {
                int readLength = java.lang.Math.min(mmInStream.available(),b.length-bufferOffset);
                // can alternatively use bufferedReader, guarded by isReady():
                int readResult = mmInStream.read(b, bufferOffset, readLength);
                if (readResult == -1) break;
                bufferOffset += readResult;
            }
            return bufferOffset;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1];
            int numRead = 0;
            //byte[] line = new byte[1024];
            //int currPos = 0;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    if (mCommands.isInit_in_progress()) {
                        mCommands.setRunning(true);
                        mLog.Log("INIT in Progress!");
                        mmInStream.skip(mmInStream.available());
                        sendByte(Protocol.STM32_INIT);
                        numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                        switch (buffer[0]) {
                            case Protocol.STM32_ACK:
                                mLog.Log("INIT: ACK Received!");
                                mCommands.setInit_in_progress(false);
                                mCommands.setInit_complete(true);
                                mHandler.obtainMessage(Constants.MESSAGE_INIT_COMPLETE).sendToTarget();
                                break;
                            case Protocol.STM32_NACK:
                                mLog.Log("INIT: NACK Received!");
                                mCommands.setInit_in_progress(false);
                                break;
                            default:
                                mLog.Log("INIT: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                                mCommands.setInit_in_progress(false);
                                break;
                        }
                        mCommands.setRunning(false);

                    }

                    if (mCommands.isGet_in_progress()) {
                        mCommands.setRunning(true);
                        mLog.Log("GET Command in Progress!");
                        sendByte(Protocol.STM32_GET_COMMAND);
                        sendByte((byte) (~Protocol.STM32_GET_COMMAND));
                        numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                        switch (buffer[0]) {
                            case Protocol.STM32_ACK:
                                mLog.Log("GET: ACK Received!");
                                numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                byte cmd_count = buffer[0];
                                String temp = String.format("GET: %d Bytes Follow", cmd_count);
                                mLog.Log(temp);
                                numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                byte bootloader_version = buffer[0];
                                byte[] get_buffer = new byte[cmd_count];
                                for (int i = 0; i < cmd_count; i++) {
                                    numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                    get_buffer[i] = buffer[0];
                                    //temp = String.format("GET CMD: 0x%02x - %s", buffer[0], commands.getCommandName(buffer[0]));
                                    //LogTextView.d(TAG, temp);
                                }
                                numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                if (buffer[0] == Protocol.STM32_ACK) {
                                    mHandler.obtainMessage(Constants.MESSAGE_BL_VERSION, 1, -1, bootloader_version).sendToTarget();
                                    mHandler.obtainMessage(Constants.MESSAGE_GET_COMPLETE, cmd_count, -1, get_buffer).sendToTarget();
                                    mCommands.setGet_in_progress(false);
                                    mLog.Log("GET: Command success!");
                                } else {
                                    mCommands.setGet_in_progress(false);
                                    mLog.Log("GET: Command failed!");
                                }
                                break;
                            case Protocol.STM32_NACK:
                                mLog.Log("GET: NACK Received!");
                                mCommands.setGet_in_progress(false);
                                break;
                            default:
                                mLog.Log("GET: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                                mCommands.setGet_in_progress(false);
                                break;
                        }
                        mCommands.setRunning(false);
                    }

                    if (mCommands.isGvrp_in_progress()) {
                        mCommands.setRunning(true);
                        mLog.Log("GVRP Command in Progress!");
                        if (mCommands.isGet_complete() && mCommands.isActiveCommand(Protocol.STM32_GVRP_COMMAND)) {
                            sendByte(Protocol.STM32_GVRP_COMMAND);
                            sendByte((byte) (~Protocol.STM32_GVRP_COMMAND));
                            numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                            switch (buffer[0]) {
                                case Protocol.STM32_ACK:
                                    for (int i = 0; i < 3; i++) {
                                        numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                        String temp = String.format("GVRP: 0x%02x", buffer[0]);
                                        mLog.Log(temp);
                                    }
                                    mmInStream.read(buffer);
                                    if (buffer[0] == Protocol.STM32_ACK) {
                                        mCommands.setGvrp_in_progress(false);
                                        mCommands.setGvrp_complete(true);
                                        mLog.Log("GVRP: Command success!");
                                    } else {
                                        mCommands.setGvrp_in_progress(false);
                                        mLog.Log("GVRP: Command failed!");
                                    }
                                    break;
                                case Protocol.STM32_NACK:
                                    mLog.Log("GVRP: NACK Received!");
                                    mCommands.setGvrp_in_progress(false);
                                    break;
                                default:
                                    mLog.Log("GVRP: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                                    mCommands.setGvrp_in_progress(false);
                                    break;
                            }
                        } else {
                            mCommands.setGvrp_in_progress(false);
                            if (!mCommands.isGet_complete())
                                mLog.Log("GVRP: Error! GET Command not completed!");
                            else
                                mLog.Log("GVRP: Error! GVRP Command not in instruction set!");
                        }
                        mCommands.setRunning(false);
                    }

                    if (mCommands.isGid_in_progress()) {
                        mCommands.setRunning(true);
                        mLog.Log("GID Command in Progress!");
                        if (mCommands.isGet_complete() && mCommands.isActiveCommand(Protocol.STM32_GET_ID_COMMAND)) {
                            sendByte(Protocol.STM32_GET_ID_COMMAND);
                            sendByte((byte) (~Protocol.STM32_GET_ID_COMMAND));
                            numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                            switch (buffer[0]) {
                                case Protocol.STM32_ACK:
                                    numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                    byte gid_count = buffer[0];
                                    String temp = String.format("GID: %d Bytes Follow", gid_count);
                                    mLog.Log(temp);
                                    numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                    byte[] gid_buffer = new byte[gid_count];
                                    for (int i = 0; i < gid_count; i++) {
                                        numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                        gid_buffer[i] = buffer[0];
                                        String gid = String.format("GID: 0x%02x", gid_buffer[i]);
                                        mLog.Log(gid);
                                    }
                                    numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                    if (buffer[0] == Protocol.STM32_ACK) {
                                        mHandler.obtainMessage(Constants.MESSAGE_GID_COMPLETE, gid_count, -1, gid_buffer).sendToTarget();
                                        mCommands.setGid_in_progress(false);
                                        mLog.Log("GID: Command success!");
                                    } else {
                                        mCommands.setGid_in_progress(false);
                                        mLog.Log("GID: Command failed!");
                                    }
                                    break;
                                case Protocol.STM32_NACK:
                                    mLog.Log("GID: NACK Received!");
                                    mCommands.setGid_in_progress(false);
                                    break;
                                default:
                                    mLog.Log("GID: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                                    mCommands.setGid_in_progress(false);
                                    break;
                            }
                        } else {
                            mCommands.setGid_in_progress(false);
                            if (!mCommands.isGet_complete())
                                mLog.Log("GID: Error! GET Command not completed!");
                            else
                                mLog.Log("GID: Error! GID Command not in instruction set!");
                        }
                        mCommands.setRunning(false);
                    }

                    if (mCommands.isGo_in_progress()) {
                        mCommands.setRunning(true);
                        mLog.Log("GO Command in Progress!");
                        if (mCommands.isGet_complete() && mCommands.isActiveCommand(Protocol.STM32_GET_ID_COMMAND)) {
                            sendByte(Protocol.STM32_GO_COMMAND);
                            sendByte((byte) (~Protocol.STM32_GO_COMMAND));
                            numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                            switch (buffer[0]) {
                                case Protocol.STM32_ACK:
                                    long address = Constants.STM32_START_ADDRESS;
                                    byte[] buf = new byte[5];
                                    buf[0] = (byte) (address >> 24);
                                    buf[1] = (byte) ((address >> 16) & 0xFF);
                                    buf[2] = (byte) ((address >> 8) & 0xFF);
                                    buf[3] = (byte) (address & 0xFF);
                                    buf[4] = (byte) (buf[0] ^ buf[1] ^ buf[2] ^ buf[3]);
                                    write(buf);
                                    numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                    if (buffer[0] == Protocol.STM32_ACK) {
                                        mLog.Log("GO: Jump command successed!");
                                        mCommands.setGo_in_progress(false);
                                        mCommands.setGet_complete(false);
                                        mCommands.setInit_complete(false);
                                        mHandler.obtainMessage(Constants.MESSAGE_GO_COMPLETE).sendToTarget();
                                    } else if (buffer[0] == Protocol.STM32_NACK) {
                                        mLog.Log("GO: NACK Received!");
                                        mCommands.setGo_in_progress(false);
                                    } else {
                                        mLog.Log("GO: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                                        mCommands.setGo_in_progress(false);
                                    }
                                    break;
                                case Protocol.STM32_NACK:
                                    mLog.Log("GO: NACK Received!");
                                    mCommands.setGo_in_progress(false);
                                    break;
                                default:
                                    mLog.Log("GO: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                                    mCommands.setGo_in_progress(false);
                                    break;
                            }
                        } else {
                            mCommands.setGo_in_progress(false);
                            if (!mCommands.isGet_complete())
                                mLog.Log("GO: Error! GET Command not completed!");
                            else
                                mLog.Log("GO: Error! GO Command not in instruction set!");
                        }
                        mCommands.setRunning(false);
                    }

                    if (mCommands.isVersion_in_progress()) {
                        mCommands.setRunning(true);
                        mLog.Log("VERSION Command in Progress!");
                        numRead = (int) mmInStream.skip(mmInStream.available());
                        send_ml_packet(0x03, "v 0 0");
                        numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                        switch (buffer[0]) {
                            case Protocol.STM32_ACK:
                                byte[] version_buffer = new byte[5];
                                for (int i = 0; i < 5; i++) {
                                    numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                    version_buffer[i] = buffer[0];
                                }
                                numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                switch (buffer[0]) {
                                    case Protocol.STM32_ACK:
                                        mCommands.setVersion_in_progress(false);
                                        mCommands.setVersion_complete(true);
                                        if ((version_buffer[0] ^ version_buffer[1] ^ version_buffer[2] ^ version_buffer[3]) == version_buffer[4]) {
                                            int ver[] = new int[3];
                                            ver[0] = version_buffer[0];
                                            ver[1] = version_buffer[1];
                                            ver[2] = (version_buffer[2] << 8) | (version_buffer[3] & 0xff);
                                            mCommands.setVer_major(ver[0]);
                                            mCommands.setVer_minor(ver[1]);
                                            mCommands.setVer_build(ver[2]);
                                            mHandler.obtainMessage(Constants.MESSAGE_VERSION_COMPLETE, ver.length, -1, ver).sendToTarget();
                                            mCommands.setVersion_in_progress(false);
                                            mLog.Log("Version Command success (" + String.format("%d.%db%d", ver[0], ver[1], ver[2]) + ")!");
                                        } else {
                                            mCommands.setVersion_in_progress(false);
                                            mLog.Log("Version Command CRC failed!");
                                        }
                                        break;
                                    default:
                                        mCommands.setVersion_in_progress(false);
                                        mLog.Log("Version Command failed!");
                                        break;
                                }
                                break;
                            default:
                                mLog.Log("VERSION: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                                mCommands.setVersion_in_progress(false);
                                break;
                        }
                        mCommands.setRunning(false);
                    }

                    if (mCommands.isRead_in_progress()) {
                        mCommands.setRunning(true);
                        mLog.Log("READ Command in Progress!");
                        if (mCommands.isGet_complete() && mCommands.isActiveCommand(Protocol.STM32_READ_COMMAND)) {
                            long address = Constants.STM32_START_ADDRESS;
                            label:
                            for (int page = 0; page < Constants.STM32_PAGE_COUNT; page++) {
                                sendByte(Protocol.STM32_READ_COMMAND);
                                sendByte((byte) (~Protocol.STM32_READ_COMMAND));
                                numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                switch (buffer[0]) {
                                    case Protocol.STM32_ACK:
                                        byte[] buf = new byte[5];
                                        buf[0] = (byte) (address >> 24);
                                        buf[1] = (byte) ((address >> 16) & 0xFF);
                                        buf[2] = (byte) ((address >> 8) & 0xFF);
                                        buf[3] = (byte) (address & 0xFF);
                                        buf[4] = (byte) (buf[0] ^ buf[1] ^ buf[2] ^ buf[3]);
                                        write(buf);
                                        numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                        address += Constants.STM32_BYTE_COUNT;
                                        switch (buffer[0]) {
                                            case Protocol.STM32_ACK:
                                                sendByte((byte) (Constants.STM32_BYTE_COUNT - 1));
                                                sendByte((byte) ~(Constants.STM32_BYTE_COUNT - 1));
                                                numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                                switch (buffer[0]) {
                                                    case Protocol.STM32_ACK:
                                                        int[] dataBuf = new int[2];
                                                        dataBuf[0] = page;
                                                        byte[] data = new byte[Constants.STM32_BYTE_COUNT];
                                                        for (int i = 0; i < data.length; i++) {
                                                            int bytes = mmInStream.read(buffer);
                                                            if (bytes > 0) {
                                                                data[i] = buffer[0];
                                                                dataBuf[1] = i;
                                                                mHandler.obtainMessage(Constants.MESSAGE_READ_MEMORY_BYTE, dataBuf.length, -1, dataBuf).sendToTarget();
                                                                //LogTextView.d(TAG, "Read Data Byte "+String.valueOf(dataBuf[1])+" on page "+String.valueOf(dataBuf[0]));
                                                            }
                                                        }
                                                        if (page == 0)
                                                            writeToFile(data, false);
                                                        else
                                                            writeToFile(data, true);
                                                        break;
                                                }
                                                break;
                                            default:
                                                mLog.Log("READ: Address Error on Read PAGE " + String.valueOf(page) + " [" + String.format("0x%02x", buffer[0]) + "]");
                                                mHandler.obtainMessage(Constants.MESSAGE_READ_MEMORY_FAILED).sendToTarget();
                                                mCommands.setRead_in_progress(false);
                                                mCommands.setGo_in_progress(true);
                                                break label;
                                        }
                                        break;
                                    default:
                                        mLog.Log("READ: Command Error on Read PAGE " + String.valueOf(page) + " [" + String.format("0x%02x", buffer[0]) + "]");
                                        mHandler.obtainMessage(Constants.MESSAGE_READ_MEMORY_FAILED).sendToTarget();
                                        mCommands.setRead_in_progress(false);
                                        mCommands.setGo_in_progress(true);
                                        break label;
                                }
                            }
                            mHandler.obtainMessage(Constants.MESSAGE_READ_MEMORY_COMPLETE).sendToTarget();
                            mLog.Log("READ: Command success!");
                            mCommands.setRead_in_progress(false);
                        } else {
                            mCommands.setRead_in_progress(false);
                            if (!mCommands.isGet_complete())
                                mLog.Log("READ: Error! GET Command not completed!");
                            else
                                mLog.Log("READ: Error! Read Memory Command not in instruction set! (Maybe readout protected)");
                        }
                        mCommands.setRunning(false);
                    }

                    if (mCommands.isErase_in_progress()) {
                        mCommands.setRunning(true);
                        mLog.Log("EER Command in Progress!");
                        if (mCommands.isGet_complete() && mCommands.isActiveCommand(Protocol.STM32_EER_COMMAND)) {
                            sendByte(Protocol.STM32_EER_COMMAND);
                            sendByte((byte) (~Protocol.STM32_EER_COMMAND));
                            readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                            switch (buffer[0]) {
                                case Protocol.STM32_ACK:
                                    byte[] eerBuf = new byte[3];
                                    eerBuf[0] = (byte) 0xFF;
                                    eerBuf[1] = (byte) 0xFF;
                                    eerBuf[2] = (byte) (eerBuf[0] ^ eerBuf[1]);
                                    write(eerBuf);
                                    readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                    if (buffer[0] == Protocol.STM32_ACK) {
                                        mCommands.setErase_in_progress(false);
                                        mCommands.setErase_complete(true);
                                        mHandler.obtainMessage(Constants.MESSAGE_ERASE_MEMORY_COMPLETE).sendToTarget();
                                        mLog.Log("EER: Erase of Memory completed!");
                                    } else if (buffer[0] == Protocol.STM32_NACK) {
                                        mCommands.setErase_in_progress(false);
                                    } else {
                                        mLog.Log("EER: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                                        mCommands.setErase_in_progress(false);
                                    }
                                    break;
                                case Protocol.STM32_NACK:
                                    mLog.Log("EER: NACK Received!");
                                    mCommands.setErase_in_progress(false);
                                    break;
                                default:
                                    mLog.Log("EER: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                                    mCommands.setErase_in_progress(false);
                                    break;
                            }
                        } else {
                            mCommands.setErase_in_progress(false);
                            if (!mCommands.isGet_complete())
                                mLog.Log("EER: Error! GET Command not completed!");
                            else
                                mLog.Log("EER: Error! Extended Erase Command not in instruction set!");
                        }
                        mCommands.setRunning(false);
                    }

                    if (mCommands.isWrite_in_progress()) {
                        mCommands.setRunning(true);
                        boolean error = false;
                        mLog.Log("WRITE Command in Progress!");
                        if (mCommands.isGet_complete() && mCommands.isActiveCommand(Protocol.STM32_WRITE_COMMAND)) {
                            long address = Constants.STM32_START_ADDRESS;
                            BufferedInputStream firmwareBuf = null;
                            String path = mContext.getFilesDir().toString() + "/" +Constants.FIRMWARE_FILENAME;
                            long[] errBuff = new long[3];
                            //String filepath = path + "_1_4_build804" + Constants.FIRMWARE_EXTENSION;
                            String filepath = path + Constants.FIRMWARE_EXTENSION;
                            File file = new File(filepath);
                            long size = file.length();
                            errBuff[2] = size;
                            mLog.Log(String.format("WRITE: Firmware File Size: %d bytes (%d bytes)", size, (Constants.STM32_BYTE_COUNT * Constants.STM32_PAGE_COUNT)));
                            mHandler.obtainMessage(Constants.MESSAGE_WRITE_START, 1, -1, size).sendToTarget();
                            int firmwareOffset = 0;
                            byte firmwareChecksum;
                            //int firmwareSize = (int) file.length();
                            //byte[] firmwareData = new byte[firmwareSize];
                            byte[] firmwareData = new byte[Constants.STM32_BYTE_COUNT];
                            try {
                                firmwareBuf = new BufferedInputStream(new FileInputStream(file));
                            } catch (FileNotFoundException e) {
                                mLog.Log("WRITE: Cannot find/read firmware file ("+filepath+")");
                                mCommands.setWrite_in_progress(false);
                                mHandler.obtainMessage(Constants.MESSAGE_WRITE_MEMORY_FILE_ERROR).sendToTarget();
                                e.printStackTrace();
                                break;
                            }
                            for (int page = 0; page < Constants.STM32_PAGE_COUNT; page++) {
                                if (firmwareBuf.available() <= 0) {
                                    mLog.Log("WRITE: File completely written");
                                    break;
                                }
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                sendByte(Protocol.STM32_WRITE_COMMAND);
                                sendByte((byte) (~Protocol.STM32_WRITE_COMMAND));
                                readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                switch (buffer[0]) {
                                    case Protocol.STM32_ACK:
                                        byte[] buf = new byte[5];
                                        buf[0] = (byte) (address >> 24);
                                        buf[1] = (byte) ((address >> 16) & 0xFF);
                                        buf[2] = (byte) ((address >> 8) & 0xFF);
                                        buf[3] = (byte) (address & 0xFF);
                                        buf[4] = (byte) (buf[0] ^ buf[1] ^ buf[2] ^ buf[3]);
                                        write(buf);
                                        readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                        address += Constants.STM32_BYTE_COUNT;
                                        if (buffer[0] == Protocol.STM32_ACK) {
                                            int[] dataBuf = new int[2];
                                            dataBuf[0] = page;
                                            int countData = firmwareBuf.read(firmwareData, 0, firmwareData.length);
                                            mLog.LogF("Read "+String.valueOf(countData)+" bytes from firmware file!");
                                            sendByte((byte) (Constants.STM32_BYTE_COUNT - 1)); // write 256 bytes
                                            firmwareChecksum = (byte) (Constants.STM32_BYTE_COUNT - 1);
                                            if (countData < firmwareData.length) {
                                                mLog.LogF("File at end! Fillig with 0xff");
                                                for (int i = countData; i < firmwareData.length; i++)
                                                    firmwareData[i] = (byte) 0xFF;
                                                page = Constants.STM32_PAGE_COUNT;
                                            } else if (countData == -1) {
                                                error = true;
                                                mLog.Log("WRITE: File read Error on Write PAGE " + String.valueOf(page) + " [" + String.format("0x%02x", buffer[0]) + "]");
                                                errBuff[0] = page;
                                                errBuff[1] = countData;
                                                mHandler.obtainMessage(Constants.MESSAGE_WRITE_MEMORY_FAILED, errBuff.length, -1, errBuff).sendToTarget();
                                                mCommands.setWrite_in_progress(false);
                                                //mCommands.setGo_in_progress(true);
                                                if (firmwareBuf != null)
                                                    firmwareBuf.close();
                                                break;
                                            }

                                            //
                                            for (int i = 0; i < firmwareData.length; i++) {
                                                firmwareChecksum = (byte) (firmwareChecksum ^ firmwareData[i]);
                                                dataBuf[1] = i;
                                                //LogTextView.d(TAG, "Read Data Byte "+String.valueOf(dataBuf[1])+" on page "+String.valueOf(dataBuf[0]));
                                            }
                                            write(firmwareData);
                                            sendByte(firmwareChecksum);
                                            readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                            if (buffer[0] == Protocol.STM32_ACK) {
                                                mLog.LogF("WRITE: Written Offset "+String.valueOf(firmwareOffset)+" successfully");
                                            } else {
                                                error = true;
                                                mLog.Log(String.format("WRITE: Error on Writing Offset %d [0x%02x]", firmwareOffset, buffer[0]));
                                                errBuff[0] = page;
                                                errBuff[1] = countData;
                                                mHandler.obtainMessage(Constants.MESSAGE_WRITE_MEMORY_FAILED, errBuff.length, -1, errBuff).sendToTarget();
                                                mCommands.setWrite_in_progress(false);
                                                //mCommands.setGo_in_progress(true);
                                                if (firmwareBuf != null)
                                                    firmwareBuf.close();
                                                break;
                                            }
                                            mHandler.obtainMessage(Constants.MESSAGE_WRITE_MEMORY_BYTE, dataBuf.length, -1, dataBuf).sendToTarget();
                                            firmwareOffset += firmwareData.length;
                                        /*
                                        if (countData == firmwareData.length) {

                                        } else {
                                            error = true;
                                            LogTextView.d(TAG, "WRITE: Firmware File Error on Write PAGE "+String.valueOf(page)+" ["+String.format("0x%02x", buffer[0])+"]");
                                            mHandler.obtainMessage(Constants.MESSAGE_WRITE_MEMORY_FAILED).sendToTarget();
                                            mCommands.setWrite_in_progress(false);
                                            //mCommands.setGo_in_progress(true);
                                            if (firmwareBuf != null)
                                                firmwareBuf.close();
                                            break;
                                        }
                                        */
                                        } else {
                                            error = true;
                                            mLog.Log("WRITE: Address Error on Write PAGE " + String.valueOf(page) + " [" + String.format("0x%02x", buffer[0]) + "]");
                                            errBuff[0] = page;
                                            errBuff[1] = -1;
                                            mHandler.obtainMessage(Constants.MESSAGE_WRITE_MEMORY_FAILED, errBuff.length, -1, errBuff).sendToTarget();
                                            mCommands.setWrite_in_progress(false);
                                            //mCommands.setGo_in_progress(true);
                                            if (firmwareBuf != null)
                                                firmwareBuf.close();
                                            break;
                                        }
                                        break;
                                    default:
                                        error = true;
                                        mLog.Log("WRITE: Command Error on Write PAGE " + String.valueOf(page) + " [" + String.format("0x%02x", buffer[0]) + "]");
                                        errBuff[0] = page;
                                        errBuff[1] = -1;
                                        mHandler.obtainMessage(Constants.MESSAGE_WRITE_MEMORY_FAILED, errBuff.length, -1, errBuff).sendToTarget();
                                        mCommands.setWrite_in_progress(false);
                                        //mCommands.setGo_in_progress(true);
                                        if (firmwareBuf != null)
                                            firmwareBuf.close();
                                        break;
                                }
                            }
                            if (firmwareBuf != null)
                                firmwareBuf.close();
                            if (!error) {
                                mCommands.setWrite_complete(true);
                                mHandler.obtainMessage(Constants.MESSAGE_WRITE_MEMORY_COMPLETE, 1, -1, size).sendToTarget();
                                mLog.Log("WRITE: Command success!");
                                mCommands.setGo_in_progress(true);
                            }
                            mCommands.setWrite_in_progress(false);
                        } else {
                            mCommands.setWrite_in_progress(false);
                            if (!mCommands.isGet_complete())
                                mLog.Log("WRITE: Error! GET Command not completed!");
                            else
                                mLog.Log("WRITE: Error! Write Memory Command not in instruction set! (Maybe write protected!)");
                        }
                        mCommands.setRunning(false);
                    }

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothService.this.start();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            /*for (int i = 0; i < buffer.length; i++) {
                LogTextView.d(TAG, String.format("write(0x%02x)", buffer[i]));
            }*/
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
