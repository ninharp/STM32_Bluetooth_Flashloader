package de.sauernetworks.stm_bootloader;

/**
 * Created by michael on 28.06.15.
 */
public class Command {
    private String name;
    private byte cmd;

    // constructor
    public Command(byte cmd, String name) {
        this.name = name;
        this.cmd = cmd;
    }

    // getter
    public String getName() { return name; }
    public byte getCmd() { return cmd; }
    // setter

    public void setName(String name) { this.name = name; }
    public void setCmd(byte cmd) { this.cmd = cmd; }

}
