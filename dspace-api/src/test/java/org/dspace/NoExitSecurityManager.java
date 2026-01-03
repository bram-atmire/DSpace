/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace;

import java.security.Permission;

/**
 * @deprecated SecurityManager is deprecated for removal in Java 21+.
 * This class should no longer be used. Tests that rely on catching System.exit()
 * calls should be refactored to avoid calling System.exit().
 * See: https://openjdk.org/jeps/411
 */
@Deprecated(since = "10.0", forRemoval = true)
@SuppressWarnings("removal")
public class NoExitSecurityManager extends SecurityManager {
    @Override
    public void checkPermission(Permission perm) {
        // allow anything.
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
        // allow anything.
    }

    @Override
    public void checkExit(int status) {
        super.checkExit(status);
        if (status >= 0) {
            throw new ExitException(status);
        }
    }
}