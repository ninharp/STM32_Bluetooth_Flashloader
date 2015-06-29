/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.sauernetworks.firmware_updater;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

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
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private boolean nack_received = false;
    private boolean ack_received = false;
    private boolean init_in_progress = false;
    private boolean init_complete = false;
    private boolean get_in_progress = false;
    private boolean get_complete = false;
    private boolean get_read_bytes = false;
    private boolean gvrp_in_progress = false;
    private boolean gvrp_complete = false;
    private boolean gvrp_read_bytes = false;
    private boolean gid_in_progress = false;
    private boolean gid_read_bytes = false;
    private boolean gid_complete = false;
    private boolean go_in_progress = false;
    private boolean go_complete = false;
    private boolean read_in_progress = false;
    private boolean read_complete = false;
    private boolean read_read_bytes = false;
    private boolean version_in_progress = false;
    private boolean version_read_bytes = false;
    private boolean version_complete = false;
    private boolean auto_read_out = false;
    private WaitForAnswer mTask;
    private boolean wait_in_progress = false;

    private int ver_major = 0;
    private int ver_minor = 0;
    private int ver_build = 0;

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BluetoothService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        mContext = context;
    }

    public int getVer_major() {
        return ver_major;
    }

    public void setVer_major(int ver_major) {
        this.ver_major = ver_major;
    }

    public int getVer_minor() {
        return ver_minor;
    }

    public void setVer_minor(int ver_minor) {
        this.ver_minor = ver_minor;
    }

    public int getVer_build() {
        return ver_build;
    }

    public void setVer_build(int ver_build) {
        this.ver_build = ver_build;
    }

    public boolean isAuto_read_out() {
        return auto_read_out;
    }

    public void setAuto_read_out(boolean auto_read_out) {
        this.auto_read_out = auto_read_out;
    }

    public boolean isGid_complete() {
        return gid_complete;
    }

    public void setGid_complete(boolean gid_complete) {
        this.gid_complete = gid_complete;
    }

    public boolean isGid_in_progress() {
        return gid_in_progress;
    }

    public void setGid_in_progress(boolean gid_in_progress) {
        this.gid_in_progress = gid_in_progress;
    }

    public boolean isGid_read_bytes() {
        return gid_read_bytes;
    }

    public void setGid_read_bytes(boolean gid_read_bytes) {
        this.gid_read_bytes = gid_read_bytes;
    }

    public boolean isGvrp_read_bytes() {
        return gvrp_read_bytes;
    }

    public void setGvrp_read_bytes(boolean gvrp_read_bytes) {
        this.gvrp_read_bytes = gvrp_read_bytes;
    }

    public boolean isGvrp_complete() {
        return gvrp_complete;
    }

    public void setGvrp_complete(boolean gvrp_complete) {
        this.gvrp_complete = gvrp_complete;
    }

    public boolean isGvrp_in_progress() {
        return gvrp_in_progress;
    }

    public void setGvrp_in_progress(boolean gvrp_in_progress) {
        this.gvrp_in_progress = gvrp_in_progress;
    }

    public boolean isGet_in_progress() {
        return get_in_progress;
    }

    public void setGet_in_progress(boolean get_in_progress) {
        this.get_in_progress = get_in_progress;
    }

    public boolean isGet_read_bytes() {
        return get_read_bytes;
    }

    public void setGet_read_bytes(boolean get_read_bytes) {
        this.get_read_bytes = get_read_bytes;
    }

    public boolean isWait_in_progress() {
        return wait_in_progress;
    }

    public void setWait_in_progress(boolean wait_in_progress) {
        this.wait_in_progress = wait_in_progress;
    }

    public boolean isInit_in_progress() {
        return init_in_progress;
    }

    public void setInit_in_progress(boolean init_in_progress) {
        this.init_in_progress = init_in_progress;
    }

    public boolean isInit_complete() {
        return init_complete;
    }

    public void setInit_complete(boolean init_complete) {
        this.init_complete = init_complete;
    }

    public boolean isNack_received() {
        return nack_received;
    }

    public void setNack_received(boolean nack_received) {
        this.nack_received = nack_received;
    }

    public boolean isAck_received() {
        return ack_received;
    }

    public void setAck_received(boolean ack_received) {
        this.ack_received = ack_received;
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
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

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
        Log.d(TAG, "connect to: " + device);

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
        Log.d(TAG, "connected, Socket Type:" + socketType);

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
        Log.d(TAG, "stop");

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

    public boolean isGet_complete() {
        return get_complete;
    }

    public void setGet_complete(boolean get_complete) {
        this.get_complete = get_complete;
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
        mTask = new WaitForAnswer("GET Command");
        setGet_in_progress(true);
        int check = (byte) (Constants.STM32_GET_COMMAND ^ (byte) (0xFF));
        sendByte(Constants.STM32_GET_COMMAND);
        sendByte((byte) (check));
        if (!isWait_in_progress()) {
            mTask.execute();
        }
    }

    public void sendGvrpCmd() {
        mTask = new WaitForAnswer("GVRP Command");
        setGvrp_in_progress(true);
        int check = (byte) (Constants.STM32_GVRP_COMMAND ^ (byte) (0xFF));
        sendByte(Constants.STM32_GVRP_COMMAND);
        sendByte((byte) (check));
        if (!isWait_in_progress()) {
            mTask.execute();
        }
    }

    public void sendGIDCmd() {
        mTask = new WaitForAnswer("GID Command");
        setGid_in_progress(true);
        int check = (byte) (Constants.STM32_GET_ID_COMMAND ^ (byte) (0xFF));
        sendByte(Constants.STM32_GET_ID_COMMAND);
        sendByte((byte) (check));
        if (!isWait_in_progress()) {
            mTask.execute();
        }
    }

    public void sendGoCmd() {
        mTask = new WaitForAnswer("GO Command");
        setGo_in_progress(true);
        int check = (byte) (Constants.STM32_GO_COMMAND ^ (byte) (0xFF));
        sendByte(Constants.STM32_GO_COMMAND);
        sendByte((byte) (check));
        if (!isWait_in_progress()) {
            mTask.execute();
        }
    }

    public void sendInit() {
        setInit_in_progress(true);
        mTask = new WaitForAnswer("Init Sequence");
        sendByte(Constants.STM32_INIT);
        if (!isWait_in_progress())
            mTask.execute();
    }

    public void readMemory() {
        mTask = new WaitForAnswer("READ Command");
        setRead_in_progress(true);
        int check = (byte) (Constants.STM32_READ_COMMAND ^ (byte) (0xFF));
        sendByte(Constants.STM32_READ_COMMAND);
        sendByte((byte) (check));
        if (!isWait_in_progress()) {
            mTask.execute();
        }
    }

    public void getVersion() {
        mTask = new WaitForAnswer("Version Command");
        setVersion_in_progress(true);
        send_ml_packet(0x03, "v 0 0");
        if (!isWait_in_progress()) {
            mTask.execute();
        }
        setVersion_read_bytes(true);
    }

    public boolean isGo_in_progress() {
        return go_in_progress;
    }

    public void setGo_in_progress(boolean go_in_progress) {
        this.go_in_progress = go_in_progress;
    }

    public boolean isGo_complete() {
        return go_complete;
    }

    public void setGo_complete(boolean go_complete) {
        this.go_complete = go_complete;
    }

    public boolean isRead_in_progress() {
        return read_in_progress;
    }

    public void setRead_in_progress(boolean read_in_progress) {
        this.read_in_progress = read_in_progress;
    }

    public boolean isRead_complete() {
        return read_complete;
    }

    public void setRead_complete(boolean read_complete) {
        this.read_complete = read_complete;
    }

    public boolean isRead_read_bytes() {
        return read_read_bytes;
    }

    public void setRead_read_bytes(boolean read_read_bytes) {
        this.read_read_bytes = read_read_bytes;
    }

    public boolean isVersion_read_bytes() {
        return version_read_bytes;
    }

    public void setVersion_read_bytes(boolean version_read_bytes) {
        this.version_read_bytes = version_read_bytes;
    }

    public boolean isVersion_in_progress() {
        return version_in_progress;
    }

    public void setVersion_in_progress(boolean version_in_progress) {
        this.version_in_progress = version_in_progress;
    }

    public boolean isVersion_complete() {
        return version_complete;
    }

    public void setVersion_complete(boolean version_complete) {
        this.version_complete = version_complete;
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
            Log.d(TAG, "Socket Type: " + mSocketType +
                    "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

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
            Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
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
        adr = (byte) (0xFF) & adr;
        byte stx = 0x02; // Start Text Zeichen
        int bcc = 0x00; // Leeres BCC
        bcc = bcc ^ stx; // EOR auf STX
        int len = msg.length() + 1; // Adress Byte hinzurechnen
        byte[] m = msg.getBytes();
        bcc = bcc ^ len; // EOR auf LEN
        bcc = bcc ^ adr; // EOR auf ADR
//	    Log.d(TAG, "STX="+Integer.toHexString((0xFF) & stx));
//	    Log.d(TAG, "LEN="+Integer.toHexString((0xFF) & len));
//	    Log.d(TAG, "ADR="+Integer.toHexString((0xFF) & adr));
        int idx = 0;
        while (idx <= len - 2) {
            bcc = bcc ^ m[idx];
            //Log.d(TAG, "DATA["+idx+"]="+Integer.toHexString((0xFF) & m[idx]));
            idx++;
        }
//	    Log.d(TAG, "BCC="+Integer.toHexString((0xFF) & bcc));
        serialCommandBytes = new byte[len + 4];
        serialCommandBytes[0] = (byte) ((0xFF) & stx);
        serialCommandBytes[1] = (byte) ((0xFF) & len);
        serialCommandBytes[2] = (byte) ((0xFF) & adr);
        for (int i = 3; i < (len + 2); i++) {
            serialCommandBytes[i] = (byte) ((0xFF) & m[i - 3]);
//			Log.d(TAG, "serCMD["+i+"]=0x"+Integer.toHexString((0xFF) & m[i-3]));
        }
        serialCommandBytes[len + 2] = (byte) ((0xFF) & bcc);
        serialCommandBytes[len + 3] = (byte) ((0xFF) & 0x0D);
        //serialCommandBytes[len+4] = (byte) ((0xFF) & 0x0A);

        //for (int i = 0; i < serialCommandBytes.length; i++) {
        //	Log.d(TAG, "sDATA["+i+"]=0x"+Integer.toHexString((0xFF) & serialCommandBytes[i]));
        //}
        write(serialCommandBytes);
    }

    private class WaitForAnswer extends AsyncTask<String, Integer, String> {
        String message = null;

        public WaitForAnswer(String s) {
            message = new String(s);
        }

        @Override
        protected String doInBackground(String... arg0) {
            Log.d(TAG, "Starting ACK/NACK waiting task for " + message);
            setWait_in_progress(true);
            long timeBreak = System.currentTimeMillis();
            while (true) {
                long wait_time = Constants.STM32_WAIT_INIT_CHECK;
                if (!isInit_in_progress()) {
                    wait_time = Constants.STM32_WAIT_CMD_CHECK;
                }
                if ((System.currentTimeMillis() - timeBreak) >= wait_time) {
                    if (!isAck_received() || !isNack_received()) {
                        break;
                    }
                }

                if ((System.currentTimeMillis() - timeBreak) >= Constants.STM32_WAIT_TIMEOUT) {
                    Log.d(TAG, "Timeout on waiting for ACK/NACK");
                    break;
                }
            }

            if (isInit_in_progress()) {
                if (isAck_received() && !isNack_received()) {
                    setInit_complete(true);
                    Log.d(TAG, "Init complete");
                    mHandler.obtainMessage(Constants.MESSAGE_INIT_COMPLETE)
                            .sendToTarget();
                } else {
                    setInit_complete(false);
                    Log.d(TAG, "Init failed!");
                }
            }

            if (isGet_in_progress()) {
                if (isAck_received() && !isNack_received()) {
                    setGet_read_bytes(true);
                    Log.d(TAG, "GET ACK");
                } else {
                    Log.d(TAG, "GET NACK");
                }
            }

            if (isGvrp_in_progress()) {
                if (isAck_received() && !isNack_received()) {
                    setGvrp_read_bytes(true);
                    Log.d(TAG, "GVRP ACK");
                } else {
                    Log.d(TAG, "GVRP NACK");
                }
            }

            if (isGid_in_progress()) {
                if (isAck_received() && !isNack_received()) {
                    setGid_read_bytes(true);
                    Log.d(TAG, "GID ACK");
                } else {
                    Log.d(TAG, "GID NACK");
                }
            }

            if (isGo_in_progress() && !isGo_complete()) {
                if (isAck_received() && !isNack_received()) {
                    setGo_complete(true);
                    Log.d(TAG, "GO ACK");
                    long address = Constants.STM32_START_ADDRESS;
                    byte[] buf = new byte[5];
                    buf[0] = (byte) (address >> 24);
                    buf[1] = (byte) ((address >> 16) & 0xFF);
                    buf[2] = (byte) ((address >> 8) & 0xFF);
                    buf[3] = (byte) (address & 0xFF);
                    buf[4] = (byte) (buf[0] ^ buf[1] ^ buf[2] ^ buf[3]);
                    write(buf);
                } else {
                    setGo_complete(false);
                    setGo_in_progress(false);
                    Log.d(TAG, "GO NACK");
                }
            } else if (isGo_in_progress() && isGo_complete()) {
                if (isAck_received() && !isNack_received()) {
                    mHandler.obtainMessage(Constants.MESSAGE_GO_COMPLETE)
                            .sendToTarget();
                    setGo_in_progress(false);
                    setGo_complete(false);
                    Log.d(TAG, "GO Command Complete");
                } else {
                    setGo_in_progress(false);
                    setGo_complete(false);
                    Log.d(TAG, "GO Command failed!");
                }
            }

            if (isRead_in_progress()) {
                if (isAck_received() && !isNack_received()) {
                    long address = Constants.STM32_START_ADDRESS;
                    byte[] buf = new byte[5];
                    buf[0] = (byte) (address >> 24);
                    buf[1] = (byte) ((address >> 16) & 0xFF);
                    buf[2] = (byte) ((address >> 8) & 0xFF);
                    buf[3] = (byte) (address & 0xFF);
                    buf[4] = (byte) (buf[0] ^ buf[1] ^ buf[2] ^ buf[3]);
                    write(buf);
                    setRead_read_bytes(true);
                    Log.d(TAG, "READ ACK");
                } else {
                    Log.d(TAG, "READ NACK");
                }
            }

            if (isVersion_in_progress()) {
                if (isAck_received() && !isNack_received()) {
                    setVersion_read_bytes(true);
                    Log.d(TAG, "Version ACK");
                } else {
                    Log.d(TAG, "Version NACK");
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            setWait_in_progress(false);
            if (isInit_in_progress())
                setInit_in_progress(false);
        }
    }

    public void writeToFile(byte[] array, boolean overwrite) {

        String path = mContext.getFilesDir().toString()+Constants.FIRMWARE_FILENAME;
        String filepath = path + String.format("_%d_%d_build%d", getVer_major(), getVer_minor(), getVer_build()) + Constants.FIRMWARE_EXTENSION;
        //Log.d(TAG, path);
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(filepath, overwrite);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

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
            Log.d(TAG, "create ConnectedThread: " + socketType);
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

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1];
            //byte[] line = new byte[1024];
            //int currPos = 0;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    mmInStream.read(buffer);

                    if (isGet_in_progress() && isGet_read_bytes()) {
                        Log.d(TAG, "In GET data");
                        if (buffer[0] == Constants.STM32_ACK) {
                            mmInStream.read(buffer);
                            byte cmd_count = buffer[0];
                            String temp = String.format("GET: %d Bytes Follow", cmd_count);
                            Log.d(TAG, temp);
                            mmInStream.read(buffer);
                            byte bootloader_version = buffer[0];
                            byte[] get_buffer = new byte[cmd_count];
                            for (int i = 0; i < cmd_count; i++) {
                                mmInStream.read(buffer);
                                get_buffer[i] = buffer[0];
                                //temp = String.format("GET CMD: 0x%02x - %s", buffer[0], commands.getCommandName(buffer[0]));
                                //Log.d(TAG, temp);
                            }
                            mmInStream.read(buffer);
                            if (buffer[0] == Constants.STM32_ACK) {
                                mHandler.obtainMessage(Constants.MESSAGE_BL_VERSION, 1, -1, bootloader_version).sendToTarget();
                                mHandler.obtainMessage(Constants.MESSAGE_GET_COMPLETE, cmd_count, -1, get_buffer).sendToTarget();
                                setGet_in_progress(false);
                                setGet_read_bytes(false);
                                Log.d(TAG, "GET Command success!");
                            } else {
                                setGet_in_progress(false);
                                setGet_read_bytes(false);
                                Log.d(TAG, "GET Command failed!");
                            }
                        } else if (buffer[0] == Constants.STM32_NACK) {
                            setGet_in_progress(false);
                            setGet_read_bytes(false);
                            Log.d(TAG, "GET Command failed!");
                        } else {
                            //String temp = String.format("0x%02x", buffer[0]);
                            //Log.d(TAG, "GET ERR: "+temp);
                        }
                    } else if (isGvrp_in_progress() && isGvrp_read_bytes()) {
                        Log.d(TAG, "In GVRP data");
                        byte[] gvrp_buffer = new byte[3];
                        for (int i = 0; i < 3; i++) {
                            mmInStream.read(buffer);
                            gvrp_buffer[i] = buffer[0];
                            String temp = String.format("GVRP: 0x%02x", buffer[0]);
                            Log.d(TAG, temp);
                        }
                        mmInStream.read(buffer);
                        if (buffer[0] == Constants.STM32_ACK) {
                            setGvrp_in_progress(false);
                            setGvrp_read_bytes(false);
                            setGvrp_complete(true);
                            Log.d(TAG, "GVRP Command success!");
                        } else {
                            setGvrp_in_progress(false);
                            setGvrp_read_bytes(false);
                            Log.d(TAG, "GVRP Command failed!");
                        }
                    } else if (isVersion_in_progress() && isVersion_read_bytes()) {
                        Log.d(TAG, "In Version data");
                        byte[] version_buffer = new byte[5];
                        for (int i = 0; i < 5; i++) {
                            mmInStream.read(buffer);
                            version_buffer[i] = buffer[0];
                            String temp = String.format("Version: 0x%02x", buffer[0]);
                            Log.d(TAG, temp);
                        }
                        mmInStream.read(buffer);
                        if (buffer[0] == Constants.STM32_ACK) {
                            setVersion_in_progress(false);
                            setVersion_read_bytes(false);
                            setVersion_complete(true);
                            if (version_buffer[4] == (version_buffer[0] ^ version_buffer[1] ^ version_buffer[2] ^ version_buffer[3])) {
                                int ver[] = new int[3];
                                ver[0] = version_buffer[0];
                                ver[1] = version_buffer[1];
                                ver[2] = (version_buffer[2] << 8 ) | (version_buffer[3] & 0xff);
                                setVer_major(ver[0]);
                                setVer_minor(ver[1]);
                                setVer_build(ver[2]);
                                mHandler.obtainMessage(Constants.MESSAGE_VERSION_COMPLETE, ver.length, -1, ver).sendToTarget();
                                Log.d(TAG, "Version Command success!");
                            } else {
                                Log.d(TAG, "Version Command CRC failed!");
                            }
                        } else {
                            setVersion_in_progress(false);
                            setVersion_read_bytes(false);
                            Log.d(TAG, "Version Command failed!");
                        }
                    } else if (isGid_in_progress() && isGid_read_bytes()) {
                        Log.d(TAG, "In GID data");
                        mmInStream.read(buffer);
                        byte gid_count = buffer[0];
                        String temp = String.format("GID: %d Bytes Follow", gid_count);
                        Log.d(TAG, temp);
                        mmInStream.read(buffer);
                        byte[] gid_buffer = new byte[gid_count];
                        for (int i = 0; i < gid_count; i++) {
                            mmInStream.read(buffer);
                            gid_buffer[i] = buffer[0];
                            String gid = String.format("GID: 0x%02x", gid_buffer[i]);
                            Log.d(TAG, gid);
                        }
                        mmInStream.read(buffer);
                        if (buffer[0] == Constants.STM32_ACK) {
                            setGid_in_progress(false);
                            setGid_read_bytes(false);
                            setGid_complete(true);
                            mHandler.obtainMessage(Constants.MESSAGE_GID_COMPLETE, gid_count, -1, gid_buffer).sendToTarget();
                            Log.d(TAG, "GID Command success!");
                        } else {
                            setGid_in_progress(false);
                            setGid_read_bytes(false);
                            Log.d(TAG, "GID Command failed!");
                        }
                    } else if (isRead_in_progress() && isRead_read_bytes()) {
                        //Log.d(TAG, "In READ data");
                        if (buffer[0] == Constants.STM32_ACK) {
                            byte[] data = new byte[Constants.STM32_READ_BYTE_COUNT];
                            boolean error = false;
                            long address = Constants.STM32_START_ADDRESS;
                            for (int page = 0; page < Constants.STM32_READ_PAGE_COUNT; page++) {
                                //String add = String.format("0x%08x", address);
                                //Log.d(TAG, "Read address "+add+" on page "+String.valueOf(page));
                                if (page > 0) {
                                    //Log.d(TAG, "Read: Not first reading");
                                    int check = (byte) (Constants.STM32_READ_COMMAND ^ (byte) (0xFF));
                                    sendByte(Constants.STM32_READ_COMMAND);
                                    sendByte((byte) (check));
                                    mmInStream.read(buffer);
                                    if (buffer[0] == Constants.STM32_ACK) {
                                        //Log.d(TAG, "READ ACK CMD PAGE "+String.valueOf(page));
                                        address += Constants.STM32_READ_BYTE_COUNT;
                                        byte[] buf = new byte[5];
                                        buf[0] = (byte) (address >> 24);
                                        buf[1] = (byte) ((address >> 16) & 0xFF);
                                        buf[2] = (byte) ((address >> 8) & 0xFF);
                                        buf[3] = (byte) (address & 0xFF);
                                        buf[4] = (byte) (buf[0] ^ buf[1] ^ buf[2] ^ buf[3]);
                                        write(buf);
                                        mmInStream.read(buffer);
                                        if (buffer[0] == Constants.STM32_ACK) {
                                            //Log.d(TAG, "READ ACK PAGE "+String.valueOf(page));
                                        } else {
                                            Log.d(TAG, "READ NACK PAGE "+String.valueOf(page));
                                            error = true;
                                        }
                                    } else {
                                        error = true;
                                        Log.d(TAG, "READ NACK CMD PAGE "+String.valueOf(page));
                                    }
                                }

                                if (!error) {
                                    // TODO byte count here
                                    sendByte((byte) 0xFF);
                                    sendByte((byte) 0x00);
                                    mmInStream.read(buffer);
                                    if (buffer[0] == Constants.STM32_ACK) {
                                        if (page == 0)
                                            mmInStream.read(buffer); // dunno why, but ACK wont be cleared from stream on first read
                                        int[] buf = new int[2];
                                        buf[0] = page;
                                        for (int i = 0; i < data.length; i++) {
                                            mmInStream.read(buffer);
                                            data[i] = buffer[0];
                                            buf[1] = i;
                                            mHandler.obtainMessage(Constants.MESSAGE_READ_MEMORY_BYTE, buf.length, -1, buf).sendToTarget();
                                            //Log.d(TAG, "Read Data Byte "+String.valueOf(buf[1])+" on page "+String.valueOf(buf[0]));
                                        }
                                        if (page == 0)
                                            writeToFile(data, false);
                                        else
                                            writeToFile(data, true);

                                        //mHandler.obtainMessage(Constants.MESSAGE_GET_COMPLETE, cmd_count, -1, get_buffer).sendToTarget();
                                        //Log.d(TAG, "READ Command success! Page:" + String.valueOf(page));
                                    } else {
                                        Log.d(TAG, "READ Command failed! Page:" + String.valueOf(page));
                                        mHandler.obtainMessage(Constants.MESSAGE_READ_MEMORY_FAILED).sendToTarget();
                                    }
                                } else {
                                    mHandler.obtainMessage(Constants.MESSAGE_READ_MEMORY_FAILED).sendToTarget();
                                }
                            }
                            mHandler.obtainMessage(Constants.MESSAGE_READ_MEMORY_DONE).sendToTarget();
                            Log.d(TAG, "READ Command success!");
                            setRead_in_progress(false);
                            setRead_read_bytes(false);
                        } else if (buffer[0] == Constants.STM32_NACK) {
                            setGet_in_progress(false);
                            setGet_read_bytes(false);
                            Log.d(TAG, "READ Command failed! Address problem");
                        }
                    } else {
                        if (buffer[0] == Constants.STM32_ACK) {
                            setAck_received(true);
                            Log.d(TAG, "ACK");
                            mHandler.obtainMessage(Constants.MESSAGE_ACK_RECEIVED)
                                    .sendToTarget();
                        } else if (buffer[0] == Constants.STM32_NACK) {
                            setNack_received(false);
                            Log.d(TAG, "NACK");
                            mHandler.obtainMessage(Constants.MESSAGE_NACK_RECEIVED)
                                    .sendToTarget();
                        } else {
                            String readMessage = new String(); //readBuf, 0, msg.arg1);
                            readMessage = String.format("0x%02x", buffer[0]);
                            Log.d(TAG, "Read: " + readMessage);
                            // Send the obtained bytes to the UI Activity
                            /*mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                                    .sendToTarget();*/
                        }
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
