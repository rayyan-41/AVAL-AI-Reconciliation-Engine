package aval.persistence;

import aval.common.enums.*;
import aval.domain.SystemUser;
import aval.domain.ai.MatchHypothesis;
import aval.domain.ai.ReconciliationRecord;
import aval.domain.ai.StandardizedTransaction;
import aval.domain.core.ClientOrganization;
import aval.domain.core.MatchingConfig;
import aval.domain.core.ReconciliationWorkspace;
import aval.domain.ingestion.RawInternalLedger;
import aval.domain.ingestion.RawTransaction;
import org.junit.jupiter.api.*;
import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for DataStore using H2 in-memory database.
 *
 * H2 2.x does not support PostgreSQL's  ON CONFLICT … DO UPDATE SET col = EXCLUDED.col
 * syntax even in MODE=PostgreSQL.  Tests that exercise those upsert code-paths are
 * annotated {@code @Disabled} and can only be run against a real PostgreSQL instance.
 * All other tests use direct-SQL helpers (insertXxxDirectly) to seed preconditions,
 * keeping every assertion on the DataStore's *read* or non-upsert *write* methods.
 */
public class DataStoreH2Test {

    // Each test instance gets its own uniquely named in-memory DB
    private final String dbName   = "testdb_" + UUID.randomUUID().toString().replace("-", "");
    private final String jdbcUrl  = "jdbc:h2:mem:" + dbName
                                    + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";

    private Connection   keepAlive;     // Holds the in-memory DB alive for the test lifetime
    private DataStore    dataStore;
    private H2DataSource h2DataSource;

    @BeforeEach
    void setUp() throws SQLException {
        keepAlive    = DriverManager.getConnection(jdbcUrl, "sa", "");
        h2DataSource = new H2DataSource(jdbcUrl);
        createSchema(keepAlive);
        dataStore    = new DataStore(h2DataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null && !keepAlive.isClosed()) keepAlive.close();
    }

    private void createSchema(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS client_organization "
                    + "(org_id UUID PRIMARY KEY, name VARCHAR(255))");
            s.execute("CREATE TABLE IF NOT EXISTS reconciliation_workspace "
                    + "(workspace_id UUID PRIMARY KEY, org_id UUID, "
                    + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, status VARCHAR(30))");
            s.execute("CREATE TABLE IF NOT EXISTS financial_dataset "
                    + "(dataset_id UUID PRIMARY KEY, workspace_id UUID, file_path TEXT, "
                    + " source_type VARCHAR(30), status VARCHAR(20), import_date DATE)");
            s.execute("CREATE TABLE IF NOT EXISTS raw_transactions "
                    + "(transaction_id UUID PRIMARY KEY, raw_date TEXT, raw_amount TEXT, "
                    + " narrative TEXT, transaction_type VARCHAR(20), source_dataset_id UUID)");
            s.execute("CREATE TABLE IF NOT EXISTS match_hypotheses "
                    + "(hypothesis_id UUID PRIMARY KEY, ledger_id UUID, bank_id UUID, "
                    + " confidence_score DOUBLE, match_type VARCHAR(30), "
                    + " status VARCHAR(30), justification TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS reconciliation_records "
                    + "(record_id UUID PRIMARY KEY, hypothesis_id UUID, "
                    + " confirming_user_id UUID, reconciled_at TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS app_user "
                    + "(user_id UUID PRIMARY KEY, full_name TEXT, cnic TEXT, "
                    + " username VARCHAR(100) UNIQUE, role VARCHAR(20), "
                    + " location TEXT, password_hash TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS standardized_ledger "
                    + "(transaction_id UUID PRIMARY KEY, value_date DATE, "
                    + " amount DECIMAL(19,4), narrative TEXT, "
                    + " transaction_type VARCHAR(20), source_dataset_id UUID)");
            s.execute("CREATE TABLE IF NOT EXISTS standardized_bank "
                    + "(transaction_id UUID PRIMARY KEY, value_date DATE, "
                    + " amount DECIMAL(19,4), narrative TEXT, "
                    + " transaction_type VARCHAR(20), source_dataset_id UUID)");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ClientOrganization
    // ─────────────────────────────────────────────────────────────────────────

