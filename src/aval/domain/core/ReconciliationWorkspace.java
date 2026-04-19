package aval.domain.core;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Layer 1 - Core, covers UC1

import aval.common.enums.WorkspaceStatus;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ingestion.FinancialDataset;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//@desc:   Session-scoped container that groups together all datasets, match hypotheses, and reconciliation records for a single reconciliation job.
//@grasp:  Information Expert
//@gof:    N/A
public class ReconciliationWorkspace {
    private UUID workspaceId;
    private WorkspaceStatus status;
    private ClientOrganization clientOrganization;
    private MatchingConfig matchingConfig;
    private List<FinancialDataset> datasets;
    private List<MatchHypothesis> hypotheses;
    private List<ReconciliationRecord> records;

    public ReconciliationWorkspace(UUID workspaceId, ClientOrganization clientOrganization, MatchingConfig matchingConfig) {
        this.workspaceId = workspaceId;
        this.status = WorkspaceStatus.OPEN; // UC1 creates workspaces in OPEN state
        this.clientOrganization = clientOrganization;
        this.matchingConfig = matchingConfig;
        this.datasets = new ArrayList<>();
        this.hypotheses = new ArrayList<>();
        this.records = new ArrayList<>();
    }
}