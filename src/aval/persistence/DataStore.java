package aval.persistence;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Persistence, covers All

import aval.domain.core.ClientOrganization;
import aval.domain.core.ReconciliationWorkspace;
import aval.domain.ingestion.FinancialDataset;
import aval.domain.ingestion.RawTransaction;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.SystemUser;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

//@desc:   The sole repository holding a JDBC connection to execute SQL against the PostgreSQL database.
//@grasp:  Controller
//@gof:    Repository / DAO
public class DataStore {
    private Connection connection;

    public DataStore(Connection connection) {
        this.connection = connection;
    }

    public void saveClientOrganization(ClientOrganization org) {
    }

    public ClientOrganization findClientOrganizationById(UUID id) {
        return null;
    }

    public void saveReconciliationWorkspace(ReconciliationWorkspace workspace) {
    }

    public ReconciliationWorkspace findReconciliationWorkspaceById(UUID id) {
        return null;
    }

    public void saveFinancialDataset(FinancialDataset dataset) {
    }

    public void saveRawTransactions(List<RawTransaction> rawTransactions) {
    }

    public void saveStandardizedTransactions(List<StandardizedTransaction> standardizedTransactions) {
    }

    public void saveMatchHypotheses(List<MatchHypothesis> hypotheses) {
    }

    public void saveReconciliationRecords(List<ReconciliationRecord> records) {
    }

    public SystemUser findSystemUserById(UUID id) {
        return null;
    }
}

