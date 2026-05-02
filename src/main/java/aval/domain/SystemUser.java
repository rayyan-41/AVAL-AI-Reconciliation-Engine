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
    private final String fullName;
    private final String cnic;
    private final String username;
    private final UserRole role;
    private final String location;

    //Constructor
    public SystemUser(UUID userId, String fullName, String cnic, String username, UserRole role, String location) {
        this.userId = userId;
        this.fullName = fullName;
        this.cnic = cnic;
        this.username = username;
        this.role = role;
        this.location = location;
    }

    //---------- Methods ------------//

    //Getters
    public UUID getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getCnic() { return cnic; }
    public String getUsername() { return username; }
    public UserRole getRole() { return role; }
    public String getLocation() { return location; }
}
