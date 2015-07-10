package de.sauernetworks.stm_bootloader;

/**
 * stm32_bluetooth_flashloader - Open Source Android App to flash ST STM32 over bluetooth
 * Copyright (C) 2015 Michael Sauer <sauer.uetersen@gmail.com>
 *
 * Device Table adapted from stm32flash
 * Copyright (C) 2010 Geoffrey McRae <geoff@spacevs.com>
 * Copyright (C) 2014 Antonio Borneo <borneo.antonio@gmail.com>
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
 * Created by Michael Sauer at 01:04 on 05.07.15
 **/
public class Devices {
    private static final int DEVICE_COUNT = 29;
    Device[] devices = new Device[DEVICE_COUNT];

    /*
    * Device table, corresponds to the "Bootloader device-dependant parameters"
    * table in ST document AN2606.
    * Note that the option bytes upper range is inclusive!
    */
    public Devices() {
        /* F0 */
        devices[0] = new Device(0x440, "STM32F051xx" , 0x20001000, 0x20002000, 0x08000000, 0x08010000,  4, 1024, 0x1FFFF800, 0x1FFFF80B, 0x1FFFEC00, 0x1FFFF800);
        devices[1] = new Device(0x444, "STM32F030/F031"    , 0x20001000, 0x20002000, 0x08000000, 0x08010000,  4, 1024, 0x1FFFF800, 0x1FFFF80B, 0x1FFFEC00, 0x1FFFF800);
        devices[2] = new Device(0x445, "STM32F042xx"       , 0x20001800, 0x20001800, 0x08000000, 0x08008000,  4, 1024, 0x1FFFF800, 0x1FFFF80F, 0x1FFFC400, 0x1FFFF800);
        devices[3] = new Device(0x448, "STM32F072xx"       , 0x20001800, 0x20004000, 0x08000000, 0x08020000,  2, 2048, 0x1FFFF800, 0x1FFFF80F, 0x1FFFC800, 0x1FFFF800);
	    /* F1 */
        devices[4] = new Device(0x412, "Low-density"       , 0x20000200, 0x20002800, 0x08000000, 0x08008000,  4, 1024, 0x1FFFF800, 0x1FFFF80F, 0x1FFFF000, 0x1FFFF800);
        devices[5] = new Device(0x410, "Medium-density"    , 0x20000200, 0x20005000, 0x08000000, 0x08020000,  4, 1024, 0x1FFFF800, 0x1FFFF80F, 0x1FFFF000, 0x1FFFF800);
        devices[6] = new Device(0x414, "High-density"      , 0x20000200, 0x20010000, 0x08000000, 0x08080000,  2, 2048, 0x1FFFF800, 0x1FFFF80F, 0x1FFFF000, 0x1FFFF800);
        devices[7] = new Device(0x420, "Medium-density VL" , 0x20000200, 0x20002000, 0x08000000, 0x08020000,  4, 1024, 0x1FFFF800, 0x1FFFF80F, 0x1FFFF000, 0x1FFFF800);
        devices[8] = new Device(0x428, "High-density VL"   , 0x20000200, 0x20008000, 0x08000000, 0x08080000,  2, 2048, 0x1FFFF800, 0x1FFFF80F, 0x1FFFF000, 0x1FFFF800);
        devices[9] = new Device(0x418, "Connectivity line" , 0x20001000, 0x20010000, 0x08000000, 0x08040000,  2, 2048, 0x1FFFF800, 0x1FFFF80F, 0x1FFFB000, 0x1FFFF800);
        devices[10] = new Device(0x430, "XL-density"        , 0x20000800, 0x20018000, 0x08000000, 0x08100000,  2, 2048, 0x1FFFF800, 0x1FFFF80F, 0x1FFFE000, 0x1FFFF800);
	    /* Note that F2 and F4 devices have sectors of different page sizes
           and only the first sectors (of one page size) are included here */
	    /* F2 */
        devices[11] = new Device(0x411, "STM32F2xx"         , 0x20002000, 0x20020000, 0x08000000, 0x08100000,  4, 16384, 0x1FFFC000, 0x1FFFC00F, 0x1FFF0000, 0x1FFF77DF);
	    /* F3 */
        devices[12] = new Device(0x432, "STM32F373/8"       , 0x20001400, 0x20008000, 0x08000000, 0x08040000,  2, 2048, 0x1FFFF800, 0x1FFFF80F, 0x1FFFD800, 0x1FFFF800);
        devices[13] = new Device(0x422, "F302xB/303xB/358"  , 0x20001400, 0x20010000, 0x08000000, 0x08040000,  2, 2048, 0x1FFFF800, 0x1FFFF80F, 0x1FFFD800, 0x1FFFF800);
        devices[14] = new Device(0x439, "STM32F302x4(6/8)"  , 0x20001800, 0x20004000, 0x08000000, 0x08040000,  2, 2048, 0x1FFFF800, 0x1FFFF80F, 0x1FFFD800, 0x1FFFF800);
        devices[15] = new Device(0x438, "F303x4/334/328"    , 0x20001800, 0x20003000, 0x08000000, 0x08040000,  2, 2048, 0x1FFFF800, 0x1FFFF80F, 0x1FFFD800, 0x1FFFF800);
	    /* F4 */
        devices[16] = new Device(0x413, "STM32F40/1"        , 0x20002000, 0x20020000, 0x08000000, 0x08100000,  4, 16384, 0x1FFFC000, 0x1FFFC00F, 0x1FFF0000, 0x1FFF77DF);
	    /* 0x419 is also used for STM32F429/39 but with other bootloader ID... */
        devices[17] = new Device(0x419, "STM32F427/37"      , 0x20002000, 0x20030000, 0x08000000, 0x08100000,  4, 16384, 0x1FFFC000, 0x1FFFC00F, 0x1FFF0000, 0x1FFF77FF);
        devices[18] = new Device(0x423, "STM32F401xB(C)"    , 0x20003000, 0x20010000, 0x08000000, 0x08100000,  4, 16384, 0x1FFFC000, 0x1FFFC00F, 0x1FFF0000, 0x1FFF77FF);
        devices[19] = new Device(0x433, "STM32F401xD(E)"    , 0x20003000, 0x20018000, 0x08000000, 0x08080000,  4, 16384, 0x1FFFC000, 0x1FFFC00F, 0x1FFF0000, 0x1FFF77FF);
	    /* L0 */
        devices[20] = new Device(0x417, "L05xxx/06xxx"      , 0x20001000, 0x20002000, 0x08000000, 0x08010000, 32,  128, 0x1FF80000, 0x1FF8000F, 0x1FF00000, 0x1FF01000);
	    /* L1 */
        devices[21] = new Device(0x416, "L1xxx6(8/B)"       , 0x20000800, 0x20004000, 0x08000000, 0x08020000, 16,  256, 0x1FF80000, 0x1FF8000F, 0x1FF00000, 0x1FF01000);
        devices[22] = new Device(0x429, "L1xxx6(8/B)A"      , 0x20001000, 0x20008000, 0x08000000, 0x08020000, 16,  256, 0x1FF80000, 0x1FF8000F, 0x1FF00000, 0x1FF01000);
        devices[23] = new Device(0x427, "L1xxxC"            , 0x20001000, 0x20008000, 0x08000000, 0x08020000, 16,  256, 0x1FF80000, 0x1FF8000F, 0x1FF00000, 0x1FF02000);
        devices[24] = new Device(0x436, "L1xxxD"            , 0x20001000, 0x2000C000, 0x08000000, 0x08060000, 16,  256, 0x1ff80000, 0x1ff8000F, 0x1FF00000, 0x1FF02000);
        devices[25] = new Device(0x437, "L1xxxE"            , 0x20001000, 0x20014000, 0x08000000, 0x08060000, 16,  256, 0x1ff80000, 0x1ff8000F, 0x1FF00000, 0x1FF02000);
	    /* These are not (yet) in AN2606: */
        devices[26] = new Device(0x641, "Medium_Density PL" , 0x20000200, 0x00005000, 0x08000000, 0x08020000,  4, 1024, 0x1FFFF800, 0x1FFFF80F, 0x1FFFF000, 0x1FFFF800);
        devices[27] = new Device(0x9a8, "STM32W-128K"       , 0x20000200, 0x20002000, 0x08000000, 0x08020000,  1, 1024, 0, 0, 0, 0);
        devices[28] = new Device(0x9b0, "STM32W-256K"       , 0x20000200, 0x20004000, 0x08000000, 0x08040000,  1, 2048, 0, 0, 0, 0);
    }