    /** Seeds data via direct SQL, then exercises findClientOrganizationById. */
    @Test
    void saveAndFindClientOrganization_roundTrip() throws SQLException {
        UUID orgId = UUID.randomUUID();
        insertClientOrgDirectly(orgId, "HBL Corp");

        ClientOrganization found = dataStore.findClientOrganizationById(orgId);
        assertNotNull(found);
        assertEquals(orgId,     found.getOrgId());
        assertEquals("HBL Corp", found.getName());
    }

    @Test
    void findClientOrganizationById_notFound_returnsNull() {
        assertNull(dataStore.findClientOrganizationById(UUID.randomUUID()));
    }

    /**
     * Tests the ON CONFLICT upsert path — H2 2.x cannot parse that syntax.
     * Run against a real PostgreSQL instance.
     */
    @Disabled("H2 2.x does not support ON CONFLICT … EXCLUDED — requires PostgreSQL")
    @Test
    void saveClientOrganization_upsert_updatesName() {
        UUID orgId = UUID.randomUUID();
        dataStore.saveClientOrganization(new ClientOrganization(orgId, "Old Name", null));
        dataStore.saveClientOrganization(new ClientOrganization(orgId, "New Name", null));
        assertEquals("New Name", dataStore.findClientOrganizationById(orgId).getName());
    }

    @Test
    void findAllClients_returnsAllSaved() throws SQLException {
        insertClientOrgDirectly(UUID.randomUUID(), "Alpha");
        insertClientOrgDirectly(UUID.randomUUID(), "Beta");
        insertClientOrgDirectly(UUID.randomUUID(), "Gamma");
        assertEquals(3, dataStore.findAllClients().size());
    }

