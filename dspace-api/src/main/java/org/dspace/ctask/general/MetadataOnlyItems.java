/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

import org.apache.log4j.Logger;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;

import java.io.IOException;
import java.sql.SQLException;


/**
 * A curation job to identify items without any bitstreams attached to them.
 *
 * @author Bram Luyten (bram@mire.be)
 */                                                                                                                                                                           em
public class MetadataOnlyItems extends AbstractCurationTask
{

    // The status of this item
    private int status = Curator.CURATE_UNSET;

    // The results of processing this
    // private List<String> results = null;

    // The log4j logger for this class
    private static Logger log = Logger.getLogger(MetadataOnlyItems.class);


    /**
     * Identify whether a dspaceobject has fulltext attached to it.
     *
     * @param dso The DSpaceObject to be checked
     * @return The curation task status of the checking
     */
    @Override
    public int perform(DSpaceObject dso)
    {
        try {
            distribute(dso);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // The results that we'll return
        StringBuilder results = new StringBuilder();

        // Unless this is an item, we'll skip this item
        status = Curator.CURATE_SKIP;

        logDebugMessage("The target dso is " + dso.getName());

        if (dso instanceof Item)
        {
            try {
                Item item = (Item)dso;

                for (Bundle bundle : item.getBundles()) {
                    if ("ORIGINAL".equals(bundle.getName())) {
                        Bitstream[] bitstreams = bundle.getBitstreams();

                        if (bitstreams.length == 0) {
                            results.append(item.getHandle());
                        }
                    }

                status = Curator.CURATE_SUCCESS;

                }
            }  catch (SQLException sqle) {
                // Something went wrong
                logDebugMessage(sqle.getMessage());
                status = Curator.CURATE_ERROR;
            }

        }

        logDebugMessage("About to report: " + results.toString());
        setResult(results.toString());
        report(results.toString());

        return status;
    }

    /**
     * Debugging logging if required
     *
     * @param message The message to log
     */
    private void logDebugMessage(String message)
    {
        if (log.isDebugEnabled())
        {
            log.debug(message);
        }
    }
}
