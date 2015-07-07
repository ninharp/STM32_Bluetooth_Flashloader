/**
 * stm32_bluetooth_flashloader - Open Source Android App to flash ST STM32 over bluetooth
 * Copyright (C) 2015 Michael Sauer <sauer.uetersen@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * Created by Michael Sauer at 01:10 on 28.06.15
 **/

package de.sauernetworks.stm32_bluetooth_flashloader;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import de.sauernetworks.stm_bootloader.Bootloader;
import de.sauernetworks.stm_bootloader.Commands;
import de.sauernetworks.stm_bootloader.Devices;
import de.sauernetworks.stm_bootloader.OnBootloaderEventListener;
import de.sauernetworks.tools.FileDialog;
import de.sauernetworks.tools.Logger;

import static de.sauernetworks.stm_bootloader.Protocol.STM32_BYTE_COUNT;
import static de.sauernetworks.stm_bootloader.Protocol.STM32_PAGE_COUNT;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class BluetoothUpdaterFragment extends Fragment {

    private static final String TAG = "STM32_Bluetooth_Flashloader";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int DIALOG_DOWNLOAD_PROGRESS = 0;
    private static final int DIALOG_UPLOAD_PROGRESS = 1;
    private static final int DIALOG_CONNECT_PROGRESS = 2;
    private static final int DIALOG_ERASE_PROGRESS = 3;
    private static final int DIALOG_START_BOOTLOADER_INFO = 4;

    private long firmware_upload_size = 0;

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
    private LinearLayout mDebugLayout1;
    private LinearLayout mDebugLayout2;
    private LinearLayout mDebugLayout3;
    private LinearLayout mDebugLayout4;

    private ProgressDialog mProgressDialog;

    private String mConnectedDeviceName = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothService mBluetoothService = null;

    private Commands mCommands;
    private Bootloader mBootloader;
    private Logger mLog;
    private SharedPreferences sharedPrefs;
    private File mPath;
    FileDialog fileWriteDialog;
    FileDialog fileReadDialog;
    private Devices mDevices;
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
    private int prefVerbose = 2;

    long timerTemp = 0;
    long timeReadMemory = 0;
    long timeWriteMemory = 0;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
        mDevices = new Devices();
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
                /*if (firmware_upload_size > 1024)
                    mProgressDialog.setMessage("Uploading memory..\n(1/"+String.valueOf(firmware_upload_size / 1024)+" kb)");
                else*/
                mProgressDialog.setMessage("Uploading memory..\n(1/" + String.valueOf(firmware_upload_size) + " bytes)");
                if ((firmware_upload_size / 256) <= 1)
                    mProgressDialog.setMax(1);
                else
                    mProgressDialog.setMax((int)(firmware_upload_size / STM32_BYTE_COUNT));
                mProgressDialog.setProgressNumberFormat("%1d of %2d Pages written");
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
                break;
            case DIALOG_START_BOOTLOADER_INFO:
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(R.string.dialog_start_bootloader_info_message)
                        .setPositiveButton(R.string.dialog_start_bootloader_info_button_start, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                final Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            mBootloader.init();
                                        } catch (IOException e) {
                                            Toast.makeText(getActivity(), R.string.toast_error_command_running, Toast.LENGTH_SHORT).show();
                                            e.printStackTrace();
                                        }
                                    }
                                }, 500);
                            }
                        })
                        .setNegativeButton(R.string.dialog_start_bootloader_info_button_cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // User cancelled the dialog
                            }
                        }).show();
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
        mDebugLayout1 = (LinearLayout) view.findViewById(R.id.layout_debug_1);
        mDebugLayout2 = (LinearLayout) view.findViewById(R.id.layout_debug_2);
        mDebugLayout3 = (LinearLayout) view.findViewById(R.id.layout_debug_3);
        mDebugLayout4 = (LinearLayout) view.findViewById(R.id.layout_debug_4);
        mLog = new Logger(this.getActivity(), true, true, TAG, 9);

        mPath = new File(Environment.getExternalStorageDirectory() + "//STM32//");
        fileWriteDialog = new FileDialog(this.getActivity(), mPath);
        fileWriteDialog.setFileEndsWith(".bin");

        fileReadDialog = new FileDialog(this.getActivity(), mPath);
        fileReadDialog.setSelectDirectoryOption(false);

        readConfiguration();
        LogTextView(1, "Started!");
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupUI() {
        mLog.Log(9, "setupUI()");

        mBootloaderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check that we're actually connected before trying anything
                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    return;
                }

                mCommands.setAuto_read_out(false);
                mCommands.setAuto_write_to(false);
                mLog.Log(3, "Sending Bootloader jump command");
                LogTextView(3, "Sending Bootloader jump command");
                mBluetoothService.send_ml_packet(0x03, "y 0 0");
            }
        });

        mInitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check that we're actually connected before trying anything
                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!mBootloader.isCommandRunning()) {
                    LogTextView(8, "Init Sequence in Progress!");
                    try {
                        if (mBootloader.init()) {
                            mLog.Log(3, "Init sequence complete!");
                            LogTextView(3, "Init Sequence complete!");
                        } else {
                            mLog.Log(Constants.ERROR, "Init sequence failed or already sent!");
                            LogTextView(Constants.ERROR, "Init Sequence failed or already sent!");
                        }
                    } catch (IOException e) {
                        mLog.Log(Constants.ERROR, "Init sequence I/O exception!");
                        Toast.makeText(getActivity(), R.string.toast_error_input_output, Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                    mCommands.setRunning(false);
                } else {
                    Toast.makeText(getActivity(), R.string.toast_error_command_running, Toast.LENGTH_SHORT).show();
                }
            }
        });

        mGetCmdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check that we're actually connected before trying anything
                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!mBootloader.isCommandRunning()) {
                    try {
                        if (mBootloader.getCommands()) {
                            String temp = String.format("Bootloader Version: %02x", mBootloader.getBootloaderVersion());
                            LogTextView(3, temp);
                            mLog.Log(temp);
                            for (byte cmd : mBootloader.getBootloaderCommands()) {
                                String getCommand = String.format("Command: %s", mCommands.getCommandName(cmd));
                                LogTextView(5, getCommand);
                                mLog.Log(5, getCommand);
                            }
                        } else {
                            Toast.makeText(getActivity(), "Failed to get supported commands from Bootloader!", Toast.LENGTH_SHORT).show();
                            mLog.Log(Constants.ERROR, "Failed to get supported commands from Bootloader!");
                        }
                    } catch (IOException e) {
                        mLog.Log(Constants.ERROR, "GET Command I/O exception!");
                        Toast.makeText(getActivity(), R.string.toast_error_input_output, Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(getActivity(), R.string.toast_error_command_running, Toast.LENGTH_SHORT).show();
                }
            }
        });

        mGvrpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check that we're actually connected before trying anything
                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!mBootloader.isCommandRunning()) {
                    try {
                        if (mBootloader.getReadProtection()) {
                                String getCommand = String.format("Read Protection: 0x%02x 0x%02x", mBootloader.getBootloaderReadProtection()[0], mBootloader.getBootloaderReadProtection()[0]);
                                LogTextView(5, getCommand);
                                mLog.Log(5, getCommand);
                        } else {
                            Toast.makeText(getActivity(), "Failed to Read Protection Status from Bootloader!", Toast.LENGTH_SHORT).show();
                            mLog.Log(Constants.ERROR, "Failed to Read Protection Status from Bootloader!");
                        }
                    } catch (IOException e) {
                        mLog.Log(Constants.ERROR, "GVRP Command I/O exception!");
                        Toast.makeText(getActivity(), R.string.toast_error_input_output, Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                }
            }
        });

        mGidButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check that we're actually connected before trying anything
                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!mBootloader.isCommandRunning()) {
                    try {
                        if (mBootloader.getDeviceInfo()) {
                            byte[] gidBuf = mBootloader.getBootloaderProductId();
                            String gid;
                            String name;
                            if (gidBuf.length == 1) {
                                gid = String.format("Product ID: 0x04%02x", gidBuf[0]);
                            } else
                                gid = String.format("Product ID: 0x%02x%02x", gidBuf[0], gidBuf[1]);

                            name = String.format("Product Name: %s", mBootloader.getBootloaderProductName());
                            LogTextView(2, gid);
                            LogTextView(2, name);
                            mLog.Log(2, gid);
                            mLog.Log(2, name);
                        } else {
                            Toast.makeText(getActivity(), "Failed to get device information from Bootloader!", Toast.LENGTH_SHORT).show();
                            mLog.Log(Constants.ERROR, "Failed to get device information from Bootloader!");
                        }
                    } catch (IOException e) {
                        mLog.Log(Constants.ERROR, "GID Command I/O exception!");
                        Toast.makeText(getActivity(), R.string.toast_error_input_output, Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                }
            }
        });

        mGoCmdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check that we're actually connected before trying anything
                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!mBootloader.isCommandRunning()) {
                    try {
                        if (mBootloader.doJump()) {
                            mLog.Log(2, "GO Command complete! Reset done!");
                            LogTextView(2, "GO Command complete! Reset done!");
                        } else {
                            Toast.makeText(getActivity(), "Failed to send a jump command to Bootloader!", Toast.LENGTH_SHORT).show();
                            mLog.Log(Constants.ERROR, "Failed to send a jump command to Bootloader!");
                        }
                    } catch (IOException e) {
                        mLog.Log(Constants.ERROR, "GO Command I/O exception!");
                        Toast.makeText(getActivity(), R.string.toast_error_input_output, Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                }
            }
        });

        mReadMemoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check that we're actually connected before trying anything
                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!mBootloader.isCommandRunning()) {
                    new ReadMemoryOperation().execute();
                }
            }
        });

        mEraseMemoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check that we're actually connected before trying anything
                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!mBootloader.isCommandRunning()) {
                    new ExtendedEraseMemoryOperation().execute();
                }
            }
        });

        mWriteMemoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check that we're actually connected before trying anything
                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!mBootloader.isCommandRunning()) {
                    fileWriteDialog.addFileListener(new FileDialog.FileSelectedListener() {
                        public void fileSelected(File file) {
                            mLog.Log(7, "selected file " + file.toString());
                            firmware_upload_size = getFileSize(file.toString());
                            new WriteMemoryOperation().execute(file.toString());
                        }
                    });
                    fileWriteDialog.showDialog();
                }
            }
        });

        mCheckUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check that we're actually connected before trying anything
                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    return;
                }

                LogTextView(3, "Getting running Firmware Version");
                mBluetoothService.getVersion();
            }
        });

        mDownloadMemoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check that we're actually connected before trying anything
                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    return;
                }

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
                /*fileReadDialog.addDirectoryListener(new FileDialog.DirectorySelectedListener() {
                      public void directorySelected(File directory) {
                          mLog.Log(7, "Selected dir: " + directory.toString());

                      }
                });
                fileReadDialog.showDialog();*/
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
                            try {
                                mBootloader.init();
                            } catch (IOException e) {
                                e.printStackTrace();
                                Toast.makeText(getActivity(), R.string.toast_error_command_running, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }, finalInitDelay + 1);
                } else {
                    mLog.Log(5, "Starting Bootloader info dialog");
                    createDialog(DIALOG_START_BOOTLOADER_INFO);
                }
            }
        });

        mUploadMemoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check that we're actually connected before trying anything
                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    return;
                }

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
                fileWriteDialog.addFileListener(new FileDialog.FileSelectedListener() {
                    public void fileSelected(File file) {
                        mLog.Log(7, "selected file " + file.toString());
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
                                    try {
                                        mBootloader.init();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        Toast.makeText(getActivity(), R.string.toast_error_command_running, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }, finalInitDelay + 1);
                        } else {
                            mLog.Log(5, "Starting Bootloader info dialog");
                            createDialog(DIALOG_START_BOOTLOADER_INFO);
                        }
                    }
                });
                fileWriteDialog.showDialog();
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

    private class ReadMemoryOperation extends AsyncTask<Integer, Long, Integer> {
        @Override
        protected Integer doInBackground(Integer... params) {
            try {
                int readPages = mBootloader.readMemory();
                mLog.Log(1, "Read "+String.valueOf(readPages)+" Pages");
                return readPages;
            } catch (IOException e) {
                closeDialog();
                mHandler.obtainMessage(Constants.MESSAGE_IO_ERROR).sendToTarget();
                e.printStackTrace();
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            //int readPages = Integer.valueOf(result);
            int readPages = result;
            closeDialog();
            if (readPages > 0) {
                timeReadMemory = System.currentTimeMillis() - timerTemp;
                timeReadMemory = timeReadMemory / 1000;
                int b = (readPages * mBootloader.getReadBlockSize());
                if (b > 1024) {
                    int kb = b / 1024;
                    mLog.Log(1, "Download memory complete in " + String.valueOf(timeReadMemory) + " seconds! (" + kb + "kb)");
                    Toast.makeText(getActivity(), "Download memory complete in " + String.valueOf(timeReadMemory) + " seconds! (" + kb + "kb)", Toast.LENGTH_SHORT).show();
                    LogTextView(1, "Download memory complete in " + String.valueOf(timeReadMemory) + " seconds! (" + kb + "kb)");
                } else {
                    mLog.Log(1, "Download memory complete in " + String.valueOf(timeReadMemory) + " seconds! (" + b + "bytes)");
                    Toast.makeText(getActivity(), "Download memory complete in " + String.valueOf(timeReadMemory) + " seconds! (" + b + "bytes)", Toast.LENGTH_SHORT).show();
                    LogTextView(1, "Download memory complete in " + String.valueOf(timeReadMemory) + " seconds! (" + b + "bytes)");
                }
            } else {
                Toast.makeText(getActivity(), "Failed to read memory from device!", Toast.LENGTH_SHORT).show();
                mLog.Log(Constants.ERROR, "Failed to read memory from device!");
            }
        }

        @Override
        protected void onPreExecute() {
            createDialog(DIALOG_DOWNLOAD_PROGRESS);
            LogTextView(3, "Downloading memory to file.. ");
            mLog.Log(3, "Downloading memory to file.. ");
            timerTemp = System.currentTimeMillis();

            mBootloader.setOnReadByteListener(new OnBootloaderEventListener() {
                @Override
                public void onByte(long[] num) {
                    publishProgress(num[0]);
                }
            });
        }

        @Override
        protected void onProgressUpdate(Long... num) {
            mProgressDialog.setProgress(num[0].intValue());
            if (mBootloader.getFullReadMemory())
                mProgressDialog.setMessage("Downloading whole memory.. ("+String.valueOf(((num[0]+1)* STM32_BYTE_COUNT)/1024)+"/"+((STM32_BYTE_COUNT*STM32_PAGE_COUNT)/1024)+" kb)");
            else
                mProgressDialog.setMessage("Downloading memory.. ("+String.valueOf(((num[0]+1)* STM32_BYTE_COUNT)/1024)+" kb)");
        }
    }

    private class WriteMemoryOperation extends AsyncTask<String, Long, Integer> {
        long[] wrPage;

        @Override
        protected Integer doInBackground(String... params) {
            try {
                mBootloader.writeMemory(params[0]);
                mLog.Log(1, "Wrote "+String.valueOf(wrPage[0])+" Pages");
                return 1;
            } catch (IOException e) {
                closeDialog();
                mHandler.obtainMessage(Constants.MESSAGE_IO_ERROR).sendToTarget();
                e.printStackTrace();
            }
            return 0;
        }

        @Override
        protected void onPreExecute() {
            timerTemp = System.currentTimeMillis();
            createDialog(DIALOG_UPLOAD_PROGRESS);
            wrPage = new long[4];

            mBootloader.setOnWriteByteListener(new OnBootloaderEventListener() {
                @Override
                public void onByte(long[] num) {
                    publishProgress(num[0], num[1], num[2]);
                    wrPage[0] = num[0];
                    wrPage[1] = num[1];
                }
            });
        }

        @Override
        protected void onPostExecute(Integer result) {
            timeWriteMemory = System.currentTimeMillis() - timerTemp;
            timeWriteMemory = timeWriteMemory / 1000;
            closeDialog();
            if (result == 1) {
                String size;
                if (firmware_upload_size > 1024) size = String.format("(%d kb)", (firmware_upload_size / 1024));
                else size = String.format("(%d bytes)", firmware_upload_size);
                mLog.Log(1, "Upload memory complete in " + String.valueOf(timeWriteMemory) + " seconds! (" + size + " bytes)");
                Toast.makeText(getActivity(), "Upload memory complete in " + String.valueOf(timeWriteMemory) + " seconds! (" + size + " bytes)", Toast.LENGTH_SHORT).show();
                LogTextView(1, "Upload memory complete in " + String.valueOf(timeWriteMemory) + " seconds! (" + size + " bytes)");
            } else {
                if (wrPage == null) {
                    wrPage = new long[4];
                    wrPage[0] = 1;
                    wrPage[1] = 1;
                    wrPage[2] = 1;
                }
                mLog.Log(1, "Uploading memory failed on Page " + String.valueOf(wrPage[0]) + " of " + String.valueOf(wrPage[2] / STM32_BYTE_COUNT) + " on Byte " + String.valueOf((wrPage[0] * STM32_BYTE_COUNT) + wrPage[1]));
                LogTextView(1, "Uploading memory failed on Page " + String.valueOf(wrPage[0]) + " of " + String.valueOf(wrPage[2] / STM32_BYTE_COUNT) + " on Byte " + String.valueOf((wrPage[0] * STM32_BYTE_COUNT) + wrPage[1]));
                closeDialog();
                Toast.makeText(getActivity(), "Failed to upload memory", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onProgressUpdate(Long... bufWrite) {
            mLog.LogF("setProgress "+ String.valueOf(bufWrite[0]));
            mProgressDialog.setProgress(bufWrite[0].intValue());
            int currByte = (bufWrite[0].intValue() * STM32_BYTE_COUNT) + bufWrite[1].intValue();
                    /*if (currByte > 1024)
                        mProgressDialog.setMessage("Uploading memory..\n("+ String.valueOf(currByte) +"/"+String.valueOf(firmware_upload_size / 1024)+" kb)");
                    else*/
            mProgressDialog.setMessage("Uploading memory..\n("+ String.valueOf(currByte) +"/" + String.valueOf(firmware_upload_size) + " bytes)");
        }
    }

    private long getFileSize(String param) {
        File f = new File(param);
        return f.length();
    }

    private class ExtendedEraseMemoryOperation extends AsyncTask<Integer, Integer, Integer> {
        @Override
        protected Integer doInBackground(Integer... params) {
            try {
                if (mBootloader.extendedEraseMemory()) {
                    return 1;
                } else {
                    return 0;
                }
            } catch (IOException e) {
                mLog.Log(Constants.ERROR, "EER: Command I/O exception!");
                Toast.makeText(getActivity(), R.string.toast_error_input_output, Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == 1) {
                LogTextView(2, "Extended Erase Memory completed!");
            } else {
                Toast.makeText(getActivity(), "Failed to extended erase device!", Toast.LENGTH_SHORT).show();
                mLog.Log(Constants.ERROR, "EER: Failed to extended erase device!");
            }
            closeDialog();
        }

        @Override
        protected void onPreExecute() {
            LogTextView(4, "Erase Memory started!");
            mLog.Log(4, "EER: Erase Memory started!");
            createDialog(DIALOG_ERASE_PROGRESS);
        }

        @Override
        protected void onProgressUpdate(Integer... num) {
        }
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
                            mBootloader = mBluetoothService.getBootloader();
                            sendBootloaderSetting();
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            createDialog(DIALOG_CONNECT_PROGRESS);
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            closeDialog();
                            mBootloader = null;
                            //TODO: clearBootloaderSettings
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String();//writeBuf);
                    writeMessage = String.format("Sending: 0x%02x", writeBuf[0]);
                    //mLog.Log(9, writeMessage);
                    //LogTextView.d(TAG, "Write: "+writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(); //readBuf, 0, msg.arg1);
                    readMessage = String.format("Received: 0x%02x", readBuf[0]);
                    //mLog.Log(9, readMessage);
                    //LogTextView.d(TAG, "Read: "+readMessage);
                    break;
                case Constants.MESSAGE_VERSION_COMPLETE:
                    int[] verBuf = (int[]) msg.obj;
                    String ver = String.format("Firmware Version: %d.%d.%d", verBuf[0], verBuf[1], verBuf[2]);
                    LogTextView(3, ver);
                    mLog.Log(ver);
                    break;
                case Constants.MESSAGE_IO_ERROR:
                    mLog.Log(Constants.ERROR, "I/O exception!");
                    if (null != activity) {
                        Toast.makeText(getActivity(), R.string.toast_error_input_output, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    mLog.Log(2, "Connected to " + mConnectedDeviceName);
                    LogTextView(2, "Connected to " + mConnectedDeviceName);
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


    public void LogTextView(int i, String message) {
        if (i <= getPrefVerbose())
            mLogTextView.append("["+String.valueOf(i)+"] "+message+"\n");
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
                Intent settingsIntent = new Intent(this.getActivity(), UserSettingsActivity.class);
                startActivityForResult(settingsIntent, Constants.RESULT_SETTINGS);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void sendBootloaderSetting() {
        if (mBootloader != null) {
            mBootloader.setSkipReadOutBytes(Integer.parseInt(sharedPrefs.getString("prefSkipBytes", "32")));
            mBootloader.setFullReadMemory(sharedPrefs.getBoolean("prefFullRead", false));
            mBootloader.setPreEraseAll(sharedPrefs.getBoolean("prefEraseAll", true));
            mBootloader.setResetAfterWrite(sharedPrefs.getBoolean("prefResetWrite", true));
            mBootloader.setSendBootloaderCommand(sharedPrefs.getString("prefBootloaderCommand", "magic string"));
            mBootloader.setSendInitSequence(sharedPrefs.getBoolean("prefSendInit", true));
            mBootloader.setBootloaderCommand(sharedPrefs.getBoolean("prefSendBootloaderCommand", true));
            mBootloader.setBooloaderInitDelay(sharedPrefs.getString("prefBootLoaderInit", "1000"));
        }
    }

    private void readConfiguration() {
        // Read out shared preference file
        setPrefDebug(sharedPrefs.getBoolean("prefDebug", true)); // Debug Flag
        mLog.setEnabled(sharedPrefs.getBoolean("prefLog", true));
        mLog.setSyslog(sharedPrefs.getBoolean("prefSyslog", false));
        setPrefShowOnlyMatchingDevices(sharedPrefs.getBoolean("prefShowOnlyMatchingDevices", true));
        setPrefShowDeviceMatch(sharedPrefs.getString("prefShowDeviceMatch", "device_match_here"));

        try {
            int verbosity = Integer.parseInt(sharedPrefs.getString("prefVerbose", "2"));
            setPrefVerbose(verbosity);
            mLog.Log(8, "Setting verbosity level to "+String.valueOf(verbosity));
        } catch (NumberFormatException e) {
            setPrefVerbose(2);
            mLog.Log(8, "Error on setting verbose level. Falling back to default value of 2");
            mLog.Log(9, "Error: "+e.getMessage());
        }

        if (isPrefDebug()) {
            if (mDebugLayout1 != null)
                mDebugLayout1.setVisibility(View.VISIBLE);
            if (mDebugLayout2 != null)
                mDebugLayout2.setVisibility(View.VISIBLE);
            if (mDebugLayout3 != null)
                mDebugLayout3.setVisibility(View.VISIBLE);
            if (mDebugLayout4 != null)
                mDebugLayout4.setVisibility(View.VISIBLE);
        } else {
            if (mDebugLayout1 != null)
                mDebugLayout1.setVisibility(View.GONE);
            if (mDebugLayout2 != null)
                mDebugLayout2.setVisibility(View.GONE);
            if (mDebugLayout3 != null)
                mDebugLayout3.setVisibility(View.GONE);
            if (mDebugLayout4 != null)
                mDebugLayout4.setVisibility(View.GONE);
        }
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

    public static boolean isPrefShowOnlyMatchingDevices() {
        return prefShowOnlyMatchingDevices;
    }

    public void setPrefShowOnlyMatchingDevices(boolean prefShowOnlyMatchingDevices) {
        BluetoothUpdaterFragment.prefShowOnlyMatchingDevices = prefShowOnlyMatchingDevices;
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

    public static String getPrefBootloaderCommand() {
        return prefBootloaderCommand;
    }

    public boolean isPrefSendBootloaderCommand() {
        return prefSendBootloaderCommand;
    }

    public int getPrefVerbose() {
        return prefVerbose;
    }

    public void setPrefVerbose(int prefVerbose) {
        this.prefVerbose = prefVerbose;
    }
}
