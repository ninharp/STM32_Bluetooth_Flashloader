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

import android.app.ActionBar;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class BluetoothUpdaterFragment extends Fragment {

    private static final String TAG = "STM32_FWU_Fragment";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int DIALOG_DOWNLOAD_PROGRESS = 0;

    // Layout Views
    private Button mInitButton;
    private Button mGetCmdButton;
    private Button mBootloaderButton;
    private Button mGvrpButton;
    private Button mGidButton;
    private Button mGoCmdButton;
    private Button mReadMemoryButton;
    private Button mCheckUpdateButton;
    private Button mDownloadFirmwareButton;
    private TextView mLogTextView;

    private ProgressDialog mProgressDialog;

    private Commands commands = new Commands();

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothService mBluetoothService = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupUI() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mBluetoothService == null) {
            setupUI();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBluetoothService != null) {
            mBluetoothService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mBluetoothService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mBluetoothService.getState() == BluetoothService.STATE_NONE) {
                // Start the Bluetooth chat services
                mBluetoothService.start();
            }
        }
    }

    private void createDialog(int id) {
        switch (id) {
            case DIALOG_DOWNLOAD_PROGRESS:
                mProgressDialog = new ProgressDialog(this.getActivity());
                mProgressDialog.setMessage("Downloading firmware.. (Page 1/2048)");
                mProgressDialog.setMax(100);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_updater, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        //mSendButton = (Button) view.findViewById(R.id.button_send);
        mInitButton = (Button) view.findViewById(R.id.button_init);
        mGetCmdButton = (Button) view.findViewById(R.id.button_get);
        mBootloaderButton = (Button) view.findViewById(R.id.button_bootloader);
        mGvrpButton = (Button) view.findViewById(R.id.button_gvrp);
        mGidButton = (Button) view.findViewById(R.id.button_gid);
        mGoCmdButton = (Button) view.findViewById(R.id.button_gocmd);
        mReadMemoryButton = (Button) view.findViewById(R.id.button_read_memory);
        mDownloadFirmwareButton = (Button) view.findViewById(R.id.button_download_firmware);
        mCheckUpdateButton = (Button) view.findViewById(R.id.button_check_version);
        mLogTextView = (TextView) view.findViewById(R.id.list_log);
        Log("Started!\n");
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupUI() {
        Log.d(TAG, "setupUI()");

        //String message = textView.getText().toString();
        //sendMessage(message);

        mBootloaderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothService.setAuto_read_out(false);
                if (mBluetoothService.getVer_major() == 0) {
                    Log.d(TAG, "Retrieving current Firmware Version");
                    mBluetoothService.getVersion();
                }
                Log.d(TAG, "Sending Bootloader jump command");
                Log("Sending Bootloader jump command");
                mBluetoothService.send_ml_packet(0x03, "y 0 0");
            }
        });

        mInitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothService.setAuto_read_out(false);
                mBluetoothService.sendInit();
                /*
                if (!mBluetoothService.isInit_complete()) {
                    mBluetoothService.sendInit();
                } else {
                    Log.d(TAG, "Init already done!");
                }
                */
            }
        });

        mGetCmdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothService.setAuto_read_out(false);
                mBluetoothService.sendGetCmd();
                /*
                if (mBluetoothService.isInit_complete()) {
                    mBluetoothService.sendGetCmd();
                } else {
                    Log.d(TAG, "Init not sent!");
                    mInitButton.setEnabled(true);
                }
                */
            }
        });

        mGvrpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothService.setAuto_read_out(false);
                mBluetoothService.sendGvrpCmd();
            }
        });

        mGidButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothService.setAuto_read_out(false);
                mBluetoothService.sendGIDCmd();
            }
        });

        mGoCmdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothService.setAuto_read_out(false);
                mBluetoothService.sendGoCmd();
            }
        });

        mReadMemoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                byte[] w = new byte[5];
                for (int i = 0; i < w.length; i++)
                    w[i] = (byte) i;
                mBluetoothService.writeToFile(w);
                */
                mBluetoothService.setAuto_read_out(false);
                mBluetoothService.readMemory();
                createDialog(DIALOG_DOWNLOAD_PROGRESS);
            }
        });

        mCheckUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothService.setAuto_read_out(true);
                Log("Getting running Firmware Version");
                mBluetoothService.getVersion();
            }
        });

        mDownloadFirmwareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothService.setAuto_read_out(true);
                Log("Getting running Firmware Version");
                mBluetoothService.getVersion();
                Log("Sending Bootloader jump command");
                mBluetoothService.send_ml_packet(0x03, "y 0 0");
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mBluetoothService.sendInit();
                    }
                }, 1000);
                //mBluetoothService.readMemory();
                //createDialog(DIALOG_DOWNLOAD_PROGRESS);
            }
        });

        // Initialize the BluetoothService to perform bluetooth connections
        mBluetoothService = new BluetoothService(getActivity(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mBluetoothService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            //mOutEditText.setText(mOutStringBuffer);
        }
    }

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    Log.d(TAG, "Retrieving current Firmware Version");
                                    mBluetoothService.getVersion();
                                }
                            }, 100);
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    /*
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String();//writeBuf);
                    writeMessage = String.format("0x%02x", writeBuf[0]);
                    Log.d(TAG, "Write: "+writeMessage);
                    */
                    break;
                case Constants.MESSAGE_READ:
                    /*
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(); //readBuf, 0, msg.arg1);
                    readMessage = String.format("0x%02x", readBuf[0]);
                    Log.d(TAG, "Read: "+readMessage);
                    */
                    break;
                case Constants.MESSAGE_ACK_RECEIVED:
                    //Log.d(TAG, "ACK Received!");
                    break;
                case Constants.MESSAGE_NACK_RECEIVED:
                    //Log.d(TAG, "NACK Received!");
                    break;
                case Constants.MESSAGE_INIT_COMPLETE:
                    Log.d(TAG, "Init sequence complete!");
                    if (mBluetoothService.isAuto_read_out())
                        mBluetoothService.sendGetCmd();
                    //mInitButton.setEnabled(false);
                    break;
                case Constants.MESSAGE_INIT_FAILED:
                    Log.d(TAG, "Init sequence failed or already sent!");
                    break;
                case Constants.MESSAGE_VERSION_COMPLETE:
                    int[] verBuf = (int[]) msg.obj;
                    String ver = String.format("Firmware Version: %d.%d.%d", verBuf[0], verBuf[1], verBuf[2]);
                    Log(ver);
                    Log.d(TAG, ver);
                    break;
                case Constants.MESSAGE_BL_VERSION:
                    byte readBuf = (byte) msg.obj;
                    String temp = String.format("Bootloader Version: %02x", readBuf);
                    Log(temp);
                    Log.d(TAG, temp);
                    break;
                case Constants.MESSAGE_GET_COMPLETE:
                    byte[] getBuf = (byte[]) msg.obj;
                    if (!mBluetoothService.isGet_complete()) {
                        for (int c = 0; c < getBuf.length; c++) {
                            commands.addCommand(getBuf[c]);
                            String getCommand = String.format("Command: %s", commands.getCommandName(getBuf[c]));
                            Log(getCommand);
                        }

                    } else {
                        Log.d(TAG, "Get Command already done. Not populating log again");
                    }
                    mBluetoothService.setGet_complete(true);
                    if (mBluetoothService.isAuto_read_out())
                        mBluetoothService.sendGIDCmd();
                    break;
                case Constants.MESSAGE_GID_COMPLETE:
                    byte[] gidBuf = (byte[]) msg.obj;
                    for (int c = 0; c < gidBuf.length; c++) {
                        String gid = String.format("Product ID: 0x04%02x", gidBuf[c]);
                        Log(gid);
                        Log.d(TAG, gid);
                    }
                    if (gidBuf[0] == Constants.STM32_PID) {
                        Log("Correct Product ID found! Update possible!");
                        if (mBluetoothService.isAuto_read_out()) {
                            mBluetoothService.readMemory();
                            createDialog(DIALOG_DOWNLOAD_PROGRESS);
                        }
                    } else {
                        Toast.makeText(activity, "Wrong product found! No firmware updates/downloads possible", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_READ_MEMORY_BYTE:
                    int[] numByte = (int[]) msg.obj;
                    //String numByteText = String.format("READ Byte %d on page %d received!", numByte[1], numByte[0]);
                    //Log(numByteText);
                    mProgressDialog.setSecondaryProgress((numByte[0] * 100) / Constants.STM32_READ_PAGE_COUNT);
                    mProgressDialog.setProgress((numByte[0] * 100) / Constants.STM32_READ_BYTE_COUNT);
                    mProgressDialog.setMessage("Downloading firmware..\r\n(Page "+String.valueOf(numByte[0])+"/"+String.valueOf(Constants.STM32_READ_PAGE_COUNT)+")");
                    break;
                case Constants.MESSAGE_READ_MEMORY_DONE:
                    mProgressDialog.dismiss();
                    int kb = (Constants.STM32_READ_PAGE_COUNT * Constants.STM32_READ_BYTE_COUNT)/1024;
                    Toast.makeText(activity, "Download firmware complete! ("+kb+"kb)", Toast.LENGTH_SHORT).show();
                    if (mBluetoothService.isAuto_read_out())
                        mBluetoothService.sendGoCmd();
                    break;
                case Constants.MESSAGE_READ_MEMORY_FAILED:
                    int[] numPage = (int[]) msg.obj;
                    Log.d(TAG, "Downloading firmware failed on Page "+String.valueOf(numPage[0])+" of "+String.valueOf(Constants.STM32_READ_PAGE_COUNT)+" on Byte "+String.valueOf(numPage[1]));
                    mProgressDialog.dismiss();
                    Toast.makeText(activity, "Failed to Download firmware", Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };


    public void Log(String message) {
        mLogTextView.append(message+"\n");
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so setCommand up a chat session
                    setupUI();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    /**
     * Establish connection with other divice
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mBluetoothService.connect(device, secure);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.updater, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.secure_connect_scan:
                Log.d(TAG, "Connect");
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
