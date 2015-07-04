package de.sauernetworks.stm_bootloader;

/**
 * Created by michael on 28.06.15.
 */
public class Commands {
    private static final String TAG = "STM32_FWU_CMDS";
    private static int cmd_count = 12;
    private int cmd_active_count = 0;
    Command[] commands = new Command[cmd_count];
    byte[] active_commands = new byte[cmd_count];

    private boolean init_in_progress = false;
    private boolean init_complete = false;
    private boolean get_in_progress = false;
    private boolean get_complete = false;
    private boolean gvrp_in_progress = false;
    private boolean gvrp_complete = false;
    private boolean gid_in_progress = false;
    private boolean gid_complete = false;
    private boolean go_in_progress = false;
    private boolean go_complete = false;
    private boolean read_in_progress = false;
    private boolean read_complete = false;
    private boolean read_full = false;
    private boolean version_in_progress = false;
    private boolean version_complete = false;
    private boolean erase_in_progress = false;
    private boolean erase_complete = false;
    private boolean write_in_progress = false;
    private boolean write_complete = false;
    private boolean auto_read_out = false;
    private boolean auto_write_to = false;
    private boolean command_running = false;

    private int ver_major = 0;
    private int ver_minor = 0;
    private int ver_build = 0;
    private int skipBytes;

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
        if (cmd_active_count <= active_commands.length-1)
            active_commands[cmd_active_count++] = cmd;
        //LogTextView.d(TAG, String.format("Added active command %02x on pos %d", cmd, (cmd_active_count-1)));
    }

    public void clearActiveCommands() {
        cmd_active_count = 0;
        active_commands = new byte[cmd_count];
    }

    public byte[] getActiveCommands() {
        return active_commands;
    }

    public int getActiveCommandCount() {
        return cmd_active_count;
    }

    public boolean isActiveCommand(byte cmd) {
        if (active_commands.length > 0) {
            for (byte active_command : active_commands) {
                if (cmd == active_command)
                    return true;
            }
        } else {
            return false;
        }
        return false;
    }

    public boolean isRunning() {
        return command_running;
    }

    public void setRunning(boolean running) {
        this.command_running = running;
    }

    public int getVer_major() {
        return ver_major;
    }

    public void setVer_major(int ver_major) {
        this.ver_major = ver_major;
    }

    public int getVer_minor() {
        return ver_minor;
    }

    public void setVer_minor(int ver_minor) {
        this.ver_minor = ver_minor;
    }

    public int getVer_build() {
        return ver_build;
    }

    public void setVer_build(int ver_build) {
        this.ver_build = ver_build;
    }

    public boolean isAuto_read_out() {
        return auto_read_out;
    }

    public void setAuto_read_out(boolean auto_read_out) {
        this.auto_read_out = auto_read_out;
    }

    public boolean isGid_complete() {
        return gid_complete;
    }

    public void setGid_complete(boolean gid_complete) {
        this.gid_complete = gid_complete;
    }

    public boolean isGid_in_progress() {
        return gid_in_progress;
    }

    public void setGid_in_progress(boolean gid_in_progress) {
        this.gid_in_progress = gid_in_progress;
    }

    public boolean isGvrp_complete() {
        return gvrp_complete;
    }

    public void setGvrp_complete(boolean gvrp_complete) {
        this.gvrp_complete = gvrp_complete;
    }

    public boolean isGvrp_in_progress() {
        return gvrp_in_progress;
    }

    public void setGvrp_in_progress(boolean gvrp_in_progress) {
        this.gvrp_in_progress = gvrp_in_progress;
    }

    public boolean isGet_in_progress() {
        return get_in_progress;
    }

    public void setGet_in_progress(boolean get_in_progress) {
        this.get_in_progress = get_in_progress;
    }

    public boolean isGet_complete() {
        return get_complete;
    }

    public void setGet_complete(boolean get_complete) {
        this.get_complete = get_complete;
    }

    public boolean isInit_in_progress() {
        return init_in_progress;
    }

    public void setInit_in_progress(boolean init_in_progress) {
        this.init_in_progress = init_in_progress;
    }

    public boolean isInit_complete() {
        return init_complete;
    }

    public void setInit_complete(boolean init_complete) {
        this.init_complete = init_complete;
    }

    public boolean isGo_in_progress() {
        return go_in_progress;
    }

    public void setGo_in_progress(boolean go_in_progress) {
        this.go_in_progress = go_in_progress;
    }

    public boolean isGo_complete() {
        return go_complete;
    }

    public void setGo_complete(boolean go_complete) {
        this.go_complete = go_complete;
    }

    public boolean isRead_in_progress() {
        return read_in_progress;
    }

    public void setRead_in_progress(boolean read_in_progress) {
        this.read_in_progress = read_in_progress;
    }

    public boolean isRead_complete() {
        return read_complete;
    }

    public void setRead_complete(boolean read_complete) {
        this.read_complete = read_complete;
    }

    public boolean isVersion_in_progress() {
        return version_in_progress;
    }

    public void setVersion_in_progress(boolean version_in_progress) {
        this.version_in_progress = version_in_progress;
    }

    public boolean isVersion_complete() {
        return version_complete;
    }

    public void setVersion_complete(boolean version_complete) {
        this.version_complete = version_complete;
    }

    public boolean isErase_complete() {
        return erase_complete;
    }

    public void setErase_complete(boolean erase_complete) {
        this.erase_complete = erase_complete;
    }

    public boolean isErase_in_progress() {
        return erase_in_progress;
    }

    public void setErase_in_progress(boolean erase_in_progress) {
        this.erase_in_progress = erase_in_progress;
    }

    public boolean isWrite_in_progress() {
        return write_in_progress;
    }

    public void setWrite_in_progress(boolean write_in_progress) {
        this.write_in_progress = write_in_progress;
    }

    public boolean isWrite_complete() {
        return write_complete;
    }

    public void setWrite_complete(boolean write_complete) {
        this.write_complete = write_complete;
    }

    public boolean isRead_full() {
        return read_full;
    }

    public void setRead_full(boolean read_full) {
        this.read_full = read_full;
    }

    public boolean isAuto_write_to() {
        return auto_write_to;
    }

    public void setAuto_write_to(boolean auto_write_to) {
        this.auto_write_to = auto_write_to;
    }

    public void setSkipBytes(int skipBytes) {
        this.skipBytes = skipBytes;
    }

    public void resetStates() {
        setAuto_read_out(false);
        setAuto_write_to(false);
        setGet_in_progress(false);
        setWrite_complete(false);
        setWrite_in_progress(false);
        setErase_complete(false);
        setErase_in_progress(false);
        setRead_complete(false);
        setRead_full(false);
        setRead_in_progress(false);
        setGid_complete(false);
        setGid_in_progress(false);
        setGvrp_in_progress(false);
        setGvrp_complete(false);
        setInit_complete(false);
        setInit_in_progress(false);
        setGo_complete(false);
        setGo_in_progress(false);
        setVersion_complete(false);
        setVersion_in_progress(false);
        setVer_major(0);
        setVer_minor(0);
        setVer_build(0);
        setRunning(false);
    }

    public int getSkipBytes() {
        return skipBytes;
    }
}
