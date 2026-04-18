package aval.domain.core;

//Responsibility: Safwan
//Status: In Progress
//Explanation: This class is responsible for Layer 1 - Core, covers UC1

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//@desc:   Owns all data required to answer questions about its workspaces, rule sets, and identity.
//@grasp:  Information Expert, Creator
//@gof:    N/A
public class ClientOrganization {
    private UUID orgId;
    private String legalName;
    private String pkTaxId;
    private String industryType;
    private List<String> bankAccountNumbers;
    private AccountingRuleSet ruleSet;
    private List<ReconciliationWorkspace> workspaces;
    private LocalDate createdAt;

    public ClientOrganization(UUID orgId, String legalName, String pkTaxId, String industryType, List<String> bankAccountNumbers, AccountingRuleSet ruleSet, LocalDate createdAt) {
        this.orgId = orgId;
        this.legalName = legalName;
        this.pkTaxId = pkTaxId;
        this.industryType = industryType;
        this.bankAccountNumbers = new ArrayList<>(bankAccountNumbers);
        this.ruleSet = ruleSet;
        this.createdAt = createdAt;
        this.workspaces = new ArrayList<>();
    }

    public ReconciliationWorkspace getActiveWorkspace() {
        return null;
    }

    public ReconciliationWorkspace getWorkspaceByQuarter(String quarter) {
        return null;
    }

    public boolean validateBankAccountFormat(String acc) {
        return false;
    }

    public AccountingRuleSet getRuleSet() {
        return this.ruleSet;
    }

    public void addWorkspace(ReconciliationWorkspace ws) {
        this.workspaces.add(ws);
    }
}