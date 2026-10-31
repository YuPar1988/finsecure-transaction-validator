# FinSecure Pay – Transaction Validator

A Spring Boot service that validates payment transactions, stores every API call in a database, and provides a secured web dashboard for reviewing request logs and monitoring the API. It ships with a Dockerfile, Kubernetes manifests, a Jenkins pipeline with security gates, and Prometheus/Grafana monitoring with email alerts.

## Contents

- [Architecture](#architecture)
- [Features](#features)
- [API](#api)
- [Running locally](#running-locally)
- [Tests](#tests)
- [Container image](#container-image)
- [Kubernetes deployment](#kubernetes-deployment)
- [CI/CD pipeline](#cicd-pipeline)
- [Security](#security)
- [Monitoring and alerting](#monitoring-and-alerting)
- [Logs and troubleshooting](#logs-and-troubleshooting)
- [Project structure](#project-structure)

## Architecture

```
 Developer ──push──► GitHub ──webhook──► Jenkins
                                          │ Checkout → Build → Test → Package
                                          │ SonarQube + OWASP Dependency-Check
                                          │ Docker build → Trivy scan → Push
                                          ▼
                                     Docker Hub
                                          │ image
                                          ▼
 ┌─────────────────────── Managed Kubernetes (AKS / EKS / GKE) ───────────────────────┐
 │  namespace: finsecure                                                              │
 │   Service (LoadBalancer) ──► Deployment transaction-validator (2+ pods, probes)    │
 │                                   │  ConfigMap + Secret                            │
 │                                   ▼                                                │
 │                              PostgreSQL (StatefulSet + PVC)                        │
 │  namespace: monitoring                                                             │
 │   Prometheus ◄─ServiceMonitor─ /actuator/prometheus   Grafana   Alertmanager ─► SMTP│
 └────────────────────────────────────────────────────────────────────────────────────┘
```

## Features

| Area | What it does |
|------|--------------|
| Validation API | `POST /validate` checks transaction ID format and duplicates, account number format and Luhn checksum, merchant ID, amount limits and precision, supported currency, and blocked countries. Returns `APPROVED` (200) or `REJECTED` (422) with error codes and a risk level. |
| Health | `GET /health` returns status, version, environment and pod name. Kubernetes uses `/actuator/health/liveness` and `/actuator/health/readiness`. |
| Request log | Every call to `/validate`, `/health` and `/api/**` is saved to the `api_request_log` table: request ID, time, method, path, status, duration, outcome, transaction ID, client IP, user agent, pod, and the request/response bodies (account numbers masked). |
| Login | Form login backed by the `app_user` table with BCrypt passwords. The admin account is created on first start from `ADMIN_USERNAME` / `ADMIN_PASSWORD`. |
| Dashboard | Request volume over time, success rate, response times, approval rate, status code breakdown, per-endpoint stats and latest requests. Refreshes automatically. |
| Request logs | Search and filter by request/transaction ID, endpoint, method, status class, outcome and date range, with paging, a detail view per request, and CSV export. |
| API monitoring | Live health components, uptime, heap, CPU, threads, DB pool, and per-endpoint Micrometer timers for the current pod. |
| API tester | Send sample transactions (valid and each failure type) and generate batches of traffic from the browser. |
| Metrics | Prometheus metrics at `/actuator/prometheus`, including `finsecure_transactions_total{result,risk}` and `finsecure_validation_errors_total{code}`. |

## API

### `POST /validate`

```json
{
  "transactionId": "TXN-100001",
  "accountNumber": "4111111111111111",
  "merchantId": "MER1001",
  "amount": 150.75,
  "currency": "SGD",
  "country": "SG"
}
```

Approved (HTTP 200):

```json
{ "transactionId": "TXN-100001", "status": "APPROVED", "riskLevel": "LOW", "errors": [], "validatedAt": "2026-10-31T02:15:00Z" }
```

Rejected (HTTP 422):

```json
{
  "transactionId": "TXN-100002",
  "status": "REJECTED",
  "riskLevel": "HIGH",
  "errors": [
    { "code": "AMOUNT_LIMIT_EXCEEDED", "field": "amount", "message": "Amount exceeds the single transaction limit of 10000.00" },
    { "code": "CURRENCY_UNSUPPORTED", "field": "currency", "message": "Currency XYZ is not supported. Allowed: [SGD, USD, EUR, GBP, MYR]" }
  ],
  "validatedAt": "2026-10-31T02:15:00Z"
}
```

| Rule | Error codes |
|------|-------------|
| Transaction ID required, 6–40 letters/digits/hyphens, not processed before | `TXN_ID_REQUIRED`, `TXN_ID_INVALID`, `TXN_DUPLICATE` |
| Account number required, 10–16 digits, valid Luhn checksum | `ACCOUNT_REQUIRED`, `ACCOUNT_INVALID`, `ACCOUNT_CHECKSUM` |
| Merchant ID required, 4–20 upper-case letters/digits | `MERCHANT_REQUIRED`, `MERCHANT_INVALID` |
| Amount required, between min and max, at most 2 decimals | `AMOUNT_REQUIRED`, `AMOUNT_TOO_LOW`, `AMOUNT_LIMIT_EXCEEDED`, `AMOUNT_PRECISION` |
| Currency required and in the allowed list | `CURRENCY_REQUIRED`, `CURRENCY_UNSUPPORTED` |
| Country optional; 2-letter ISO code, not blocked | `COUNTRY_INVALID`, `COUNTRY_BLOCKED` |

Limits, currencies and blocked countries come from configuration (`VALIDATION_*` variables / ConfigMap). Malformed JSON returns HTTP 400 with `MALFORMED_REQUEST`.

Each response carries an `X-Request-Id` header. Use it to find the stored entry at `/logs/request/{id}`.

### `GET /health`

```json
{ "status": "UP", "service": "FinSecure Pay Transaction Validator", "version": "1.0.0", "environment": "production", "pod": "transaction-validator-7c9d-abcde", "timestamp": "..." }
```

## Running locally

Requirements: JDK 21+, Docker (optional). Maven is not required; use the wrapper.

```bash
# Run with the in-memory H2 database
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Open http://localhost:8080. If `ADMIN_PASSWORD` is not set, a random admin password is generated and printed once in the startup log. To choose one:

```bash
ADMIN_PASSWORD='your-password' ./mvnw spring-boot:run
# PowerShell: $env:ADMIN_PASSWORD='your-password'; .\mvnw.cmd spring-boot:run
```

### Full stack with Docker Compose

Runs the app with PostgreSQL, Prometheus, Alertmanager and Grafana.

```bash
cp .env.example .env               # then edit the passwords
mkdir -p secrets && printf '%s' '<smtp-app-password>' > secrets/smtp_password
docker compose up -d --build
```

| Service | URL |
|---------|-----|
| Application | http://localhost:8080 |
| Prometheus | http://localhost:9090 |
| Alertmanager | http://localhost:9093 |
| Grafana | http://localhost:3000 (admin / `GRAFANA_ADMIN_PASSWORD`) |

Edit the sender and recipient addresses in `monitoring/alertmanager/alertmanager.yml` before expecting alert emails.

## Tests

```bash
./mvnw test
```

- `TransactionValidatorTest`: every validation rule, boundaries, Luhn and risk levels (unit tests, repository mocked).
- `MaskingTest`: account number masking in stored bodies.
- `TransactionApiIntegrationTest`: `/health`, approve/reject/duplicate/malformed flows, request log persistence and masking, dashboard statistics, Prometheus metrics, probes.
- `SecurityAndPagesTest`: login, wrong password, protected pages, logout, CSRF rules, security headers.

Reports: `target/surefire-reports`, coverage: `target/site/jacoco/index.html`.

## Container image

```bash
docker build --build-arg APP_VERSION=1.0.0 -t <dockerhub-user>/finsecure-transaction-validator:1.0.0 .
docker run --rm -p 8080:8080 -e ADMIN_PASSWORD='your-password' <dockerhub-user>/finsecure-transaction-validator:1.0.0
curl http://localhost:8080/health

docker login
docker push <dockerhub-user>/finsecure-transaction-validator:1.0.0
```

The image is multi-stage (JDK build, JRE Alpine runtime), runs as a non-root user (UID 10001), uses Spring Boot layered extraction and has a container health check.

## Kubernetes deployment

Manifests are in `k8s/` and applied with Kustomize.

| File | Purpose |
|------|---------|
| `namespace.yaml` | `finsecure` namespace |
| `configmap.yaml` | Environment, DB URL, validation limits |
| `secret.example.yaml` | Keys the app expects (reference only, no real values) |
| `postgres.yaml` | PostgreSQL StatefulSet with a persistent volume |
| `deployment.yaml` | 2 replicas, rolling update (`maxUnavailable: 0`), startup/readiness/liveness probes, resource limits, non-root, read-only root filesystem |
| `service.yaml` | `LoadBalancer` service on port 80 |
| `pdb-hpa.yaml` | PodDisruptionBudget and HorizontalPodAutoscaler (2–5 pods) |
| `monitoring/` | ServiceMonitor, PrometheusRule and Helm values for kube-prometheus-stack |

### Create a cluster

Azure (AKS):

```bash
az group create -n finsecure-rg -l southeastasia
az aks create -g finsecure-rg -n finsecure-aks --node-count 2 --node-vm-size Standard_B2s --generate-ssh-keys
az aks get-credentials -g finsecure-rg -n finsecure-aks
```

AWS (EKS) – the cluster used for this project is defined in `k8s/cluster/eks-cluster.yaml`
(2 × t3.medium in public subnets, no NAT gateway, EBS CSI driver and metrics-server add-ons):

```bash
eksctl create cluster -f k8s/cluster/eks-cluster.yaml
kubectl apply -f k8s/cluster/storageclass-gp3.yaml     # default storage class for the PostgreSQL volume

# delete everything when finished
eksctl delete cluster -f k8s/cluster/eks-cluster.yaml --disable-nodegroup-eviction
```

Google Cloud (GKE):

```bash
gcloud container clusters create finsecure-gke --zone asia-southeast1-a --num-nodes 2 --machine-type e2-medium
gcloud container clusters get-credentials finsecure-gke --zone asia-southeast1-a
```

### Deploy

```bash
kubectl apply -f k8s/namespace.yaml
kubectl -n finsecure create secret generic finsecure-secrets \
  --from-literal=DB_USERNAME=finsecure \
  --from-literal=DB_PASSWORD='<strong-password>' \
  --from-literal=ADMIN_USERNAME=admin \
  --from-literal=ADMIN_PASSWORD='<strong-password>'

# set your Docker Hub user in k8s/kustomization.yaml (images.newName), then
kubectl apply -k k8s/
kubectl -n finsecure rollout status deployment/transaction-validator
kubectl -n finsecure get pods,svc
curl http://<EXTERNAL-IP>/health
```

### Self-healing check

```bash
kubectl -n finsecure get pods
kubectl -n finsecure delete pod <one-transaction-validator-pod>
kubectl -n finsecure get pods -w      # a replacement pod is created and becomes Ready
```

### Rollout and rollback

```bash
kubectl -n finsecure rollout history deployment/transaction-validator
kubectl -n finsecure rollout undo deployment/transaction-validator                 # previous version
kubectl -n finsecure rollout undo deployment/transaction-validator --to-revision=3 # specific revision
```

The Jenkins job can also roll back with `ACTION=rollback`.

## CI/CD pipeline

`Jenkinsfile` stages:

1. **Checkout** – reads the version from `pom.xml` and tags the image `<version>-<build number>`.
2. **Build** – `./mvnw clean compile`.
3. **Test** – JUnit 5 tests; results published to Jenkins. Any failing test stops the pipeline, so nothing is packaged, pushed or deployed.
4. **Package** – builds and archives the JAR.
5. **Security Checks** (parallel)
   - SonarQube analysis, then `waitForQualityGate abortPipeline: true`.
   - OWASP Dependency-Check, failing on any dependency with CVSS ≥ 7.
6. **Container Build** – `docker build`.
7. **Container Scan** – Trivy; the build fails on fixable HIGH or CRITICAL vulnerabilities.
8. **Push** – pushes the versioned tag and `latest` to Docker Hub.
9. **Deploy** – sets the image in Kustomize, applies the manifests, records the change cause and waits for the rollout. If the rollout fails it is undone automatically.
10. **Smoke Test** – calls `/health` through the load balancer and rolls back if it does not report `UP`.

Email is sent on success and failure (Email Extension plugin).

### Jenkins setup

A ready Jenkins image with Docker CLI, kubectl and Trivy, plus SonarQube, is in `ci/`:

```bash
docker compose -f ci/docker-compose.ci.yml up -d --build
# Jenkins: http://localhost:8081   SonarQube: http://localhost:9000
```

Jenkins configuration:

| Item | Value |
|------|-------|
| Credential `dockerhub-credentials` | Username + Docker Hub access token |
| Credential `kubeconfig` | Secret file: kubeconfig for the cluster |
| Credential `nvd-api-key` | Secret text: NVD API key (free from nvd.nist.gov) |
| Credential `sonar-token` | Secret text: SonarQube token, used by the SonarQube server entry |
| Manage Jenkins → System → SonarQube servers | Name `sonarqube`, URL `http://sonarqube:9000`, token `sonar-token` |
| Manage Jenkins → System → Extended E-mail Notification | SMTP server, port 587, TLS, SMTP credentials, default recipients |
| Global environment variable `DOCKERHUB_REPO` | `<dockerhub-user>/finsecure-transaction-validator` |
| Job | Pipeline from SCM → this GitHub repository, script path `Jenkinsfile` |

All of the above is applied automatically by Jenkins Configuration as Code (`ci/jenkins/casc.yaml`);
secrets are read from files described in `ci/README-secrets.md`.

The quality gate is checked by the pipeline itself (it polls the SonarQube API), so no SonarQube
webhook is needed. New commits are picked up by SCM polling every 2 minutes.

## Security

- **Secrets** are never in the source tree. Locally they come from `.env` (git-ignored); in Kubernetes from the `finsecure-secrets` Secret; in Jenkins from the credentials store via `withCredentials`, so they are masked in logs.
- **No default admin password.** If none is provided, a random one is generated.
- **Passwords** are hashed with BCrypt. Dashboard pages require the `ADMIN` role; sessions are HTTP-only cookies; CSRF protection is on for all forms.
- **Headers**: Content-Security-Policy (`script-src 'self'`, no inline scripts), `X-Content-Type-Options`, `X-Frame-Options`, Referrer-Policy.
- **Data protection**: account numbers are masked in stored request bodies and in the `transactions` table. CSV export neutralises formula injection.
- **Security gates**: SonarQube quality gate, OWASP Dependency-Check (CVSS ≥ 7) and Trivy (HIGH/CRITICAL) each stop the pipeline before the image is pushed or deployed. Accepted exceptions must be added to `dependency-check-suppressions.xml` or `.trivyignore` with a reason.
- **Runtime hardening**: non-root container, read-only root filesystem, all Linux capabilities dropped, no privilege escalation, seccomp `RuntimeDefault`, resource limits.

## Monitoring and alerting

Install kube-prometheus-stack (commands are at the top of `k8s/monitoring/kube-prometheus-stack-values.yaml`), then:

```bash
kubectl apply -f k8s/monitoring/servicemonitor.yaml -f k8s/monitoring/prometheusrule.yaml
kubectl -n monitoring create configmap finsecure-dashboard \
  --from-file=monitoring/grafana/dashboards/finsecure-dashboard.json
kubectl -n monitoring label configmap finsecure-dashboard grafana_dashboard=1
```

The Grafana dashboard **FinSecure Pay – Transaction Validator** shows instances up, request rate, rejection rate, p50/p95/p99 latency, approved vs rejected per minute, rejection reasons, heap and CPU per pod.

| Alert | Condition |
|-------|-----------|
| `FinSecureAppDown` | A pod cannot be scraped for 1 minute |
| `FinSecureReplicasUnavailable` | Fewer than 2 available replicas for 2 minutes (Kubernetes only) |
| `HighTransactionRejectionRate` | More than 50% of transactions rejected over 2 minutes, for 1 minute |
| `HighServerErrorRate` | More than 5% HTTP 5xx over 5 minutes |
| `SlowValidateResponses` | `/validate` p95 above 1 second for 5 minutes |
| `HighJvmHeapUsage` | Heap above 85% for 5 minutes |

To trigger `HighTransactionRejectionRate`, open **API Tester → Traffic generator**, choose *About 90% rejected* and send 200 requests. The alert goes to *Pending*, then *Firing* about a minute later, and Alertmanager sends the email.

## Logs and troubleshooting

1. **Application request log** – Dashboard → *API Request Logs*. Filter by status `5xx` or outcome `ERROR`, open an entry to see the request, response, pod and duration. Every response returns `X-Request-Id`; open `/logs/request/<id>` to jump straight to it.
2. **Container logs** – each log line includes the request ID:
   ```bash
   kubectl -n finsecure logs deployment/transaction-validator --all-containers --since=15m
   kubectl -n finsecure logs <pod> --previous           # logs from a crashed container
   kubectl -n finsecure logs -l app.kubernetes.io/name=transaction-validator | grep <request-id>
   ```
3. **Pod state and events**:
   ```bash
   kubectl -n finsecure get pods
   kubectl -n finsecure describe pod <pod>
   kubectl -n finsecure get events --sort-by=.lastTimestamp
   ```
4. **Cloud logging** – container stdout is collected by the platform's log service (Azure Monitor Container Insights, CloudWatch Container Insights, or Google Cloud Logging) where it can be searched by namespace, pod or request ID.

## Project structure

```
├── src/main/java/com/finsecure/validator
│   ├── transaction/   validation rules, service, REST controller, entities
│   ├── requestlog/    request log filter, entity, search and statistics
│   ├── monitoring/    live metrics snapshot for the monitoring page
│   ├── user/          users, login, admin bootstrap
│   ├── health/        /health endpoint
│   ├── web/           dashboard and log pages
│   └── config/        security and configuration properties
├── src/main/resources templates (Thymeleaf), static CSS/JS, application.yml
├── src/test/java      JUnit 5 tests
├── k8s/               Kubernetes manifests and monitoring resources
├── monitoring/        Prometheus, Alertmanager and Grafana configuration
├── ci/                Jenkins image and local CI stack
├── Dockerfile
├── docker-compose.yml
└── Jenkinsfile
```
