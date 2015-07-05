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
package de.sauernetworks.tools;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class Logger {

    String filename = "firmware_updater";
    String fileext = "log";
    String tag = "firmware_updater";
    Context mContext;
    boolean enabled = true;
    boolean syslog = true;
    int verbose = 3;

    FileOutputStream osLog;

    public Logger(Context mContext, boolean enabled, boolean syslog, String tag, int verbose) {
        this.mContext = mContext;
        this.enabled = enabled;
        this.syslog = syslog;
        this.tag = tag;
        this.verbose = verbose;
        openLogFile(true);
    }

    public Logger(Context mContext) {
        this.mContext = mContext;
        if (openLogFile(true) == 1) {
            Log.d(tag, "Error on opening log file!");
        }
    }

    private int openLogFile(boolean append) {
        String path = Environment.getExternalStorageDirectory() + "//STM32//";
        String filepath = path + filename + "." + this.fileext;
        try {
            osLog = new FileOutputStream(filepath, append);
            return 0;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return 1;
        }
    }

    public void Log(int verbose, String message) {
        message = String.format("[%d] %s", verbose, message);
        if (verbose <= this.verbose) {
            writeLog(message);
            if (syslog)
                Log.i(tag, message);
        }
    }

    public void Log(String message) {
        if (verbose >= 9) {
            message = String.format("[D] %s", message);
            writeLog(message);
            if (syslog)
                Log.d(tag, message);
        }
    }

    public void LogF(String message) {
        if (verbose >= 9) {
            message = String.format("[D] %s", message);
            writeLog(message);
        }
    }

    private boolean writeLog(String message) {
        if (enabled) {
            try {
                if (!message.contains("\n"))
                    message = message + "\n";
                osLog.write(message.getBytes());
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    public String getFileext() {
        return fileext;
    }

    public void setFileext(String fileext) {
        this.fileext = fileext;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
        if (filename.contains(".")) {
            try {
                String[] f = filename.split(":");
                this.filename = f[0];
                this.fileext = f[1];
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        } else {
            this.filename = filename;
        }
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public int getVerbose() {
        return verbose;
    }

    public void setVerbose(int verbose) {
        this.verbose = verbose;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSyslog() { return syslog; }

    public void setSyslog(boolean syslog) { this.syslog = syslog; }
}
