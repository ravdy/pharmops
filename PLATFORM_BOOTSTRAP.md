# PharmOps — Platform Bootstrap Guide

> **Goal:** Get all 5 microservices running on EKS via ArgoCD GitOps.
> CI/CD (GitHub Actions) comes in Phase 2. Monitoring comes in Phase 3.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [What You Need to Customise](#2-what-you-need-to-customise)
3. [Two Repositories — Clear Responsibilities](#3-two-repositories--clear-responsibilities)
4. [Bootstrap Overview](#4-bootstrap-overview)
5. [Step 1 — Terraform: Create All Infrastructure](#5-step-1--terraform-create-all-infrastructure)
6. [Step 2 — Connect kubectl](#6-step-2--connect-kubectl)
7. [Step 3 — Install Cluster Add-ons and ArgoCD](#7-step-3--install-cluster-add-ons-and-argocd)
8. [Step 4 — Initialize Database Schemas](#8-step-4--initialize-database-schemas)
9. [Step 5 — Build Docker Images and Push to ECR](#9-step-5--build-docker-images-and-push-to-ecr)
10. [Step 6 — Update Image Tags in GitOps Repo](#10-step-6--update-image-tags-in-gitops-repo)
11. [Step 7 — Apply ArgoCD Project and Applications](#11-step-7--apply-argocd-project-and-applications)
12. [Step 8 — Verify Everything is Running](#12-step-8--verify-everything-is-running)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Prerequisites

### 1.1 Local Tools

Install all tools before starting.

| Tool | Min Version | Verify |
|------|------------|--------|
| AWS CLI | v2.x | `aws --version` |
| Terraform | >= 1.10 | `terraform -version` |
| kubectl | >= 1.28 | `kubectl version --client` |
| Docker | >= 24 | `docker --version` |
| Helm | >= 3.12 | `helm version` |
| ArgoCD CLI | >= 2.8 | `argocd version --client` |
| Git | >= 2.x | `git --version` |

### 1.2 AWS Account

Configure the AWS CLI with credentials that have permissions to create VPC, EKS, RDS, ECR, IAM roles, and Secrets Manager secrets:

```bash
aws configure
# Enter: Access Key ID, Secret Access Key, Region (us-east-1), Output (json)

# Verify
aws sts get-caller-identity
```

### 1.3 Docker Multi-Platform Build Setup

EKS nodes run on `linux/amd64`. The `docker buildx` command works on **Mac, Linux, and Windows** — the Docker command itself is identical across all platforms. The only difference is how you set shell variables before running the commands.

Create a multi-platform builder (required on Apple Silicon Macs; recommended on all platforms):

```bash
docker buildx create --name pharma-builder --use
docker buildx inspect --bootstrap
```

#### Shell variable syntax by platform

All build commands in this guide use bash syntax. If you are on **Windows PowerShell**, translate variables like this:

| Bash (Mac/Linux/Git Bash) | PowerShell (Windows) |
|--------------------------|---------------------|
| `export AWS_ACCOUNT_ID=$(aws sts ...)` | `$env:AWS_ACCOUNT_ID = aws sts ...` |
| `${AWS_ACCOUNT_ID}` | `$env:AWS_ACCOUNT_ID` |
| `${REGISTRY}/api-gateway:v1.0.0` | `"$env:REGISTRY/api-gateway:v1.0.0"` |

PowerShell example for ECR login and image build:
```powershell
$env:AWS_ACCOUNT_ID = aws sts get-caller-identity --query Account --output text
$env:REGISTRY = "$env:AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com"

aws ecr get-login-password --region us-east-1 | `
  docker login --username AWS --password-stdin $env:REGISTRY

docker buildx build --platform linux/amd64 `
  -t "$env:REGISTRY/api-gateway:v1.0.0" --push services/api-gateway
```

> **Tip:** Using **Git Bash** on Windows lets you run the bash commands exactly as written in this guide without any translation.

### 1.4 Fork and Clone Repositories

Fork both repositories on GitHub into your account, then clone:

```bash
git clone https://github.com/<YOUR_GITHUB_USERNAME>/pharmops.git
git clone https://github.com/<YOUR_GITHUB_USERNAME>/pharmops-gitops.git
```

---

## 2. What You Need to Customise

After Terraform runs (Step 1), replace these three placeholders across the pharmops-gitops repo before deploying.

### 2.1 `<AWS_ACCOUNT_ID>` → Your 12-digit AWS account ID

Files to update:

| File | What it sets |
|------|-------------|
| `envs/dev/values-api-gateway.yaml` | ECR image URL, IAM role ARN |
| `envs/dev/values-auth-service.yaml` | ECR image URL |
| `envs/dev/values-catalog-service.yaml` | ECR image URL |
| `envs/dev/values-notification-service.yaml` | ECR image URL |
| `envs/dev/values-pharma-ui.yaml` | ECR image URL |
| `k8s-manifests/pharma-ui/deployment.yaml` | ECR image URL |

Quick replace (run from inside `pharmops-gitops`):
```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
find envs/dev k8s-manifests -name "*.yaml" -exec \
  sed -i "s/<AWS_ACCOUNT_ID>/$AWS_ACCOUNT_ID/g" {} \;
```

### 2.2 `<RDS_ENDPOINT>` → Your RDS hostname

Files to update: `values-auth-service.yaml`, `values-catalog-service.yaml`, `values-notification-service.yaml`

Get it after Terraform completes:
```bash
cd pharmops/pharma-devops/terraform/envs/dev
terraform output rds_endpoint
```

Quick replace:
```bash
RDS_ENDPOINT="<your-rds-endpoint>"
find envs/dev -name "*.yaml" -exec \
  sed -i "s/<RDS_ENDPOINT>/$RDS_ENDPOINT/g" {} \;
```

### 2.3 `<YOUR_GITHUB_USERNAME>` → Your GitHub username

Files to update: all files under `argocd/apps/dev/` and `argocd/projects/`

Quick replace:
```bash
GITHUB_USERNAME="<your-github-username>"
find argocd -name "*.yaml" -exec \
  sed -i "s/<YOUR_GITHUB_USERNAME>/$GITHUB_USERNAME/g" {} \;
```

Commit and push these changes before proceeding to Step 7.

---

## 3. Two Repositories — Clear Responsibilities

| Repo | Contains |
|------|----------|
| `pharmops` | Microservice source code, Dockerfiles, Terraform infrastructure |
| `pharmops-gitops` | Helm charts, ArgoCD applications, K8s manifests, DB init SQL |

---

## 4. Bootstrap Overview

```
Step 1  → Terraform: VPC + ECR + EKS + RDS         (pharmops repo)
Step 2  → Connect kubectl to EKS cluster
Step 3  → Install cluster add-ons + ArgoCD          (pharmops-gitops repo)
Step 4  → Initialize database schemas
Step 5  → Build Docker images and push to ECR       (pharmops repo)
Step 6  → Update image tags in GitOps repo          (pharmops-gitops repo)
Step 7  → Apply ArgoCD project and applications     (pharmops-gitops repo)
Step 8  → Verify everything is running
```

---

## 5. Step 1 — Terraform: Create All Infrastructure

### 5.1 Bootstrap Remote State (One-Time)

```bash
# Create S3 bucket for Terraform state (bucket name must be globally unique)
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

aws s3api create-bucket \
  --bucket pharma-tf-state-${ACCOUNT_ID} \
  --region us-east-1

aws s3api put-bucket-versioning \
  --bucket pharma-tf-state-${ACCOUNT_ID} \
  --versioning-configuration Status=Enabled
```

> **No DynamoDB needed:** Terraform >= 1.10 supports native S3 state locking using S3 conditional writes — no DynamoDB table is required. Simply enable versioning on the S3 bucket (done above) and ensure your `backend.tf` does **not** include a `dynamodb_table` line. Terraform will handle locking automatically.
>
> If you are on an older Terraform version (< 1.10), you would also need to create a DynamoDB table and add `dynamodb_table = "pharma-tf-lock"` to `backend.tf`. Upgrading to 1.10+ is recommended.

### 5.2 Apply Terraform

```bash
cd pharmops/pharma-devops/terraform/envs/dev

terraform init
terraform plan
terraform apply
# Takes ~20 minutes (EKS cluster creation is the slowest step)
```

**What gets created:**

| Resource | Details |
|----------|---------|
| VPC | 10.0.0.0/16, 3 subnet tiers (public, private, data) |
| EKS Cluster | pharma-dev-eks, t3.small nodes |
| RDS PostgreSQL | db.t3.micro, multi-AZ off, database: pharmadb |
| ECR Repositories | 5 repos (one per service) |
| IAM Roles | EKS node role, External Secrets Operator role |
| Secrets Manager | /pharma/dev/db-credentials, /pharma/dev/jwt-secret |

> **Teaching point:** One `terraform apply` creates ~30 AWS resources in the correct dependency order — this is Infrastructure as Code.

### 5.3 Create AWS Secrets Manager Secrets

```bash
# Database credentials (use a strong password)
aws secretsmanager create-secret \
  --name /pharma/dev/db-credentials \
  --secret-string '{"username":"pharma","password":"<YOUR_DB_PASSWORD>"}' \
  --region us-east-1

# JWT signing secret (minimum 32 characters)
aws secretsmanager create-secret \
  --name /pharma/dev/jwt-secret \
  --secret-string '{"secret":"<YOUR_JWT_SECRET_MIN_32_CHARS>"}' \
  --region us-east-1
```

---

### ✅ Validation — Step 1

```bash
# EKS cluster exists
aws eks describe-cluster --name pharma-dev-eks --query 'cluster.status' --output text
# Expected: ACTIVE

# ECR repositories exist
aws ecr describe-repositories --query 'repositories[].repositoryName' --output table
# Expected: api-gateway, auth-service, drug-catalog-service, notification-service, pharma-ui

# Secrets exist
aws secretsmanager list-secrets --query 'SecretList[].Name' --output table
# Expected: /pharma/dev/db-credentials and /pharma/dev/jwt-secret
```

---

## 6. Step 2 — Connect kubectl

```bash
aws eks update-kubeconfig \
  --region us-east-1 \
  --name pharma-dev-eks \
  --alias pharma-dev

# Verify nodes are Ready
kubectl get nodes
```

---

### ✅ Validation — Step 2

```bash
kubectl get nodes
# Expected: nodes listed with STATUS=Ready

kubectl config current-context
# Expected: pharma-dev
```

---

## 7. Step 3 — Install Cluster Add-ons and ArgoCD

Run all commands from inside the `pharmops-gitops` directory:

```bash
cd pharmops-gitops

# Add Helm repositories
helm repo add ingress-nginx    https://kubernetes.github.io/ingress-nginx
helm repo add external-secrets https://charts.external-secrets.io
helm repo add argo             https://argoproj.github.io/argo-helm
helm repo update

# Create namespaces
kubectl apply -f k8s/namespaces.yaml

# Install External Secrets Operator
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace kube-system \
  --set installCRDs=true \
  --set serviceAccount.annotations."eks\.amazonaws\.com/role-arn"="arn:aws:iam::${AWS_ACCOUNT_ID}:role/pharma-dev-eso-role" \
  --wait

# Wait for CRDs to be registered before applying ClusterSecretStore
kubectl wait --for=condition=established \
  crd/clustersecretstores.external-secrets.io \
  --timeout=60s

# Clear kubectl's stale API discovery cache
rm -rf ~/.kube/cache/discovery

# Apply External Secrets — syncs DB credentials and JWT secret from Secrets Manager
kubectl apply -f k8s/external-secrets/cluster-secret-store.yaml
kubectl apply -f k8s/external-secrets/dev-external-secrets.yaml

# Install NGINX Ingress Controller
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --values k8s/ingress/nginx-values.yaml --wait

# Install ArgoCD
kubectl apply -f argocd/install/argocd-namespace.yaml
helm upgrade --install argocd argo/argo-cd \
  --namespace argocd \
  --set server.service.type=ClusterIP \
  --wait
```

Get the ArgoCD admin password:
```bash
kubectl get secret argocd-initial-admin-secret \
  -n argocd \
  -o jsonpath='{.data.password}' | base64 -d
echo ""
```

---

### ✅ Validation — Step 3

```bash
# ArgoCD pods running
kubectl get pods -n argocd
# Expected: all pods Running

# External Secrets synced into Kubernetes Secrets
kubectl get secrets -n dev
# Expected: db-credentials and jwt-secret are present

# Ingress controller running
kubectl get pods -n ingress-nginx
# Expected: ingress-nginx-controller pod Running
```

---

## 8. Step 4 — Initialize Database Schemas

```bash
cd pharmops/pharma-devops/terraform/envs/dev

# Get RDS endpoint (strip the :5432 port suffix)
export RDS_ENDPOINT=$(terraform output -raw rds_endpoint | cut -d: -f1)

kubectl run psql-init \
  --image=postgres:15-alpine \
  --namespace=dev \
  --restart=Never \
  --env="PGPASSWORD=<YOUR_DB_PASSWORD>" \
  -- psql -h ${RDS_ENDPOINT} -U pharma -d pharmadb \
  -f /dev/stdin < pharmops-gitops/db-init/01-schemas.sql

kubectl logs psql-init -n dev
kubectl delete pod psql-init -n dev
```

---

### ✅ Validation — Step 4

```bash
kubectl run psql-check \
  --image=postgres:15-alpine \
  --namespace=dev \
  --restart=Never \
  --env="PGPASSWORD=<YOUR_DB_PASSWORD>" \
  -- psql -h ${RDS_ENDPOINT} -U pharma -d pharmadb \
  -c "\dn"
kubectl logs psql-check -n dev
# Expected: schemas listed — auth, drug_catalog (and others)
kubectl delete pod psql-check -n dev
```

---

## 9. Step 5 — Build Docker Images and Push to ECR

```bash
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com"
export IMAGE_TAG="v1.0.0"

# Login to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin ${REGISTRY}
```

Build and push each service. The `--platform linux/amd64` flag is required — EKS nodes are x86, but you may be building on Apple Silicon (arm64):

```bash
cd pharmops

# api-gateway
docker buildx build --platform linux/amd64 \
  -t ${REGISTRY}/api-gateway:${IMAGE_TAG} --push services/api-gateway

# auth-service
docker buildx build --platform linux/amd64 \
  -t ${REGISTRY}/auth-service:${IMAGE_TAG} --push services/auth-service

# drug-catalog-service
docker buildx build --platform linux/amd64 \
  -t ${REGISTRY}/drug-catalog-service:${IMAGE_TAG} --push services/drug-catalog-service

# notification-service
docker buildx build --platform linux/amd64 \
  -t ${REGISTRY}/notification-service:${IMAGE_TAG} --push services/notification-service

# pharma-ui
# Important: .env.production (REACT_APP_API_URL=/api) is baked in at build time.
# Do not remove this file — without it the login page cannot reach the API.
docker buildx build --platform linux/amd64 \
  -t ${REGISTRY}/pharma-ui:${IMAGE_TAG} --push services/pharma-ui
```

> **ECR repository names must match exactly:** `api-gateway`, `auth-service`, `drug-catalog-service`, `notification-service`, `pharma-ui`. If Terraform created different names, update the `repository` field in the corresponding `envs/dev/values-*.yaml` file.

---

### ✅ Validation — Step 5

```bash
for repo in api-gateway auth-service drug-catalog-service notification-service pharma-ui; do
  echo -n "$repo: "
  aws ecr describe-images --repository-name $repo \
    --query 'imageDetails[0].imageTags[0]' --output text 2>/dev/null || echo "NOT FOUND"
done
# Expected: each shows v1.0.0
```

---

## 10. Step 6 — Update Image Tags in GitOps Repo

After the placeholders from Section 2 have been replaced, update the image tags to match what was pushed.

```bash
cd pharmops-gitops

export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com"
export IMAGE_TAG="v1.0.0"

# Update Helm values files (api-gateway, auth-service, catalog-service, notification-service)
for svc in api-gateway auth-service catalog-service notification-service; do
  sed -i "s|tag:.*|tag: ${IMAGE_TAG}|" "envs/dev/values-${svc}.yaml"
done

# Update pharma-ui raw manifest
sed -i "s|image:.*pharma-ui.*|image: ${REGISTRY}/pharma-ui:${IMAGE_TAG}|" \
  k8s-manifests/pharma-ui/deployment.yaml

git add .
git commit -m "chore: set image tags to ${IMAGE_TAG} for dev"
git push
```

> **Teaching point:** With Helm, image tags live in one `values.yaml` per service. With raw manifests (pharma-ui), you have to find and edit the exact line in `deployment.yaml`. This is one of the reasons Helm is preferred for production services.

---

## 11. Step 7 — Apply ArgoCD Project and Applications

```bash
cd pharmops-gitops

# Login to ArgoCD
kubectl port-forward svc/argocd-server -n argocd 8080:443 &
sleep 3
ARGOCD_PASSWORD=$(kubectl get secret argocd-initial-admin-secret \
  -n argocd -o jsonpath='{.data.password}' | base64 -d)
argocd login localhost:8080 --insecure --username admin --password $ARGOCD_PASSWORD

# Apply project and RBAC
kubectl apply -f argocd/projects/pharma-project.yaml
kubectl apply -f k8s/rbac/cluster-roles.yaml
kubectl apply -f k8s/rbac/dev-role.yaml
kubectl apply -f k8s/rbac/rolebindings.yaml

# Apply ArgoCD Applications (one per service)
kubectl apply -f argocd/apps/dev/api-gateway/application.yaml
kubectl apply -f argocd/apps/dev/auth-service/application.yaml
kubectl apply -f argocd/apps/dev/catalog-service/application.yaml
kubectl apply -f argocd/apps/dev/notification-service/application.yaml
kubectl apply -f argocd/apps/dev/pharma-ui/application.yaml
```

ArgoCD will automatically sync and deploy all services. Watch the rollout:

```bash
kubectl get pods -n dev -w
```

### How Each Application is Deployed

**pharma-ui** — raw Kubernetes manifests (demonstrates manual manifest management):
```yaml
source:
  path: k8s-manifests/pharma-ui   # plain YAML, no Helm block
```

**All other services** — shared Helm chart with per-service values:
```yaml
source:
  path: pharma-service             # shared Helm chart
  helm:
    valueFiles:
      - ../envs/dev/values-auth-service.yaml
```

### ArgoCD UI

```bash
# Port-forward (if not already running)
kubectl port-forward svc/argocd-server -n argocd 8080:443 &
# Open: https://localhost:8080
# Username: admin  |  Password: (from Step 3)
```

---

### ✅ Validation — Step 7

```bash
# All 5 ArgoCD applications Synced
argocd app list --server localhost:8080 --insecure
# Expected: 5 apps with Sync Status=Synced, Health=Healthy

# All 5 pods Running in dev namespace
kubectl get pods -n dev
# Expected:
# api-gateway-xxx              1/1   Running
# auth-service-xxx             1/1   Running
# drug-catalog-service-xxx     1/1   Running
# notification-service-xxx     1/1   Running
# pharma-ui-xxx                1/1   Running
```

> **Note on startup time:** Spring Boot services take 60–90 seconds to start. `drug-catalog-service` may take longer on first run because Flyway runs database migrations. Pods will show `0/1 Running` until the health check passes — this is normal.

---

## 12. Step 8 — Verify Everything is Running

### Access the UI

```bash
kubectl port-forward svc/pharma-ui -n dev 8081:80
# Open: http://localhost:8081
```

### Login Credentials

| Username | Password | Role |
|----------|----------|------|
| `admin` | `changeme` | ADMIN — full access |
| `pharmacist1` | `changeme` | PHARMACIST — limited access |

> Passwords are seeded by Flyway migrations as bcrypt hashes — never stored as plain text.

### API Tests

```bash
# Port-forward API gateway for direct testing
kubectl port-forward svc/api-gateway -n dev 8082:8080 &

# 1. Login and get JWT token
TOKEN=$(curl -s -X POST http://localhost:8082/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"changeme"}' \
  | grep -o 'eyJ[^"]*')
echo "Token: ${TOKEN:0:50}..."

# 2. Drug catalog (reads from RDS database)
curl -s http://localhost:8082/api/drugs \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 3. Protected route blocked without token
curl -s -o /dev/null -w "Status without token: %{http_code}\n" \
  http://localhost:8082/api/drugs
# Expected: 401

# 4. Health checks
curl -s http://localhost:8082/api/auth/actuator/health
# Expected: {"status":"UP",...}
```

---

### ✅ Final Validation

```bash
# Login returns a JWT token
curl -s -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"changeme"}'
# Expected: {"token":"eyJ...","username":"admin","role":"ADMIN"}

# Drug catalog returns seeded data (8 drugs)
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"changeme"}' | grep -o 'eyJ[^"]*')
curl -s http://localhost:8081/api/drugs -H "Authorization: Bearer $TOKEN"
# Expected: JSON array with drugs — Crocin, Augmentin, Brufen, etc.

# All pods healthy
kubectl get pods -n dev
# Expected: all 5 pods Running, 0 CrashLoopBackOff

# ArgoCD all apps Healthy
argocd app list --server localhost:8080 --insecure
# Expected: all Sync=Synced, Health=Healthy
```

---

## 13. Troubleshooting

### Pod in CrashLoopBackOff

```bash
kubectl logs -n dev <pod-name> --previous
```

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `Connection refused` to DB | Wrong `DB_HOST` in values file or RDS Security Group not allowing EKS | Check `<RDS_ENDPOINT>` placeholder was replaced |
| `No such file or directory /tmp` | `readOnlyRootFilesystem: true` with no emptyDir | Ensure `volumeMounts` for `/tmp` is in the values file |
| `exec format error` | Image built for wrong architecture | Rebuild with `--platform linux/amd64` |
| `Invalid JWT` or 401 on all requests | `JWT_SECRET` not synced from Secrets Manager | Check `kubectl get secret jwt-secret -n dev` exists |

### Pod stuck in Pending

```bash
kubectl describe pod -n dev <pod-name>
# Check Events section — usually insufficient node capacity or missing PVC
```

t3.small nodes have a maximum of **11 pods per node**. If you have many system pods already running, add a second node via Terraform.

### ArgoCD shows OutOfSync

```bash
argocd app sync <app-name> --server localhost:8080 --insecure
```

If still OutOfSync, check for Helm rendering errors:
```bash
argocd app diff <app-name> --server localhost:8080 --insecure
```

### Login page shows network error in browser

The React app sends API requests to `/api/...` (relative path), which nginx proxies to `api-gateway`. Verify the env var was baked in at image build time:

```bash
kubectl exec -n dev deployment/pharma-ui -- \
  grep -c 'localhost:8080' /usr/share/nginx/html/static/js/main.*.js
# Expected: 0  (if it shows 1, the image was built without .env.production)
```

If it shows 1, rebuild the pharma-ui image — ensure `services/pharma-ui/.env.production` exists with `REACT_APP_API_URL=/api`.

### External Secrets not syncing

```bash
kubectl describe externalsecret db-credentials -n dev
# Check the Events section for the specific error

# Verify the secret exists in AWS Secrets Manager
aws secretsmanager get-secret-value \
  --secret-id /pharma/dev/db-credentials \
  --query SecretString --output text
```

### Flyway checksum mismatch

**Never modify an existing migration file** (`V1__`, `V2__`, etc.) after it has been applied to the database. Always create a new file (`V3__`, `V4__`) for any changes. Modifying applied migrations causes Flyway to refuse to start.

### Port 8080 conflict (ArgoCD vs local services)

```bash
# Use a different local port for ArgoCD
kubectl port-forward svc/argocd-server -n argocd 8090:443 &
argocd login localhost:8090 --insecure ...
```

---

## Architecture Reference

```
Browser (http://localhost:8081)
  │
  ▼
pharma-ui (nginx, port 80)
  │  /api/*  →  proxy to api-gateway:8080
  │  /*      →  serve React SPA (index.html)
  │
  ▼
api-gateway (Spring Cloud Gateway, port 8080)
  │  /api/auth/**          →  auth-service:8081
  │  /api/drugs/**         →  drug-catalog-service:8082
  │                            (path rewritten to /api/drug/catalog/*)
  │  /api/notifications/** →  notification-service:3000
  │  /api/inventory, etc.  →  MockDataController (stub — no backend service)
  │
  ├─▶ auth-service (port 8081)          ─▶ RDS (schema: auth)
  ├─▶ drug-catalog-service (port 8082)  ─▶ RDS (schema: drug_catalog)
  └─▶ notification-service (port 3000)
```

### Service Name Reference

All services use `fullnameOverride` in their Helm values so Kubernetes service DNS names are short and predictable:

| Directory Name | K8s Service Name | Port |
|---------------|-----------------|------|
| `api-gateway` | `api-gateway` | 8080 |
| `auth-service` | `auth-service` | 8081 |
| `drug-catalog-service` | `drug-catalog-service` | 8082 |
| `notification-service` | `notification-service` | 3000 |
| `pharma-ui` | `pharma-ui` | 80 |

---

**Next phases:**
- Phase 2: GitHub Actions CI/CD — automate docker build, push, and image tag updates
- Phase 3: Monitoring with Prometheus + Grafana
