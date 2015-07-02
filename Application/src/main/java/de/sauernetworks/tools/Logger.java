package de.sauernetworks.tools;

import android.content.Context;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import de.sauernetworks.firmware_updater.Constants;

/**
 * Created by michael on 01.07.15.
 */
public class Logger {
    String filename = "firmware_updater";
    String fileext = "log";
    String tag = "firmware_updater";
    Context mContext;
    boolean enabled = true;
    boolean syslog = true;
    int verbose = 9;

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
        String path = mContext.getFilesDir().toString() + "/";
        String filepath = path + filename + "." + this.fileext;
        //LogTextView.d(TAG, path);
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
        if (enabled) {
            if (verbose <= this.verbose) {
                writeLog(message);
                if (syslog)
                    Log.i(tag, message);
            }
        }
    }

    public void Log(String message) {
        message = String.format("[I] %s", message);
        if (enabled) {
            writeLog(message);
            if (syslog)
                Log.i(tag, message);
        }
    }

    public void LogF(String message) {
        writeLog(message);
    }

    private int writeLog(String message) {
        try {
            if (!message.contains("\n"))
                message = message + "\n";
            osLog.write(message.getBytes());
            return 0;
        } catch (IOException e) {
            e.printStackTrace();
            return 1;
        }
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
