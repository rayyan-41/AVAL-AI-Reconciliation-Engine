package aval.domain.core;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Layer 1 - Core, covers UC1

import enums.WorkspaceStatus;
import ai.Anomaly;
import ai.MatchHypothesis;
import ai.ReconciliationRecord;
import ingestion.FinancialDataset;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//@desc:   Acts as the domain-level controller for all operations scoped to a single client-quarter billing cycle.
//@grasp:  Information Expert, Controller
//@gof:    N/A
public class ReconciliationWorkspace {
    private UUID workspaceId;
    private String quarter;
    private WorkspaceStatus status;
    private List<FinancialDataset> datasets;
    private List<MatchHypothesis> hypotheses;
    private List<Anomaly> anomalies;
    private ReconciliationRecord record;
    private ClientOrganization clientOrg;

    public ReconciliationWorkspace(UUID workspaceId, String quarter, WorkspaceStatus status, ClientOrganization clientOrg) {
        this.workspaceId = workspaceId;
        this.quarter = quarter;
        this.status = status;
        this.clientOrg = clientOrg;
        this.datasets = new ArrayList<>();
        this.hypotheses = new ArrayList<>();
        this.anomalies = new ArrayList<>();
    }

    public double getMatchRate() {
        return 0.0;
    }

    public int getUnresolvedCount() {
        return 0;
    }

    public boolean isReadyForReport() {
        return false;
    }

    public void lockWorkspace() {
    }

    public List<Anomaly> getAnomalies() {
        return this.anomalies;
    }

    public void addDataset(FinancialDataset ds) {
        this.datasets.add(ds);
    }
}