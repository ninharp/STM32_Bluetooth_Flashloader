package de.sauernetworks.stm_bootloader;

public interface Protocol {
    int STM32_BYTE_COUNT = 256;
    int STM32_PAGE_COUNT = 2048; //384; // Need 2048 for full

    int STM32_READ_TIMEOUT = 2000; ///< Read timeout in milliseconds
    long STM32_EER_TIMEOUT = 15000; ///< Read timeout for EER command in milliseconds

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
