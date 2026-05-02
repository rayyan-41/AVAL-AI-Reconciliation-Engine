//Dev: Aryan
//Use Cases: All (auth)
package aval.domain;

import aval.common.enums.UserRole;
import java.util.UUID;

//@desc:   Represents an authenticated user within the AVAL ecosystem.
//@grasp:  Information Expert
//@gof:    N/A
public class SystemUser {

    //-------------- Attributes ----------------------//
    private final UUID userId;
    private final String username;
    private final UserRole role;

    //Constructor
    public SystemUser(UUID userId, String username, UserRole role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    //---------- Methods ------------//

    //Getters
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public UserRole getRole() { return role; }
}
