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
 * Created by Michael Sauer at 01:09 on 05.07.15
 **/
public class Device {
    int deviceID;
    String deviceName;
    long ram_start;
    long ram_end;
    long flash_start;
    long flash_end;
    int pages_per_sector;
    int page_size;
    long option_start;
    long option_end;
    long mem_start;
    long mem_end;

    public Device(int deviceID, String deviceName, long ram_start, long ram_end, long flash_start, long flash_end, int pages_per_sector, int page_size, long option_start, long option_end, long mem_start, long mem_end) {
        this.deviceID = deviceID;
        this.deviceName = deviceName;
        this.ram_start = ram_start;
        this.ram_end = ram_end;
        this.flash_start = flash_start;
        this.flash_end = flash_end;
        this.pages_per_sector = pages_per_sector;
        this.page_size = page_size;
        this.option_start = option_start;
        this.option_end = option_end;
        this.mem_start = mem_start;
        this.mem_end = mem_end;
    }

    public long getMem_end() {
        return mem_end;
    }

    public int getDeviceID() {
        return deviceID;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public long getRam_start() {
        return ram_start;
    }

    public long getRam_end() {
        return ram_end;
    }

    public long getFlash_start() {
        return flash_start;
    }

    public long getFlash_end() {
        return flash_end;
    }

    public int getPages_per_sector() {
        return pages_per_sector;
    }

    public int getPage_size() {
        return page_size;
    }

    public long getOption_start() {
        return option_start;
    }

    public long getOption_end() {
        return option_end;
    }

    public long getMem_start() {
        return mem_start;
    }


}
