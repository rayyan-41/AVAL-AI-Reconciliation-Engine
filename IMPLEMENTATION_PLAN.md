# AVAL AIRE — JavaFX Implementation Plan

**Version:** 1.0
**Date:** April 2026
**Author:** AVAL Engineering
**Audience:** Junior Frontend Developer
**Prototype ref:** aval-ui-prototype.html (validated)

This document provides a step-by-step guide to replicating the AVAL AIRE UI prototype in JavaFX 17. Each phase covers the FXML layout, CSS styling, and controller logic needed to reproduce the validated web design exactly. Backend wiring instructions follow each UI phase only after that phase passes its validation checklist.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Design System](#2-design-system)
3. [Phase 1 — Splash Screen](#phase-1--splash-screen)
4. [Phase 2 — Authentication Screen](#phase-2--authentication-screen)
5. [Phase 3 — Client Registry](#phase-3--client-registry)
6. [Phase 4 — Workspace Shell](#phase-4--workspace-shell)
7. [Phase 5 — Finance Dashboard](#phase-5--finance-dashboard)
8. [Phase 6 — Reconciliation Workspace](#phase-6--reconciliation-workspace)
9. [Phase 7 — Manual Check](#phase-7--manual-check)
10. [Phase 8 — Report](#phase-8--report)
11. [Appendix A — Inter-Controller Communication](#appendix-a--inter-controller-communication)
12. [Appendix B — CSS Class Reference](#appendix-b--css-class-reference)
13. [Appendix C — Common Pitfalls](#appendix-c--common-pitfalls)

---

## 1. Overview

### 1.1 What You Are Building

You are implementing the AVAL AIRE desktop application in JavaFX 17. The UI has been fully designed and validated as an HTML/CSS prototype. Your job is to replicate it faithfully — screen by screen, component by component — and then wire each screen to the existing Java backend services.

The application has four main stages:

- **Boot Screen** — animated splash with progress bar
- **Authentication Screen** — split-layout login with workspace credentials
- **Client Registry** — list of registered companies, select one to enter its workspace
- **Company Workspace** — four tabs: Finance Dashboard, Reconciliation, Manual Check, Report

> **CRITICAL RULE:** Build the UI for each phase first. Only connect backend logic after the UI passes all checklist items. Never mix layout work with backend wiring.

### 1.2 Technology Stack

| Tool | Version / Notes |
|------|----------------|
| JavaFX | 17 (LTS) |
| Java | 17 (LTS) |
| Build tool | Maven (pom.xml already configured) |
| FXML | One .fxml file per screen/view |
| CSS | One fintech-light.css (no dark mode) |
| Font | Jura (TTF, Google Fonts) + Segoe UI Semibold |
| Scene Builder | Optional — can edit FXML manually |

### 1.3 Prerequisites

Before starting, confirm all of the following are true:

- Java 17 is installed and `JAVA_HOME` is set
- Maven build passes: `mvn clean package -DskipTests`
- The existing backend code (`engine/`, `service/`, `persistence/`) compiles without errors
- You have downloaded `Jura-Bold.ttf` and `Jura-SemiBold.ttf` from Google Fonts
- You have placed both TTF files in `src/main/resources/aval/ui/fonts/`
- You are NOT modifying any file outside `src/main/java/aval/ui/` and `src/main/resources/aval/ui/`

### 1.4 File Structure

All your work lives in these two trees:

```
src/main/java/aval/ui/
  MainUIContext.java          ← already exists, manages theme
  controller/
    SplashController.java
    AuthController.java
    RegistryController.java   ← NEW
    WorkspaceController.java  ← NEW (shell + tab router)
    FinanceController.java    ← NEW
    ReconController.java      ← renamed from ReconciliationController
    ManualCheckController.java ← NEW
    ReportController.java     ← NEW

src/main/resources/aval/ui/
  views/
    Splash.fxml               ← already exists
    Auth.fxml                 ← already exists, redesign
    Registry.fxml             ← NEW
    Workspace.fxml            ← NEW (shell)
    Finance.fxml              ← NEW
    Recon.fxml                ← replaces ReconciliationHub.fxml
    ManualCheck.fxml          ← NEW
    Report.fxml               ← NEW
  styles/
    fintech-light.css         ← single stylesheet, redesign
  fonts/
    Jura-Bold.ttf             ← ADD
    Jura-SemiBold.ttf         ← ADD
```

---

## 2. Design System

### 2.1 Color Tokens

JavaFX CSS does not support CSS custom properties (variables). Declare all colors as named looked-up colors at the `.root` level so they cascade.

```css
.root {
    -fx-bg:             #ffffff;
    -fx-bg-subtle:      #f7f8fa;
    -fx-bg-inset:       #f0f2f5;
    -fx-text-primary:   #0d0d0d;
    -fx-text-secondary: #3a3a3a;
    -fx-text-muted:     #6b7280;
    -fx-text-ghost:     #a0aab4;
    -fx-accent:         #800020;
    -fx-accent-hover:   #9a0025;
    -fx-accent-dim:     #fdf2f5;
    -fx-border:         #e2e5e9;
    -fx-border-strong:  #c8cdd6;
    -fx-green-bg:       #f0faf2;
    -fx-green-border:   #b6d9be;
    -fx-green-text:     #1a5c2a;
    -fx-amber-bg:       #fffbeb;
    -fx-amber-text:     #92400e;
    -fx-red-text:       #991b1b;
}
```

### 2.2 Typography

| Use | Font |
|-----|------|
| AVAL wordmark | Jura Bold, loaded from `fonts/Jura-Bold.ttf` |
| Body / labels | Segoe UI Semibold, fallback Verdana |
| Monospaced (refs, amounts) | Consolas, fallback Courier New |
| Muted / light text | Segoe UI, weight 300 (Semilight) |

Loading Jura in JavaFX requires registering the font programmatically before any Scene is created:

```java
// In Main.java, before primaryStage.setScene(...)
Font.loadFont(getClass().getResourceAsStream(
    "/aval/ui/fonts/Jura-Bold.ttf"), 72);
Font.loadFont(getClass().getResourceAsStream(
    "/aval/ui/fonts/Jura-SemiBold.ttf"), 18);
```

Then reference it in CSS:

```css
.aval-wordmark {
    -fx-font-family: "Jura";
    -fx-font-weight: bold;
    -fx-text-fill: -fx-accent;
}
```

### 2.3 Component → JavaFX Mapping

| HTML element / concept | JavaFX equivalent |
|------------------------|-------------------|
| Screen / layout root | `StackPane` or `BorderPane` (never `AnchorPane` for new work) |
| Horizontal split | `HBox` with `HBox.hgrow=ALWAYS` on both children |
| Vertical scroll area | `ScrollPane` with `fitToWidth=true`, `VBox` inside |
| Form text field | `TextField` with `styleClass="underlined-field"` |
| Password field | `PasswordField`, same styleClass |
| Primary button | `Button` with `styleClass="btn-primary"` |
| Ghost / outline button | `Button` with `styleClass="btn-ghost"` |
| Accent button | `Button` with `styleClass="btn-accent"` |
| Exit button | `Button` with `styleClass="btn-exit"` |
| Data table | `TableView` with `styleClass="data-table"` |
| Stat card | `VBox` with `styleClass="stat-card"` |
| Metadata card | `VBox` with `styleClass="fd-card"` |
| Status badge | `Label` with `styleClass="badge badge-active"` etc. |
| Confidence pill | `Label` with `styleClass="conf-pill conf-hi"` or `"conf-lo"` |
| Progress bar (ingestion) | `ProgressBar` with `styleClass="ing-progress"` |
| Pipeline step dot | `StackPane`: Circle + SVGPath icon overlay |
| Drop zone | `VBox` with `styleClass="drop-zone"`, `setOnDragOver`/`setOnDragDropped` |
| Dual panel | `HBox`, two children each with `HBox.hgrow=ALWAYS` |
| Tab navigation | `HBox` of `ToggleButton` in a `ToggleGroup` (NOT `TabPane`) |

---

## Phase 1 — Splash Screen

> **Scope:** UI only — animate then advance to Auth

### 1.1 FXML Layout — `Splash.fxml`

The existing `Splash.fxml` is close to correct. Replace its content with:

```xml
<StackPane prefWidth="800" prefHeight="500"
    style="-fx-background-color: #faf9f6;"
    fx:controller="aval.ui.controller.SplashController">

  <!-- Top-left brand block -->
  <VBox StackPane.alignment="TOP_LEFT"
        style="-fx-padding: 130 0 0 60;" spacing="-8">
    <Label text="AVAL" styleClass="splash-aval" />
    <Label text="AIRE" styleClass="splash-aire" />
  </VBox>

  <!-- Bottom-left log label -->
  <Label fx:id="logLabel" text="Initializing..."
      StackPane.alignment="BOTTOM_LEFT"
      style="-fx-padding: 0 0 20 20;"
      styleClass="splash-log" />

  <!-- Bottom progress bar -->
  <ProgressBar fx:id="progressBar" maxWidth="Infinity"
      prefHeight="3" progress="0"
      StackPane.alignment="BOTTOM_CENTER"
      styleClass="splash-progress" />

</StackPane>
```

### 1.2 CSS Classes

```css
.splash-aval {
    -fx-font-family: "Jura";
    -fx-font-size: 72px;
    -fx-font-weight: bold;
    -fx-text-fill: #c0001a;
}

.splash-aire {
    -fx-font-family: "Segoe UI";
    -fx-font-size: 26px;
    -fx-font-weight: 300;
    -fx-text-fill: #2a2a2a;
    -fx-padding: 0 0 0 5;
}

.splash-log {
    -fx-font-family: "Consolas", "Courier New";
    -fx-font-size: 10px;
    -fx-text-fill: #aaaaaa;
}

.splash-progress .track { -fx-background-color: transparent; }
.splash-progress .bar   { -fx-background-color: #c0001a; -fx-background-insets: 0; }
```

### 1.3 Controller Logic — `SplashController.java`

Use a JavaFX `Timeline` with 5 `KeyFrame`s spaced ~300ms apart. Each `KeyFrame` advances the progress bar and updates the log label. After the last frame, load `Auth.fxml`.

```java
private static final String[] MESSAGES = {
    "Initializing AVAL AIRE runtime...",
    "Loading vectorization engine...",
    "Connecting to persistence layer...",
    "Verifying secure context...",
    "Ready."
};

@FXML private ProgressBar progressBar;
@FXML private Label logLabel;

public void initialize() {
    Timeline tl = new Timeline();
    for (int i = 0; i < MESSAGES.length; i++) {
        final int idx = i;
        double secs = 0.4 + idx * 0.32;
        tl.getKeyFrames().add(new KeyFrame(Duration.seconds(secs), e -> {
            progressBar.setProgress((double)(idx + 1) / MESSAGES.length);
            logLabel.setText(MESSAGES[idx]);
        }));
    }
    tl.setOnFinished(e -> navigateTo("Auth.fxml"));
    tl.play();
}

private void navigateTo(String fxml) {
    // load fxml, replace scene root
}
```

### 1.4 Validation Checklist

- [ ] Window opens at 800 × 500, background is off-white (`#faf9f6`)
- [ ] "AVAL" renders in Jura Bold, crimson, approximately 72px
- [ ] "AIRE" renders in lighter weight directly below with slight left indent
- [ ] Log label at bottom-left updates through 5 messages
- [ ] Progress bar fills from left to right across the bottom edge (3px, no track)
- [ ] Screen automatically advances to Auth after ~2 seconds

---

## Phase 2 — Authentication Screen

> **Scope:** UI only — validate fields, navigate to Registry

### 2.1 FXML Layout — `Auth.fxml`

A full-screen `HBox` split exactly 50/50. Left side is the welcome panel; right side contains the login form.

```xml
<HBox prefWidth="1200" prefHeight="800"
    fx:controller="aval.ui.controller.AuthController">

  <!-- LEFT PANEL -->
  <VBox styleClass="auth-left" HBox.hgrow="ALWAYS" spacing="0">
    <HBox styleClass="auth-wordmark">
      <Label text="AVAL" styleClass="auth-wm-aval" />
      <Label text="AIRE" styleClass="auth-wm-aire" />
    </HBox>
    <Label text="Intelligent reconciliation&#10;for modern finance."
        styleClass="auth-heading" wrapText="true" />
    <Label text="AVAL AIRE combines deterministic rule-matching with AI..."
        styleClass="auth-body" wrapText="true" />
    <Region styleClass="auth-rule" />
    <!-- Feature list: 4 Labels each preceded by a small dot Label -->
  </VBox>

  <!-- RIGHT PANEL -->
  <VBox styleClass="auth-right" HBox.hgrow="ALWAYS"
        alignment="CENTER" spacing="0">
    <VBox maxWidth="360" spacing="26">
      <Label text="Sign in" styleClass="lf-title" />
      <Label text="Enter your credentials to continue." styleClass="lf-sub" />
      <VBox spacing="7">
        <Label text="WORKSPACE ID" styleClass="field-label" />
        <TextField fx:id="workspaceIdField"
            promptText="WS-XXXX-XXXX"
            styleClass="underlined-field" />
      </VBox>
      <VBox spacing="7">
        <Label text="PASSKEY" styleClass="field-label" />
        <PasswordField fx:id="passkeyField"
            promptText="••••••••"
            styleClass="underlined-field" />
      </VBox>
      <Button text="Authenticate"
          maxWidth="Infinity"
          onAction="#handleAuthenticate"
          styleClass="btn-primary" />
    </VBox>
  </VBox>

</HBox>
```

### 2.2 Key CSS

```css
.auth-left {
    -fx-background-color: -fx-bg-subtle;
    -fx-border-color: transparent -fx-border transparent transparent;
    -fx-padding: 80 68 80 68;
}

.auth-wm-aval {
    -fx-font-family: "Jura"; -fx-font-size: 36px;
    -fx-font-weight: bold;   -fx-text-fill: -fx-accent;
}

.auth-wm-aire {
    -fx-font-family: "Segoe UI"; -fx-font-size: 17px;
    -fx-font-weight: 300;        -fx-text-fill: -fx-text-muted;
}

.auth-rule {
    -fx-min-height: 3; -fx-max-height: 3;
    -fx-background-color: -fx-accent;
    -fx-min-width: 36; -fx-max-width: 36;
    -fx-padding: 28 0 28 0;
}

.underlined-field {
    -fx-background-color: transparent;
    -fx-border-color: transparent transparent -fx-border-strong transparent;
    -fx-border-width: 0 0 1.5 0;
    -fx-font-size: 14px; -fx-padding: 9 2 9 2;
}

.underlined-field:focused {
    -fx-border-color: transparent transparent -fx-accent transparent;
}

.btn-primary {
    -fx-background-color: -fx-accent; -fx-text-fill: white;
    -fx-font-size: 14px; -fx-font-weight: bold;
    -fx-background-radius: 4px; -fx-padding: 12 20 12 20;
    -fx-cursor: hand;
}

.btn-primary:hover { -fx-background-color: -fx-accent-hover; }
```

### 2.3 Controller Logic

```java
@FXML private TextField workspaceIdField;
@FXML private PasswordField passkeyField;

@FXML
private void handleAuthenticate() {
    if (workspaceIdField.getText().isBlank() ||
        passkeyField.getText().isBlank()) {
        // highlight empty field border in accent-red
        return;
    }
    // Phase 2: just navigate to Registry
    navigateTo("Registry.fxml");
    // Phase 2+ backend: AuthService.authenticate(id, passkey)
}
```

### 2.4 Validation Checklist

- [ ] Left panel occupies exactly 50% of window width, right panel fills remainder
- [ ] "AVAL" wordmark uses Jura font, correct crimson colour
- [ ] Feature bullet dots (small circles) and text render on left panel
- [ ] Bordeaux accent rule (36px wide, 3px tall) is visible
- [ ] Both fields accept keyboard input; PasswordField masks characters
- [ ] Pressing Authenticate with empty fields does not navigate
- [ ] Pressing Authenticate with any non-empty input navigates to Client Registry

---

## Phase 3 — Client Registry

> **Scope:** UI + MockUIProvider data — click to enter workspace

### 3.1 Screen Architecture

The Registry is a standalone full-screen stage (not inside the Workspace shell). It has its own topbar. Entering a company replaces the entire scene with the Workspace screen. Going back to the Registry requires an explicit Exit action.

### 3.2 FXML Layout — `Registry.fxml`

```xml
<VBox prefWidth="1200" prefHeight="800"
    fx:controller="aval.ui.controller.RegistryController">

  <!-- Topbar -->
  <HBox styleClass="reg-topbar" alignment="CENTER_LEFT">
    <Label text="AVAL" styleClass="reg-wm-aval" />
    <Label text="AIRE" styleClass="reg-wm-aire" />
    <Region HBox.hgrow="ALWAYS" />
    <Label text="Signed in as Aryan" styleClass="tb-user" />
    <Button text="Sign out" onAction="#handleSignOut" styleClass="btn-ghost" />
  </HBox>

  <!-- Content -->
  <VBox VBox.vgrow="ALWAYS" styleClass="reg-body" alignment="TOP_CENTER">
    <VBox maxWidth="760" spacing="0">
      <HBox alignment="CENTER_LEFT" spacing="16">
        <VBox spacing="3">
          <Label text="Client Registry" styleClass="reg-title" />
          <Label text="Select a company to enter its workspace." styleClass="reg-sub" />
        </VBox>
        <Region HBox.hgrow="ALWAYS" />
        <Button text="+ Add Client" styleClass="btn-accent" onAction="#handleAddClient" />
      </HBox>
      <TextField fx:id="searchField" promptText="Search..."
          styleClass="reg-search-field" />
      <TableView fx:id="clientTable" VBox.vgrow="ALWAYS"
                 styleClass="client-table"
                 onMouseClicked="#handleRowClick">
        <columns>
          <TableColumn fx:id="nameCol"     text="Company"  prefWidth="280" />
          <TableColumn fx:id="industryCol" text="Industry" prefWidth="240" />
          <TableColumn fx:id="statusCol"   text="Status"   prefWidth="120" />
          <TableColumn fx:id="arrowCol"    text=""         prefWidth="60"  />
        </columns>
      </TableView>
    </VBox>
  </VBox>

</VBox>
```

### 3.3 Controller Logic — `RegistryController.java`

Use an `ObservableList` backed by `MockUIProvider`. The search field filters via a `FilteredList` wrapper.

```java
private ObservableList<ClientOrganization> allClients;
private FilteredList<ClientOrganization>   filtered;

public void initialize() {
    allClients = FXCollections.observableArrayList(
        MockUIProvider.getMockClients(7));
    filtered = new FilteredList<>(allClients, p -> true);

    searchField.textProperty().addListener((obs, old, nw) -> {
        filtered.setPredicate(c ->
            nw.isBlank() ||
            c.getName().toLowerCase().contains(nw.toLowerCase()) ||
            c.getId().toString().contains(nw)
        );
    });

    nameCol.setCellValueFactory(cd ->
        new SimpleStringProperty(cd.getValue().getName()));
    // set up other columns similarly
    // statusCol: custom cell factory that renders a styled Label badge

    clientTable.setItems(filtered);
}

@FXML
private void handleRowClick(MouseEvent e) {
    if (e.getClickCount() >= 1) {
        ClientOrganization selected =
            clientTable.getSelectionModel().getSelectedItem();
        if (selected != null)
            openWorkspace(selected);
    }
}

private void openWorkspace(ClientOrganization client) {
    // Store client in MainUIContext for workspace controllers to read
    MainUIContext.getInstance().setActiveClient(client);
    navigateTo("Workspace.fxml");
}
```

> **NOTE:** Add a `setActiveClient(ClientOrganization c)` method to `MainUIContext.java`. This allows all workspace sub-controllers to access the current company without passing it through constructors.

### 3.4 Status Badge Cell Factory

The Status column needs a custom cell factory to render colored badge Labels instead of plain text:

```java
statusCol.setCellFactory(col -> new TableCell<ClientOrganization, String>() {
    @Override
    protected void updateItem(String status, boolean empty) {
        super.updateItem(status, empty);
        if (empty || status == null) { setGraphic(null); return; }
        Label badge = new Label(status);
        badge.getStyleClass().addAll("badge",
            switch (status.toLowerCase()) {
                case "active"  -> "badge-active";
                case "pending" -> "badge-pending";
                default        -> "badge-review";
            });
        setGraphic(badge);
        setText(null);
    }
});
```

### 3.5 Validation Checklist

- [ ] Registry topbar shows AVAL AIRE wordmark (Jura font for AVAL)
- [ ] All 7 mock companies appear in the table
- [ ] Name column shows company name bold, workspace ID below in monospace muted
- [ ] Status column renders as coloured badges, not plain text
- [ ] Typing in search field filters rows in real time
- [ ] Clicking any row loads the Workspace screen
- [ ] Sign Out button returns to Auth screen

---

## Phase 4 — Workspace Shell

> **Scope:** Tab navigation, topbar, exit — no content yet

### 4.1 Architecture

The Workspace is a `BorderPane`. The top is a custom tab-navigation bar (NOT a `TabPane` — TabPane styling is too restrictive). The center is a `StackPane` that holds all four sub-views. Only one sub-view is visible at a time. Manual Check and Report start hidden and disabled until their gate conditions are met.

### 4.2 FXML Layout — `Workspace.fxml` (structure only)

```xml
<BorderPane prefWidth="1200" prefHeight="800"
    fx:controller="aval.ui.controller.WorkspaceController">

  <top>
    <HBox styleClass="ws-topbar">

      <!-- Company identity tab -->
      <HBox styleClass="ws-co-tab" alignment="CENTER_LEFT" spacing="10">
        <VBox>
          <Label fx:id="wsCompanyName" styleClass="ws-co-name" />
          <Label fx:id="wsCompanyId"   styleClass="ws-co-id" />
        </VBox>
      </HBox>

      <!-- Tab nav (ToggleGroup) -->
      <HBox fx:id="tabNav" alignment="CENTER_LEFT" HBox.hgrow="ALWAYS">
        <ToggleButton fx:id="tabFinance"  text="Finance Dashboard"
            styleClass="ws-tab" onAction="#showFinance" />
        <ToggleButton fx:id="tabRecon"    text="Reconciliation"
            styleClass="ws-tab" onAction="#showRecon" />
        <ToggleButton fx:id="tabManual"   text="Manual Check"
            styleClass="ws-tab" onAction="#showManual"
            disable="true" />
        <ToggleButton fx:id="tabReport"   text="Report"
            styleClass="ws-tab" onAction="#showReport"
            disable="true" />
      </HBox>

      <!-- Exit -->
      <Button text="Exit Workspace" styleClass="btn-exit"
              onAction="#handleExit" />
    </HBox>
  </top>

  <center>
    <!-- Sub-views loaded programmatically into this StackPane -->
    <StackPane fx:id="contentPane" />
  </center>

</BorderPane>
```

### 4.3 Tab Gating Logic — `WorkspaceController.java`

```java
public void initialize() {
    ClientOrganization client =
        MainUIContext.getInstance().getActiveClient();
    wsCompanyName.setText(client.getName());
    wsCompanyId.setText(client.getId() + " · " + client.getIndustry());

    // Load all sub-views into StackPane
    financeView  = loadFxml("Finance.fxml");
    reconView    = loadFxml("Recon.fxml");
    manualView   = loadFxml("ManualCheck.fxml");
    reportView   = loadFxml("Report.fxml");

    contentPane.getChildren().addAll(
        financeView, reconView, manualView, reportView);
    showView(financeView);

    // ToggleGroup ensures only one tab is active
    ToggleGroup tg = new ToggleGroup();
    tabFinance.setToggleGroup(tg); tabRecon.setToggleGroup(tg);
    tabManual.setToggleGroup(tg);  tabReport.setToggleGroup(tg);
    tabFinance.setSelected(true);
}

public void unlockManualCheck() {
    tabManual.setDisable(false);
    showView(manualView);
    tabManual.setSelected(true);
}

public void unlockReport() {
    tabReport.setDisable(false);
    showView(reportView);
    tabReport.setSelected(true);
}

private void showView(Node view) {
    contentPane.getChildren().forEach(n -> n.setVisible(false));
    view.setVisible(true);
}

@FXML void handleExit() {
    MainUIContext.getInstance().setActiveClient(null);
    navigateTo("Registry.fxml");
}
```

### 4.4 CSS — Workspace Topbar & Tabs

```css
.ws-topbar {
    -fx-background-color: -fx-bg;
    -fx-border-color: transparent transparent -fx-border transparent;
    -fx-min-height: 52px; -fx-max-height: 52px;
}

.ws-co-tab {
    -fx-padding: 0 24 0 24;
    -fx-border-color: transparent -fx-border transparent transparent;
    -fx-min-width: 220px;
}

.ws-co-name { -fx-font-size: 14px; -fx-font-weight: bold; }
.ws-co-id   { -fx-font-family: "Consolas"; -fx-font-size: 11px;
              -fx-text-fill: -fx-text-muted; }

.ws-tab {
    -fx-background-color: transparent;
    -fx-border-color: transparent;
    -fx-text-fill: -fx-text-muted;
    -fx-font-size: 12.5px; -fx-padding: 0 16 0 16;
    -fx-min-height: 52px; -fx-cursor: hand;
}

.ws-tab:selected {
    -fx-text-fill: -fx-accent; -fx-font-weight: bold;
    -fx-border-color: transparent transparent -fx-accent transparent;
    -fx-border-width: 0 0 2 0;
}

.ws-tab:hover:!selected { -fx-text-fill: -fx-text-primary; }
.ws-tab:disabled { -fx-opacity: 0.4; -fx-cursor: default; }

.btn-exit {
    -fx-background-color: transparent;
    -fx-border-color: -fx-border-strong;
    -fx-border-radius: 4px; -fx-background-radius: 4px;
    -fx-text-fill: -fx-text-muted;
    -fx-font-size: 12px; -fx-padding: 5 14 5 14;
    -fx-cursor: hand;
}

.btn-exit:hover {
    -fx-border-color: -fx-accent;
    -fx-text-fill: -fx-accent;
    -fx-background-color: -fx-accent-dim;
}
```

### 4.5 Validation Checklist

- [ ] Topbar shows company name and workspace ID from `MainUIContext`
- [ ] Finance Dashboard and Reconciliation tabs are clickable
- [ ] Manual Check and Report tabs appear greyed out and are not clickable
- [ ] Finance Dashboard view is shown by default on load
- [ ] Switching between Finance and Reconciliation works without errors
- [ ] Exit Workspace clears `MainUIContext` and returns to Registry

---

## Phase 5 — Finance Dashboard

> **Scope:** Company metadata, ledger info, reconciliation history

### 5.1 Layout Structure — `Finance.fxml`

A `ScrollPane` containing a `VBox` with four sections: title row, four stat cards, a two-column metadata grid, and a reconciliation history `TableView`.

```xml
<ScrollPane fitToWidth="true" styleClass="fd-scroll">
  <VBox spacing="0" style="-fx-padding: 36 40 36 40;">

    <!-- Title -->
    <Label fx:id="fdTitle" styleClass="fd-title" />
    <Label fx:id="fdSub"   styleClass="fd-sub" />

    <!-- Stat cards: 4 equal HBox children -->
    <HBox fx:id="statRow" spacing="14" style="-fx-padding: 28 0 28 0;">
      <!-- 4 x VBox.styleClass="stat-card" injected by controller -->
    </HBox>

    <!-- Metadata grid: 2 fd-card VBoxes side by side -->
    <HBox spacing="18" style="-fx-padding: 0 0 28 0;">
      <VBox styleClass="fd-card" HBox.hgrow="ALWAYS">
        <Label text="COMPANY INFORMATION" styleClass="fdc-label" />
        <GridPane fx:id="coGrid" styleClass="meta-grid" />
      </VBox>
      <VBox styleClass="fd-card" HBox.hgrow="ALWAYS">
        <Label text="LEDGER METADATA" styleClass="fdc-label" />
        <GridPane fx:id="ldgGrid" styleClass="meta-grid" />
      </VBox>
    </HBox>

    <!-- History -->
    <Label text="Reconciliation History" styleClass="section-heading" />
    <TableView fx:id="historyTable" styleClass="data-table">
      <columns>
        <TableColumn text="Date"         fx:id="hDateCol"    prefWidth="110" />
        <TableColumn text="Period"        fx:id="hPeriodCol"  prefWidth="110" />
        <TableColumn text="Transactions"  fx:id="hTxnsCol"    prefWidth="110" />
        <TableColumn text="Matched"       fx:id="hMatchedCol" prefWidth="90"  />
        <TableColumn text="Match Rate"    fx:id="hRateCol"    prefWidth="100" />
        <TableColumn text="Anomalies"     fx:id="hAnomCol"    prefWidth="90"  />
        <TableColumn text="Status"        fx:id="hStatusCol"  prefWidth="100" />
      </columns>
    </TableView>

  </VBox>
</ScrollPane>
```

### 5.2 Controller: Populating Stat Cards Programmatically

Stat cards are `VBox`es created in code and injected into the `statRow` HBox. This gives full control over styling.

```java
private VBox makeStatCard(String label, String value, String sub, String valColour) {
    VBox card = new VBox(8);
    card.getStyleClass().add("stat-card");
    HBox.setHgrow(card, Priority.ALWAYS);
    Label lbl = new Label(label); lbl.getStyleClass().add("sc-label");
    Label val = new Label(value); val.getStyleClass().add("sc-value");
    val.setStyle("-fx-text-fill: #" + valColour + ";");
    Label s   = new Label(sub);   s.getStyleClass().add("sc-sub");
    card.getChildren().addAll(lbl, val, s);
    return card;
}

// In initialize():
statRow.getChildren().addAll(
    makeStatCard("Ledger Transactions", "248",   "Current period",        "0d0d0d"),
    makeStatCard("Last Match Rate",     "98.0%", "September 2024",        "1a5c2a"),
    makeStatCard("Pending Review",      "5",     "Awaiting confirmation", "92400e"),
    makeStatCard("Flagged Anomalies",   "2",     "Requires investigation","800020")
);
```

### 5.3 Backend Connection *(after UI validated)*

| Data | Backend call |
|------|-------------|
| Reconciliation history table | `dataStore.getReconciliationHistory()` |
| Company metadata | `MainUIContext.getInstance().getActiveClient()` |
| Ledger statistics | `dataStore.getFinancialDataset(workspaceId, INTERNAL_EXCEL)` |

### 5.4 Validation Checklist

- [ ] Title shows the active company name from `MainUIContext`
- [ ] Four stat cards render in a single horizontal row with equal width
- [ ] Company info grid shows: Name, Workspace ID, Industry, Registration, Status, Onboarded
- [ ] Ledger metadata grid shows: Source file, Period, Total Records, Debits, Credits, Net, Updated
- [ ] Reconciliation history table shows 4 rows of historical data from mock
- [ ] Match Rate column shows green text; Anomalies column shows red text when non-zero
- [ ] Status column renders as coloured badges

---

## Phase 6 — Reconciliation Workspace

> **Scope:** Upload → Ingest → Pipeline → Unlock Manual Check

### 6.1 Layout Structure — `Recon.fxml`

A `VBox` with three zones stacked vertically: the action bar, the pipeline/result strip (hidden initially), and the dual panel. The dual panel is an `HBox` with two equal children.

```xml
<VBox fx:controller="aval.ui.controller.ReconController">

  <!-- Action bar -->
  <HBox fx:id="actionBar" styleClass="rw-bar" alignment="CENTER">
    <Label fx:id="periodLabel" styleClass="rw-period" />
    <Button fx:id="btnRecon" text="Perform Reconciliation"
            styleClass="btn-recon" disable="true"
            onAction="#handlePerformRecon" />
    <Label fx:id="reconHint" styleClass="rw-hint" />
  </HBox>

  <!-- Pipeline strip (hidden until reconciliation starts) -->
  <HBox fx:id="pipelineStrip" styleClass="pipeline-strip"
        visible="false" managed="false" alignment="CENTER">
    <!-- 7 StackPanes (dot + label) with arrow Labels between them -->
    <!-- Built programmatically in controller -->
  </HBox>

  <!-- Result bar (hidden until pipeline complete) -->
  <HBox fx:id="resultBar" styleClass="result-bar"
        visible="false" managed="false" alignment="CENTER">
    <Label fx:id="rbAuto"   styleClass="rb-num" />
    <Label text="auto-matched (≥95%)" styleClass="rb-label" />
    <Region prefWidth="1" styleClass="rb-sep" />
    <Label fx:id="rbManual" styleClass="rb-num-warn" />
    <Label text="require manual review" styleClass="rb-label-warn" />
  </HBox>

  <!-- Dual panel -->
  <HBox VBox.vgrow="ALWAYS">

    <!-- LEFT: Bank Statement -->
    <VBox styleClass="panel panel-left" HBox.hgrow="ALWAYS">
      <HBox styleClass="p-hdr">
        <Label text="BANK STATEMENT" styleClass="p-title" />
        <Label fx:id="bankStatus" />
      </HBox>
      <StackPane fx:id="bankPanel" VBox.vgrow="ALWAYS">
        <!-- States swapped in controller: dropZone, ingesting, ingested -->
      </StackPane>
    </VBox>

    <!-- RIGHT: Internal Ledger -->
    <VBox styleClass="panel" HBox.hgrow="ALWAYS">
      <HBox styleClass="p-hdr">
        <Label text="INTERNAL LEDGER" styleClass="p-title" />
        <Label text="Loaded" styleClass="badge badge-active" />
      </HBox>
      <VBox VBox.vgrow="ALWAYS" style="-fx-padding: 16;">
        <HBox fx:id="ledgerMetaBar" styleClass="lmb" />
        <TableView fx:id="ledgerTable" VBox.vgrow="ALWAYS" styleClass="data-table">
          <columns>
            <TableColumn text="Date"      fx:id="lDateCol" prefWidth="90" />
            <TableColumn text="Reference" fx:id="lRefCol"  prefWidth="110" />
            <TableColumn text="Narrative" fx:id="lNarrCol" />
            <TableColumn text="Type"      fx:id="lTypeCol" prefWidth="90" />
            <TableColumn text="Amount"    fx:id="lAmtCol"  prefWidth="110" />
          </columns>
        </TableView>
      </VBox>
    </VBox>

  </HBox>

</VBox>
```

### 6.2 Drop Zone & File Picker

```java
// Drag-and-drop onto the drop zone VBox
dropZone.setOnDragOver(e -> {
    if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY);
    e.consume();
});

dropZone.setOnDragDropped(e -> {
    List<File> files = e.getDragboard().getFiles();
    if (!files.isEmpty()) startIngestion(files.get(0));
    e.setDropCompleted(true); e.consume();
});

// Browse button
@FXML void handleBrowse() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Select Bank Statement");
    fc.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
    File f = fc.showOpenDialog(stage);
    if (f != null) startIngestion(f);
}
```

### 6.3 Ingestion Animation

Use a `SequentialTransition` of `PauseTransition`s. Each pause updates the `ProgressBar` and the status label. After all steps, call `finishIngestion()`.

```java
private static final String[] ING_STEPS = {
    "Parsing document structure...",
    "Extracting transaction table...",
    "Normalizing date and amount fields...",
    "Running schema standardization...",
    "Building semantic index...",
    "Ingestion complete."
};

private void startIngestion(File f) {
    showBankState("ingesting");
    ingFilename.setText(f.getName());
    SequentialTransition seq = new SequentialTransition();
    for (int i = 0; i < ING_STEPS.length; i++) {
        final int idx = i;
        PauseTransition pt = new PauseTransition(Duration.millis(460));
        pt.setOnFinished(e -> {
            ingProgress.setProgress((double)(idx+1) / ING_STEPS.length);
            ingLabel.setText(ING_STEPS[idx]);
        });
        seq.getChildren().add(pt);
    }
    seq.setOnFinished(e -> finishIngestion(f));
    seq.play();
}

private void finishIngestion(File f) {
    showBankState("ingested");
    // populate metadata labels from parsed file or mock
    btnRecon.setDisable(false);
    reconHint.setText("Both datasets loaded. Ready to reconcile.");
}

private void showBankState(String state) {
    dropZonePane.setVisible("dropzone".equals(state));
    ingestingPane.setVisible("ingesting".equals(state));
    ingestedPane.setVisible("ingested".equals(state));
    // managed property mirrors visible to prevent layout gaps
}
```

### 6.4 Pipeline Animation

When Perform Reconciliation is pressed, hide the action bar, show the pipeline strip, then animate each of the 7 pipeline steps using a `SequentialTransition`. When done, show the result bar and call `WorkspaceController.unlockManualCheck()`.

```java
@FXML void handlePerformRecon() {
    actionBar.setVisible(false); actionBar.setManaged(false);
    pipelineStrip.setVisible(true); pipelineStrip.setManaged(true);

    long[] delays = {600, 700, 900, 1100, 1200, 800, 400};
    SequentialTransition seq = new SequentialTransition();

    for (int i = 0; i < pipeSteps.size(); i++) {
        final int idx = i;
        PauseTransition pt = new PauseTransition(Duration.millis(delays[idx]));
        pt.setOnFinished(e -> markStepDone(idx));
        seq.getChildren().add(pt);
    }

    seq.setOnFinished(e -> finishReconciliation());
    seq.play();
}

private void finishReconciliation() {
    resultBar.setVisible(true); resultBar.setManaged(true);
    rbAuto.setText(String.valueOf(autoCount));
    rbManual.setText(String.valueOf(manualCount));
    // Pass manual items to ManualCheckController
    manualCheckController.setItems(pendingHypotheses);
    // Unlock the tab via WorkspaceController
    workspaceController.unlockManualCheck();
}
```

### 6.5 Backend Connection *(after UI validated)*

| Action | Backend call |
|--------|-------------|
| Load internal ledger | `ingestionService.ingestFile(wsId, ledgerPath, INTERNAL_EXCEL)` + `ingestionService.standardize(ledgerDataset)` |
| Ingest bank PDF | `ingestionService.ingestFile(wsId, file.getPath(), EXTERNAL_PDF)` + `ingestionService.standardize(bankDataset)` |
| Run matching engine | `reconciliationService.runMatching(workspace, stdLedger, stdBank)` |
| Filter review queue | `hypotheses.stream().filter(h -> h.getConfidenceScore() < 0.95).collect(toList())` |

### 6.6 Validation Checklist

- [ ] Drop zone is visible and styled with dashed border on initial load
- [ ] Dragging a file onto the drop zone starts ingestion
- [ ] Browse button opens a FileChooser filtered to `.pdf` files
- [ ] Ingestion progress bar animates through 6 steps with label updates
- [ ] After ingestion, metadata card appears with 6 populated fields
- [ ] "Replace file" link resets the panel to drop zone state
- [ ] "Perform Reconciliation" button is disabled before ingestion and enabled after
- [ ] Clicking button hides action bar, shows pipeline strip
- [ ] All 7 pipeline steps animate in sequence with circle indicators
- [ ] After pipeline completes, result bar shows auto-matched and pending counts
- [ ] Manual Check tab unlocks and becomes clickable

---

## Phase 7 — Manual Check

> **Scope:** Review sub-95% matches → Approve or Reject → unlock Report

### 7.1 Layout — `ManualCheck.fxml`

A `VBox` with a scroll area containing the header, progress bar, and review table; plus a footer bar (hidden until all items resolved).

```xml
<VBox fx:controller="aval.ui.controller.ManualCheckController">

  <ScrollPane fitToWidth="true" VBox.vgrow="ALWAYS">
    <VBox style="-fx-padding: 28 32 28 32;" spacing="20">

      <Label fx:id="mcTitle" text="Manual Check" styleClass="mc-title" />
      <Label fx:id="mcSub" styleClass="mc-sub" wrapText="true" />

      <!-- Progress -->
      <VBox spacing="6">
        <HBox>
          <Label text="Review progress" styleClass="mc-prog-label" />
          <Region HBox.hgrow="ALWAYS" />
          <Label fx:id="mcProgText" styleClass="mc-prog-label-r" />
        </HBox>
        <ProgressBar fx:id="mcProgressBar" maxWidth="Infinity"
                     progress="0" styleClass="mc-progress" />
      </VBox>

      <!-- Table -->
      <TableView fx:id="mcTable" styleClass="mc-table">
        <columns>
          <TableColumn text="Confidence"       fx:id="mcConfCol"   prefWidth="90" />
          <TableColumn text="Ledger Tx"        fx:id="mcLedgerCol" prefWidth="200" />
          <TableColumn text="Bank Tx"          fx:id="mcBankCol"   prefWidth="200" />
          <TableColumn text="AI Justification" fx:id="mcJustCol" />
          <TableColumn text="Action"           fx:id="mcActionCol" prefWidth="160" />
        </columns>
      </TableView>

    </VBox>
  </ScrollPane>

  <!-- Footer bar -->
  <HBox fx:id="mcCompleteBar" styleClass="mc-complete-bar"
        visible="false" managed="false" alignment="CENTER_LEFT">
    <Label text="All items reviewed. Proceed to generate the reconciliation report."
           styleClass="mc-complete-text" HBox.hgrow="ALWAYS" />
    <Button text="Proceed to Report" styleClass="btn-proceed"
            onAction="#handleProceedToReport" />
  </HBox>

</VBox>
```

### 7.2 Custom Cell Factories

Three columns need custom cells: Confidence (coloured pill), Ledger/Bank (multi-line transaction summary), and Action (buttons).

```java
// Confidence pill cell
mcConfCol.setCellFactory(col -> new TableCell<MatchHypothesis, Double>() {
    @Override protected void updateItem(Double conf, boolean empty) {
        super.updateItem(conf, empty);
        if (empty || conf == null) { setGraphic(null); return; }
        int pct = (int)(conf * 100);
        Label pill = new Label(pct + "%");
        pill.getStyleClass().addAll("conf-pill",
            pct >= 85 ? "conf-hi" : "conf-lo");
        setGraphic(pill); setText(null);
    }
});

// Action buttons cell
mcActionCol.setCellFactory(col -> new TableCell<MatchHypothesis, Void>() {
    private final Button approve = new Button("Approve");
    private final Button reject  = new Button("Reject");
    {
        approve.getStyleClass().add("btn-approve");
        reject.getStyleClass().add("btn-reject");
        approve.setOnAction(e -> resolveItem(getIndex(), "APPROVED"));
        reject.setOnAction(e  -> resolveItem(getIndex(), "REJECTED"));
    }
    @Override protected void updateItem(Void v, boolean empty) {
        super.updateItem(v, empty);
        MatchHypothesis item = empty ? null : getTableView().getItems().get(getIndex());
        if (item == null) { setGraphic(null); return; }
        if (item.getStatus() == HypothesisStatus.PENDING_REVIEW) {
            setGraphic(new HBox(8, approve, reject));
        } else {
            Label done = new Label(item.getStatus().name());
            done.setStyle(item.getStatus() == HypothesisStatus.CONFIRMED
                ? "-fx-text-fill: #1a5c2a;" : "-fx-text-fill: #800020;");
            setGraphic(done);
        }
        setText(null);
    }
});
```

### 7.3 Resolve Logic

```java
private int resolved = 0;

private void resolveItem(int index, String decision) {
    MatchHypothesis h = mcTable.getItems().get(index);
    if (decision.equals("APPROVED"))
        h.setStatus(HypothesisStatus.CONFIRMED);
    else
        h.setStatus(HypothesisStatus.REJECTED);
    resolved++;
    mcTable.refresh();
    double prog = (double) resolved / mcTable.getItems().size();
    mcProgressBar.setProgress(prog);
    mcProgText.setText(resolved + " of " + mcTable.getItems().size() + " resolved");
    if (resolved >= mcTable.getItems().size()) {
        mcCompleteBar.setVisible(true);
        mcCompleteBar.setManaged(true);
    }
}

@FXML void handleProceedToReport() {
    workspaceController.unlockReport();
}
```

### 7.4 Backend Connection *(after UI validated)*

| Action | Backend call |
|--------|-------------|
| Approve a match | `reconciliationService.confirmHypothesis(hypothesis, currentUser)` |
| Reject a match | `reconciliationService.rejectHypothesis(hypothesis, currentUser)` |
| Filter pending only | `hypotheses.stream().filter(h -> h.getConfidenceScore() < 0.95).collect(toList())` |

### 7.5 Validation Checklist

- [ ] Only transactions below 95% confidence appear in the table
- [ ] Confidence column shows coloured pills: amber (85–94%), red (below 85%)
- [ ] Ledger and Bank columns show narrative, reference, and signed amount
- [ ] AI Justification column shows the explanation text, wrapping as needed
- [ ] Approve button turns row to dimmed green "Approved" state
- [ ] Reject button turns row to dimmed red "Rejected" state
- [ ] Progress bar and counter update after each action
- [ ] Footer bar appears only when all items have been actioned
- [ ] "Proceed to Report" unlocks and navigates to the Report tab

---

## Phase 8 — Report

> **Scope:** Final summary — Generate report — Email to company

### 8.1 Layout — `Report.fxml`

```xml
<ScrollPane fitToWidth="true"
    fx:controller="aval.ui.controller.ReportController">
  <VBox style="-fx-padding: 36 40 36 40;" spacing="28">

    <Label fx:id="rptTitle" styleClass="fd-title" />
    <Label fx:id="rptSub"   styleClass="fd-sub" />

    <!-- 5 stat cards -->
    <HBox fx:id="rptStatRow" spacing="12" />

    <!-- Actions card -->
    <VBox styleClass="fd-card" spacing="0">
      <Label text="Export and Distribution" styleClass="uc-title" />
      <Label text="Generate the verified report or send directly to the client."
             styleClass="uc-sub" wrapText="true" />
      <HBox spacing="12">
        <Button fx:id="btnGenerate" text="Generate Report"
                styleClass="btn-gen" onAction="#handleGenerate" />
        <Button fx:id="btnEmail"    text="Email to Company"
                styleClass="btn-email" onAction="#handleEmail" />
      </HBox>
      <Label fx:id="feedbackLabel" styleClass="feedback-label"
             visible="false" managed="false" />
    </VBox>

    <!-- Lineage table -->
    <Label text="Reconciliation Lineage" styleClass="section-heading" />
    <TableView fx:id="lineageTable" styleClass="data-table">
      <columns>
        <TableColumn text="Ledger Ref"  prefWidth="110" />
        <TableColumn text="Ledger Narr" />
        <TableColumn text="Bank Ref"    prefWidth="110" />
        <TableColumn text="Bank Narr"   />
        <TableColumn text="Match Type"  prefWidth="140" />
        <TableColumn text="Confidence"  prefWidth="90" />
        <TableColumn text="Resolution"  prefWidth="130" />
      </columns>
    </TableView>

  </VBox>
</ScrollPane>
```

### 8.2 Controller Logic

```java
public void populateReport(List<MatchHypothesis> all) {
    long autoN    = all.stream().filter(h -> h.getConfidenceScore() >= 0.95).count();
    long approved = all.stream().filter(h ->
        h.getStatus() == HypothesisStatus.CONFIRMED
        && h.getConfidenceScore() < 0.95).count();
    long rejected = all.stream().filter(h ->
        h.getStatus() == HypothesisStatus.REJECTED).count();
    long total    = all.size();
    long matched  = autoN + approved;
    double rate   = (double) matched / total * 100;
    // Build 5 stat cards from these values
    // Populate lineage table from full hypothesis list
}

@FXML void handleGenerate() {
    feedbackLabel.setVisible(true); feedbackLabel.setManaged(true);
    feedbackLabel.setText("Generating...");
    // Phase 8+ backend: reportService.generateReconciliationReport(...)
    PauseTransition pt = new PauseTransition(Duration.millis(1200));
    pt.setOnFinished(e -> feedbackLabel.setText(
        "Report generated: reconciliation_report_" +
        client.getName().replace(" ", "_") + "_Sep2024.csv"));
    pt.play();
}

@FXML void handleEmail() {
    feedbackLabel.setVisible(true); feedbackLabel.setManaged(true);
    feedbackLabel.setText("Sending...");
    PauseTransition pt = new PauseTransition(Duration.millis(1000));
    pt.setOnFinished(e -> feedbackLabel.setText(
        "Report emailed to " + client.getName() + " — Delivered."));
    pt.play();
}
```

### 8.3 Backend Connection *(after UI validated)*

| Action | Backend call |
|--------|-------------|
| Generate CSV report | `reportService.generateReconciliationReport(confirmedMatches, unmatchedLedger, unmatchedBank, hypotheses, anomalies, outputPath)` |
| Email distribution | Extend `ReportService` or add an `EmailService` (not yet in codebase — stub in Phase 8) |
| Save reconciliation record | `dataStore.saveReconciliationRecord(record)` |

### 8.4 Validation Checklist

- [ ] Report only becomes accessible after Manual Check is completed
- [ ] Five stat cards show: Total, Auto-Matched, Manually Approved, Rejected, Final Rate
- [ ] Final Match Rate reflects actual approve/reject decisions from Phase 7
- [ ] Generate Report button shows feedback label with filename after 1.2 seconds
- [ ] Email button label shows the active company name
- [ ] Email button shows "Delivered" confirmation after 1 second
- [ ] Lineage table populates with all hypotheses showing match type and resolution

---

## Appendix A — Inter-Controller Communication

Because the workspace sub-controllers (Finance, Recon, ManualCheck, Report) are loaded into the same `StackPane`, they need to communicate. Use one of these two patterns:

### Pattern 1: Shared context via `MainUIContext` *(recommended for state)*

```java
// In MainUIContext.java, add:
private List<MatchHypothesis> pendingHypotheses;
private ClientOrganization activeClient;
// ... getters and setters
```

### Pattern 2: Controller injection at load time

```java
// In WorkspaceController, after loading sub-FXMLs:
FXMLLoader reconLoader = new FXMLLoader(...);
Node reconView = reconLoader.load();
ReconController reconCtrl = reconLoader.getController();
reconCtrl.setWorkspaceController(this);  // inject parent ref
```

---

## Appendix B — CSS Class Reference

| Class | Description |
|-------|-------------|
| `.btn-primary` | Accent red fill, white text, 4px radius, full width |
| `.btn-accent` | Accent red, smaller, inline (not full width) |
| `.btn-ghost` | Transparent, border only, grey text |
| `.btn-exit` | Transparent, strong border, turns red on hover |
| `.btn-recon` | Accent red, bold, center of action bar |
| `.btn-recon:disabled` | Grey fill, ghost text, cursor default |
| `.btn-approve` | Green background, green text, green border |
| `.btn-reject` | Red-tinted background, red text |
| `.btn-proceed` | Accent red, in the manual check footer |
| `.underlined-field` | No background, only bottom border, red on focus |
| `.data-table` | No background, border around, subtle header shade |
| `.stat-card` | White card, 1px border, 8px radius, shadow-xs |
| `.fd-card` | Same as stat-card with more padding |
| `.badge-active` | Green background, green text, dot indicator |
| `.badge-pending` | Amber background, amber text |
| `.badge-review` | Red-tinted background, red text |
| `.conf-pill` | Monospaced, coloured background, rounded corners |
| `.conf-hi` | Amber pill (85–94% confidence) |
| `.conf-lo` | Red pill (below 85%) |
| `.drop-zone` | Dashed border, subtle background, red on hover |
| `.ingested-card` | Green border/background with 2×2 metadata grid |
| `.pipeline-strip` | Subtle background strip, 7 steps with arrows |
| `.result-bar` | Green background strip, shows match counts |
| `.mc-complete-bar` | Green footer, appears only when all items resolved |

---

## Appendix C — Common Pitfalls

| Issue | Solution |
|-------|----------|
| TabPane vs custom tabs | Do NOT use JavaFX `TabPane`. It cannot be styled to match the design without fighting the default skin. Use `ToggleButton` in an `HBox` with a `ToggleGroup`. |
| CSS variable syntax | JavaFX uses `-fx-property` not `var(--name)`. Declare colours as named looked-up colors on `.root`. |
| Jura font not loading | `Font.loadFont()` must be called before the first Scene is set. Call it in `Main.java start()` before `show()`. |
| ProgressBar track visible | Set both `.track` and `.bar` in CSS. Without `.track { background-color: transparent }`, a grey rail shows behind the fill. |
| Table column widths wrong | Set `prefWidth` on each `TableColumn` in FXML. Do not rely on auto-resize for a fixed layout. |
| `managed` vs `visible` | When hiding a node with `setVisible(false)`, also call `setManaged(false)`. Otherwise the node still occupies layout space. |
| ScrollPane background | ScrollPane has a default grey background. Add `.scroll-pane { -fx-background-color: transparent; }` and `.scroll-pane .viewport { -fx-background-color: transparent; }` |
| Split pane divider | `SplitPane` adds a visible divider. For the dual panel, use a plain `HBox` with a manually styled separator `Region` instead. |

---

*— End of Implementation Plan —*

*CONFIDENTIAL*
