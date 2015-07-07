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

    int MESSAGE_VERSION_COMPLETE = 9;
    int MESSAGE_WRITE_MEMORY_BYTE = 10;
    int MESSAGE_WRITE_MEMORY_FAILED = 11;
    int MESSAGE_WRITE_MEMORY_COMPLETE = 12;
    int MESSAGE_WRITE_START = 14;
    int MESSAGE_WRITE_MEMORY_FILE_ERROR = 15;
    int MESSAGE_ERASE_MEMORY_START = 16;
    int MESSAGE_ERASE_MEMORY_FAILED = 17;
    int MESSAGE_IO_ERROR = 18;

    int ERROR = 3;
    int DEBUG = 9;

    int RESULT_SETTINGS = 4;

    String DEVICE_NAME = "device_name";
    String TOAST = "toast";

    String FIRMWARE_FILENAME = "backup";
    String FIRMWARE_EXTENSION = ".bin";

    long STM32_START_ADDRESS = 0x08000000;
    String STM32_NAME_PATTERN = "MagicLightV2-\\w\\w\\w\\w";
}
