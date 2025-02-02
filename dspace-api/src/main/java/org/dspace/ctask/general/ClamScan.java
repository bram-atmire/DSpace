/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.ListUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Suspendable;

/**
 * ClamScan.java
 *
 * A set of methods to scan using the ClamAV daemon.
 * This version features improved error handling, skips items without an ORIGINAL bundle,
 * reuses the ClamAV connection across items, and applies buffered I/O with a larger chunk size.
 *
 * TODO: add a check for the inputstream size limit
 *
 * @author wbossons (modified)
 */
@Suspendable(invoked = Curator.Invoked.INTERACTIVE)
public class ClamScan extends AbstractCurationTask {
    // Increase the chunk size for efficiency.
    protected final int DEFAULT_CHUNK_SIZE = 8192;
    
    protected final byte[] INSTREAM   = "zINSTREAM\0".getBytes();
    protected final byte[] PING       = "zPING\0".getBytes();
    protected final byte[] STATS      = "nSTATS\n".getBytes();
    protected final byte[] IDSESSION  = "zIDSESSION\0".getBytes();
    protected final byte[] END        = "zEND\0".getBytes();
    
    protected final String PLUGIN_PREFIX       = "clamav";
    protected final String INFECTED_MESSAGE    = "had virus detected.";
    protected final String CLEAN_MESSAGE       = "had no viruses detected.";
    protected final String CONNECT_FAIL_MESSAGE = "Unable to connect to virus service - check setup";
    protected final String SCAN_FAIL_MESSAGE    = "Error encountered using virus service - check setup";
    protected final String NEW_ITEM_HANDLE      = "in workflow";

    private static final Logger log = LogManager.getLogger();

    protected String host = null;
    protected int port = 0;
    protected int timeout = 0;
    protected boolean failfast = true;

    protected int status = Curator.CURATE_UNSET;
    protected List<String> results = null;

    // Reusable socket and streams.
    protected Socket socket = null;
    protected DataOutputStream dataOutputStream = null;
    protected BufferedInputStream dataInputStream = null;

    protected BitstreamService bitstreamService;
    
    // Buffer for file data.
    final byte[] buffer = new byte[DEFAULT_CHUNK_SIZE];

