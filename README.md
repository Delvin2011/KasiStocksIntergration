# KasiStocks Integration Service

A **SAP CAP (Cloud Application Programming) Java** microservice for managing KasiStocks reorders, deployed on **SAP BTP Cloud Foundry**.

---

## 🧱 Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 (SAP Machine JRE) |
| Framework | SAP CAP Java / Spring Boot |
| Database | SAP HANA Cloud (HDI Container) |
| Authentication | XSUAA (OAuth 2.0) |
| Alerting | SAP Alert Notification Service |
| Build | Maven (MTA build) |
| Deployment Target | SAP BTP Cloud Foundry |

---

## 🚀 CI/CD Pipeline

The project uses the **SAP Project "Piper"** continuous integration and delivery pipeline, configured in `.pipeline/config.yml`.

### Pipeline Overview

```
Code Push ──► Build (MTA) ──► Package (.mtar) ──► Deploy (Cloud Foundry)
```

### Pipeline Configuration

- **Build Tool:** `mta` — builds the Multi-Target Application archive (`.mtar`)
- **Deploy Tool:** `mtaDeployPlugin` — deploys the packaged `.mtar` to SAP BTP Cloud Foundry
- **Artifact:** `mta_archives/kasistocks-reorders_1.0.0-SNAPSHOT.mtar`

### Pipeline Steps

1. **Build** — Maven compiles the Java service (`mvn clean package -DskipTests=true`) and `npm run build` packages the HANA DB artifacts.
2. **Package** — The MTA build tool assembles all modules into a single deployable `.mtar` archive.
3. **Deploy** — The `cloudFoundryDeploy` step pushes the `.mtar` to the target Cloud Foundry space using the MTA Deploy Plugin.

---

## 🗂️ Project Structure

```
kasistocks-reorders/
├── srv/          # CAP Java service layer (Spring Boot)
├── db/           # HANA database artifacts (CDS schema, HDI)
├── mta.yaml      # MTA deployment descriptor
├── manifest.yml  # Cloud Foundry manifest (standalone deploy reference)
├── xs-security.json  # XSUAA security descriptor
└── .pipeline/
    └── config.yml    # SAP Piper CI/CD pipeline configuration
```

---

## 🔧 Local Development

### Prerequisites

- Java 21+
- Maven 3.8+
- Node.js 18+
- SAP CAP CLI (`npm install -g @sap/cds-dk`)
- Cloud Foundry CLI + MTA Deploy Plugin

### Build

```bash
mvn clean package -DskipTests=true
```

### Deploy to Cloud Foundry (manual)

```bash
mbt build
cf deploy mta_archives/kasistocks-reorders_1.0.0-SNAPSHOT.mtar
```

---

## ☁️ BTP Services Required

| Service | Plan | Purpose |
|---|---|---|
| `xsuaa` | `application` | Authentication & Authorization |
| `hana` | `hdi-shared` | Persistence (HDI Container) |
| `alert-notification` | `standard` | Operational Alerting |