    public int getDeviceCount() {
        return DEVICE_COUNT;
    }

    public String getDeviceName(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return device.getDeviceName();
        }
        return null;
    }

    public long getRamStart(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return device.getRamStart();
        }
        return 0;
    }

    public long getRamEnd(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return device.getRamEnd();
        }
        return 0;
    }

    public long getFlashStart(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return device.getFlashStart();
        }
        return 0;
    }

    public long getFlashEnd(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return device.getFlashEnd();
        }
        return 0;
    }

    public long getOptionStart(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return device.getOptionStart();
        }
        return 0;
    }

    public long getOptionEnd(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return device.getOptionEnd();
        }
        return 0;
    }

    public long getMemoryStart(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return device.getMemStart();
        }
        return 0;
    }

    public long getMemoryEnd(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return device.getMemEnd();
        }
        return 0;
    }

    public int getPagesPerSector(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return device.getPagesPerSector();
        }
        return 0;
    }

    public int getPageSize(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return device.getPageSize();
        }
        return 0;
    }

    public Device getDevice(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return device;
        }
        return null;
    }

    public long getFlashSize(int deviceID) {
        for (Device device : devices) {
            if (device.getDeviceID() == deviceID)
                return (device.getFlashEnd()-device.getFlashStart())/1024;
        }
        return 0;
    }
}
