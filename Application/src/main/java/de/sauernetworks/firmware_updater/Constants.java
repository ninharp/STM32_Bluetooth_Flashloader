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

/**
 * Defines several constants used between {@link BluetoothService} and the UI.
 */
public interface Constants {

    int DEBUG = 1;

    // Message types sent from the BluetoothChatService Handler
    int MESSAGE_STATE_CHANGE = 1;
    int MESSAGE_READ = 2;
    int MESSAGE_WRITE = 3;
    int MESSAGE_DEVICE_NAME = 4;
    int MESSAGE_TOAST = 5;

    int MESSAGE_ACK_RECEIVED = 6;
    int MESSAGE_NACK_RECEIVED = 7;
    int MESSAGE_INIT_COMPLETE = 8;
    int MESSAGE_INIT_FAILED = 9;
    int MESSAGE_GET_COMPLETE = 10;
    int MESSAGE_BL_VERSION = 11;
    int MESSAGE_GID_COMPLETE = 12;
    int MESSAGE_GO_COMPLETE = 13;
    int MESSAGE_READ_MEMORY_BYTE = 14;
    int MESSAGE_READ_MEMORY_FAILED = 15;
    int MESSAGE_READ_MEMORY_DONE = 16;
    int MESSAGE_VERSION_COMPLETE = 17;

    // Key names received from the BluetoothChatService Handler
    String DEVICE_NAME = "device_name";
    String TOAST = "toast";

    // TODO firmware check
    int FIRMWARE_MIN_MINOR = 4;
    int FIRMWARE_MIN_BUILD = 800;

    String FIRMWARE_FILENAME = "/firmwaretest";
    String FIRMWARE_EXTENSION = ".bin";

    int STM32_READ_BYTE_COUNT = 256;
    int STM32_READ_PAGE_COUNT = 16;
    long STM32_START_ADDRESS = 0x08000000;
    byte STM32_PID = 0x33;
    String STM32_NAME_PATTERN = "MagicLightV2-\\w\\w\\w\\w";
    int STM32_READ_TIMEOUT = 2000;

    byte STM32_INIT = 0x7F;

    byte STM32_ACK = 0x79;
    byte STM32_NACK = 0x1F;

    byte STM32_GET_COMMAND = 0x00;
    byte STM32_GVRP_COMMAND = 0x01;
    byte STM32_GET_ID_COMMAND = 0x02;
    byte STM32_GO_COMMAND = 0x21;
    byte STM32_READ_COMMAND = 0x11;

}