    /**
     * Initializes the task, loading configuration and opening a ClamAV session.
     */
    @Override
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
        host    = configurationService.getProperty(PLUGIN_PREFIX + ".service.host");
        port    = configurationService.getIntProperty(PLUGIN_PREFIX + ".service.port");
        timeout = configurationService.getIntProperty(PLUGIN_PREFIX + ".socket.timeout");
        failfast = configurationService.getBooleanProperty(PLUGIN_PREFIX + ".scan.failfast");
        bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        
        // Open one ClamAV session for the entire run.
        openSession();
    }
    
    /**
     * Called at the end of the curation run to close the ClamAV connection.
     * (This method is not overriding a superclass method.)
     */
    public void finish(Curator curator, boolean status) {
        closeSession();
    }

    /**
     * Process a single DSpace object. For Items, if an ORIGINAL bundle is present,
     * each bitstream is streamed to ClamAV for scanning.
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException {
        status = Curator.CURATE_SKIP;
        logDebugMessage("The target dso is " + dso.getName());
        if (dso instanceof Item) {
            status = Curator.CURATE_SUCCESS;
            Item item = (Item) dso;
            
            // Ensure a live connection (reopen if necessary).
            if (!isSessionOpen()) {
                try {
                    openSession();
                } catch (IOException ioE) {
                    setResult(CONNECT_FAIL_MESSAGE);
                    return Curator.CURATE_ERROR;
                }
            }
            
            try {
                List<Bundle> bundles = itemService.getBundles(item, "ORIGINAL");
                if (ListUtils.emptyIfNull(bundles).isEmpty()) {
                    setResult("No ORIGINAL bundle found for item: " + getItemHandle(item));
                    return Curator.CURATE_SKIP;
                }
                Bundle bundle = bundles.get(0);
                results = new ArrayList<>();
                for (Bitstream bitstream : bundle.getBitstreams()) {
                    try (InputStream inputstream = bitstreamService.retrieve(Curator.curationContext(), bitstream)) {
                        logDebugMessage("Scanning " + bitstream.getName() + " . . . ");
                        int bstatus = scan(bitstream, inputstream, getItemHandle(item));
                        if (bstatus == Curator.CURATE_ERROR) {
                            setResult(SCAN_FAIL_MESSAGE);
                            status = bstatus;
                            // Invalidate the connection on error so that subsequent items can try to reconnect.
                            closeSession();
                            break;
                        }
                        if (failfast && bstatus == Curator.CURATE_FAIL) {
                            status = bstatus;
                            break;
                        } else if (bstatus == Curator.CURATE_FAIL && status == Curator.CURATE_SUCCESS) {
                            status = bstatus;
                        }
                    } catch (Exception e) {
                        log.error("Error scanning bitstream " + bitstream.getName() + " for item: " + getItemHandle(item), e);
                        status = Curator.CURATE_ERROR;
                        closeSession();
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Error scanning item: " + getItemHandle(item), e);
                status = Curator.CURATE_ERROR;
            }
            
            if (status != Curator.CURATE_ERROR) {
                formatResults(item);
            }
        }
        return status;
    }

    /**
     * Opens a socket connection to the ClamAV daemon, wrapping its streams with buffers,
     * and sends the IDSESSION command.
     */
    protected void openSession() throws IOException {
        socket = new Socket();
        try {
            logDebugMessage("Connecting to " + host + ":" + port);
            socket.connect(new InetSocketAddress(host, port));
            socket.setSoTimeout(timeout);
            // Wrap the streams in buffered layers.
            dataOutputStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            dataInputStream = new BufferedInputStream(socket.getInputStream());
            dataOutputStream.write(IDSESSION);
            dataOutputStream.flush();
            logDebugMessage("IDSESSION command sent.");
        } catch (IOException e) {
            log.error("Failed to open ClamAV session", e);
            throw e;
        }
    }
    
    /**
     * Checks whether the ClamAV session is open.
     */
    protected boolean isSessionOpen() {
        return (socket != null && socket.isConnected() && !socket.isClosed());
    }
    
    /**
     * Closes the ClamAV session by sending the END command and closing the socket.
     */
    protected void closeSession() {
        if (dataOutputStream != null) {
            try {
                dataOutputStream.write(END);
                dataOutputStream.flush();
            } catch (IOException e) {
                log.error("Exception closing dataOutputStream", e);
            }
        }
        if (socket != null) {
            try {
                logDebugMessage("Closing the socket for ClamAV daemon . . . ");
                socket.close();
            } catch (IOException e) {
                log.error("Exception closing socket", e);
            }
        }
    }

    /**
     * Scans a bitstream by streaming its contents in chunks to the ClamAV daemon.
     * The file is read until the end, and each chunk is preceded by its length.
     *
     * @param bitstream   the bitstream for reporting results
     * @param inputstream the InputStream to read
     * @param itemHandle  the item handle for reporting results
     * @return a status code indicating the result of the scan
     */
    protected int scan(Bitstream bitstream, InputStream inputstream, String itemHandle) {
        try {
            dataOutputStream.write(INSTREAM);
        } catch (IOException e) {
            log.error("Error writing INSTREAM command", e);
            return Curator.CURATE_ERROR;
        }
        int bytesRead;
        try {
            // Read until the end of the stream.
            while ((bytesRead = inputstream.read(buffer)) != -1) {
                dataOutputStream.writeInt(bytesRead);
                dataOutputStream.write(buffer, 0, bytesRead);
            }
            // Terminate with a 0-length chunk.
            dataOutputStream.writeInt(0);
            dataOutputStream.flush();
        } catch (IOException e) {
            log.error("Error sending file data to ClamAV", e);
            return Curator.CURATE_ERROR;
        }
        
        int responseLength;
        try {
            // Read the response from ClamAV.
            responseLength = dataInputStream.read(buffer);
        } catch (IOException e) {
            log.error("Error reading result from socket", e);
            return Curator.CURATE_ERROR;
        }
        
        if (responseLength > 0) {
            String response = new String(buffer, 0, responseLength);
            logDebugMessage("Response: " + response);
            if (response.contains("FOUND")) {
                String itemMsg = "item - " + itemHandle + ": ";
                String bsMsg = "bitstream - " + bitstream.getName() +
                               ": SequenceId - " + bitstream.getSequenceID() + ": infected";
                report(itemMsg + bsMsg);
                results.add(bsMsg);
                return Curator.CURATE_FAIL;
            } else {
                return Curator.CURATE_SUCCESS;
            }
        }
        return Curator.CURATE_ERROR;
    }
    
    /**
     * Formats and sets the results for an Item.
     */
    protected void formatResults(Item item) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Item: ").append(getItemHandle(item)).append(" ");
        if (status == Curator.CURATE_FAIL) {
            sb.append(INFECTED_MESSAGE);
            int count = 0;
            for (String scanresult : results) {
                sb.append("\n").append(scanresult).append("\n");
                count++;
            }
            sb.append(count).append(" virus(es) found. failfast: ").append(failfast);
        } else {
            sb.append(CLEAN_MESSAGE);
        }
        setResult(sb.toString());
    }
    
    /**
     * Returns the handle of the Item or a default value if not set.
     */
    protected String getItemHandle(Item item) {
        String handle = item.getHandle();
        return (handle != null) ? handle : NEW_ITEM_HANDLE;
    }
    
    /**
     * Logs debug messages only if debug logging is enabled.
     */
    protected void logDebugMessage(String message) {
        if (log.isDebugEnabled()) {
            log.debug(message);
        }
    }
}