    @Test
    void findAllClients_emptyTable_returnsEmptyList() {
        assertTrue(dataStore.findAllClients().isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ReconciliationWorkspace
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void saveAndFindReconciliationWorkspace_roundTrip() throws SQLException {
        UUID orgId = UUID.randomUUID();
        UUID wsId  = UUID.randomUUID();
        insertClientOrgDirectly(orgId, "Corp");
        insertWorkspaceDirectly(wsId, orgId);

        ReconciliationWorkspace found = dataStore.findReconciliationWorkspaceById(wsId);
        assertNotNull(found);
        assertEquals(wsId, found.getWorkspaceId());
    }

    @Test
    void findReconciliationWorkspaceById_notFound_returnsNull() {
        assertNull(dataStore.findReconciliationWorkspaceById(UUID.randomUUID()));
    }

    @Test
    void updateWorkspaceStatus_updatesExistingWorkspace() throws SQLException {
        UUID orgId = UUID.randomUUID();
        UUID wsId  = UUID.randomUUID();
        insertClientOrgDirectly(orgId, "Org");
        insertWorkspaceDirectly(wsId, orgId);

        dataStore.updateWorkspaceStatus(wsId, WorkspaceStatus.IN_PROGRESS);

        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT status FROM reconciliation_workspace WHERE workspace_id = ?")) {
            ps.setObject(1, wsId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals("IN_PROGRESS", rs.getString("status"));
        }
    }

    @Test
    void updateWorkspaceStatus_nonExistentId_throwsSQLException() {
        assertThrows(SQLException.class, () ->
            dataStore.updateWorkspaceStatus(UUID.randomUUID(), WorkspaceStatus.COMPLETED));
    }

    @Test
    void deleteReconciliationWorkspace_removesIt() throws SQLException {
        UUID orgId = UUID.randomUUID();
        UUID wsId  = UUID.randomUUID();
        insertClientOrgDirectly(orgId, "Org");
        insertWorkspaceDirectly(wsId, orgId);

        dataStore.deleteReconciliationWorkspace(wsId);

        assertNull(dataStore.findReconciliationWorkspaceById(wsId));
    }

    @Test
    void deleteReconciliationWorkspace_nonExistent_throwsSQLException() {
        assertThrows(SQLException.class, () ->
            dataStore.deleteReconciliationWorkspace(UUID.randomUUID()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FinancialDataset
    // ─────────────────────────────────────────────────────────────────────────

    @Disabled("H2 2.x does not support ON CONFLICT … EXCLUDED — requires PostgreSQL")
    @Test
    void saveFinancialDataset_withWorkspaceId_persists() throws SQLException {
        UUID wsId = UUID.randomUUID();
        UUID dsId = UUID.randomUUID();
        insertWorkspaceDirectly(wsId, null);

        RawInternalLedger dataset = new RawInternalLedger(
            dsId, LocalDate.now(), "/test.xlsx", DatasetStatus.PARSED, "SAP", "Q1");
        dataStore.saveFinancialDataset(dataset, wsId);

        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT dataset_id FROM financial_dataset WHERE dataset_id = ?")) {
            ps.setObject(1, dsId);
            assertTrue(ps.executeQuery().next());
        }
    }

    @Test
    void deleteFinancialDataset_removesDataset() throws SQLException {
        UUID wsId = UUID.randomUUID();
        UUID dsId = UUID.randomUUID();
        insertWorkspaceDirectly(wsId, null);
        insertDatasetDirectly(dsId, wsId, "INTERNAL_EXCEL");    // bypass ON CONFLICT

        dataStore.deleteFinancialDataset(dsId);

        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT dataset_id FROM financial_dataset WHERE dataset_id = ?")) {
            ps.setObject(1, dsId);
            assertFalse(ps.executeQuery().next());
        }
    }

    @Test
    void deleteFinancialDataset_nonExistent_throwsSQLException() {
        assertThrows(SQLException.class, () ->
            dataStore.deleteFinancialDataset(UUID.randomUUID()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  saveDatasetWithTransactions (transactional)
    // ─────────────────────────────────────────────────────────────────────────

    @Disabled("H2 2.x does not support ON CONFLICT … EXCLUDED — requires PostgreSQL")
    @Test
    void saveDatasetWithTransactions_persistsBothAtomically() throws Exception {
        UUID wsId = UUID.randomUUID();
        insertWorkspaceDirectly(wsId, null);
        UUID dsId = UUID.randomUUID();
        RawInternalLedger ledger = new RawInternalLedger(
            dsId, LocalDate.now(), "/atomic.xlsx", DatasetStatus.PARSED, "SAP", "Q1");
        UUID txId = UUID.randomUUID();
        setRawTransactions(ledger, List.of(
            new RawTransaction(txId, "2024-01-10", "999.00", "Atomic test", TransactionType.DEBIT, ledger)));

        dataStore.saveDatasetWithTransactions(ledger, wsId);

        try (Connection c = h2DataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM financial_dataset WHERE dataset_id = ?")) {
                ps.setObject(1, dsId);
                assertTrue(ps.executeQuery().next(), "Dataset should be persisted");
            }
            try (PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM raw_transactions WHERE transaction_id = ?")) {
                ps.setObject(1, txId);
                assertTrue(ps.executeQuery().next(), "Transaction should be persisted");
            }
        }
    }

    @Disabled("H2 2.x does not support ON CONFLICT … EXCLUDED — requires PostgreSQL")
    @Test
    void saveDatasetWithTransactions_emptyTransactionsList_savesDatasetOnly() throws Exception {
        UUID wsId = UUID.randomUUID();
        insertWorkspaceDirectly(wsId, null);
        UUID dsId = UUID.randomUUID();
        RawInternalLedger ledger = new RawInternalLedger(
            dsId, LocalDate.now(), "/empty.xlsx", DatasetStatus.PARSED, "SAP", "Q1");

        dataStore.saveDatasetWithTransactions(ledger, wsId);

        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM financial_dataset WHERE dataset_id = ?")) {
            ps.setObject(1, dsId);
            assertTrue(ps.executeQuery().next(), "Dataset should be persisted even with 0 transactions");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MatchHypotheses
    // ─────────────────────────────────────────────────────────────────────────

    @Disabled("H2 2.x does not support ON CONFLICT … EXCLUDED — requires PostgreSQL")
    @Test
    void saveMatchHypotheses_persistsAll() throws SQLException {
        MatchHypothesis h = buildHypothesis(0.9, MatchType.EXACT_RULE, HypothesisStatus.PENDING_REVIEW);
        dataStore.saveMatchHypotheses(List.of(h));

        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM match_hypotheses WHERE hypothesis_id = ?")) {
            ps.setObject(1, h.getHypothesisId());
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(0.9,        rs.getDouble("confidence_score"), 0.001);
            assertEquals("EXACT_RULE", rs.getString("match_type"));
        }
    }

    @Test
    void updateHypothesisStatus_updatesStatusAndJustification() throws SQLException {
        MatchHypothesis h = buildHypothesis(0.8, MatchType.AI_PROBABILISTIC, HypothesisStatus.PENDING_REVIEW);
        insertMatchHypothesisDirectly(h);       // bypass ON CONFLICT

        dataStore.updateHypothesisStatus(h.getHypothesisId(), HypothesisStatus.APPROVED, "Confirmed by auditor");

        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT status, justification FROM match_hypotheses WHERE hypothesis_id = ?")) {
            ps.setObject(1, h.getHypothesisId());
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals("APPROVED",             rs.getString("status"));
            assertEquals("Confirmed by auditor", rs.getString("justification"));
        }
    }

    @Test
    void updateHypothesisStatus_nonExistentId_throwsSQLException() {
        assertThrows(SQLException.class, () ->
            dataStore.updateHypothesisStatus(UUID.randomUUID(), HypothesisStatus.REJECTED, "reason"));
    }

    @Disabled("H2 2.x does not support ON CONFLICT … EXCLUDED — requires PostgreSQL")
    @Test
    void saveMatchingResults_persistsBothHypothesesAndRecords() throws SQLException {
        MatchHypothesis h = buildHypothesis(0.95, MatchType.EXACT_RULE, HypothesisStatus.AUTO_RECONCILED);
        SystemUser user   = new SystemUser(UUID.randomUUID(), "System", "c", "sys", UserRole.ADMIN, "x");
        ReconciliationRecord record = new ReconciliationRecord(h, user);

        dataStore.saveMatchingResults(List.of(h), List.of(record));

        try (Connection c = h2DataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM match_hypotheses WHERE hypothesis_id = ?")) {
                ps.setObject(1, h.getHypothesisId());
                assertTrue(ps.executeQuery().next());
            }
            try (PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM reconciliation_records WHERE record_id = ?")) {
                ps.setObject(1, record.getRecordId());
                assertTrue(ps.executeQuery().next());
            }
        }
    }

    @Disabled("H2 2.x does not support ON CONFLICT … EXCLUDED — requires PostgreSQL")
    @Test
    void saveMatchingResults_noRecords_persistsHypothesesOnly() throws SQLException {
        MatchHypothesis h = buildHypothesis(0.7, MatchType.AI_PROBABILISTIC, HypothesisStatus.PENDING_REVIEW);
        dataStore.saveMatchingResults(List.of(h), null);

        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM match_hypotheses WHERE hypothesis_id = ?")) {
            ps.setObject(1, h.getHypothesisId());
            assertTrue(ps.executeQuery().next());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ReconciliationRecords
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void saveReconciliationRecords_persistsRecord() throws SQLException {
        MatchHypothesis h = buildHypothesis(1.0, MatchType.EXACT_RULE, HypothesisStatus.AUTO_RECONCILED);
        insertMatchHypothesisDirectly(h);       // bypass ON CONFLICT

        SystemUser user = new SystemUser(UUID.randomUUID(), "Auditor", "c", "aud", UserRole.AUDITOR, "City");
        ReconciliationRecord record = new ReconciliationRecord(h, user);
        dataStore.saveReconciliationRecords(List.of(record));

        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM reconciliation_records WHERE record_id = ?")) {
            ps.setObject(1, record.getRecordId());
            assertTrue(ps.executeQuery().next());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  User Registration and Authentication
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void registerUser_persistsUser() throws SQLException {
        String hash = BCrypt.hashpw("securePass123", BCrypt.gensalt());
        dataStore.registerUser("John Doe", "12345-6789012-3", "johndoe",
                UserRole.ACCOUNTANT, "Karachi", hash);

        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM app_user WHERE username = ?")) {
            ps.setString(1, "johndoe");
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals("John Doe",    rs.getString("full_name"));
            assertEquals("ACCOUNTANT",  rs.getString("role"));
        }
    }

    @Test
    void isAnyUserRegistered_emptyTable_returnsFalse() {
        assertFalse(dataStore.isAnyUserRegistered());
    }

    @Test
    void isAnyUserRegistered_afterRegistration_returnsTrue() {
        dataStore.registerUser("Admin", "00000-0000000-0", "admin_user",
                UserRole.ADMIN, "City", BCrypt.hashpw("pass", BCrypt.gensalt()));
        assertTrue(dataStore.isAnyUserRegistered());
    }

    @Test
    void findSystemUserByUsername_existingUser_returnsCorrectData() {
        dataStore.registerUser("Jane Doe", "99999-0000000-9", "janedoe",
                UserRole.AUDITOR, "Lahore", BCrypt.hashpw("pass123", BCrypt.gensalt()));

        SystemUser found = dataStore.findSystemUserByUsername("janedoe");
        assertNotNull(found);
        assertEquals("janedoe",        found.getUsername());
        assertEquals(UserRole.AUDITOR, found.getRole());
        assertEquals("Jane Doe",       found.getFullName());
        assertEquals("Lahore",         found.getLocation());
    }

    @Test
    void findSystemUserByUsername_nonExistentUser_returnsNull() {
        assertNull(dataStore.findSystemUserByUsername("nobody"));
    }

    @Test
    void findSystemUserByCredentials_correctPassword_returnsUser() {
        String pass = "correctPass!";
        dataStore.registerUser("Correct User", "11111-0000000-1", "correctuser",
                UserRole.ACCOUNTANT, "Karachi", BCrypt.hashpw(pass, BCrypt.gensalt()));

        SystemUser found = dataStore.findSystemUserByCredentials("correctuser", pass);
        assertNotNull(found);
        assertEquals("correctuser", found.getUsername());
    }

    @Test
    void findSystemUserByCredentials_wrongPassword_returnsNull() {
        dataStore.registerUser("User", "22222-0000000-2", "user_wp",
                UserRole.ADMIN, "Lahore", BCrypt.hashpw("rightPass", BCrypt.gensalt()));
        assertNull(dataStore.findSystemUserByCredentials("user_wp", "wrongPass"));
    }

    @Test
    void findSystemUserByCredentials_nonExistentUser_returnsNull() {
        assertNull(dataStore.findSystemUserByCredentials("nobody", "anypass"));
    }

    @Test
    void findSystemUserById_existingUser_returnsUser() throws SQLException {
        UUID userId = UUID.randomUUID();
        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO app_user (user_id, full_name, cnic, username, role, location, password_hash) "
               + "VALUES (?,?,?,?,?,?,?)")) {
            ps.setObject(1, userId);
            ps.setString(2, "Direct Insert");
            ps.setString(3, "cnic");
            ps.setString(4, "directuser");
            ps.setString(5, "ADMIN");
            ps.setString(6, "City");
            ps.setString(7, BCrypt.hashpw("pass", BCrypt.gensalt()));
            ps.executeUpdate();
        }

        SystemUser found = dataStore.findSystemUserById(userId);
        assertNotNull(found);
        assertEquals(userId, found.getUserId());
    }

    @Test
    void findSystemUserById_notFound_returnsNull() {
        assertNull(dataStore.findSystemUserById(UUID.randomUUID()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Workspace Retrieval
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void findLatestWorkspaceForClient_existing_returnsIt() throws SQLException {
        UUID orgId = UUID.randomUUID();
        UUID wsId  = UUID.randomUUID();
        insertClientOrgDirectly(orgId, "Big Corp");     // bypass ON CONFLICT
        insertWorkspaceDirectly(wsId, orgId);

        Optional<ReconciliationWorkspace> result = dataStore.findLatestWorkspaceForClient(orgId);
        assertTrue(result.isPresent());
        assertEquals(wsId, result.get().getWorkspaceId());
    }

    @Test
    void findLatestWorkspaceForClient_none_returnsEmpty() {
        assertTrue(dataStore.findLatestWorkspaceForClient(UUID.randomUUID()).isEmpty());
    }

    @Test
    void getLedgerTransactionsForWorkspace_returnsRows() throws SQLException {
        UUID wsId = UUID.randomUUID();
        UUID dsId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        insertWorkspaceDirectly(wsId, null);
        insertDatasetDirectly(dsId, wsId, "INTERNAL_EXCEL");
        insertStandardizedLedger(txId, dsId, LocalDate.of(2024, 1, 1), new BigDecimal("1000.00"), "Salary");

        List<StandardizedTransaction> result = dataStore.getLedgerTransactionsForWorkspace(wsId);
        assertEquals(1, result.size());
        assertEquals(txId,     result.get(0).getTransactionId());
        assertEquals("Salary", result.get(0).getNarrative());
    }

    @Test
    void getLedgerTransactionsForWorkspace_noData_returnsEmpty() {
        assertTrue(dataStore.getLedgerTransactionsForWorkspace(UUID.randomUUID()).isEmpty());
    }

    @Test
    void getBankTransactionsForWorkspace_returnsRows() throws SQLException {
        UUID wsId = UUID.randomUUID();
        UUID dsId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        insertWorkspaceDirectly(wsId, null);
        insertDatasetDirectly(dsId, wsId, "EXTERNAL_PDF");
        insertStandardizedBank(txId, dsId, LocalDate.of(2024, 1, 2), new BigDecimal("500.00"), "Bank Deposit");

        List<StandardizedTransaction> result = dataStore.getBankTransactionsForWorkspace(wsId);
        assertEquals(1, result.size());
        assertEquals(txId, result.get(0).getTransactionId());
    }

    @Test
    void getBankTransactionsForWorkspace_noData_returnsEmpty() {
        assertTrue(dataStore.getBankTransactionsForWorkspace(UUID.randomUUID()).isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Reconciliation History
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getReconciliationHistory_emptyTable_returnsEmptyList() {
        assertTrue(dataStore.getReconciliationHistory().isEmpty());
    }

    @Test
    void getReconciliationHistory_afterSavingRecord_returnsEntry() throws SQLException {
        MatchHypothesis h = buildHypothesis(0.9, MatchType.EXACT_RULE, HypothesisStatus.AUTO_RECONCILED);
        insertMatchHypothesisDirectly(h);       // bypass ON CONFLICT
        SystemUser user = new SystemUser(UUID.randomUUID(), "u", "c", "u", UserRole.ADMIN, "x");
        dataStore.saveReconciliationRecords(List.of(new ReconciliationRecord(h, user)));

        List<String> history = dataStore.getReconciliationHistory();
        assertFalse(history.isEmpty());
        assertTrue(history.get(0).contains("EXACT_RULE"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Raw Transactions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void saveRawTransactions_persistsAll() throws SQLException {
        UUID wsId = UUID.randomUUID();
        UUID dsId = UUID.randomUUID();
        insertWorkspaceDirectly(wsId, null);
        insertDatasetDirectly(dsId, wsId, "INTERNAL_EXCEL");    // bypass ON CONFLICT

        RawInternalLedger ledger = new RawInternalLedger(
            dsId, LocalDate.now(), "/test.xlsx", DatasetStatus.PARSED, "SAP", "Q1");
        UUID txId1 = UUID.randomUUID();
        UUID txId2 = UUID.randomUUID();
        dataStore.saveRawTransactions(List.of(
            new RawTransaction(txId1, "2024-01-01", "500.00", "Pay1", TransactionType.DEBIT,   ledger),
            new RawTransaction(txId2, "2024-01-02", "300.00", "Pay2", TransactionType.CREDIT,  ledger)
        ));

        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM raw_transactions WHERE source_dataset_id = ?")) {
            ps.setObject(1, dsId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Domain-object helpers
    // ─────────────────────────────────────────────────────────────────────────

    private MatchHypothesis buildHypothesis(double confidence, MatchType type, HypothesisStatus status) {
        StandardizedTransaction ledger = new StandardizedTransaction(
            UUID.randomUUID(), LocalDate.now(), new BigDecimal("100.00"),
            "Ledger", TransactionType.DEBIT, UUID.randomUUID());
        StandardizedTransaction bank = new StandardizedTransaction(
            UUID.randomUUID(), LocalDate.now(), new BigDecimal("100.00"),
            "Bank", TransactionType.CREDIT, UUID.randomUUID());
        MatchHypothesis h = new MatchHypothesis(ledger, bank, confidence, type);
        h.setStatus(status);
        return h;
    }

    private void setRawTransactions(RawInternalLedger ledger, List<RawTransaction> txns) throws Exception {
        java.lang.reflect.Field field =
            aval.domain.ingestion.FinancialDataset.class.getDeclaredField("rawTransactions");
        field.setAccessible(true);
        field.set(ledger, new java.util.ArrayList<>(txns));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Direct-SQL seed helpers  (plain INSERT — no ON CONFLICT)
    // ─────────────────────────────────────────────────────────────────────────

    /** Inserts a client_organization row directly, bypassing DataStore's ON CONFLICT upsert. */
    private void insertClientOrgDirectly(UUID orgId, String name) throws SQLException {
        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO client_organization (org_id, name) VALUES (?, ?)")) {
            ps.setObject(1, orgId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    private void insertWorkspaceDirectly(UUID wsId, UUID orgId) throws SQLException {
        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO reconciliation_workspace (workspace_id, org_id, status) VALUES (?, ?, ?)")) {
            ps.setObject(1, wsId);
            ps.setObject(2, orgId);
            ps.setString(3, "OPEN");
            ps.executeUpdate();
        }
    }

    private void insertDatasetDirectly(UUID dsId, UUID wsId, String sourceType) throws SQLException {
        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO financial_dataset "
               + "(dataset_id, workspace_id, file_path, source_type, status, import_date) "
               + "VALUES (?,?,?,?,?,?)")) {
            ps.setObject(1, dsId);
            ps.setObject(2, wsId);
            ps.setString(3, "/test.file");
            ps.setString(4, sourceType);
            ps.setString(5, "PARSED");
            ps.setDate(6,  Date.valueOf(LocalDate.now()));
            ps.executeUpdate();
        }
    }

    /** Inserts a match_hypotheses row directly, bypassing DataStore's ON CONFLICT upsert. */
    private void insertMatchHypothesisDirectly(MatchHypothesis h) throws SQLException {
        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO match_hypotheses "
               + "(hypothesis_id, ledger_id, bank_id, confidence_score, match_type, status, justification) "
               + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, h.getHypothesisId());
            ps.setObject(2, h.getLedgerTransaction().getTransactionId());
            ps.setObject(3, h.getBankTransaction().getTransactionId());
            ps.setDouble(4, h.getConfidenceScore());
            ps.setString(5, h.getMatchType().name());
            ps.setString(6, h.getStatus().name());
            ps.setString(7, h.getJustification());
            ps.executeUpdate();
        }
    }

    private void insertStandardizedLedger(UUID txId, UUID dsId,
                                          LocalDate date, BigDecimal amount, String narrative)
            throws SQLException {
        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO standardized_ledger "
               + "(transaction_id, value_date, amount, narrative, transaction_type, source_dataset_id) "
               + "VALUES (?,?,?,?,?,?)")) {
            ps.setObject(1, txId);
            ps.setDate(2,  Date.valueOf(date));
            ps.setBigDecimal(3, amount);
            ps.setString(4, narrative);
            ps.setString(5, "DEBIT");
            ps.setObject(6, dsId);
            ps.executeUpdate();
        }
    }

    private void insertStandardizedBank(UUID txId, UUID dsId,
                                        LocalDate date, BigDecimal amount, String narrative)
            throws SQLException {
        try (Connection c  = h2DataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO standardized_bank "
               + "(transaction_id, value_date, amount, narrative, transaction_type, source_dataset_id) "
               + "VALUES (?,?,?,?,?,?)")) {
            ps.setObject(1, txId);
            ps.setDate(2,  Date.valueOf(date));
            ps.setBigDecimal(3, amount);
            ps.setString(4, narrative);
            ps.setString(5, "CREDIT");
            ps.setObject(6, dsId);
            ps.executeUpdate();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  H2 DataSource — issues a fresh JDBC connection for each getConnection()
    // ─────────────────────────────────────────────────────────────────────────

    private static class H2DataSource implements DataSource {
        private final String url;
        H2DataSource(String url) { this.url = url; }

        @Override public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, "sa", "");
        }
        @Override public Connection  getConnection(String u, String p) throws SQLException { return getConnection(); }
        @Override public PrintWriter getLogWriter()                     { return null; }
        @Override public void        setLogWriter(PrintWriter w)        {}
        @Override public void        setLoginTimeout(int s)             {}
        @Override public int         getLoginTimeout()                  { return 0; }
        @Override public Logger      getParentLogger()                  { return null; }
        @Override public <T> T       unwrap(Class<T> c)                 { return null; }
        @Override public boolean     isWrapperFor(Class<?> c)           { return false; }
    }
}
