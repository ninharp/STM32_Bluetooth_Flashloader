package de.sauernetworks.stm_bootloader;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.sauernetworks.stm32_bluetooth_flashloader.Constants;
import de.sauernetworks.tools.Logger;

/**
 * stm32_bluetooth_flashloader - Open Source Android App to flash ST STM32 over bluetooth
 * Copyright (C) 2015 Michael Sauer <sauer.uetersen@gmail.com>
 * <p/>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * <p/>
 * Created by Michael Sauer at 02:17 on 05.07.15
 **/
public class Bootloader {
    private InputStream mmInStream;
    private OutputStream mmOutStream;
    private Logger mLog;
    private Context mContext;
    private Handler mHandler;
    private Devices mDevices;
    private Commands mCommands;

    private OnBootloaderEventListener mOnReadMemoryByteListener;
    private OnBootloaderEventListener mOnWriteMemoryByteListener;

    private boolean commandRunning;

    private byte bootloaderVersion;
    private byte[] bootloaderCommands;
    private int bootloaderCommandCount;
    private byte[] bootloaderProductId;
    private String bootloaderProductName;
    private byte[] bootloaderReadProtection;
    private boolean bootloaderCommandsRead; // Is GET Command already run?
    private boolean bootloaderGIDRead;

    private int skipReadOutBytes;
    private int readBlockSize;
    private int writeBlockSize;
    private boolean fullRead;
    private boolean preEraseAll;
    private boolean resetAfterWrite;
    private String bootloaderRunCommand;
    private boolean sendInit;
    private boolean sendBootloaderCommand;
    private int initDelay;

    public Bootloader(Context mContext, InputStream mmInStream, OutputStream mmOutStream, Logger mLog, Handler mHandler) {
        this.mContext = mContext;
        this.mmInStream = mmInStream;
        this.mmOutStream = mmOutStream;
        this.mLog = mLog;
        this.mHandler = mHandler;
        mDevices = new Devices();
        mCommands = new Commands();
        commandRunning = false;
        bootloaderVersion = 0;
        bootloaderCommandCount = 0;
        bootloaderCommandsRead = false;
        bootloaderGIDRead = false;
        bootloaderReadProtection = new byte[2];
        skipReadOutBytes = 32;
        readBlockSize = Protocol.STM32_BYTE_COUNT;
        writeBlockSize = Protocol.STM32_BYTE_COUNT;
    }

    public void setOnReadByteListener(OnBootloaderEventListener listener) {
        mOnReadMemoryByteListener = listener;
    }

    public void setOnWriteByteListener(OnBootloaderEventListener listener) {
        mOnWriteMemoryByteListener = listener;
    }

    public Commands commands() { return mCommands; }

