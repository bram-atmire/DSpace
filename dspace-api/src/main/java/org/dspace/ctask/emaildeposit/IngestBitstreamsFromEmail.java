/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.emaildeposit;

import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.Site;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;

import java.io.IOException;

import java.io.*;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;

/**
 * IngestBitstreamsFromEmail is a task that scans an email inbox for bitstreams that users
 * submitted as an attachment to an email message.
 *
 * The task assumes that the item the bitstream should be attached to already exists in DSpace and that the
 * email references the itemID in the subject.
 *
 * As a blunt approach to avoid that the same bitstream gets added twice, bitstreams can only be ingested from the
 * email box for items that have no bitstreams in DSpace.
 *
 * This task has the Distributive interface because the list of emails in the accounts inbox
 * should only be pulled once.
 *
 * When executed against the Site object, the task can potentially add bitstreams to all items
 * in the repository, regardless in which collection they are, and regardless whether they are archived or in workflow.
 *
 * When executed against a Collection, bitstreams will only be added if they can be matched with items in the collection.
 *
 * When executed against an Item, a bitstream will only be ingested from the email inbox if it is a match with the item.
 *
 * @author bram-atmire
 */
@Distributive
public class IngestBitstreamsFromEmail extends AbstractCurationTask
{

    public static final String TMP_DIR = System.getProperty("java.io.tmpdir")
    protected int status = Curator.CURATE_UNSET;
    protected String result = null;
    protected List<EmailedBitstream> temporaryBitstreams;

    public void init(Curator curator, String taskId) throws IOException
    {
        super.init(curator, taskId);
        temporaryBitstreams = retrieveBitstreamsFromMailbox();

    }

    @Override
    public int perform(DSpaceObject dso) throws IOException
    {

		if (dso instanceof Site)
        {
            status = Curator.CURATE_SUCCESS;
            result = "Mailbox checked and bitstreams ingested";
            
            setResult(result);
            report(result);
		}

        if (dso instanceof Item)
        {

            status = Curator.CURATE_SUCCESS;
            result = "Mailbox checked and bitstreams ingested";

            setResult(result);
            report(result);
        }
        
        return status;
    }

    /**
     * Check the mailbox and temporarily store all emails that reference an item ID in the subject that doesn't
     * have any bitstreams attached to them yet.
     *
     * @throws MessagingException
     * @throws IOException
     */
    private List<EmailedBitstream> retrieveBitstreamsFromMailbox() throws MessagingException, IOException {
        Folder folder = null;
        Store store = null;

        try {
            Properties props = System.getProperties();
            props.setProperty("mail.store.protocol", "imaps");

            Session session = Session.getDefaultInstance(props, null);
            // session.setDebug(true);

            store = session.getStore("imaps");
            store.connect("imap.gmail.com","dspace.analyzer@gmail.com", "SET-VIA-ENV");
            folder = store.getFolder("Inbox");
          /* Others GMail folders :
           * [Gmail]/All Mail   This folder contains all of your Gmail messages.
           * [Gmail]/Drafts     Your drafts.
           * [Gmail]/Sent Mail  Messages you sent to other people.
           * [Gmail]/Spam       Messages marked as spam.
           * [Gmail]/Starred    Starred messages.
           * [Gmail]/Trash      Messages deleted from Gmail.
           */

            folder.open(Folder.READ_WRITE);
            Message messages[] = folder.getMessages();
            System.out.println("No of Messages : " + folder.getMessageCount());
            System.out.println("No of Unread Messages : " + folder.getUnreadMessageCount());
            for (int i=0; i < messages.length; ++i) {
                System.out.println("MESSAGE #" + (i + 1) + ":");
                Message msg = messages[i];

                if (!msg.isSet(Flags.Flag.SEEN)) {
                    String from = "unknown";
                    if (msg.getReplyTo().length >= 1) {
                        from = msg.getReplyTo()[0].toString();
                    } else if (msg.getFrom().length >= 1) {
                        from = msg.getFrom()[0].toString();
                    }
                    String subject = msg.getSubject();
                    System.out.println("Saving ... " + subject + " " + from);
                    // you may want to replace the spaces with "_"
                    // the TEMP directory is used to store the files
                    String filename = TMP_DIR + subject;
                    saveParts(msg.getContent(), filename);
                    msg.setFlag(Flags.Flag.SEEN, true);
                    // to delete the message
                    // msg.setFlag(Flags.Flag.DELETED, true);
                }
            }
        }
        finally {
            if (folder != null) { folder.close(true); }
            if (store != null) { store.close(); }
        }
    }

    private static void saveParts(Object content, String filename)
            throws IOException, MessagingException
    {
        OutputStream out = null;
        InputStream in = null;
        try {
            if (content instanceof Multipart) {
                Multipart multi = ((Multipart)content);
                int parts = multi.getCount();
                for (int j=0; j < parts; ++j) {
                    MimeBodyPart part = (MimeBodyPart)multi.getBodyPart(j);
                    if (part.getContent() instanceof Multipart) {
                        // part-within-a-part, do some recursion...
                        saveParts(part.getContent(), filename);
                    }
                    else {
                        String extension = "";
                        if (part.isMimeType("text/html")) {
                            extension = "html";
                        }
                        else {
                            if (part.isMimeType("text/plain")) {
                                extension = "txt";
                            }
                            else {
                                //  Try to get the name of the attachment
                                extension = part.getDataHandler().getName();
                            }
                            filename = filename + "." + extension;
                            System.out.println("... " + filename);
                            out = new FileOutputStream(new File(filename));
                            in = part.getInputStream();
                            int k;
                            while ((k = in.read()) != -1) {
                                out.write(k);
                            }
                        }
                    }
                }
            }
        }
        finally {
            if (in != null) { in.close(); }
            if (out != null) { out.flush(); out.close(); }
        }
    }



}
