package de.sauernetworks.firmware_updater;

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

    String DEVICE_NAME = "device_name";
    String TOAST = "toast";

    // TODO firmware check
    int FIRMWARE_MIN_MINOR = 4;
    int FIRMWARE_MIN_BUILD = 800;

    String FIRMWARE_FILENAME = "backup";
    String FIRMWARE_EXTENSION = ".bin";

    int FIRMWARE_MAX_EMPTY_BYTES = 32;

    long STM32_START_ADDRESS = 0x08000000;
    byte STM32_PID = 0x33;
    String STM32_NAME_PATTERN = "MagicLightV2-\\w\\w\\w\\w";
}
