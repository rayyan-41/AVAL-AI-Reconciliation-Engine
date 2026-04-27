# Understanding Maven Directory Layouts: Custom vs. Standard

This guide explains the difference between using a custom "flattened" directory structure and the standard Enterprise Maven directory layout, using the AVAL-AI-Reconciliation-Engine project as a concrete example.

## 1. The Custom "Flattened" Structure

In the early stages of this project, the codebase used a custom, simplified directory structure where all Java code and UI resources (`.fxml`, `.css`) lived directly under the `src/` directory:

```text
src/
└── aval/
    ├── Main.java
    ├── service/
    │   └── IngestionService.java
    └── ui/
        ├── styles/
        │   └── fintech-dark.css
        └── views/
            └── Dashboard.fxml
```

### Why this requires custom `pom.xml` configuration

By default, Maven expects Java code to be in `src/main/java` and non-code resources to be in `src/main/resources`. Because the flattened structure doesn't follow this convention, Maven cannot find your code or resources automatically.

To make Maven compile the code and package the resources correctly in the flattened structure, the `pom.xml` must explicitly override the default paths:

```xml
<!-- Old pom.xml configuration for flattened structure -->
<build>
    <!-- Explicitly tell Maven where to find Java source files -->
    <sourceDirectory>src</sourceDirectory>
    
    <resources>
        <resource>
            <!-- Explicitly tell Maven where to find non-code resources -->
            <directory>src</directory>
            <includes>
                <include>**/*.fxml</include>
                <include>**/*.css</include>
            </includes>
        </resource>
    </resources>
    
    <plugins>
        <!-- ... plugins ... -->
    </plugins>
</build>
```

## 2. The Standard Enterprise Maven Structure

To align with industry standards, the project was restructured into the standard Maven directory layout. This separates executable code from static resources:

```text
src/
└── main/
    ├── java/                  <-- Only Java source code
    │   └── aval/
    │       ├── Main.java
    │       └── service/
    │           └── IngestionService.java
    └── resources/             <-- Only non-code assets (CSS, FXML, SQL, etc.)
        └── aval/
            └── ui/
                ├── styles/
                │   └── fintech-dark.css
                └── views/
                    └── Dashboard.fxml
```

### Why this simplifies the `pom.xml`

Because this structure perfectly matches Maven's default expectations, you no longer need to explicitly define the `<sourceDirectory>` or `<resources>`. Maven automatically knows to:
1. Compile everything in `src/main/java`.
2. Copy everything in `src/main/resources` directly into the final `.jar` file.

The `pom.xml` becomes much cleaner and easier to maintain:

```xml
<!-- New pom.xml configuration for standard structure -->
<build>
    <!-- No sourceDirectory or resources overrides needed! -->
    
    <plugins>
        <plugin>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-maven-plugin</artifactId>
            <version>0.0.8</version>
            <configuration>
                <mainClass>aval.Main</mainClass>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Summary

*   **Flattened (`src/aval/`)**: Good for quick prototypes but mixes concerns (code vs. resources). Requires verbose `pom.xml` overrides so Maven knows where to look.
*   **Standard (`src/main/java/` & `src/main/resources/`)**: The industry standard. Separates concerns cleanly. Relies on "convention over configuration," allowing the `pom.xml` to remain minimal and default-driven.
