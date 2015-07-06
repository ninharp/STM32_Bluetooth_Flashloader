package de.sauernetworks.stm_bootloader;

import android.content.Context;

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

    private Devices mDevices;
    private byte bootloaderVersion;
    private byte[] bootloaderCommands;
    private int bootloaderCommandCount;
    private byte[] bootloaderProductId;
    private String bootloaderProductName;
    private boolean bootloaderCommandsRead; // Is GET Command already run?
    private boolean bootloaderGIDRead;

    public Bootloader(Context mContext, InputStream mmInStream, OutputStream mmOutStream, Logger mLog) {
        this.mContext = mContext;
        this.mmInStream = mmInStream;
        this.mmOutStream = mmOutStream;
        this.mLog = mLog;
        mDevices = new Devices();
        bootloaderVersion = 0;
        bootloaderCommandCount = 0;
        bootloaderCommandsRead = false;
        bootloaderGIDRead = false;
    }

    public boolean doInit() throws IOException {
        byte[] buffer = new byte[1];
        mLog.Log(8, "INIT in Progress!");
        mmInStream.skip(mmInStream.available());
        sendByte(Protocol.STM32_INIT);
        int numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
        switch (buffer[0]) {
            case Protocol.STM32_ACK:
                mLog.Log(9, "INIT: ACK Received!");
                return true;
            case Protocol.STM32_NACK:
                mLog.Log(9, "INIT: NACK Received!");
                return false;
            default:
                mLog.Log(3, "INIT: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                return false;
        }
    }

    public boolean doGetCommand() throws IOException {
        byte[] buffer = new byte[1];
        mLog.Log(8, "GET Command in Progress!");
        sendByte(Protocol.STM32_GET_COMMAND);
        sendByte((byte) (~Protocol.STM32_GET_COMMAND));
        int numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
        switch (buffer[0]) {
            case Protocol.STM32_ACK:
                mLog.Log("GET: ACK Received!");
                numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                byte cmd_count = buffer[0];
                String temp = String.format("GET: %d Bytes Follow", cmd_count);
                mLog.Log(7, temp);
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
                    mLog.Log(4, "GET: Command success!");
                    return true;
                } else {
                    mLog.Log(3, "GET: Command failed!");
                    return false;
                }
            case Protocol.STM32_NACK:
                mLog.Log(8, "GET: NACK Received!");
                return false;
            default:
                mLog.Log(3, "GET: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                return false;
        }
    }

    public boolean doGVRPCommand() throws IOException {
        byte[] buffer = new byte[1];
        mLog.Log(8, "GVRP Command in Progress!");
        if (bootloaderCommandsRead) { // && mCommands.isActiveCommand(Protocol.STM32_GVRP_COMMAND)) {
            sendByte(Protocol.STM32_GVRP_COMMAND);
            sendByte((byte) (~Protocol.STM32_GVRP_COMMAND));
            int numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
            switch (buffer[0]) {
                case Protocol.STM32_ACK:
                    for (int i = 0; i < 3; i++) {
                        numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                        String temp = String.format("GVRP: 0x%02x", buffer[0]);
                        mLog.Log(7, temp);
                    }
                    mmInStream.read(buffer);
                    if (buffer[0] == Protocol.STM32_ACK) {
                        mLog.Log(4, "GVRP: Command success!");
                        return true;
                    } else {
                        mLog.Log(3, "GVRP: Command failed!");
                        return false;
                    }
                case Protocol.STM32_NACK:
                    mLog.Log(3, "GVRP: NACK Received!");
                    return false;
                default:
                    mLog.Log(3, "GVRP: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                    return false;
            }
        } else {
            if (bootloaderCommandsRead)
                mLog.Log(3, "GVRP: Error! GET Command not completed!");
            else
                mLog.Log(3, "GVRP: Error! GVRP Command not in instruction set!");
            return false;
        }
    }

    public boolean doGIDCommand() throws IOException {
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
                    mLog.Log(temp);
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
                        mLog.Log(4, "GID: Command success!");
                        bootloaderGIDRead = true;
                        bootloaderProductId = gid_buffer.clone();
                        return true;
                    } else {
                        bootloaderGIDRead = false;
                        mLog.Log(3, "GID: Command failed!");
                        return false;
                    }
                case Protocol.STM32_NACK:
                    bootloaderGIDRead = false;
                    mLog.Log(3, "GID: NACK Received!");
                    return false;
                default:
                    bootloaderGIDRead = false;
                    mLog.Log(3, "GID: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                    return false;
            }
        } else {
            if (bootloaderCommandsRead && !bootloaderGIDRead) // && !mCommands.isRunning())
                mLog.Log(3, "GID: Error! GET Command not completed!");
            else
                mLog.Log(3, "GID: Error! GID Command not completed!");
            bootloaderGIDRead = false;
            return false;
        }
    }

    public boolean doGOCommand() throws IOException {
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
                    mmOutStream.write(buf);
                    numRead = readTimeout(buffer, Protocol.STM32_READ_TIMEOUT);
                    if (buffer[0] == Protocol.STM32_ACK) {
                        mLog.Log(4, "GO: Jump command successed!");
                        return true;
                    } else if (buffer[0] == Protocol.STM32_NACK) {
                        mLog.Log(3, "GO: NACK Received!");
                        return false;
                    } else {
                        mLog.Log(3, "GO: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                        return false;
                    }
                case Protocol.STM32_NACK:
                    mLog.Log(3, "GO: NACK Received!");
                    return false;
                default:
                    mLog.Log(3, "GO: No valid byte received! (" + String.format("0x%02x", buffer[0]) + ")");
                    return false;
            }
        } else {
            if (bootloaderCommandsRead)
                mLog.Log("GO: Error! GET Command not completed!");
            else
                mLog.Log("GO: Error! GO Command not in instruction set!");
            return false;
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
        return bufferOffset;
    }

    /**
     * Sends a byte
     *
     * @param b A byte to send.
     */
    private void sendByte(byte b) throws IOException {

        // Check that we're actually connected before trying anything
        /*if (getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this.getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }*/
        // Get the message bytes and tell the BluetoothChatService to write
        byte[] send = new byte[1];
        send[0] = b;
        mmOutStream.write(send);
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

    public String getBootloaderProductName() {
        if (bootloaderGIDRead && bootloaderCommandsRead)
            return bootloaderProductName;
        else return null;
    }

}
