package de.sauernetworks.stm32bootloader;

public interface Protocol {
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

    int STM32_BYTE_COUNT = 256;
    int STM32_PAGE_COUNT = 2048; //384; // Need 2048 for full

    int STM32_READ_TIMEOUT = 2000; ///< Read timeout in milliseconds

    byte STM32_INIT = 0x7F;

    byte STM32_ACK = 0x79;
    byte STM32_NACK = 0x1F;

    byte STM32_GET_COMMAND = 0x00;
    byte STM32_GVRP_COMMAND = 0x01;
    byte STM32_GET_ID_COMMAND = 0x02;
    byte STM32_READ_COMMAND = 0x11;
    byte STM32_GO_COMMAND = 0x21;
    byte STM32_WRITE_COMMAND = 0x31;
    byte STM32_EER_COMMAND = 0x44;

}
