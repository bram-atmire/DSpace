/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 *
 *
 * Refactored (JUnit 4.13.2) version of ManageGroupsFeatureIT.
 *
 * – uses SpringRunner, so all @Autowired fields keep working
 * – builds the complete DSpace object graph only once
 * – drives the five regular‑role assertions via a compact role matrix
 */
package org.dspace.app.rest.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumMap;
import java.util.Map;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.service.SiteService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.GroupService;
import org.dspace.services.ConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class ManageGroupsFeatureIT extends AbstractControllerIntegrationTest {

    /* ------------------------------------------------------------
     * Spring‑managed collaborators
     * ---------------------------------------------------------- */
    @Autowired private SiteService siteService;
    @Autowired private ConfigurationService configurationService;
    @Autowired private GroupService groupService;

    /* ------------------------------------------------------------
     * Heavy shared fixture (static so it’s created only once)
     * ---------------------------------------------------------- */
    private static boolean INITIALISED = false;

    private static Community topLevelCommunity;
    private static Community subCommunity;
    private static Collection collection;

    private static EPerson communityAdmin;
    private static EPerson subCommunityAdmin;
    private static EPerson collectionAdmin;
    private static EPerson submitter;

    /** JWT cache – avoids logging in five times per test method */
    private static final Map<Role,String> TOKENS = new EnumMap<>(Role.class);

    private static String siteUri;

    /* ------------------------------------------------------------
     * One‑time set‑up (runs only on the first test instance)
     * ---------------------------------------------------------- */
    @Before
    public void initOnce() throws Exception {
        if (INITIALISED) {
            return;
        }

        context.turnOffAuthorisationSystem();

        /* ---------- Users ---------- */
        communityAdmin = EPersonBuilder.createEPerson(context)
                .withNameInMetadata("John", "CommunityAdmin")
                .withEmail("communityAdmin@my.edu")
                .withPassword(password)
                .build();

        subCommunityAdmin = EPersonBuilder.createEPerson(context)
                .withNameInMetadata("John", "SubCommunityAdmin")
                .withEmail("subCommunityAdmin@my.edu")
                .withPassword(password)
                .build();

        collectionAdmin = EPersonBuilder.createEPerson(context)
                .withNameInMetadata("John", "CollectionAdmin")
                .withEmail("collectionAdmin@my.edu")
                .withPassword(password)
                .build();

        submitter = EPersonBuilder.createEPerson(context)
                .withNameInMetadata("John", "Submitter")
                .withEmail("submitter@my.edu")
                .withPassword(password)
                .build();

        /* ---------- DSpace object hierarchy ---------- */
        topLevelCommunity = CommunityBuilder.createCommunity(context)
                .withName("Top‑level Community")
                .withAdminGroup(communityAdmin)      // creates COMMUNITY_*_ADMIN
                .build();

        subCommunity = CommunityBuilder.createCommunity(context)
                .withName("SubCommunity")
                .withAdminGroup(subCommunityAdmin)
                .addParentCommunity(context, topLevelCommunity)
                .build();

        collection = CollectionBuilder.createCollection(context, subCommunity)
                .withName("Collection")
                .withAdminGroup(collectionAdmin)
                .withSubmitterGroup(submitter)
                .build();

        context.restoreAuthSystemState();

        /* ---------- Site URI & JWTs ---------- */
        siteUri = "http://localhost/api/core/site/" + siteService.findSite(context).getID();

        TOKENS.put(Role.ADMIN,                getAuthToken(admin.getEmail(),          password));
        TOKENS.put(Role.COMMUNITY_ADMIN,      getAuthToken(communityAdmin.getEmail(), password));
        TOKENS.put(Role.SUBCOMMUNITY_ADMIN,   getAuthToken(subCommunityAdmin.getEmail(), password));
        TOKENS.put(Role.COLLECTION_ADMIN,     getAuthToken(collectionAdmin.getEmail(), password));
        TOKENS.put(Role.SUBMITTER,            getAuthToken(submitter.getEmail(),      password));

        /* ---------- Misc test config ---------- */
        configurationService.setProperty(
                "org.dspace.app.rest.authorization.AlwaysThrowExceptionFeature.turnoff", "true");

        INITIALISED = true;
    }

    /* ------------------------------------------------------------
     * Role/expectation table (the “role matrix”)
     * ---------------------------------------------------------- */
    private static Object[][] roleMatrix() {
        return new Object[][] {
            { Role.ADMIN,              true  },
            { Role.COMMUNITY_ADMIN,    true  },
            { Role.SUBCOMMUNITY_ADMIN, true  },
            { Role.COLLECTION_ADMIN,   true  },
            { Role.SUBMITTER,          false }
        };
    }

    /* ------------------------------------------------------------
     * Single test that iterates over the matrix
     * ---------------------------------------------------------- */
    @Test
    public void defaultConfiguration_roleMatrix() throws Exception {

        for (Object[] row : roleMatrix()) {
            Role role       = (Role)    row[0];
            boolean allowed = (Boolean) row[1];

            getClient(TOKENS.get(role))
                .perform(get("/api/authz/authorizations/search/object")
                         .param("embed", "feature")
                         .param("uri",   siteUri))
                .andExpect(status().isOk())
                .andExpect(allowed
                    ? jsonPath("$._embedded.authorizations[?(@._embedded.feature.id=='canManageGroups')]").exists()
                    : jsonPath("$._embedded.authorizations[?(@._embedded.feature.id=='canManageGroups')]").doesNotExist());
        }
    }

    /* ------------------------------------------------------------
     * Helper enum
     * ---------------------------------------------------------- */
    private enum Role {
        ADMIN,
        COMMUNITY_ADMIN,
        SUBCOMMUNITY_ADMIN,
        COLLECTION_ADMIN,
        SUBMITTER
    }
}
