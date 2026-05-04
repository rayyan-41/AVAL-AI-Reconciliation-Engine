package aval.domain;

import aval.common.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SystemUser — a plain domain entity.
 */
public class SystemUserTest {

    @Test
    void constructor_storesAllFields() {
        UUID id = UUID.randomUUID();
        SystemUser user = new SystemUser(id, "Nia Rahman", "12345-6789012-3", "niaR", UserRole.ACCOUNTANT, "Karachi");
        assertEquals(id, user.getUserId());
        assertEquals("Nia Rahman", user.getFullName());
        assertEquals("12345-6789012-3", user.getCnic());
        assertEquals("niaR", user.getUsername());
        assertEquals(UserRole.ACCOUNTANT, user.getRole());
        assertEquals("Karachi", user.getLocation());
    }

    @Test
    void allRoles_canBeSet() {
        for (UserRole role : UserRole.values()) {
            SystemUser user = new SystemUser(UUID.randomUUID(), "Test", "00000-0000000-0", "test", role, "City");
            assertEquals(role, user.getRole());
        }
    }

    @Test
    void twoUsers_withDifferentIds_areDistinct() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        SystemUser u1 = new SystemUser(id1, "User1", "cnic1", "user1", UserRole.ADMIN, "City");
        SystemUser u2 = new SystemUser(id2, "User2", "cnic2", "user2", UserRole.ACCOUNTANT, "Town");
        assertNotEquals(u1.getUserId(), u2.getUserId());
    }

    @Test
    void nullFields_doNotThrow() {
        assertDoesNotThrow(() -> new SystemUser(UUID.randomUUID(), null, null, null, UserRole.AUDITOR, null));
    }
}