    public boolean init() throws IOException {
        commandRunning = true;
        byte[] buffer = new byte[1];
        mLog.Log(8, "INIT in Progress!");
        mmInStream.skip(mmInStream.available());
        sendByte(Protocol.STM32_INIT);
        int numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
        switch (buffer[0]) {
            case Protocol.STM32_ACK:
                mLog.Log(Constants.DEBUG, "INIT: ACK Received!");
                commandRunning = false;
                return true;
            case Protocol.STM32_NACK:
                mLog.Log(Constants.DEBUG, "INIT: NACK Received!");
                commandRunning = false;
                return false;
            default:
                mLog.Log(Constants.ERROR, "INIT: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                commandRunning = false;
                return false;
        }
    }

    public boolean getCommands() throws IOException {
        commandRunning = true;
        byte[] buffer = new byte[1];
        mLog.Log(8, "GET Command in Progress!");
        sendByte(Protocol.STM32_GET_COMMAND);
        sendByte((byte) (~Protocol.STM32_GET_COMMAND));
        int numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
        switch (buffer[0]) {
            case Protocol.STM32_ACK:
                mLog.Log(Constants.DEBUG, "GET: ACK Received!");
                numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                byte cmd_count = buffer[0];
                String temp = String.format("GET: %d Bytes Follow", cmd_count);
                mLog.Log(Constants.DEBUG, temp);
                numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                byte bootloader_version = buffer[0];
                byte[] get_buffer = new byte[cmd_count];
                for (int i = 0; i < cmd_count; i++) {
                    numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                    get_buffer[i] = buffer[0];
                }
                numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                if (buffer[0] == Protocol.STM32_ACK) {
                    bootloaderVersion = bootloader_version;
                    bootloaderCommands = get_buffer.clone();
                    bootloaderCommandCount = cmd_count;
                    bootloaderCommandsRead = true;
                    for (byte cmd : bootloaderCommands) {
                        mCommands.addCommand(cmd);
                        //String getCommand = String.format("Command: %s", mCommands.getCommandName(cmd));
                        //mLog.Log(5, getCommand);
                    }
                    mLog.Log(2, "GET: Command success!");
                    commandRunning = false;
                    return true;
                } else {
                    mLog.Log(Constants.ERROR, "GET: Command failed!");
                    commandRunning = false;
                    return false;
                }
            case Protocol.STM32_NACK:
                commandRunning = false;
                mLog.Log(Constants.DEBUG, "GET: NACK Received!");
                return false;
            default:
                mLog.Log(Constants.ERROR, "GET: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                commandRunning = false;
                return false;
        }
    }

    public boolean getReadProtection() throws IOException {
        commandRunning = true;
        byte[] buffer = new byte[1];
        mLog.Log(8, "GVRP Command in Progress!");
        if (bootloaderCommandsRead) { // && mCommands.isActiveCommand(Protocol.STM32_GVRP_COMMAND)) {
            sendByte(Protocol.STM32_GVRP_COMMAND);
            sendByte((byte) (~Protocol.STM32_GVRP_COMMAND));
            int numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
            switch (buffer[0]) {
                case Protocol.STM32_ACK:
                    byte[] gvrp = new byte[3];
                    for (int i = 0; i < 3; i++) {
                        numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                        String temp = String.format("GVRP: 0x%02x", buffer[0]);
                        mLog.Log(Constants.DEBUG, temp);
                        gvrp[i] = buffer[0];
                    }
                    bootloaderVersion = gvrp[0];
                    bootloaderReadProtection[0] = gvrp[1];
                    bootloaderReadProtection[1] = gvrp[2];
                    mmInStream.read(buffer);
                    if (buffer[0] == Protocol.STM32_ACK) {
                        mLog.Log(2, "GVRP: Command success!");
                        commandRunning = false;
                        return true;
                    } else {
                        mLog.Log(Constants.ERROR, "GVRP: Command failed!");
                        commandRunning = false;
                        return false;
                    }
                case Protocol.STM32_NACK:
                    mLog.Log(Constants.ERROR, "GVRP: NACK Received!");
                    commandRunning = false;
                    return false;
                default:
                    mLog.Log(Constants.ERROR, "GVRP: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                    commandRunning = false;
                    return false;
            }
        } else {
            if (bootloaderCommandsRead)
                mLog.Log(Constants.ERROR, "GVRP: Error! GET Command not completed!");
            else
                mLog.Log(Constants.ERROR, "GVRP: Error! GVRP Command not in instruction set!");
            commandRunning = false;
            return false;
        }
    }

    public boolean getDeviceInfo() throws IOException {
        commandRunning = true;
        byte[] buffer = new byte[1];
        mLog.Log(8, "GID Command in Progress!");
        if (bootloaderCommandsRead) { // && mCommands.isActiveCommand(Protocol.STM32_GET_ID_COMMAND)) {
            sendByte(Protocol.STM32_GET_ID_COMMAND);
            sendByte((byte) (~Protocol.STM32_GET_ID_COMMAND));
            int numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
            switch (buffer[0]) {
                case Protocol.STM32_ACK:
                    numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                    byte gid_count = buffer[0];
                    String temp = String.format("GID: %d Bytes Follow", gid_count);
                    mLog.Log(Constants.DEBUG, temp);
                    numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);  // TODO check expected gid count bytes
                    byte[] gid_buffer = new byte[gid_count];
                    for (int i = 0; i < gid_count; i++) {
                        numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                        gid_buffer[i] = buffer[0];
                        String gid = String.format("GID: 0x%02x", gid_buffer[i]);
                        mLog.Log(7, gid);
                    }
                    numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                    if (buffer[0] == Protocol.STM32_ACK) {
                        mLog.Log(2, "GID: Command success!");
                        bootloaderGIDRead = true;
                        bootloaderProductId = gid_buffer.clone();
                        if (bootloaderProductId.length == 1) {
                            bootloaderProductName = mDevices.getDeviceName(bootloaderProductId[0] + 0x0400);
                        } else {
                            bootloaderProductName = mDevices.getDeviceName(bootloaderProductId[0] + bootloaderProductId[1]);
                        }
                        commandRunning = false;
                        return true;
                    } else {
                        bootloaderGIDRead = false;
                        mLog.Log(Constants.ERROR, "GID: Command failed!");
                        commandRunning = false;
                        return false;
                    }
                case Protocol.STM32_NACK:
                    bootloaderGIDRead = false;
                    commandRunning = false;
                    mLog.Log(Constants.DEBUG, "GID: NACK Received!");
                    return false;
                default:
                    bootloaderGIDRead = false;
                    commandRunning = false;
                    mLog.Log(Constants.DEBUG, "GID: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                    return false;
            }
        } else {
            if (bootloaderCommandsRead && !bootloaderGIDRead) // && !mCommands.isRunning())
                mLog.Log(Constants.ERROR, "GID: Error! GET Command not completed!");
            else
                mLog.Log(Constants.ERROR, "GID: Error! GID Command not completed!");
            commandRunning = false;
            bootloaderGIDRead = false;
            return false;
        }
    }

    public boolean doJump() throws IOException {
        commandRunning = true;
        byte[] buffer = new byte[1];
        mLog.Log(8, "GO Command in Progress!");
        if (bootloaderCommandsRead && bootloaderGIDRead) { // && mCommands.isActiveCommand(Protocol.STM32_GET_ID_COMMAND)) {
            sendByte(Protocol.STM32_GO_COMMAND);
            sendByte((byte) (~Protocol.STM32_GO_COMMAND));
            int numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
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
                        mLog.Log(4, "GO: Jump command successed!");
                        commandRunning = false;
                        return true;
                    } else if (buffer[0] == Protocol.STM32_NACK) {
                        mLog.Log(Constants.ERROR, "GO: NACK Received!");
                        commandRunning = false;
                        return false;
                    } else {
                        mLog.Log(Constants.ERROR, "GO: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                        commandRunning = false;
                        return false;
                    }
                case Protocol.STM32_NACK:
                    mLog.Log(Constants.ERROR, "GO: NACK Received!");
                    commandRunning = false;
                    return false;
                default:
                    mLog.Log(Constants.ERROR, "GO: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                    commandRunning = false;
                    return false;
            }
        } else {
            if (bootloaderCommandsRead)
                mLog.Log(Constants.ERROR, "GO: Error! GET Command not completed!");
            else
                mLog.Log(Constants.ERROR, "GO: Error! GO Command not in instruction set!");
            commandRunning = false;
            return false;
        }
    }

    public boolean extendedEraseMemory() throws IOException {
        commandRunning = true;
        byte[] buffer = new byte[1];
        mLog.Log(8, "EER Command in Progress!");
        if (bootloaderCommandsRead && bootloaderGIDRead) { // && mCommands.isActiveCommand(Protocol.STM32_GET_ID_COMMAND)) {
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
                    long maxTimeMillis = System.currentTimeMillis() + Protocol.STM32_EER_TIMEOUT;
                    while (System.currentTimeMillis() < maxTimeMillis && mmInStream.read(buffer) <= 0);
                    if (buffer[0] == Protocol.STM32_ACK) {
                        mLog.Log(4, "Extended Erase Memory completed!");
                        commandRunning = false;
                        return true;
                    } else if (buffer[0] == Protocol.STM32_NACK) {
                        mLog.Log(Constants.ERROR, "EER: Mass Erase of Memory failed!");
                        commandRunning = false;
                        return false;
                    } else {
                        mLog.Log(Constants.ERROR, "EER: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                        commandRunning = false;
                        return false;
                    }
                case Protocol.STM32_NACK:
                    mLog.Log(Constants.ERROR, "EER: NACK Received!");
                    commandRunning = false;
                    return false;
                default:
                    mLog.Log(Constants.ERROR, "EER: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                    commandRunning = false;
                    return false;
            }
        } else {
            if (bootloaderCommandsRead)
                mLog.Log("EER: Error! GET Command not completed!");
            else
                mLog.Log("EER: Error! GO Command not in instruction set!");
            commandRunning = false;
            return false;
        }
    }

    public boolean writeMemory(String path) throws IOException {
        commandRunning = true;
        byte[] buffer = new byte[1];
        boolean error = false;
        mLog.Log("WRITE Command in Progress!");
        if (bootloaderCommandsRead && bootloaderGIDRead) { //TODO command in active  cmds
            long address = Constants.STM32_START_ADDRESS; //todo address from devices
            BufferedInputStream firmwareBuf;
            long[] errBuff = new long[3];
            //String path = mContext.getFilesDir().toString() + "/" + Constants.FIRMWARE_FILENAME;
            //String filepath = path + "_1_4_build804" + Constants.FIRMWARE_EXTENSION;
            /*String filepath = path + Constants.FIRMWARE_EXTENSION;
            if (firmware_filename.length() > 0)
                filepath = firmware_filename;*/
            File file = new File(path);
            long size = file.length();
            errBuff[2] = size;
            mLog.Log(String.format("WRITE: Firmware File %s with Size: %d bytes", file.getCanonicalPath(), size));
            //mHandler.obtainMessage(Constants.MESSAGE_WRITE_START, 1, -1, size).sendToTarget();
            int firmwareOffset = 0;
            byte firmwareChecksum;
            //int firmwareSize = (int) file.length();
            //byte[] firmwareData = new byte[firmwareSize];
            byte[] firmwareData = new byte[Protocol.STM32_BYTE_COUNT];
            try {
                firmwareBuf = new BufferedInputStream(new FileInputStream(file));
            } catch (FileNotFoundException e) {
                mLog.Log("WRITE: Cannot find/read firmware file (" + path + ")");
                //mHandler.obtainMessage(Constants.MESSAGE_WRITE_MEMORY_FILE_ERROR).sendToTarget();
                e.printStackTrace();
                return false;
            }
            for (int page = 0; page < Protocol.STM32_PAGE_COUNT; page++) {
                if (firmwareBuf.available() <= 0) {
                    mLog.Log(3, "WRITE: File completely written");
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
                        address += Protocol.STM32_BYTE_COUNT;
                        if (buffer[0] == Protocol.STM32_ACK) {
                            long[] dataBuf = new long[4];
                            dataBuf[3] = size;
                            dataBuf[0] = page;
                            int countData = firmwareBuf.read(firmwareData, 0, firmwareData.length);
                            sendByte((byte) (Protocol.STM32_BYTE_COUNT - 1)); // write 256 bytes
                            firmwareChecksum = (byte) (Protocol.STM32_BYTE_COUNT - 1);
                            if (countData < firmwareData.length) {
                                mLog.Log(7, "WRITE: File at end! Filling with 0xff");
                                for (int i = countData; i < firmwareData.length; i++)
                                    firmwareData[i] = (byte) 0xFF;
                                page = Protocol.STM32_PAGE_COUNT;
                            } else if (countData == -1) {
                                error = true;
                                mLog.Log(Constants.ERROR, "WRITE: File read Error on Write PAGE " + String.valueOf(page) + " [" + String.format("0x%02x", buffer[0]) + "]");
                                errBuff[0] = page;
                                errBuff[1] = countData;
                                if (firmwareBuf != null)
                                    firmwareBuf.close();
                                break;
                            }

                            //
                            for (int i = 0; i < firmwareData.length; i++) {
                                firmwareChecksum = (byte) (firmwareChecksum ^ firmwareData[i]);
                                dataBuf[1] = i;
                            }
                            write(firmwareData);
                            sendByte(firmwareChecksum);
                            readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                            if (buffer[0] == Protocol.STM32_ACK) {
                                //mLog.LogF("WRITE: Written Offset " + String.valueOf(firmwareOffset) + " successfully");
                            } else {
                                error = true;
                                mLog.Log(Constants.ERROR, String.format("WRITE: Error on Writing Offset %d [0x%02x]", firmwareOffset, buffer[0]));
                                errBuff[0] = page;
                                errBuff[1] = countData;
                                mHandler.obtainMessage(Constants.MESSAGE_WRITE_MEMORY_FAILED, errBuff.length, -1, errBuff).sendToTarget();
                                if (firmwareBuf != null)
                                    firmwareBuf.close();
                                break;
                            }

                            if (mOnWriteMemoryByteListener != null)
                                mOnWriteMemoryByteListener.onByte(dataBuf);
                            firmwareOffset += firmwareData.length;
                        } else {
                            error = true;
                            mLog.Log(Constants.ERROR, "WRITE: Address Error on Write PAGE " + String.valueOf(page) + " [" + String.format("0x%02x", buffer[0]) + "]");
                            errBuff[0] = page;
                            errBuff[1] = -1;
                            if (firmwareBuf != null)
                                firmwareBuf.close();
                            break;
                        }
                        break;
                    default:
                        error = true;
                        mLog.Log(Constants.ERROR, "WRITE: Command Error on Write PAGE " + String.valueOf(page) + " [" + String.format("0x%02x", buffer[0]) + "]");
                        errBuff[0] = page;
                        errBuff[1] = -1;
                        if (firmwareBuf != null)
                            firmwareBuf.close();
                        break;
                }
            }
            if (firmwareBuf != null)
                firmwareBuf.close();
            commandRunning = false;
            if (!error) {
                mLog.Log(3, "WRITE: Command success!");
                return true;
            } else {
                return false;
            }
        } else {
            if (bootloaderCommandsRead)
                mLog.Log(Constants.ERROR, "WRITE: Error! GET Command not completed!");
            else
                mLog.Log("WRITE: Error! Write Memory Command not in instruction set! (Maybe write protected!)");
            commandRunning = false;
            return false;
        }
    }

    public int readMemory() throws IOException {
        commandRunning = false;
        byte[] buffer = new byte[1];
        int numRead;
        mCommands.setRunning(true);
        mLog.Log("READ: Command in Progress!");
        int read_pages = 0;
        if (bootloaderCommandsRead && bootloaderGIDRead) {
            //mHandler.obtainMessage(Constants.MESSAGE_READ_MEMORY_START).sendToTarget();
            long address = Constants.STM32_START_ADDRESS; // TODO: Start adress and page count from devices
            int emptyBytes = 0; // TODO check empty bytes
            for (int page = 0; page < Protocol.STM32_PAGE_COUNT; page++) {
                if (emptyBytes > skipReadOutBytes && !fullRead) {
                    mLog.LogF("READ: Read " + String.valueOf(skipReadOutBytes) + " bytes of 0xff. Only empty bytes follow!");
                    break;
                }
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
                        address += Protocol.STM32_BYTE_COUNT;
                        switch (buffer[0]) {
                            case Protocol.STM32_ACK:
                                sendByte((byte) (Protocol.STM32_BYTE_COUNT - 1));
                                sendByte((byte) ~(Protocol.STM32_BYTE_COUNT - 1));
                                numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                switch (buffer[0]) {
                                    case Protocol.STM32_ACK:
                                        long[] dataBuf = new long[2];
                                        dataBuf[0] = page;
                                        byte[] data = new byte[getReadBlockSize()]; // TODO: blocksize to read in expert settings
                                        for (int i = 0; i < data.length; i++) {
                                            numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                                            if (numRead > 0) {
                                                data[i] = buffer[0];
                                                dataBuf[1] = i;
                                                if (mOnReadMemoryByteListener != null)
                                                    mOnReadMemoryByteListener.onByte(dataBuf);
                                                if (buffer[0] == (byte) 0xFF) emptyBytes++;
                                                else
                                                    emptyBytes = 0;
                                            }
                                        }
                                        if (page == 0)
                                            writeToFile(data, false);
                                        else
                                            writeToFile(data, true); // TODO file write error exception
                                        //mLog.LogF("READ: Read Page "+String.valueOf(page));
                                        read_pages++;
                                        break;
                                }
                                break;
                            default:
                                mLog.Log(Constants.ERROR, "READ: Address Error on Read PAGE " + String.valueOf(page) + " [" + String.format("0x%02x", buffer[0]) + "]");
                                commandRunning = false;
                                return 0;
                        }
                        break;
                    default:
                        mLog.Log(Constants.ERROR, "READ: Command Error on Read PAGE " + String.valueOf(page) + " [" + String.format("0x%02x", buffer[0]) + "]");
                        commandRunning = false;
                        return 0;
                }
            }
            mLog.Log(4, "READ: Read " + String.valueOf(read_pages) + " Pages");
            mLog.Log(4, "READ: Command success!");
            commandRunning = false;
            return read_pages;
        } else {
            if (!bootloaderCommandsRead)
                mLog.Log(Constants.ERROR, "READ: Error! GET Command not completed!");
            else
                mLog.Log(Constants.ERROR, "READ: Error! Read Memory Command not in instruction set! (Maybe readout protected)");
            return 0;
        }
    }

    private int readTimeout(byte[] b, int timeoutMillis) throws IOException {
        int bufferOffset = 0;
        long maxTimeMillis = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < maxTimeMillis && bufferOffset < b.length) {
            int readLength = java.lang.Math.min(mmInStream.available(), b.length - bufferOffset);
            // can alternatively use bufferedReader, guarded by isReady():
            int readResult = mmInStream.read(b, bufferOffset, readLength);
            if (readResult == -1) break;
            bufferOffset += readResult;
        }
        // Share the sent message back to the UI Activity
        mHandler.obtainMessage(Constants.MESSAGE_READ, -1, -1, b)
                .sendToTarget();
        return bufferOffset;
    }

    public void write(byte[] buffer) {
        try {
            mmOutStream.write(buffer);

            // Share the sent message back to the UI Activity
            mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                    .sendToTarget();
        } catch (IOException e) {
            mHandler.obtainMessage(Constants.MESSAGE_IO_ERROR, -1, -1, buffer)
                    .sendToTarget();
            mLog.Log(2, "Error: Exception during write ("+e.getMessage()+")");
        }
    }

    public boolean writeToFile(byte[] array, boolean overwrite) {
        String path = Environment.getExternalStorageDirectory() + "//STM32//";
        String filepath;
        filepath = path + "backup" + Constants.FIRMWARE_EXTENSION; // Add date!?
        /* MagicLight specific
        if (mCommands.getVer_major() > 0) {
            filepath = path + String.format("%s_%d_%d_build%d",  Constants.FIRMWARE_FILENAME, mCommands.getVer_major(), mCommands.getVer_minor(), mCommands.getVer_build()) + Constants.FIRMWARE_EXTENSION;
        } else {
            filepath = path + "backup" + Constants.FIRMWARE_EXTENSION; // Add date!?
        }
        */
        //LogTextView.d(TAG, path); // TODO: Handler message for filename?
        FileOutputStream stream;
        try {
            stream = new FileOutputStream(filepath, overwrite);
            try {
                stream.write(array);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            } finally {
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return true;

    }

    private void sendByte(byte b) throws IOException {
        byte[] send = new byte[1];
        send[0] = b;
        write(send);
    }

    public byte getBootloaderVersion() {
        return bootloaderVersion;
    }

    public byte[] getBootloaderCommands() {
        return bootloaderCommands;
    }

    public int getBootloaderCommandCount() {
        return bootloaderCommandCount;
    }

    public byte[] getBootloaderProductId() {
        return bootloaderProductId;
    }

    public byte[] getBootloaderReadProtection() {
        return bootloaderReadProtection;
    }

    public String getBootloaderProductName() {
        if (bootloaderGIDRead && bootloaderCommandsRead)
            return bootloaderProductName;
        else return null;
    }

    public boolean isCommandRunning() {
        return commandRunning;
    }

    public void setCommandRunning(boolean commandRunning) {
        this.commandRunning = commandRunning;
    }

    public int getReadBlockSize() {
        return readBlockSize;
    }

    public int getWriteBlockSize() {
        return writeBlockSize;
    }

    public void setSkipReadOutBytes(int skip) {
        skipReadOutBytes = skip;
    }

    public void setFullReadMemory(boolean prefFullRead) {
        fullRead = prefFullRead;
    }

    public void setPreEraseAll(boolean prefEraseAll) {
        preEraseAll = prefEraseAll;
    }

    public void setResetAfterWrite(boolean prefResetWrite) {
        resetAfterWrite = prefResetWrite;
    }

    public void setSendBootloaderCommand(String prefBootloaderCommand) {
        bootloaderRunCommand = prefBootloaderCommand;
    }

    public void setSendInitSequence(boolean prefSendInit) {
        sendInit = prefSendInit;
    }


    public void setBootloaderCommand(boolean prefSendBootloaderCommand) {
        sendBootloaderCommand = prefSendBootloaderCommand;
    }

    public boolean setBooloaderInitDelay(String prefBootLoaderInit) {
        try {
            initDelay = Integer.parseInt(prefBootLoaderInit);
            return true;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean getFullReadMemory() {
        return fullRead;
    }
}

