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

package de.sauernetworks.stm_bluetooth_flashloader;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import de.sauernetworks.stm_bootloader.Commands;
import de.sauernetworks.tools.AboutDialog;
import de.sauernetworks.tools.FileDialog;
import de.sauernetworks.tools.Logger;

import static de.sauernetworks.stm_bootloader.Protocol.*;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class BluetoothUpdaterFragment extends Fragment {

    private static final String TAG = "STM_Bluetooth_Flashloader";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int DIALOG_DOWNLOAD_PROGRESS = 0;
    private static final int DIALOG_UPLOAD_PROGRESS = 1;
    private static final int DIALOG_CONNECT_PROGRESS = 2;
    private static final int DIALOG_ERASE_PROGRESS = 3;

    private long firmware_size = 0;

    // Layout Views
    private Button mInitButton;
    private Button mGetCmdButton;
    private Button mBootloaderButton;
    private Button mGvrpButton;
    private Button mGidButton;
    private Button mGoCmdButton;
    private Button mReadMemoryButton;
    private Button mEraseMemoryButton;
    private Button mWriteMemoryButton;
    private Button mCheckUpdateButton;
    private Button mDownloadMemoryButton;
    private Button mUploadMemoryButton;
    private TextView mLogTextView;

    private ProgressDialog mProgressDialog;

    private String mConnectedDeviceName = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothService mBluetoothService = null;

    private Commands mCommands;
    private Logger mLog;
    private SharedPreferences sharedPrefs;
    FileDialog fileDialog;
    /* Preferences */
    private static String prefShowDeviceMatch;
    private String prefSkipBytes;
    private boolean prefFullRead = false;
    private boolean prefLog = true;
    private boolean prefSyslog = false;
    private static boolean prefShowOnlyMatchingDevices = false;
    private boolean prefResetWrite = true;
    private boolean prefEraseAll = true;
    private boolean prefDebug = true;
    private boolean prefResetRead = true;
    private boolean prefSendBootloaderCommand = true;
    private static String prefBootloaderCommand;
    private String prefBootloaderInitDelay;

    long timerTemp = 0;
    long timeReadMemory = 0;
    long timeWriteMemory = 0;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mLog = new Logger(this.getActivity(), true, true, TAG, 9);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }

        File mPath = new File(Environment.getExternalStorageDirectory() + "//STM32//");
        fileDialog = new FileDialog(this.getActivity(), mPath);
        fileDialog.setFileEndsWith(".bin");
        //fileDialog.addFileListener(new FileDialog.FileSelectedListener() {
        //    public void fileSelected(File file) {
        //        mLog.LogF("selected file " + file.toString());
        //    }
        //});
        //fileDialog.addDirectoryListener(new FileDialog.DirectorySelectedListener() {
        //  public void directorySelected(File directory) {
        //      Log.d(getClass().getName(), "selected dir " + directory.toString());
        //  }
        //});
        //fileDialog.setSelectDirectoryOption(false);
        //fileDialog.showDialog();

        readConfiguration();
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

    public void setCommands(Commands cmds) {
        this.mCommands = cmds;
    }

    private void createDialog(int id) {
        switch (id) {
            case DIALOG_CONNECT_PROGRESS:
                mProgressDialog = new ProgressDialog(this.getActivity());
                mProgressDialog.setMessage("Connecting..");
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
                break;
            case DIALOG_ERASE_PROGRESS:
                mProgressDialog = new ProgressDialog(this.getActivity());
                mProgressDialog.setMessage("Erasing Memory..");
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
                break;
            case DIALOG_DOWNLOAD_PROGRESS:
                mProgressDialog = new ProgressDialog(this.getActivity());
                mProgressDialog.setMessage("Downloading memory..\n(1/"+String.valueOf((STM32_PAGE_COUNT * STM32_BYTE_COUNT) / 1024)+" kb)");
                mProgressDialog.setMax(STM32_PAGE_COUNT);
                mProgressDialog.setProgressNumberFormat("%1d of %2d Pages read");
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
                break;
            case DIALOG_UPLOAD_PROGRESS:
                mProgressDialog = new ProgressDialog(this.getActivity());
                /*if (firmware_size > 1024)
                    mProgressDialog.setMessage("Uploading memory..\n(1/"+String.valueOf(firmware_size / 1024)+" kb)");
                else*/
                mProgressDialog.setMessage("Uploading memory..\n(1/" + String.valueOf(firmware_size) + " bytes)");
                if ((firmware_size / 256) <= 1)
                    mProgressDialog.setMax(1);
                else
                    mProgressDialog.setMax((int)(firmware_size/ STM32_BYTE_COUNT));
                mProgressDialog.setProgressNumberFormat("%1d of %2d Pages written");
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
                break;
        }
    }

    private void closeDialog() {
        if (mProgressDialog != null)
            if (mProgressDialog.isShowing())
                mProgressDialog.dismiss();
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
        mEraseMemoryButton = (Button) view.findViewById(R.id.button_erase_memory);
        mWriteMemoryButton = (Button) view.findViewById(R.id.button_write_memory);
        mDownloadMemoryButton = (Button) view.findViewById(R.id.button_download_memory);
        mUploadMemoryButton = (Button) view.findViewById(R.id.button_upload_memory);
        mCheckUpdateButton = (Button) view.findViewById(R.id.button_check_version);
        mLogTextView = (TextView) view.findViewById(R.id.list_log);
        LogTextView("Started!\n");
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupUI() {
        mLog.Log("setupUI()");

        //String message = textView.getText().toString();
        //sendMessage(message);

        mBootloaderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCommands.setAuto_read_out(false);
                mCommands.setAuto_write_to(false);
                mLog.Log("Sending Bootloader jump command");
                LogTextView("Sending Bootloader jump command");
                mBluetoothService.send_ml_packet(0x03, "y 0 0");
            }
        });

        mInitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCommands.setAuto_read_out(false);
                mCommands.setAuto_write_to(false);
                mBluetoothService.sendInit();
            }
        });

        mGetCmdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCommands.setAuto_read_out(false);
                mCommands.setAuto_write_to(false);
                mBluetoothService.sendGetCmd();
            }
        });

        mGvrpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCommands.setAuto_read_out(false);
                mCommands.setAuto_write_to(false);
                mBluetoothService.sendGvrpCmd();
            }
        });

        mGidButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCommands.setAuto_read_out(false);
                mCommands.setAuto_write_to(false);
                mBluetoothService.sendGIDCmd();
            }
        });

        mGoCmdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCommands.setAuto_read_out(false);
                mBluetoothService.sendGoCmd();
            }
        });

        mReadMemoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCommands.setAuto_read_out(false);
                mCommands.setAuto_write_to(false);
                mBluetoothService.readMemory();
            }
        });

        mEraseMemoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCommands.setAuto_read_out(false);
                mCommands.setAuto_write_to(false);
                mBluetoothService.sendEraseCmd();
            }
        });

        mWriteMemoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCommands.setAuto_read_out(false);
                mCommands.setAuto_write_to(false);
                mBluetoothService.writeMemory();
            }
        });

        mCheckUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LogTextView("Getting running Firmware Version");
                mBluetoothService.getVersion();
            }
        });

        mDownloadMemoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Handler handler = new Handler();
                long initDelay = 1000;
                long initDelayPref = Integer.parseInt(getPrefBootloaderInitDelay());
                if (initDelayPref > -1 && initDelayPref < 2001) {
                    mLog.Log(8, "Setting Init delay value to " + getPrefBootloaderInitDelay());
                    initDelay = initDelayPref;
                } else {
                    mLog.Log(8, "Init Delay not in Range. Setting default value of 1000ms");
                }
                mCommands.setAuto_read_out(true);
                if (isPrefSendBootloaderCommand()) {
                    //mBluetoothService.getVersion(); // MagicLight specific command
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothService.send_ml_packet(0x03, "y 0 0");
                        }
                    }, 500);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothService.sendInit();
                        }
                    }, initDelay + 1);
                } else {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothService.sendInit();
                        }
                    }, 500);
                }
            }
        });

        mUploadMemoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Handler handler = new Handler();
                long initDelay = 1000;
                long initDelayPref = Integer.parseInt(getPrefBootloaderInitDelay());
                if (initDelayPref > -1 && initDelayPref < 2001) {
                    mLog.Log(8, "Setting Init delay value to " + getPrefBootloaderInitDelay());
                    initDelay = initDelayPref;
                } else {
                    mLog.Log(8, "Init Delay not in Range. Setting default value of 1000ms");
                }
                final long finalInitDelay = initDelay;
                fileDialog.addFileListener(new FileDialog.FileSelectedListener() {
                    public void fileSelected(File file) {
                        mLog.LogF("selected file " + file.toString());
                        mBluetoothService.setMemoryFilename(file.toString());
                        mCommands.setAuto_write_to(true);
                        if (isPrefSendBootloaderCommand()) {
                            mBluetoothService.getVersion();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mBluetoothService.send_ml_packet(0x03, "y 0 0");
                                }
                            }, 500);
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mBluetoothService.sendInit();
                                }
                            }, finalInitDelay + 1);
                        } else {
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mBluetoothService.sendInit();
                                }
                            }, 500);
                        }
                    }
                });
                fileDialog.showDialog();
                /*
                mCommands.setAuto_write_to(true);
                mBluetoothService.getVersion();
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mBluetoothService.send_ml_packet(0x03, "y 0 0");
                    }
                }, 500);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mBluetoothService.sendInit();
                    }
                }, 1000);
                */
            }
        });

        // Initialize the BluetoothService to perform bluetooth connections
        mBluetoothService = new BluetoothService(getActivity(), mHandler, mCommands, mLog);

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
                            closeDialog();
                            /*
                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    LogTextView.d(TAG, "Retrieving current Firmware Version");
                                    mBluetoothService.getVersion();
                                }
                            }, 100);
                            */
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            createDialog(DIALOG_CONNECT_PROGRESS);
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            closeDialog();
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    /*
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String();//writeBuf);
                    writeMessage = String.format("0x%02x", writeBuf[0]);
                    LogTextView.d(TAG, "Write: "+writeMessage);
                    */
                    break;
                case Constants.MESSAGE_READ:
                    /*
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(); //readBuf, 0, msg.arg1);
                    readMessage = String.format("0x%02x", readBuf[0]);
                    LogTextView.d(TAG, "Read: "+readMessage);
                    */
                    break;
                case Constants.MESSAGE_ACK_RECEIVED:
                    //LogTextView.d(TAG, "ACK Received!");
                    break;
                case Constants.MESSAGE_NACK_RECEIVED:
                    //LogTextView.d(TAG, "NACK Received!");
                    break;
                case Constants.MESSAGE_INIT_COMPLETE:
                    mLog.Log("Init sequence complete!");
                    if (mCommands.isAuto_read_out() || mCommands.isAuto_write_to())
                        mBluetoothService.sendGetCmd();
                    //mInitButton.setEnabled(false);
                    LogTextView("Init Sequence complete!");
                    break;
                case Constants.MESSAGE_INIT_FAILED:
                    mLog.Log("Init sequence failed or already sent!");
                    LogTextView("Init Sequence failed or already sent!");
                    break;
                case Constants.MESSAGE_VERSION_COMPLETE:
                    int[] verBuf = (int[]) msg.obj;
                    String ver = String.format("Firmware Version: %d.%d.%d", verBuf[0], verBuf[1], verBuf[2]);
                    LogTextView(ver);
                    mLog.Log(ver);
                    break;
                case Constants.MESSAGE_ERASE_MEMORY_START:
                    LogTextView("Erase Memory started!");
                    createDialog(DIALOG_ERASE_PROGRESS);
                    break;
                case Constants.MESSAGE_ERASE_MEMORY_COMPLETE:
                    LogTextView("Erase Memory completed!");
                    closeDialog();
                    if (mCommands.isAuto_read_out()) {
                        mBluetoothService.readMemory();
                    }
                    if (mCommands.isAuto_write_to()) {
                        mBluetoothService.writeMemory();
                    }
                    break;
                case Constants.MESSAGE_ERASE_MEMORY_FAILED:
                    LogTextView("Erase Memory failed!");
                    Toast.makeText(activity, "Mass erase of memory failed!", Toast.LENGTH_SHORT).show();
                    closeDialog();
                    break;
                case Constants.MESSAGE_BL_VERSION:
                    byte readBuf = (byte) msg.obj;
                    String temp = String.format("Bootloader Version: %02x", readBuf);
                    if (!mCommands.isGet_complete()) {
                        LogTextView(temp);
                    }
                    mLog.Log(temp);
                    break;
                case Constants.MESSAGE_GET_COMPLETE:
                    byte[] getBuf = (byte[]) msg.obj;
                    if (!mCommands.isGet_complete() && mCommands.getActiveCommandCount() <= 0) {
                        for (byte aGetBuf : getBuf) {
                            mCommands.addCommand(aGetBuf);
                            String getCommand = String.format("Command: %s", mCommands.getCommandName(aGetBuf));
                            LogTextView(getCommand);
                        }
                    }
                    mCommands.setGet_complete(true);
                    if (mCommands.isAuto_read_out() || mCommands.isAuto_write_to())
                        mBluetoothService.sendGIDCmd();
                    break;
                case Constants.MESSAGE_GID_COMPLETE:
                    byte[] gidBuf = (byte[]) msg.obj;
                    for (byte aGidBuf : gidBuf) {
                        String gid = String.format("Product ID: 0x04%02x", aGidBuf);
                        if (!mCommands.isGid_complete())
                            LogTextView(gid);
                        mLog.Log(gid);
                    }
                    if (gidBuf[0] == Constants.STM32_PID) {
                        if (!mCommands.isGid_complete())
                            LogTextView("Correct Product ID found! Update possible!");
                        mCommands.setGid_complete(true);
                        if (!mCommands.isAuto_read_out() && mCommands.isAuto_write_to()) {
                            if (isPrefEraseAll())
                                mBluetoothService.sendEraseCmd();
                            else
                                mBluetoothService.writeMemory();
                        }
                        else if (mCommands.isAuto_read_out() && !mCommands.isAuto_write_to())
                            mBluetoothService.readMemory();
                    } else {
                        Toast.makeText(activity, "Wrong product found! No firmware updates/downloads possible", Toast.LENGTH_SHORT).show();
                    }

                    break;
                case Constants.MESSAGE_READ_MEMORY_START:
                    LogTextView("Downloading memory file...");
                    timerTemp = System.currentTimeMillis();
                    createDialog(DIALOG_DOWNLOAD_PROGRESS);
                    break;
                case Constants.MESSAGE_READ_MEMORY_BYTE:
                    int[] numByte = (int[]) msg.obj;
                    //String numByteText = String.format("READ Byte %d on page %d received!", numByte[1], numByte[0]);
                    //LogTextView(numByteText);

                    //mProgressDialog.setProgress((numByte[0] * 100) / Protocol.STM32_PAGE_COUNT);
                    mProgressDialog.setProgress(numByte[0]);
                    //mProgressDialog.setSecondaryProgress((numByte[0] * 100) / Protocol.STM32_BYTE_COUNT);
                    //mProgressDialog.setMessage("Downloading memory..\r\n(Page "+String.valueOf(numByte[0]+1)+"/"+String.valueOf(Protocol.STM32_PAGE_COUNT)+")");
                    if (isPrefFullRead())
                        mProgressDialog.setMessage("Downloading whole memory.. ("+String.valueOf(((numByte[0]+1)* STM32_BYTE_COUNT)/1024)+"/"+((STM32_BYTE_COUNT*STM32_PAGE_COUNT)/1024)+" kb)");
                    else
                        mProgressDialog.setMessage("Downloading memory.. ("+String.valueOf(((numByte[0]+1)* STM32_BYTE_COUNT)/1024)+" kb)");
                    break;
                case Constants.MESSAGE_READ_MEMORY_COMPLETE:
                    timeReadMemory = System.currentTimeMillis() - timerTemp;
                    timeReadMemory = timeReadMemory / 1000;
                    int written_pages = (int) msg.obj;
                    closeDialog();
                    int b = (written_pages * STM32_BYTE_COUNT);
                    if (b > 1024) {
                        int kb = b / 1024;
                        mLog.Log("Download memory complete in "+String.valueOf(timeReadMemory)+" seconds! (" + kb + "kb)");
                        Toast.makeText(activity, "Download memory complete in "+String.valueOf(timeReadMemory)+" seconds! (" + kb + "kb)", Toast.LENGTH_SHORT).show();
                        LogTextView("Download memory complete in "+String.valueOf(timeReadMemory)+" seconds! (" + kb + "kb)");
                    } else {
                        mLog.Log("Download memory complete in "+String.valueOf(timeReadMemory)+" seconds! (" + b + "bytes)");
                        Toast.makeText(activity, "Download memory complete in "+String.valueOf(timeReadMemory)+" seconds! (" + b + "bytes)", Toast.LENGTH_SHORT).show();
                        LogTextView("Download memory complete in "+String.valueOf(timeReadMemory)+" seconds! (" + b + "bytes)");
                    }
                    if (mCommands.isAuto_read_out())
                        mBluetoothService.sendGoCmd(); // TODO make switch in settings maybe!?
                    break;
                case Constants.MESSAGE_READ_MEMORY_FAILED:
                    mCommands.resetStates();
                    int[] numPage = (int[]) msg.obj;
                    if (numPage == null) {
                        numPage = new int[2];
                        numPage[0] = 1;
                        numPage[1] = 1;
                    }
                    mLog.Log("Downloading memory failed on Page " + String.valueOf(numPage[0]) + " of " + String.valueOf(STM32_PAGE_COUNT) + " on Byte " + String.valueOf(numPage[1]));
                    LogTextView("Downloading memory failed on Page " + String.valueOf(numPage[0]) + " of " + String.valueOf(STM32_PAGE_COUNT) + " on Byte " + String.valueOf(numPage[1]));
                    closeDialog();
                    Toast.makeText(activity, "Failed to Download memory", Toast.LENGTH_SHORT).show();
                    break;

                case Constants.MESSAGE_WRITE_START:
                    firmware_size = (long) msg.obj;
                    timerTemp = System.currentTimeMillis();
                    createDialog(DIALOG_UPLOAD_PROGRESS);
                    break;
                case Constants.MESSAGE_WRITE_MEMORY_FAILED:
                    long[] wrPage = (long[]) msg.obj;
                    if (wrPage == null) {
                        wrPage = new long[3];
                        wrPage[0] = 1;
                        wrPage[1] = 1;
                        wrPage[2] = 1;
                    }
                    mLog.Log("Uploading memory failed on Page " + String.valueOf(wrPage[0]) + " of " + String.valueOf(wrPage[2]/ STM32_BYTE_COUNT) + " on Byte " + String.valueOf((wrPage[0] * STM32_BYTE_COUNT) + wrPage[1]));
                    LogTextView("Uploading memory failed on Page " + String.valueOf(wrPage[0]) + " of " + String.valueOf(wrPage[2]/ STM32_BYTE_COUNT) + " on Byte " + String.valueOf((wrPage[0] * STM32_BYTE_COUNT) + wrPage[1]));
                    closeDialog();
                    Toast.makeText(activity, "Failed to upload memory", Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_WRITE_MEMORY_FILE_ERROR:
                    mCommands.resetStates();
                    mLog.Log("Uploading memory failed! Cannot open/find memory file");
                    LogTextView("Uploading memory failed! Cannot open/find memory file");
                    closeDialog();
                    Toast.makeText(activity, "Failed to open memory file", Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_WRITE_MEMORY_BYTE:
                    int[] bufWrite = (int[]) msg.obj;
                    mProgressDialog.setProgress(bufWrite[0]);
                    int currByte = (bufWrite[0] * STM32_BYTE_COUNT) + bufWrite[1];
                    /*if (currByte > 1024)
                        mProgressDialog.setMessage("Uploading memory..\n("+ String.valueOf(currByte) +"/"+String.valueOf(firmware_size / 1024)+" kb)");
                    else*/
                    mProgressDialog.setMessage("Uploading memory..\n("+ String.valueOf(currByte) +"/" + String.valueOf(firmware_size) + " bytes)");
                    break;
                case Constants.MESSAGE_WRITE_MEMORY_COMPLETE:
                    timeWriteMemory = System.currentTimeMillis() - timerTemp;
                    timeWriteMemory = timeWriteMemory / 1000;
                    long fileSize = (long) msg.obj;
                    closeDialog();
                    String size;
                    if (fileSize > 1024) {
                        size = String.format("(%d kb)", (fileSize / 1024));
                    } else {
                        size = String.format("(%d bytes)", fileSize);
                    }

                    mLog.Log("Upload memory complete in "+String.valueOf(timeWriteMemory)+" seconds! (" + size + " bytes)");
                    Toast.makeText(activity, "Upload memory complete in "+String.valueOf(timeWriteMemory)+" seconds! (" + size + " bytes)", Toast.LENGTH_SHORT).show();
                    LogTextView("Upload memory complete in "+String.valueOf(timeWriteMemory)+" seconds! (" + size + " bytes)");
                    if (mCommands.isAuto_read_out())
                        if (isPrefResetWrite())
                            mBluetoothService.sendGoCmd();
                    break;
                case Constants.MESSAGE_GO_COMPLETE:
                    LogTextView("Restart done!");
                    mCommands.resetStates();
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


    public void LogTextView(String message) {
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
                    mLog.Log("BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
            case Constants.RESULT_SETTINGS:
                readConfiguration();
                mLog.Log(9, "Result settings");
                break;
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
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            case R.id.settings:
                Intent i = new Intent(this.getActivity(), UserSettingsActivity.class);
                startActivityForResult(i, Constants.RESULT_SETTINGS);
                return true;
            case R.id.about:
                AboutDialog about = new AboutDialog(this.getActivity());
                about.setTitle(getResources().getString(R.string.title_about));
                about.show();
        }
        return super.onOptionsItemSelected(item);
    }

    private void readConfiguration() {
        // Read out shared preference file
        setPrefDebug(sharedPrefs.getBoolean("prefDebug", true)); // Debug Flag
        setPrefEraseAll(sharedPrefs.getBoolean("prefEraseAll", true));
        setPrefResetWrite(sharedPrefs.getBoolean("prefResetWrite", true));
        setPrefFullRead(sharedPrefs.getBoolean("prefFullRead", false));
        mCommands.setRead_full(isPrefFullRead());
        setPrefLog(sharedPrefs.getBoolean("prefLog", true));
        setPrefSyslog(sharedPrefs.getBoolean("prefSyslog", false));
        setPrefShowOnlyMatchingDevices(sharedPrefs.getBoolean("prefShowOnlyMatchingDevices", true));
        setPrefShowDeviceMatch(sharedPrefs.getString("prefShowDeviceMatch", "device_match_here"));
        mCommands.setSkipBytes(Integer.parseInt(sharedPrefs.getString("prefSkipBytes", "32")));
        setPrefBootloaderCommand(sharedPrefs.getString("prefBootloaderCommand", "magic string"));
        setPrefSendBootloaderCommand(sharedPrefs.getBoolean("prefSendBootloaderCommand", true));
        setPrefBootloaderInitDelay(sharedPrefs.getString("prefBootLoaderInit", "1000"));
        //exitDialogEnabled = sharedPrefs.getBoolean("prefExitDialog", true);
    }

    private void writeConfiguration() {
        //sharedPrefs.edit().putBoolean("prefDebug", DEBUG).apply();
        //sharedPrefs.edit().putBoolean("prefExitDialog", exitDialogEnabled).apply();
    }

    public static String getPrefShowDeviceMatch() {
        return prefShowDeviceMatch;
    }

    public void setPrefShowDeviceMatch(String prefShowDeviceMatch) {
        BluetoothUpdaterFragment.prefShowDeviceMatch = prefShowDeviceMatch;
    }

    public String getPrefSkipBytes() {
        return prefSkipBytes;
    }

    public void setPrefSkipBytes(String prefSkipBytes) {
        this.prefSkipBytes = prefSkipBytes;
    }

    public boolean isPrefFullRead() {
        return prefFullRead;
    }

    public void setPrefFullRead(boolean prefFullRead) {
        this.prefFullRead = prefFullRead;
    }

    public boolean isPrefLog() {
        return prefLog;
    }

    public void setPrefLog(boolean prefLog) {
        this.prefLog = prefLog;
    }

    public boolean isPrefSyslog() {
        return prefSyslog;
    }

    public void setPrefSyslog(boolean prefSyslog) {
        this.prefSyslog = prefSyslog;
    }

    public static boolean isPrefShowOnlyMatchingDevices() {
        return prefShowOnlyMatchingDevices;
    }

    public void setPrefShowOnlyMatchingDevices(boolean prefShowOnlyMatchingDevices) {
        BluetoothUpdaterFragment.prefShowOnlyMatchingDevices = prefShowOnlyMatchingDevices;
    }

    public boolean isPrefResetWrite() {
        return prefResetWrite;
    }

    public void setPrefResetWrite(boolean prefResetWrite) {
        this.prefResetWrite = prefResetWrite;
    }

    public boolean isPrefEraseAll() {
        return prefEraseAll;
    }

    public void setPrefEraseAll(boolean prefEraseAll) {
        this.prefEraseAll = prefEraseAll;
    }

    public boolean isPrefDebug() {
        return prefDebug;
    }

    public void setPrefDebug(boolean prefDebug) {
        this.prefDebug = prefDebug;
    }

    public String getPrefBootloaderInitDelay() {
        return prefBootloaderInitDelay;
    }

    public void setPrefBootloaderInitDelay(String prefBootloaderInitDelay) {
        this.prefBootloaderInitDelay = prefBootloaderInitDelay;
    }

    public static String getPrefBootloaderCommand() {
        return prefBootloaderCommand;
    }

    public void setPrefBootloaderCommand(String prefBootloaderCommand) {
        BluetoothUpdaterFragment.prefBootloaderCommand = prefBootloaderCommand;
    }

    public boolean isPrefSendBootloaderCommand() {
        return prefSendBootloaderCommand;
    }

    public void setPrefSendBootloaderCommand(boolean prefSendBootloaderCommand) {
        this.prefSendBootloaderCommand = prefSendBootloaderCommand;
    }

    public boolean isPrefResetRead() {
        return prefResetRead;
    }

    public void setPrefResetRead(boolean prefResetRead) {
        this.prefResetRead = prefResetRead;
    }
}