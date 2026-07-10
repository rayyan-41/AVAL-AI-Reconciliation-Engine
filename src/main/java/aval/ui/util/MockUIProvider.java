package aval.ui.util;

import aval.domain.core.ClientOrganization;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utility class to provide mock data for the UI, ensuring complete decoupling
 * from the actual backend logic during frontend development.
 */
public class MockUIProvider {

    public static List<ClientOrganization> getMockClients(int count) {
        List<ClientOrganization> clients = new ArrayList<>();
        String[] names = {
            "Pacific Trust",
            "BrightLine Retail",
            "Azure Logistics",
            "Bordeaux Estates",
            "Onyx Holdings",
        };
        for (int i = 0; i < count; i++) {
            clients.add(
                new ClientOrganization(
                    UUID.randomUUID(),
                    names[i % names.length],
                    "Active - Last sync: " + (i + 1) + "h ago"
                )
            );
        }
        return clients;
    }
}
