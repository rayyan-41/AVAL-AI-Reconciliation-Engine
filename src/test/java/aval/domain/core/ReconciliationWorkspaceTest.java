package aval.domain.core;

import aval.common.enums.WorkspaceStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReconciliationWorkspace — state and collection management.
 */
public class ReconciliationWorkspaceTest {

    private ClientOrganization org() {
        return new ClientOrganization(UUID.randomUUID(), "Test Client", "contact@test.com");
    }

    // ── construction ──────────────────────────────────────────────────────────

    @Test
    void newWorkspace_defaultStatus_isOpen() {
        ReconciliationWorkspace ws = new ReconciliationWorkspace(
            UUID.randomUUID(), org(), new MatchingConfig()
        );
        assertEquals(WorkspaceStatus.OPEN, ws.getStatus());
    }

    @Test
    void newWorkspace_idIsPreserved() {
        UUID id = UUID.randomUUID();
        ReconciliationWorkspace ws = new ReconciliationWorkspace(id, org(), new MatchingConfig());
        assertEquals(id, ws.getWorkspaceId());
    }

    @Test
    void newWorkspace_clientOrgIsLinked() {
        ClientOrganization o = org();
        ReconciliationWorkspace ws = new ReconciliationWorkspace(UUID.randomUUID(), o, new MatchingConfig());
        assertSame(o, ws.getClientOrganization());
    }

    @Test
    void newWorkspace_matchingConfigIsLinked() {
        MatchingConfig cfg = new MatchingConfig();
        ReconciliationWorkspace ws = new ReconciliationWorkspace(UUID.randomUUID(), org(), cfg);
        assertSame(cfg, ws.getMatchingConfig());
    }

    @Test
    void newWorkspace_withNullMatchingConfig_usesDefault() {
        ReconciliationWorkspace ws = new ReconciliationWorkspace(UUID.randomUUID(), org(), null);
        assertNotNull(ws.getMatchingConfig(), "Null config should fall back to default MatchingConfig");
    }

    // ── collections are empty initially ──────────────────────────────────────

    @Test
    void newWorkspace_datasets_initiallyEmpty() {
        ReconciliationWorkspace ws = new ReconciliationWorkspace(
            UUID.randomUUID(), org(), new MatchingConfig()
        );
        assertNotNull(ws.getDatasets());
        assertTrue(ws.getDatasets().isEmpty());
    }

    @Test
    void newWorkspace_hypotheses_initiallyEmpty() {
        ReconciliationWorkspace ws = new ReconciliationWorkspace(
            UUID.randomUUID(), org(), new MatchingConfig()
        );
        assertNotNull(ws.getHypotheses());
        assertTrue(ws.getHypotheses().isEmpty());
    }

    @Test
    void newWorkspace_records_initiallyEmpty() {
        ReconciliationWorkspace ws = new ReconciliationWorkspace(
            UUID.randomUUID(), org(), new MatchingConfig()
        );
        assertNotNull(ws.getRecords());
        assertTrue(ws.getRecords().isEmpty());
    }

    // ── status transitions ────────────────────────────────────────────────────

    @Test
    void setStatus_toCompleted_updatesStatus() {
        ReconciliationWorkspace ws = new ReconciliationWorkspace(
            UUID.randomUUID(), org(), new MatchingConfig()
        );
        ws.setStatus(WorkspaceStatus.COMPLETED);
        assertEquals(WorkspaceStatus.COMPLETED, ws.getStatus());
    }

    @Test
    void setStatus_toLocked_updatesStatus() {
        ReconciliationWorkspace ws = new ReconciliationWorkspace(
            UUID.randomUUID(), org(), new MatchingConfig()
        );
        ws.setStatus(WorkspaceStatus.LOCKED);
        assertEquals(WorkspaceStatus.LOCKED, ws.getStatus());
    }

    @Test
    void allWorkspaceStatuses_canBeSet() {
        ReconciliationWorkspace ws = new ReconciliationWorkspace(
            UUID.randomUUID(), org(), new MatchingConfig()
        );
        for (WorkspaceStatus status : WorkspaceStatus.values()) {
            ws.setStatus(status);
            assertEquals(status, ws.getStatus());
        }
    }
}
