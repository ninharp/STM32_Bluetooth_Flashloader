package de.sauernetworks.stm_bootloader;
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
