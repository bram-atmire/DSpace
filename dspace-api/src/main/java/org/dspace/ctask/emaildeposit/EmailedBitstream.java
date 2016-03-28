package org.dspace.ctask.emaildeposit;

/**
 * EmailedBitstream represents a bitstream that was emailed by the submitter in order to have it attached to an item.
 *
 * The class assumes that the emailed bitstream is temporarily stored on the server, and that it has not been
 * ingested into DSpace itself.
 *
 * @author bram-atmire
 */
public class EmailedBitstream {

    private String submitter;
    private String itemID;
    private String temporaryPath;

    public EmailedBitstream(String submitter, String itemID, String temporaryPath) {
        this.submitter = submitter;
        this.itemID = itemID;
        this.temporaryPath = temporaryPath;
    }

    public String getSubmitter() {
        return submitter;
    }

    public void setSubmitter(String submitter) {
        this.submitter = submitter;
    }

    public String getItemID() {
        return itemID;
    }

    public void setItemID(String itemID) {
        this.itemID = itemID;
    }

    public String getTemporaryPath() {
        return temporaryPath;
    }

    public void setTemporaryPath(String temporaryPath) {
        this.temporaryPath = temporaryPath;
    }
}
