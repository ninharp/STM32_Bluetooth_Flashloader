package de.sauernetworks.stm32_bluetooth_flashloader;
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
 **/
public interface Constants {
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
    int MESSAGE_READ_MEMORY_COMPLETE = 16;
    int MESSAGE_VERSION_COMPLETE = 17;
    int MESSAGE_WRITE_MEMORY_BYTE = 18;
    int MESSAGE_WRITE_MEMORY_FAILED = 19;
    int MESSAGE_WRITE_MEMORY_COMPLETE = 20;
    int MESSAGE_ERASE_MEMORY_COMPLETE = 21;
    int MESSAGE_WRITE_START = 22;
    int MESSAGE_WRITE_MEMORY_FILE_ERROR = 23;
    int MESSAGE_ERASE_MEMORY_START = 24;
    int MESSAGE_ERASE_MEMORY_FAILED = 25;
    int MESSAGE_READ_MEMORY_START = 26;

    int RESULT_SETTINGS = 4;

    String DEVICE_NAME = "device_name";
    String TOAST = "toast";

    String FIRMWARE_FILENAME = "backup";
    String FIRMWARE_EXTENSION = ".bin";

    long STM32_START_ADDRESS = 0x08000000;
    byte STM32_PID = 0x33;
    String STM32_NAME_PATTERN = "MagicLightV2-\\w\\w\\w\\w";
}
