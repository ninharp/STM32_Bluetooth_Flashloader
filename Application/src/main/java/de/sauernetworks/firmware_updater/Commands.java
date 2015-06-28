package de.sauernetworks.firmware_updater;

import android.util.Log;

/**
 * Created by michael on 28.06.15.
 */
public class Commands {
    private static final String TAG = "STM32_FWU_CMDS";
    private static int cmd_count = 12;
    private int cmd_active_count = 0;
    Command[] commands = new Command[cmd_count];
    byte[] active_commands = new byte[cmd_count];

    public Commands() {
        commands[0] = new Command((byte) 0x00, "Get Command");
        commands[1] = new Command((byte) 0x01, "Get Version and Read Protection Status");
        commands[2] = new Command((byte) 0x02, "Get ID");
        commands[3] = new Command((byte) 0x11, "Read Memory Command");
        commands[4] = new Command((byte) 0x21, "Go Command");
        commands[5] = new Command((byte) 0x31, "Write Memory Command");
        commands[6] = new Command((byte) 0x43, "Erase Command");
        commands[7] = new Command((byte) 0x44, "Extended Erase Command");
        commands[8] = new Command((byte) 0x63, "Write Protect Command");
        commands[9] = new Command((byte) 0x73, "Write Unprotect Command");
        commands[10] = new Command((byte) 0x82, "Readout Protect Command");
        commands[11] = new Command((byte) 0x92, "Readout Unprotect Command");
    }

    public String getCommandName(byte command) {
        for (int c = 0; c < cmd_count; c++)
            if (commands[c].getCmd() == command)
                return commands[c].getName();
        return null;
    }

    public void addCommand(byte cmd) {
        active_commands[cmd_active_count++] = cmd;
        String temp = String.format("Added active command %02x on pos %d", cmd, (cmd_active_count-1));
        Log.d(TAG, temp);
    }

    public byte[] getActiveCommands() {
        return active_commands;
    }

    public int getActiveCommandCount() {
        return cmd_active_count;
    }
}
