package aval.domain.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClientOrganization.
 */
public class ClientOrganizationTest {

    @Test
    void constructor_storesAllFields() {
        UUID id = UUID.randomUUID();
        ClientOrganization org = new ClientOrganization(id, "AVAL Corp", "contact@aval.com");
        assertEquals(id, org.getOrgId());
        assertEquals("AVAL Corp", org.getName());
        assertEquals("contact@aval.com", org.getContactMetadata());
    }

    @Test
    void workspaces_initiallyEmpty() {
        ClientOrganization org = new ClientOrganization(UUID.randomUUID(), "Test", null);
        assertNotNull(org.getWorkspaces());
        assertTrue(org.getWorkspaces().isEmpty());
    }

    @Test
    void addWorkspace_increasesSize() {
        ClientOrganization org = new ClientOrganization(UUID.randomUUID(), "Test", null);
        ReconciliationWorkspace ws = new ReconciliationWorkspace(
            UUID.randomUUID(), org, new MatchingConfig()
        );
        org.addWorkspace(ws);
        assertEquals(1, org.getWorkspaces().size());
    }

    @Test
    void addWorkspace_multipleWorkspaces_allStored() {
        ClientOrganization org = new ClientOrganization(UUID.randomUUID(), "Test", null);
        for (int i = 0; i < 3; i++) {
            org.addWorkspace(new ReconciliationWorkspace(UUID.randomUUID(), org, new MatchingConfig()));
        }
        assertEquals(3, org.getWorkspaces().size());
    }

    @Test
    void nullContactMetadata_isAllowed() {
        ClientOrganization org = new ClientOrganization(UUID.randomUUID(), "Test", null);
        assertNull(org.getContactMetadata());
    }
}
