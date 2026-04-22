//Dev: Safwan
//Use Cases: UC1
package aval.domain.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//@desc:   Top-level domain entity representing a Pakistani SME client registered in the AVAL system.
//@grasp:  Information Expert, Pure Fabrication
//@gof:    N/A
public class ClientOrganization {
    private UUID orgId;
    private String name;
    private String contactMetadata;
    private List<ReconciliationWorkspace> workspaces;

    public ClientOrganization(UUID orgId, String name, String contactMetadata) {
        this.orgId = orgId;
        this.name = name;
        this.contactMetadata = contactMetadata;
        this.workspaces = new ArrayList<>();
    }
}