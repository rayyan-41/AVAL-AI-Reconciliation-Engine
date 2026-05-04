//Dev: Safwan
//Use Cases: UC1
package aval.domain.core;

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

    //-------------- Attribute ----------------------//
    private UUID workspaceId;
    private WorkspaceStatus status;
    private ClientOrganization clientOrganization;
    private MatchingConfig matchingConfig;
    private List<FinancialDataset> datasets;
    private List<MatchHypothesis> hypotheses;
    private List<ReconciliationRecord> records;

    //Constructor
    public ReconciliationWorkspace(
        UUID workspaceId,
        ClientOrganization clientOrganization,
        MatchingConfig matchingConfig
    ) {
        this.workspaceId = workspaceId;
        this.status = WorkspaceStatus.OPEN; // UC1 creates workspaces in OPEN state
        this.clientOrganization = clientOrganization;
        this.matchingConfig = matchingConfig != null
            ? matchingConfig
            : new MatchingConfig();
        this.datasets = new ArrayList<>();
        this.hypotheses = new ArrayList<>();
        this.records = new ArrayList<>();
    }
    
    //---------- Methods ------------//
    
    //Getters
    public UUID getWorkspaceId() { return workspaceId; }
    public ClientOrganization getClientOrganization() { return clientOrganization; }
    public MatchingConfig getMatchingConfig() { return matchingConfig; }
    public List<FinancialDataset> getDatasets() { return datasets; }
    public List<MatchHypothesis> getHypotheses() { return hypotheses; }
    public List<ReconciliationRecord> getRecords() { return records; }
    public WorkspaceStatus getStatus() { return status; }
    //Setters
    public void setStatus(WorkspaceStatus status) { this.status = status; }

}
