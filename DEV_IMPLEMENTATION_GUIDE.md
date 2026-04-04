# Pharma DevOps — Dev Environment Implementation Guide

> **Scope:** Bring the dev environment live on a brand-new AWS account.
> **Region:** us-east-1 | **Environment:** dev (qa/prod follow the same pattern)

---

## OS Support

This guide covers both **Mac** and **Windows**. Commands are shown side-by-side where they differ.

> **Windows students — strong recommendation: use WSL2**
> WSL2 (Windows Subsystem for Linux) lets you run a full Ubuntu terminal on Windows.
> Every bash command in this guide works identically inside WSL2.
> If you use WSL2, follow the **Mac/Linux** column everywhere.
> Only use the PowerShell column if WSL2 is not an option.

---

## Table of Contents

### Phase 1 — Infrastructure & Initial Deployment
1. [Prerequisites — Local Machine](#1-prerequisites--local-machine)
2. [AWS Account Bootstrap](#2-aws-account-bootstrap)
3. [Bootstrap Terraform Remote State](#3-bootstrap-terraform-remote-state)
4. [Terraform — Provision Dev Infrastructure](#4-terraform--provision-dev-infrastructure)
5. [Configure kubectl for EKS](#5-configure-kubectl-for-eks)
6. [Install Cluster Add-ons](#6-install-cluster-add-ons)
7. [Create AWS Secrets Manager Entries](#7-create-aws-secrets-manager-entries)
8. [Install & Configure ArgoCD](#8-install--configure-argocd)
9. [Set Up GitHub Repositories](#9-set-up-github-repositories)
10. [Configure GitHub Actions Secrets](#10-configure-github-actions-secrets)
11. [Bootstrap — First Docker Image Build](#11-bootstrap--first-docker-image-build)
12. [Deploy Services via ArgoCD](#12-deploy-services-via-argocd)
13. [Verify Everything Works](#13-verify-everything-works)
14. [Access URLs Summary](#14-access-urls-summary)
15. [Troubleshooting](#15-troubleshooting)

### Phase 2 — CI/CD with GitHub Actions
16. [Phase 2 Overview](#16-phase-2-overview)
17. [Workflow Architecture](#17-workflow-architecture)
18. [Branch Strategy & Environment Routing](#18-branch-strategy--environment-routing)
19. [How Each Pipeline Runs](#19-how-each-pipeline-runs)
20. [Adding a New Service to CI/CD](#20-adding-a-new-service-to-cicd)
21. [Working with Pull Requests](#21-working-with-pull-requests)
22. [Verify CI/CD End-to-End](#22-verify-cicd-end-to-end)
23. [Troubleshooting CI/CD](#23-troubleshooting-cicd)

### Phase 3 — Monitoring with Prometheus & Grafana
24. [Phase 3 Overview](#24-phase-3-overview)
25. [Deploy the Monitoring Stack](#25-deploy-the-monitoring-stack)
26. [Configure AlertManager](#26-configure-alertmanager)
27. [Expose Application Metrics](#27-expose-application-metrics)
28. [Add ServiceMonitors for Your Services](#28-add-servicemonitors-for-your-services)
29. [Import Grafana Dashboards](#29-import-grafana-dashboards)
30. [Set Up Custom Alert Rules](#30-set-up-custom-alert-rules)
31. [Verify Monitoring End-to-End](#31-verify-monitoring-end-to-end)
32. [Troubleshooting Monitoring](#32-troubleshooting-monitoring)

---

## 1. Prerequisites — Local Machine

### 1.1 Windows Only — Install WSL2 First

Open **PowerShell as Administrator** and run:

```powershell
wsl --install
# Installs WSL2 + Ubuntu by default
# Restart your PC when prompted
```

After restart, open **Ubuntu** from the Start Menu. Set a username and password.
From this point forward, **use the Ubuntu terminal** for all commands.

---

### 1.2 Install Required Tools

| Tool | Version | Mac (Terminal) | Windows WSL2/Ubuntu | Windows Native (PowerShell) |
|------|---------|----------------|---------------------|------------------------------|
| AWS CLI | v2.x | `brew install awscli` | `sudo apt install awscli` or use curl installer below | MSI installer from aws.amazon.com |
| Terraform | >= 1.7 | `brew install terraform` | Use tfenv below | choco install terraform |
| kubectl | >= 1.29 | `brew install kubectl` | Use snap or binary below | choco install kubernetes-cli |
| Helm | >= 3.14 | `brew install helm` | `curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \| bash` | choco install kubernetes-helm |
| Docker Desktop | >= 25 | Download from docker.com | Docker Desktop for Windows (enable WSL2 backend) | Download from docker.com |
| Java JDK | 17 | `brew install openjdk@17` | `sudo apt install openjdk-17-jdk` | `winget install Microsoft.OpenJDK.17` |
| Node.js | 18 LTS | `brew install node@18` | `nvm install 18` (see below) | `winget install OpenJS.NodeJS.LTS` |
| Git | any | `brew install git` | `sudo apt install git` | `winget install Git.Git` |
| jq | any | `brew install jq` | `sudo apt install jq` | `winget install jqlang.jq` |

---

### 1.3 Mac — Install All Tools

```bash
# Install Homebrew if not already installed
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install all tools in one command
brew install awscli terraform kubectl helm openjdk@17 node@18 git jq

# Docker Desktop — download manually from docker.com and install
# After install, start Docker Desktop from Applications
```

---

### 1.4 Windows WSL2 — Install All Tools

Open your **Ubuntu (WSL2)** terminal and run each block:

```bash
# Update package list
sudo apt update && sudo apt upgrade -y

# AWS CLI v2
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
sudo apt install unzip -y
unzip awscliv2.zip
sudo ./aws/install
rm -rf awscliv2.zip aws/

# Terraform (via tfenv for easy version management)
git clone --depth=1 https://github.com/tfutils/tfenv.git ~/.tfenv
echo 'export PATH="$HOME/.tfenv/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
tfenv install 1.7.5
tfenv use 1.7.5

# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -sL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
rm kubectl

# Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Java 17
sudo apt install openjdk-17-jdk -y

# Node.js 18 via nvm
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
source ~/.bashrc
nvm install 18
nvm use 18
nvm alias default 18

# Git and jq
sudo apt install git jq -y

# Docker — use Docker Desktop for Windows with WSL2 backend enabled
# In Docker Desktop: Settings → Resources → WSL Integration → Enable for Ubuntu
# Then verify inside WSL2:
docker --version
```

---

### 1.5 Windows Native — Install via Chocolatey (Alternative)

Open **PowerShell as Administrator**:

```powershell
# Install Chocolatey
Set-ExecutionPolicy Bypass -Scope Process -Force
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# Install all tools
choco install awscli terraform kubernetes-cli kubernetes-helm git jq -y

# Java 17
winget install Microsoft.OpenJDK.17

# Node.js 18
winget install OpenJS.NodeJS.LTS

# Docker Desktop — download from docker.com (GUI installer)
```

---

### 1.6 Verify All Installations

**Mac / WSL2:**
```bash
aws --version          # aws-cli/2.x.x
terraform --version    # Terraform v1.7.x
kubectl version --client --short
helm version --short
docker --version
java --version         # openjdk 17
node --version         # v18.x.x
git --version
jq --version
```

**Windows PowerShell:**
```powershell
aws --version
terraform --version
kubectl version --client
helm version
docker --version
java --version
node --version
git --version
jq --version
```

---

## 2. AWS Account Bootstrap

### 2.1 Create IAM Admin User (Brand-New Account)

This is done in the **AWS Console** — same on Mac and Windows:

1. Log in → IAM → Users → **Create user**
2. Username: `devops-admin`
3. Attach policy: `AdministratorAccess`
4. Create **Access Key** (type: CLI) → **Download CSV**

---

### 2.2 Configure AWS CLI

**Mac / WSL2 (bash):**
```bash
aws configure
# AWS Access Key ID:     <paste from CSV>
# AWS Secret Access Key: <paste from CSV>
# Default region:        us-east-1
# Default output format: json
```

**Windows PowerShell:**
```powershell
aws configure
# Same prompts — paste values from CSV
```

Verify (same on all platforms):
```bash
aws sts get-caller-identity
# Returns: Account, UserId, Arn
```

---

### 2.3 Set Account ID as a Variable

**Mac / WSL2:**
```bash
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export AWS_REGION=us-east-1
echo "Account: $AWS_ACCOUNT_ID | Region: $AWS_REGION"
```

**Windows PowerShell:**
```powershell
$env:AWS_ACCOUNT_ID = (aws sts get-caller-identity --query Account --output text)
$env:AWS_REGION = "us-east-1"
Write-Host "Account: $env:AWS_ACCOUNT_ID | Region: $env:AWS_REGION"
```

> **Tip for Windows students:** Variables set with `$env:` last only for the current PowerShell session. Re-run these lines if you open a new window.

---

## 3. Bootstrap Terraform Remote State

The S3 bucket and DynamoDB table must exist **before** `terraform init`. Create them once manually.

### 3.1 Create S3 Bucket

**Mac / WSL2:**
```bash
# Create bucket
aws s3api create-bucket \
  --bucket pharma-tf-state \
  --region us-east-1

# Enable versioning
aws s3api put-bucket-versioning \
  --bucket pharma-tf-state \
  --versioning-configuration Status=Enabled

# Block all public access
aws s3api put-public-access-block \
  --bucket pharma-tf-state \
  --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

# Enable encryption
aws s3api put-bucket-encryption \
  --bucket pharma-tf-state \
  --server-side-encryption-configuration '{
    "Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]
  }'
```

**Windows PowerShell:**
```powershell
# Create bucket
aws s3api create-bucket `
  --bucket pharma-tf-state `
  --region us-east-1

# Enable versioning
aws s3api put-bucket-versioning `
  --bucket pharma-tf-state `
  --versioning-configuration Status=Enabled

# Block all public access
aws s3api put-public-access-block `
  --bucket pharma-tf-state `
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

# Enable encryption
aws s3api put-bucket-encryption `
  --bucket pharma-tf-state `
  --server-side-encryption-configuration '{\"Rules\":[{\"ApplyServerSideEncryptionByDefault\":{\"SSEAlgorithm\":\"AES256\"}}]}'
```

> **PowerShell note:** Use backtick `` ` `` for line continuation (not `\`). Escape inner quotes with `\"`

---

### 3.2 Create DynamoDB Lock Table

**Mac / WSL2:**
```bash
aws dynamodb create-table \
  --table-name pharma-tf-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1
```

**Windows PowerShell:**
```powershell
aws dynamodb create-table `
  --table-name pharma-tf-lock `
  --attribute-definitions AttributeName=LockID,AttributeType=S `
  --key-schema AttributeName=LockID,KeyType=HASH `
  --billing-mode PAY_PER_REQUEST `
  --region us-east-1
```

Verify (same on all platforms):
```bash
aws s3 ls | grep pharma-tf-state
aws dynamodb describe-table --table-name pharma-tf-lock --query "Table.TableStatus"
```

---

## 4. Terraform — Provision Dev Infrastructure

### 4.1 Navigate to Dev Environment

**Mac / WSL2:**
```bash
cd ~/pharma-devops/terraform/envs/dev
```

**Windows PowerShell:**
```powershell
cd C:\Users\YourName\pharma-devops\terraform\envs\dev
```

**Windows WSL2** (if project is on Windows drive, accessible via `/mnt/c`):
```bash
cd /mnt/c/Users/YourName/pharma-devops/terraform/envs/dev
```

> **Tip:** Clone the project directly inside WSL2 home (`~/pharma-devops`) for best performance on Windows.

---

### 4.2 Create terraform.tfvars

> **Never commit this file to Git** — it's already in `.gitignore`

**Mac / WSL2:**
```bash
cat > terraform.tfvars << 'EOF'
db_password = "PharmaSecure#2024Dev!"
jwt_secret  = "pharma-jwt-super-secret-dev-key-min-32-chars"
EOF
```

**Windows PowerShell:**
```powershell
@"
db_password = "PharmaSecure#2024Dev!"
jwt_secret  = "pharma-jwt-super-secret-dev-key-min-32-chars"
"@ | Out-File -FilePath terraform.tfvars -Encoding utf8
```

Use a strong password (upper + lower + digits + special chars).

---

### 4.3 Initialize, Plan, and Apply

These commands are **identical on all platforms**:

```bash
# Initialize — downloads providers and modules
terraform init

# Preview what will be created
terraform plan -out=tfplan

# Apply — takes 15–25 minutes
terraform apply tfplan
```

Review the plan — you should see:
- 1 VPC, 6 subnets, 1 NAT Gateway, route tables
- 1 EKS cluster + managed node group (2x t3.small)
- 1 RDS PostgreSQL db.t3.micro
- 5 ECR repositories
- IAM roles (IRSA for ESO, GitHub Actions OIDC)
- Secrets Manager entries (dev/pharma/db, dev/pharma/jwt)

> EKS creation is the slowest part — around 15 minutes.

---

### 4.4 Capture Outputs

**Mac / WSL2:**
```bash
terraform output

export EKS_CLUSTER_NAME="pharma-dev-cluster"
export RDS_ENDPOINT=$(terraform output -raw rds_endpoint)
export ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

echo "EKS: $EKS_CLUSTER_NAME"
echo "RDS: $RDS_ENDPOINT"
echo "ECR: $ECR_REGISTRY"
```

**Windows PowerShell:**
```powershell
terraform output

$env:EKS_CLUSTER_NAME = "pharma-dev-cluster"
$env:RDS_ENDPOINT = (terraform output -raw rds_endpoint)
$env:ECR_REGISTRY = "$env:AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com"

Write-Host "EKS: $env:EKS_CLUSTER_NAME"
Write-Host "RDS: $env:RDS_ENDPOINT"
Write-Host "ECR: $env:ECR_REGISTRY"
```

---

## 5. Configure kubectl for EKS

### 5.1 Update kubeconfig

**Mac / WSL2:**
```bash
aws eks update-kubeconfig \
  --region us-east-1 \
  --name pharma-dev-cluster \
  --alias pharma-dev-cluster
```

**Windows PowerShell:**
```powershell
aws eks update-kubeconfig `
  --region us-east-1 `
  --name pharma-dev-cluster `
  --alias pharma-dev-cluster
```

---

### 5.2 Verify Cluster Access

These commands are **identical on all platforms**:

```bash
kubectl get nodes
# Should show 2 nodes: STATUS = Ready

kubectl get nodes -o wide
# Verify instance type: t3.small
```

---

### 5.3 Create Namespaces

**Mac / WSL2:**
```bash
kubectl apply -f ~/pharma-devops/k8s/namespaces.yaml
```

**Windows PowerShell:**
```powershell
kubectl apply -f C:\Users\YourName\pharma-devops\k8s\namespaces.yaml
```

Verify (same everywhere):
```bash
kubectl get namespaces
# Should show: dev, qa, prod, argocd, monitoring, ingress-nginx
```

---

## 6. Install Cluster Add-ons

### 6.1 Add Helm Repositories

Identical on all platforms:
```bash
helm repo add ingress-nginx   https://kubernetes.github.io/ingress-nginx
helm repo add external-secrets https://charts.external-secrets.io
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add argo             https://argoproj.github.io/argo-helm
helm repo update
```

---

### 6.2 Install NGINX Ingress Controller

**Mac / WSL2:**
```bash
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  --values ~/pharma-devops/k8s/ingress/nginx-values.yaml \
  --wait
```

**Windows PowerShell:**
```powershell
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx `
  --namespace ingress-nginx `
  --create-namespace `
  --values C:\Users\YourName\pharma-devops\k8s\ingress\nginx-values.yaml `
  --wait
```

Get the Load Balancer DNS name (wait 2–3 minutes after install):
```bash
kubectl get svc -n ingress-nginx ingress-nginx-controller \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

> **Save this DNS name** — needed for Route 53 or DNS mapping later.

---

### 6.3 Install External Secrets Operator (ESO)

**Mac / WSL2:**
```bash
helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace kube-system \
  --set installCRDs=true \
  --set serviceAccount.annotations."eks\.amazonaws\.com/role-arn"="arn:aws:iam::${AWS_ACCOUNT_ID}:role/pharma-dev-eso-role" \
  --wait
```

**Windows PowerShell:**
```powershell
helm upgrade --install external-secrets external-secrets/external-secrets `
  --namespace kube-system `
  --set installCRDs=true `
  --set "serviceAccount.annotations.eks\.amazonaws\.com/role-arn=arn:aws:iam::$env:AWS_ACCOUNT_ID`:role/pharma-dev-eso-role" `
  --wait
```

Apply manifests:

**Mac / WSL2:**
```bash
kubectl apply -f ~/pharma-devops/k8s/external-secrets/cluster-secret-store.yaml
kubectl apply -f ~/pharma-devops/k8s/external-secrets/dev-external-secrets.yaml
```

**Windows PowerShell:**
```powershell
kubectl apply -f C:\Users\YourName\pharma-devops\k8s\external-secrets\cluster-secret-store.yaml
kubectl apply -f C:\Users\YourName\pharma-devops\k8s\external-secrets\dev-external-secrets.yaml
```

Verify (same everywhere):
```bash
kubectl get clustersecretstore
kubectl get externalsecrets -n dev
# STATUS column should show: SecretSynced
```

---

### 6.4 Install Prometheus + Grafana

**Mac / WSL2:**
```bash
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --values ~/pharma-devops/k8s/monitoring/prometheus-values.yaml \
  --wait \
  --timeout 10m
```

**Windows PowerShell:**
```powershell
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack `
  --namespace monitoring `
  --create-namespace `
  --values C:\Users\YourName\pharma-devops\k8s\monitoring\prometheus-values.yaml `
  --wait `
  --timeout 10m
```

Verify (same everywhere):
```bash
kubectl get pods -n monitoring
# Should show: prometheus, grafana, alertmanager, kube-state-metrics, node-exporter
```

---

## 7. Create AWS Secrets Manager Entries

Terraform creates these automatically. Verify they exist:

```bash
aws secretsmanager list-secrets --region us-east-1 \
  --query 'SecretList[].Name' --output table
# Should show: dev/pharma/db  and  dev/pharma/jwt
```

If missing, create manually:

**Mac / WSL2:**
```bash
aws secretsmanager create-secret \
  --name "dev/pharma/db" \
  --region us-east-1 \
  --secret-string "{
    \"username\": \"pharmaadmin\",
    \"password\": \"PharmaSecure#2024Dev!\",
    \"host\": \"${RDS_ENDPOINT}\",
    \"port\": \"5432\",
    \"dbname\": \"pharmadb\"
  }"

aws secretsmanager create-secret \
  --name "dev/pharma/jwt" \
  --region us-east-1 \
  --secret-string "{\"secret\": \"pharma-jwt-super-secret-dev-key-min-32-chars\"}"
```

**Windows PowerShell:**
```powershell
aws secretsmanager create-secret `
  --name "dev/pharma/db" `
  --region us-east-1 `
  --secret-string "{`"username`":`"pharmaadmin`",`"password`":`"PharmaSecure#2024Dev!`",`"host`":`"$env:RDS_ENDPOINT`",`"port`":`"5432`",`"dbname`":`"pharmadb`"}"

aws secretsmanager create-secret `
  --name "dev/pharma/jwt" `
  --region us-east-1 `
  --secret-string "{`"secret`":`"pharma-jwt-super-secret-dev-key-min-32-chars`"}"
```

---

### 7.1 Initialize Database Schemas

Create schemas in RDS using a temporary Kubernetes pod (same command on all platforms):

```bash
export RDS_ENDPOINT=$(terraform -chdir=~/pharma-devops/terraform/envs/dev output -raw rds_endpoint)

kubectl run psql-init --rm -it \
  --image=postgres:15-alpine \
  --namespace=dev \
  --env="PGPASSWORD=PharmaSecure#2024Dev!" \
  -- psql -h ${RDS_ENDPOINT} -U pharmaadmin -d pharmadb \
  -c "
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS drug_catalog;
GRANT ALL ON SCHEMA auth, drug_catalog TO pharmaadmin;
"
```

**Windows PowerShell — set RDS_ENDPOINT first:**
```powershell
$env:RDS_ENDPOINT = (terraform -chdir=C:\Users\YourName\pharma-devops\terraform\envs\dev output -raw rds_endpoint)
```
Then run the `kubectl run` command above (it works in PowerShell too).

---

## 8. Install & Configure ArgoCD

### 8.1 Install ArgoCD

**Mac / WSL2:**
```bash
kubectl apply -f ~/pharma-devops/argocd/install/argocd-namespace.yaml

helm upgrade --install argocd argo/argo-cd \
  --namespace argocd \
  --set server.service.type=ClusterIP \
  --wait
```

**Windows PowerShell:**
```powershell
kubectl apply -f C:\Users\YourName\pharma-devops\argocd\install\argocd-namespace.yaml

helm upgrade --install argocd argo/argo-cd `
  --namespace argocd `
  --set server.service.type=ClusterIP `
  --wait
```

---

### 8.2 Get ArgoCD Admin Password

**Mac / WSL2:**
```bash
kubectl get secret argocd-initial-admin-secret \
  -n argocd \
  -o jsonpath='{.data.password}' | base64 -d
echo ""
```

**Windows PowerShell:**
```powershell
$encoded = kubectl get secret argocd-initial-admin-secret `
  -n argocd -o jsonpath='{.data.password}'
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($encoded))
```

---

### 8.3 Port-Forward ArgoCD UI

Same on all platforms:
```bash
kubectl port-forward svc/argocd-server -n argocd 8080:443
```

Open browser: `https://localhost:8080`
- Username: `admin`
- Password: (from step 8.2)

> On Windows, run this in a separate terminal window and keep it open.

---

### 8.4 Install ArgoCD CLI (Optional)

**Mac:**
```bash
brew install argocd
```

**Windows — download binary:**
1. Go to: https://github.com/argoproj/argo-cd/releases/latest
2. Download `argocd-windows-amd64.exe`
3. Rename to `argocd.exe`
4. Move to a folder in your PATH (e.g., `C:\Windows\System32`)

**WSL2:**
```bash
curl -sSL -o /usr/local/bin/argocd \
  https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
chmod +x /usr/local/bin/argocd
```

Login:
```bash
argocd login localhost:8080 \
  --username admin \
  --password <password-from-8.2> \
  --insecure
```

---

### 8.5 Apply ArgoCD Project and Applications

**Mac / WSL2:**
```bash
kubectl apply -f ~/pharma-devops/argocd/projects/pharma-project.yaml
kubectl apply -f ~/pharma-devops/argocd/apps/dev/pharma-dev-app.yaml
```

**Windows PowerShell:**
```powershell
kubectl apply -f C:\Users\YourName\pharma-devops\argocd\projects\pharma-project.yaml
kubectl apply -f C:\Users\YourName\pharma-devops\argocd\apps\dev\pharma-dev-app.yaml
```

> **Before applying:** Edit `pharma-dev-app.yaml` and update `repoURL` to your actual GitHub pharma-helm-charts URL.

---

## 9. Set Up GitHub Repositories

### 9.1 Create Two Repositories on GitHub

Log in to GitHub and create **two new repositories**:

| Repository | Visibility | Purpose |
|-----------|-----------|---------|
| `pharma-devops` | Private | Microservice source code + GitHub Actions workflows |
| `pharma-helm-charts` | Private | Helm charts + per-env values (ArgoCD watches this) |

---

### 9.2 Push Source Code — pharma-devops

**Mac / WSL2:**
```bash
cd ~/pharma-devops

git init
git checkout -b main
git add .
git commit -m "Initial commit: pharma DevOps project"
git remote add origin https://github.com/YOUR_ORG/pharma-devops.git
git push -u origin main

# Create develop branch
git checkout -b develop
git push -u origin develop
```

**Windows PowerShell:**
```powershell
cd C:\Users\YourName\pharma-devops

git init
git checkout -b main
git add .
git commit -m "Initial commit: pharma DevOps project"
git remote add origin https://github.com/YOUR_ORG/pharma-devops.git
git push -u origin main

git checkout -b develop
git push -u origin develop
```

---

### 9.3 Push Helm Charts — pharma-helm-charts

**Mac / WSL2:**
```bash
mkdir -p ~/pharma-helm-charts
cp -r ~/pharma-devops/helm-charts/* ~/pharma-helm-charts/

cd ~/pharma-helm-charts
git init
git checkout -b main
git add .
git commit -m "Initial Helm charts"
git remote add origin https://github.com/YOUR_ORG/pharma-helm-charts.git
git push -u origin main
```

**Windows PowerShell:**
```powershell
New-Item -ItemType Directory -Path C:\Users\YourName\pharma-helm-charts -Force
Copy-Item -Recurse C:\Users\YourName\pharma-devops\helm-charts\* `
  C:\Users\YourName\pharma-helm-charts\

cd C:\Users\YourName\pharma-helm-charts
git init
git checkout -b main
git add .
git commit -m "Initial Helm charts"
git remote add origin https://github.com/YOUR_ORG/pharma-helm-charts.git
git push -u origin main
```

---

## 10. Configure GitHub Actions Secrets

### 10.1 Set Up AWS OIDC (No Static Keys)

GitHub Actions uses OIDC to get short-lived AWS credentials automatically. No access keys stored anywhere.

**Step 1 — Create OIDC provider in AWS (run once):**

**Mac / WSL2:**
```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

**Windows PowerShell:**
```powershell
aws iam create-open-id-connect-provider `
  --url https://token.actions.githubusercontent.com `
  --client-id-list sts.amazonaws.com `
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

**Step 2 — Create IAM role for GitHub Actions:**

**Mac / WSL2:**
```bash
export GITHUB_ORG=YOUR_GITHUB_ORG_OR_USERNAME

cat > /tmp/github-oidc-trust.json << EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Federated": "arn:aws:iam::${AWS_ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"
    },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
      },
      "StringLike": {
        "token.actions.githubusercontent.com:sub": "repo:${GITHUB_ORG}/pharma-devops:*"
      }
    }
  }]
}
EOF

aws iam create-role \
  --role-name pharma-github-actions-role \
  --assume-role-policy-document file:///tmp/github-oidc-trust.json

aws iam attach-role-policy \
  --role-name pharma-github-actions-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser

echo "Role ARN: arn:aws:iam::${AWS_ACCOUNT_ID}:role/pharma-github-actions-role"
```

**Windows PowerShell:**
```powershell
$GITHUB_ORG = "YOUR_GITHUB_ORG_OR_USERNAME"

$trustPolicy = @"
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Federated": "arn:aws:iam::$env:AWS_ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
    },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
      },
      "StringLike": {
        "token.actions.githubusercontent.com:sub": "repo:${GITHUB_ORG}/pharma-devops:*"
      }
    }
  }]
}
"@

$trustPolicy | Out-File -FilePath "$env:TEMP\github-oidc-trust.json" -Encoding utf8

aws iam create-role `
  --role-name pharma-github-actions-role `
  --assume-role-policy-document "file://$env:TEMP\github-oidc-trust.json"

aws iam attach-role-policy `
  --role-name pharma-github-actions-role `
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser

Write-Host "Role ARN: arn:aws:iam::$env:AWS_ACCOUNT_ID`:role/pharma-github-actions-role"
```

---

### 10.2 Add Secrets to GitHub Repository

Go to: **GitHub → pharma-devops → Settings → Secrets and variables → Actions → New repository secret**

Add these three secrets:

| Secret Name | Value |
|------------|-------|
| `AWS_GITHUB_ACTIONS_ROLE_ARN` | `arn:aws:iam::YOUR_ACCOUNT_ID:role/pharma-github-actions-role` |
| `HELM_CHARTS_TOKEN` | GitHub Personal Access Token (see step 10.3) |
| `HELM_CHARTS_REPO` | `https://github.com/YOUR_ORG/pharma-helm-charts.git` |

---

### 10.3 Create GitHub Personal Access Token

1. GitHub → (your avatar, top right) → Settings
2. Developer settings → Personal access tokens → **Fine-grained tokens**
3. Click **Generate new token**
   - Name: `helm-charts-write`
   - Expiration: 90 days
   - Repository access: Only `pharma-helm-charts`
   - Permissions → Contents: **Read and Write**
4. Click **Generate token** → Copy and paste into `HELM_CHARTS_TOKEN` secret

---

### 10.4 Set Up GitHub Environments (Prod Approval Gate)

Go to: **GitHub → pharma-devops → Settings → Environments**

Create three environments:

**dev** — click New environment → name: `dev` → no protection rules → Save

**qa** — click New environment → name: `qa` → no protection rules → Save

**prod** — click New environment → name: `prod`
- Enable **Required reviewers** → add yourself
- Enable **Deployment branches** → Selected branches → `main` only
- Save protection rules

> When a pipeline runs on the `main` branch, the prod deploy job will pause and wait for your approval in the GitHub UI before proceeding.

---

## 11. Bootstrap — First Docker Image Build

GitHub Actions hasn't run yet, so no images exist in ECR. Do a one-time manual build.

### 11.1 ECR Login

**Mac / WSL2:**
```bash
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com
```

**Windows PowerShell:**
```powershell
$password = aws ecr get-login-password --region us-east-1
$password | docker login --username AWS --password-stdin `
  "$env:AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com"
```

---

### 11.2 Build and Push All Services

**Mac / WSL2 — save as `build-all.sh` and run:**
```bash
#!/bin/bash
set -e

REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com"
TAG="v1.0.0"
PROJECT="${HOME}/pharma-devops/services"

services=(
  auth-service api-gateway drug-catalog-service
  notification-service pharma-ui
)

for svc in "${services[@]}"; do
  echo "===== Building $svc ====="
  docker build -t "${REGISTRY}/${svc}:${TAG}" "${PROJECT}/${svc}"
  docker push "${REGISTRY}/${svc}:${TAG}"
  echo "Pushed: ${REGISTRY}/${svc}:${TAG}"
done

echo "===== All images pushed! ====="
```

```bash
chmod +x build-all.sh
./build-all.sh
```

**Windows PowerShell — save as `build-all.ps1` and run:**
```powershell
$REGISTRY = "$env:AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com"
$TAG = "v1.0.0"
$PROJECT = "C:\Users\YourName\pharma-devops\services"

$services = @(
  "auth-service", "api-gateway", "drug-catalog-service",
  "notification-service", "pharma-ui"
)

foreach ($svc in $services) {
  Write-Host "===== Building $svc =====" -ForegroundColor Cyan
  docker build -t "${REGISTRY}/${svc}:${TAG}" "${PROJECT}\${svc}"
  docker push "${REGISTRY}/${svc}:${TAG}"
  Write-Host "Pushed: ${REGISTRY}/${svc}:${TAG}" -ForegroundColor Green
}

Write-Host "===== All images pushed! =====" -ForegroundColor Green
```

```powershell
.\build-all.ps1
```

> Initial build takes 15–20 minutes due to Maven dependency downloads.

---

### 11.3 Update Helm Values with ECR Registry and Tag

Edit each file in `pharma-helm-charts/envs/dev/values-*.yaml`.

The `image` section should look like this (replace `123456789012` with your account ID):

```yaml
image:
  repository: 123456789012.dkr.ecr.us-east-1.amazonaws.com/auth-service
  tag: v1.0.0
  pullPolicy: IfNotPresent
```

**Mac / WSL2 — update all dev values files at once:**
```bash
REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com"

for f in ~/pharma-helm-charts/envs/dev/values-*.yaml; do
  sed -i "s|repository:.*|repository: ${REGISTRY}/$(basename $f values-.yaml | sed 's/values-//')|" "$f"
  sed -i "s|tag:.*|tag: v1.0.0|" "$f"
done
```

**Windows PowerShell:**
```powershell
$REGISTRY = "$env:AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com"

Get-ChildItem C:\Users\YourName\pharma-helm-charts\envs\dev\values-*.yaml | ForEach-Object {
  $svc = $_.BaseName -replace "^values-", ""
  (Get-Content $_.FullName) `
    -replace "repository:.*", "repository: $REGISTRY/$svc" `
    -replace "tag:.*", "tag: v1.0.0" |
    Set-Content $_.FullName
}
```

Then commit and push:
```bash
cd ~/pharma-helm-charts    # Mac/WSL2
# cd C:\Users\YourName\pharma-helm-charts   # Windows

git add .
git commit -m "Bootstrap: set initial image tags v1.0.0 for dev"
git push
```

---

## 12. Deploy Services via ArgoCD

### 12.1 Apply RBAC Manifests

**Mac / WSL2:**
```bash
kubectl apply -f ~/pharma-devops/k8s/rbac/cluster-roles.yaml
kubectl apply -f ~/pharma-devops/k8s/rbac/dev-role.yaml
kubectl apply -f ~/pharma-devops/k8s/rbac/rolebindings.yaml
```

**Windows PowerShell:**
```powershell
kubectl apply -f C:\Users\YourName\pharma-devops\k8s\rbac\cluster-roles.yaml
kubectl apply -f C:\Users\YourName\pharma-devops\k8s\rbac\dev-role.yaml
kubectl apply -f C:\Users\YourName\pharma-devops\k8s\rbac\rolebindings.yaml
```

---

### 12.2 Sync ArgoCD Application

**Via UI** (same on all platforms):
1. Open `https://localhost:8080` (keep port-forward running)
2. Click `pharma-dev` application
3. Click **Sync** → **Synchronize**

**Via CLI** (same on all platforms):
```bash
argocd app sync pharma-dev
argocd app wait pharma-dev --timeout 300
```

---

### 12.3 Watch Pods Start

Same on all platforms:
```bash
kubectl get pods -n dev -w
```

Expected final state:
```
NAME                                  READY   STATUS    
auth-service-xxx                      1/1     Running   
api-gateway-xxx                       1/1     Running   
drug-catalog-service-xxx              1/1     Running   
notification-service-xxx              1/1     Running   
pharma-ui-xxx                         1/1     Running   
```

---

## 13. Verify Everything Works

### 13.1 Check All Resources

Same on all platforms:
```bash
kubectl get pods -n dev
kubectl get svc -n dev
kubectl get ingress -n dev
```

---

### 13.2 Test Auth Service

**Mac / WSL2:**
```bash
# Start port-forward in background
kubectl port-forward svc/auth-service -n dev 8081:8081 &

# Health check
curl http://localhost:8081/actuator/health

# Login test
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin@pharma.com", "password": "Admin@123"}'
```

**Windows PowerShell:**
```powershell
# Start port-forward (open a separate terminal for this)
kubectl port-forward svc/auth-service -n dev 8081:8081

# In another terminal:
# Health check
Invoke-RestMethod -Uri http://localhost:8081/actuator/health

# Login test
$body = '{"username": "admin@pharma.com", "password": "Admin@123"}'
Invoke-RestMethod -Uri http://localhost:8081/api/auth/login `
  -Method POST `
  -ContentType "application/json" `
  -Body $body
```

---

### 13.3 Test GitHub Actions Pipeline

**Mac / WSL2:**
```bash
cd ~/pharma-devops
git checkout -b feature/test-gha-pipeline
echo "# pipeline test" >> services/auth-service/README.md
git add .
git commit -m "test: trigger GitHub Actions pipeline"
git push origin feature/test-gha-pipeline
```

**Windows PowerShell:**
```powershell
cd C:\Users\YourName\pharma-devops
git checkout -b feature/test-gha-pipeline
Add-Content services\auth-service\README.md "# pipeline test"
git add .
git commit -m "test: trigger GitHub Actions pipeline"
git push origin feature/test-gha-pipeline
```

Then go to: **GitHub → pharma-devops → Actions tab**

You should see:
- `CI/CD — Auth Service` workflow triggered
- Jobs: Build & Test → OWASP Scan → Docker Build & Push → Deploy to dev
- ArgoCD auto-syncs new image tag within 3 minutes

---

### 13.4 Verify Monitoring

Same on all platforms — start port-forwards in separate terminals:

```bash
# Grafana
kubectl port-forward svc/monitoring-grafana -n monitoring 3000:80
# Open: http://localhost:3000   Username: admin

# Get Grafana password
kubectl get secret -n monitoring monitoring-grafana \
  -o jsonpath="{.data.admin-password}" | base64 -d

# Prometheus
kubectl port-forward svc/monitoring-kube-prometheus-stack-prometheus -n monitoring 9090:9090
# Open: http://localhost:9090
# Try query: up{namespace="dev"}
```

**Windows PowerShell — get Grafana password:**
```powershell
$encoded = kubectl get secret -n monitoring monitoring-grafana `
  -o jsonpath="{.data.admin-password}"
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($encoded))
```

---

## 14. Access URLs Summary

| Service | URL / How to Access |
|---------|---------------------|
| **ArgoCD UI** | port-forward 8080 → `https://localhost:8080` |
| **Pharma UI** | `https://app.dev.yourdomain.com` (via NGINX Ingress) |
| **API Gateway** | `https://api.dev.yourdomain.com` |
| **Grafana** | port-forward 3000 → `http://localhost:3000` |
| **Prometheus** | port-forward 9090 → `http://localhost:9090` |

### Optional: DNS Setup

1. Get NLB DNS:
   ```bash
   kubectl get svc -n ingress-nginx ingress-nginx-controller \
     -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
   ```
2. In Route 53 (or any DNS provider) → create CNAME:
   - `*.dev.yourdomain.com` → NLB DNS hostname

---

## 15. Troubleshooting

### Pod in CrashLoopBackOff

```bash
kubectl logs <pod-name> -n dev --previous
kubectl describe pod <pod-name> -n dev
```

Common causes:
- DB connection failure → check RDS security group allows EKS node SG on port 5432
- Secret not found → `kubectl get externalsecrets -n dev`
- Wrong image tag → check image pull errors in `kubectl describe pod`

---

### ECR Image Pull Error

```bash
aws ecr describe-images --repository-name auth-service --region us-east-1

aws iam list-attached-role-policies --role-name pharma-dev-node-role
# Verify AmazonEC2ContainerRegistryReadOnly is attached
```

---

### ArgoCD App Stuck in Progressing

```bash
argocd app get pharma-dev --refresh
argocd app sync pharma-dev --force
```

---

### ESO Not Syncing Secrets

```bash
kubectl describe clustersecretstore aws-secrets-manager
kubectl describe externalsecret -n dev
kubectl logs -n kube-system -l app.kubernetes.io/name=external-secrets
```

---

### GitHub Actions OIDC Error

```
Error: Could not assume role with OIDC
```

Check:
1. OIDC provider exists in AWS IAM → Identity providers
2. Role trust policy has correct GitHub org/repo in the `sub` condition
3. Secret `AWS_GITHUB_ACTIONS_ROLE_ARN` is set correctly in GitHub repo secrets

---

### Terraform State Locked

**Mac / WSL2:**
```bash
aws dynamodb delete-item \
  --table-name pharma-tf-lock \
  --key '{"LockID": {"S": "pharma-tf-state/envs/dev/terraform.tfstate"}}'
```

**Windows PowerShell:**
```powershell
aws dynamodb delete-item `
  --table-name pharma-tf-lock `
  --key '{\"LockID\": {\"S\": \"pharma-tf-state/envs/dev/terraform.tfstate\"}}'
```

---

### Windows-Specific Issues

| Problem | Solution |
|---------|---------|
| `\r\n` line ending errors in scripts | Run: `git config --global core.autocrlf false` before cloning |
| Docker not found in WSL2 | Docker Desktop → Settings → WSL Integration → enable Ubuntu |
| `kubectl` not found in WSL2 | Install kubectl inside WSL2 (Step 1.4), not just on Windows |
| Permission denied on `.sh` script | Run `chmod +x script.sh` inside WSL2 |
| AWS CLI not found in WSL2 | Install AWS CLI inside WSL2 (Step 1.4) — Windows install doesn't carry over |
| Terraform path issue on Windows | Use `terraform.exe` or ensure PATH is set via System environment variables |

---

## Quick Reference: Implementation Order

```
Day 1 — Infrastructure (Phase 1)
  ✅ Step 1   Install tools (Mac: brew | Windows: WSL2 recommended)
  ✅ Step 2   Create IAM user, configure AWS CLI
  ✅ Step 3   Create S3 bucket + DynamoDB table (Terraform state)
  ✅ Step 4   Terraform init → plan → apply (15–25 min)
  ✅ Step 5   Configure kubectl, create namespaces

Day 1 — Cluster Setup (Phase 1)
  ✅ Step 6   Install NGINX Ingress, ESO via Helm
  ✅ Step 7   Verify Secrets Manager, initialise RDS schemas

Day 2 — GitOps + Deploy (Phase 1)
  ✅ Step 8   Install ArgoCD, get password, apply AppProject
  ✅ Step 9   Create GitHub repos, push source code + Helm charts
  ✅ Step 10  Configure OIDC, add GitHub secrets, set up environments
  ✅ Step 11  Manual docker build + push (bootstrap, first time only)
  ✅ Step 12  ArgoCD sync → watch 5 pods start
  ✅ Step 13  Verify services, test end-to-end

Day 3 — CI/CD (Phase 2)
  ✅ Step 16  Understand workflow architecture (3 reusable + 11 triggers)
  ✅ Step 17  Configure GitHub Environments (dev / qa / prod)
  ✅ Step 18  Trigger first automated pipeline on feature branch
  ✅ Step 19  Verify image in ECR + ArgoCD auto-sync to dev
  ✅ Step 20  Open a PR → observe PR checks (test + OWASP scan)
  ✅ Step 21  Merge to develop → verify deploy to qa
  ✅ Step 22  Merge to main → approve prod gate → verify prod deploy

Day 4 — Monitoring (Phase 3)
  ✅ Step 24  Deploy Prometheus + Grafana + AlertManager via Helm
  ✅ Step 25  Configure AlertManager SMTP secret
  ✅ Step 26  Verify Spring Boot /actuator/prometheus endpoints
  ✅ Step 27  Apply ServiceMonitor manifests for all services
  ✅ Step 28  Import Kubernetes + JVM Grafana dashboards
  ✅ Step 29  Configure custom alert rules
  ✅ Step 30  Trigger test alert, verify email notification
```

---

---

# Phase 2 — CI/CD with GitHub Actions

---

## 16. Phase 2 Overview

> **Goal:** Every push to a service directory automatically tests, builds a Docker image, pushes it to ECR, and updates the Helm values file — triggering ArgoCD to deploy the new version.

### What Phase 1 already set up

Phase 1 gave you:
- 5 services running on EKS, deployed by ArgoCD from a Helm charts repo
- GitHub OIDC role (`pharma-github-actions-role`) so Actions can push to ECR without static AWS keys
- Three GitHub Environments (`dev`, `qa`, `prod`) with a manual approval gate on prod
- GitHub secrets: `AWS_GITHUB_ACTIONS_ROLE_ARN`, `HELM_CHARTS_TOKEN`, `HELM_CHARTS_REPO`

### What Phase 2 activates

The workflows in `.github/workflows/` are already written. Phase 2 is about:
1. Understanding how they work
2. Triggering them by making a real code change
3. Watching the full pipeline run end-to-end
4. Verifying ArgoCD picks up the new image automatically

### CI/CD Flow Diagram

```
Developer Push (feature/**, develop, main)
         │
         ▼
  GitHub Actions Trigger
  (only when service files change)
         │
         ▼
  ┌──────────────────────────────────────┐
  │  Reusable Workflow                   │
  │  Job 1: Build & Test (Maven/npm)     │
  │  Job 2: OWASP Scan (non-blocking)   │
  │  Job 3: Docker Build → ECR Push     │
  │  Job 4: Update Helm values → push   │
  └──────────────────────────────────────┘
         │
         ▼
  pharma-helm-charts repo (values-*.yaml updated)
         │
         ▼
  ArgoCD detects Git change (polls every 3 min)
         │
         ▼
  Auto-sync (dev/qa) / Manual sync (prod)
         │
         ▼
  kubectl rolling update → new pods running
```

---

## 17. Workflow Architecture

The project uses a **DRY (Don't Repeat Yourself)** pattern with reusable workflows.

### File layout

```
.github/workflows/
├── _reusable-springboot.yml   # Template for Spring Boot services
├── _reusable-nodejs.yml       # Template for notification-service
├── _reusable-react.yml        # Template for pharma-ui
│
├── ci-api-gateway.yml         # Thin trigger → calls _reusable-springboot.yml
├── ci-auth-service.yml        # Thin trigger → calls _reusable-springboot.yml
├── ci-drug-catalog-service.yml # Thin trigger → calls _reusable-springboot.yml
├── ci-notification-service.yml  # Calls _reusable-nodejs.yml
└── ci-pharma-ui.yml             # Calls _reusable-react.yml
```

### Per-service trigger file (example)

`ci-auth-service.yml` — only runs when auth-service files change:

```yaml
on:
  push:
    branches: [main, develop, "feature/**", "bugfix/**", "hotfix/**"]
    paths:
      - "services/auth-service/**"
      - ".github/workflows/ci-auth-service.yml"
  pull_request:
    branches: [main, develop]
    paths:
      - "services/auth-service/**"

jobs:
  ci-cd:
    uses: ./.github/workflows/_reusable-springboot.yml
    with:
      service-name: auth-service
      service-path: services/auth-service
    secrets: inherit
```

> **Teaching point:** The `paths:` filter means changing `services/pharma-ui/` will not trigger the `auth-service` pipeline. Independent pipelines — independent deployments.

### Reusable Spring Boot template — 4 jobs

```
Job 1: test           → mvn -B verify (blocks if tests fail)
Job 2: security-scan  → OWASP dependency-check (continue-on-error: true)
Job 3: docker-build-push → OIDC → ECR login → build → push :sha + :latest
Job 4: deploy         → clone helm-charts → sed image tag → git push [skip ci]
```

### Reusable Node.js template — 3 jobs

```
Job 1: test           → npm ci + npm test
Job 2: docker-build-push → same OIDC + ECR pattern
Job 3: deploy         → same Helm values update
```

### Reusable React template — 3 jobs

```
Job 1: build-and-test → npm ci + npm test + npm run build (CI=false for warnings)
                      → uploads build/ artifact
Job 2: docker-build-push → multi-stage Docker build → nginx image → ECR
Job 3: deploy         → same Helm values update
```

---

## 18. Branch Strategy & Environment Routing

| Branch | Target Environment | ArgoCD Sync |
|--------|-------------------|-------------|
| `feature/**`, `bugfix/**`, `hotfix/**` | `dev` | Automatic |
| `develop` | `qa` | Automatic |
| `main` | `prod` | **Manual — requires approval** |

The routing logic lives inside the reusable workflows:

```yaml
environment: ${{ github.ref_name == 'main' && 'prod' || github.ref_name == 'develop' && 'qa' || 'dev' }}
```

### Image tag strategy

| Tag | Example | Purpose |
|-----|---------|---------|
| Short commit SHA | `a3f7c2b1` | Pinned, immutable — used in Helm values |
| `latest` | `latest` | Convenience tag — always points to newest build |

The Helm values file stores the SHA tag (not `latest`) so ArgoCD always knows exactly which commit is running:

```yaml
# helm-charts/envs/dev/values-auth-service.yaml
image:
  repository: 123456789012.dkr.ecr.us-east-1.amazonaws.com/auth-service
  tag: "a3f7c2b1"    # ← updated automatically by GitHub Actions
  pullPolicy: IfNotPresent
```

---

## 19. How Each Pipeline Runs

### 19.1 Pull Request (test only — no deploy)

On a PR targeting `main` or `develop`:
- Job 1 (test) runs → results posted as PR check
- Job 2 (OWASP scan) runs → non-blocking, results uploaded as artifact
- Jobs 3 & 4 are **skipped** (guarded by `if: github.event_name == 'push'`)

The PR shows green/red checks. Merge is blocked if tests fail.

### 19.2 Push to feature branch (build + deploy dev)

1. Test job passes
2. Docker image built, tagged `<8-char-sha>` and `latest`, pushed to ECR
3. Deploy job clones `pharma-helm-charts`, updates `envs/dev/values-<service>.yaml`:
   ```
   tag: "a3f7c2b1"
   ```
4. Commits with `[skip ci]` to prevent infinite loop, pushes
5. ArgoCD detects the change within 3 minutes → rolling update in `dev` namespace

### 19.3 Push to develop (build + deploy qa)

Same as above, but updates `envs/qa/values-<service>.yaml`.
ArgoCD syncs the `pharma-qa` app automatically.

### 19.4 Push to main (build + deploy prod — with approval gate)

1. Test + build + push run as normal
2. Deploy job pauses at the **GitHub Environment protection rule** for `prod`
3. A notification appears in GitHub Actions UI: **"Waiting for approval"**
4. Reviewer clicks **Review deployments** → **Approve and deploy**
5. Helm values in `envs/prod/` updated → ArgoCD prod app synced manually (or auto if configured)

---

## 20. Adding a New Service to CI/CD

When a new service is added (e.g., `billing-service`), follow this checklist:

### 20.1 Create the trigger workflow

```bash
cp .github/workflows/ci-auth-service.yml \
   .github/workflows/ci-billing-service.yml
```

Edit `ci-billing-service.yml`:
```yaml
name: "CI/CD — Billing Service"

on:
  push:
    branches: [main, develop, "feature/**", "bugfix/**", "hotfix/**"]
    paths:
      - "services/billing-service/**"
      - ".github/workflows/ci-billing-service.yml"
  pull_request:
    branches: [main, develop]
    paths:
      - "services/billing-service/**"

jobs:
  ci-cd:
    uses: ./.github/workflows/_reusable-springboot.yml
    with:
      service-name: billing-service
      service-path: services/billing-service
    secrets: inherit
```

### 20.2 Create ECR repository

Add to Terraform ECR module (`terraform/modules/ecr/main.tf`):

```hcl
locals {
  services = [
    "auth-service", "api-gateway", "drug-catalog-service",
    "notification-service", "pharma-ui",
    "billing-service"    # ← new
  ]
}
```

Apply:
```bash
cd terraform/envs/dev
terraform plan -out=tfplan
terraform apply tfplan
```

### 20.3 Add Helm values files

```bash
# Copy an existing values file as template
cp helm-charts/envs/dev/values-auth-service.yaml \
   helm-charts/envs/dev/values-billing-service.yaml

# Edit the new file — set service name, port, image repository
```

Do the same for `envs/qa/` and `envs/prod/`.

> **Current 5 services with Helm values:** `auth-service`, `api-gateway`, `drug-catalog-service`, `notification-service`, `pharma-ui`

### 20.4 Commit everything

```bash
git add .github/workflows/ci-billing-service.yml \
        helm-charts/envs/dev/values-billing-service.yaml \
        helm-charts/envs/qa/values-billing-service.yaml \
        helm-charts/envs/prod/values-billing-service.yaml
git commit -m "feat: add billing-service CI/CD pipeline and Helm values"
git push
```

The pipeline triggers on the first push that touches `services/billing-service/**`.

---

## 21. Working with Pull Requests

### Standard feature workflow

```bash
# Create feature branch
git checkout -b feature/add-drug-search

# Make changes
# ... edit code ...

# Push — triggers CI tests only (no deploy)
git push origin feature/add-drug-search

# Open PR on GitHub → review PR checks
# Merge to develop → triggers deploy to QA
# Merge to main   → triggers deploy to prod (with approval gate)
```

### What the PR reviewer sees

In the PR, under **Checks**:
- `CI/CD — Drug Catalog Service / Build & Test` — must be green
- `CI/CD — Drug Catalog Service / OWASP Dependency Check` — informational

### Hotfix workflow (skip QA, go straight to prod)

```bash
git checkout -b hotfix/fix-auth-token main
# ... fix ...
git push origin hotfix/fix-auth-token
# PR → main (skips develop/QA intentionally)
# Approve prod gate → deploy
# Cherry-pick back to develop:
git checkout develop && git cherry-pick <sha> && git push
```

---

## 22. Verify CI/CD End-to-End

### 22.1 Trigger the first automated pipeline

**Mac / WSL2:**
```bash
cd ~/pharma-devops
git checkout -b feature/test-cicd-pipeline
echo "# CI test $(date)" >> services/auth-service/README.md
git add .
git commit -m "test: trigger GitHub Actions CI pipeline"
git push origin feature/test-cicd-pipeline
```

**Windows PowerShell:**
```powershell
cd C:\Users\YourName\pharma-devops
git checkout -b feature/test-cicd-pipeline
Add-Content services\auth-service\README.md "# CI test $(Get-Date)"
git add .
git commit -m "test: trigger GitHub Actions CI pipeline"
git push origin feature/test-cicd-pipeline
```

### 22.2 Watch the pipeline

Go to: **GitHub → pharma-devops → Actions tab**

You should see `CI/CD — Auth Service` running. Click it to watch jobs:

```
Build & Test           → ~3 min (Maven compile + tests)
OWASP Dependency Check → ~5 min (runs in parallel, non-blocking)
Docker Build & Push    → ~4 min (multi-stage build + ECR push)
Deploy → dev           → ~1 min (Helm values update + git push)
```

### 22.3 Verify image in ECR

```bash
aws ecr describe-images \
  --repository-name auth-service \
  --region us-east-1 \
  --query 'sort_by(imageDetails,& imagePushedAt)[-1]' \
  --output table
```

You should see the new image with a recent `imagePushedAt` timestamp.

### 22.4 Verify ArgoCD auto-syncs

```bash
# Watch ArgoCD sync status
argocd app get pharma-dev --refresh

# Watch pods rolling update
kubectl get pods -n dev -w
```

ArgoCD detects the Helm values change within ~3 minutes and triggers a rolling update. The old pod stays running until the new one passes health checks.

### 22.5 Verify the new image is running

```bash
# Check the image tag on the running pod
kubectl get pod -n dev -l app=auth-service \
  -o jsonpath='{.items[0].spec.containers[0].image}'
```

The output should contain your short SHA tag (e.g., `a3f7c2b1`).

---

## 23. Troubleshooting CI/CD

### OIDC "Could not assume role" error

```
Error: Could not assume role with OIDC: Not authorized to perform sts:AssumeRoleWithWebIdentity
```

Checklist:
1. OIDC provider exists: **AWS Console → IAM → Identity providers** — look for `token.actions.githubusercontent.com`
2. Trust policy has your exact GitHub org/repo:
   ```bash
   aws iam get-role --role-name pharma-github-actions-role \
     --query 'Role.AssumeRolePolicyDocument'
   ```
   Verify `sub` condition matches `repo:YOUR_ORG/pharma-devops:*`
3. Secret `AWS_GITHUB_ACTIONS_ROLE_ARN` is set in **GitHub → repo → Settings → Secrets → Actions**

---

### Helm values update fails — file not found

```
ERROR: helm-charts/envs/dev/values-auth-service.yaml not found
```

The `pharma-helm-charts` repo may not have the values file for that environment.

```bash
# Check what exists in the helm-charts repo
ls ~/pharma-helm-charts/envs/dev/
```

Create the missing file by copying from another service and adjusting the service name and port.

---

### Pipeline loops — triggered by its own commit

This is prevented by the `[skip ci]` tag in the deploy commit message:

```bash
git commit -m "ci: deploy auth-service:a3f7c2b1 to dev [skip ci]"
```

If a loop occurs, verify the commit message includes `[skip ci]` exactly.

---

### Docker build fails — out of disk on runner

GitHub-hosted runners have ~14 GB disk. Maven dependency cache (`actions/setup-java` with `cache: maven`) avoids re-downloading dependencies. If builds still fail:

```yaml
# Add this step before Maven build to free space
- name: Free disk space
  run: |
    sudo rm -rf /usr/share/dotnet
    sudo rm -rf /usr/local/lib/android
```

---

### ECR push fails — access denied

Verify the IAM role has `AmazonEC2ContainerRegistryPowerUser` attached:

```bash
aws iam list-attached-role-policies \
  --role-name pharma-github-actions-role
```

---

---

# Phase 3 — Monitoring with Prometheus & Grafana

---

## 24. Phase 3 Overview

> **Goal:** Full observability — metrics from Kubernetes infrastructure and all 11 application services, visualised in Grafana, with AlertManager sending email notifications for critical issues.

### Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| **Prometheus** | via kube-prometheus-stack | Metrics collection, storage (15 days), alerting rules |
| **Grafana** | via kube-prometheus-stack | Dashboards, visualisation |
| **AlertManager** | via kube-prometheus-stack | Alert routing → email |
| **kube-state-metrics** | bundled | K8s object metrics (pods, deployments, etc.) |
| **node-exporter** | bundled | Host-level metrics (CPU, memory, disk) |
| **prom-client** | in services | Exposes `/actuator/prometheus` on Spring Boot and `/metrics` on Node.js |

### Monitoring Architecture

```
Spring Boot services  →  /actuator/prometheus  ←  Prometheus scrapes via ServiceMonitor
Node.js service       →  /metrics              ←  Prometheus scrapes via ServiceMonitor
K8s cluster           →  kube-state-metrics    ←  Prometheus scrapes automatically
EKS nodes             →  node-exporter         ←  Prometheus scrapes automatically
                                  │
                                  ▼
                            Prometheus
                         (stores 15 days)
                                  │
                    ┌─────────────┴──────────────┐
                    ▼                             ▼
                 Grafana                    AlertManager
              (dashboards)               (email → devops@pharma.com)
```

---

## 25. Deploy the Monitoring Stack

### 25.1 Add Prometheus Helm repo (already done in Phase 1 step 6.1)

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
```

### 25.2 Deploy the full stack

The configuration file is already written at `k8s/monitoring/prometheus-values.yaml`.

**Mac / WSL2:**
```bash
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --values ~/pharma-devops/k8s/monitoring/prometheus-values.yaml \
  --wait \
  --timeout 10m
```

**Windows PowerShell:**
```powershell
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack `
  --namespace monitoring `
  --create-namespace `
  --values C:\Users\YourName\pharma-devops\k8s\monitoring\prometheus-values.yaml `
  --wait `
  --timeout 10m
```

This installs (~5 min):
- Prometheus server with 20 Gi persistent storage
- Grafana with 10 Gi persistent storage
- AlertManager with 5 Gi persistent storage
- kube-state-metrics DaemonSet
- node-exporter DaemonSet

### 25.3 Verify all pods are running

```bash
kubectl get pods -n monitoring
```

Expected output:
```
NAME                                                     READY   STATUS
alertmanager-monitoring-kube-prometheus-alertmanager-0   2/2     Running
monitoring-grafana-xxx                                   3/3     Running
monitoring-kube-prometheus-operator-xxx                  1/1     Running
monitoring-kube-state-metrics-xxx                        1/1     Running
monitoring-prometheus-node-exporter-xxx                  1/1     Running
prometheus-monitoring-kube-prometheus-prometheus-0       2/2     Running
```

---

## 26. Configure AlertManager

### 26.1 Create SMTP secret

The `prometheus-values.yaml` references a plaintext SMTP password (`changeme`). Replace it with a proper K8s secret.

**Mac / WSL2:**
```bash
# Create secret with real SMTP credentials
kubectl create secret generic alertmanager-smtp \
  --namespace monitoring \
  --from-literal=smtp_auth_password="YourSMTPPasswordHere"
```

**Windows PowerShell:**
```powershell
kubectl create secret generic alertmanager-smtp `
  --namespace monitoring `
  --from-literal=smtp_auth_password="YourSMTPPasswordHere"
```

Then update `k8s/monitoring/prometheus-values.yaml` to reference the secret:

```yaml
alertmanager:
  config:
    global:
      smtp_smarthost: "smtp.pharma.com:587"
      smtp_from: "alertmanager@pharma.com"
      smtp_auth_username: "alertmanager@pharma.com"
      smtp_auth_password_file: /etc/alertmanager/secrets/alertmanager-smtp/smtp_auth_password
```

Re-apply:
```bash
helm upgrade monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --values ~/pharma-devops/k8s/monitoring/prometheus-values.yaml \
  --reuse-values
```

### 26.2 Verify AlertManager configuration

```bash
# Port-forward AlertManager UI
kubectl port-forward svc/monitoring-kube-prometheus-alertmanager -n monitoring 9093:9093
# Open: http://localhost:9093
# Click Status → Config to verify SMTP settings loaded
```

---

## 27. Expose Application Metrics

All Spring Boot services already have the Prometheus actuator dependency in their `pom.xml`. Verify:

### 27.1 Spring Boot services — check actuator endpoints

```bash
# Port-forward a service
kubectl port-forward svc/auth-service -n dev 8081:8081 &

# Check metrics endpoint
curl http://localhost:8081/actuator/prometheus | head -30
```

You should see lines like:
```
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",...} 1.23e+08
http_server_requests_seconds_count{...} 42
```

### 27.2 Node.js notification-service — check metrics endpoint

```bash
kubectl port-forward svc/notification-service -n dev 3000:3000 &
curl http://localhost:3000/metrics | head -20
```

### 27.3 Verify all services expose metrics

```bash
for svc in auth-service api-gateway drug-catalog-service; do
  echo -n "=== $svc: "
  kubectl exec -n dev deploy/$svc -- \
    wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -o '"status":"[^"]*"' || echo "unreachable"
done
```

---

## 28. Add ServiceMonitors for Your Services

A `ServiceMonitor` is a Kubernetes CRD (Custom Resource Definition) that tells Prometheus where to scrape metrics from.

### 28.1 Create ServiceMonitor for Spring Boot services

Create `k8s/monitoring/servicemonitors.yaml`:

```yaml
# ServiceMonitor for all Spring Boot services in dev namespace
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: pharma-springboot-services
  namespace: monitoring
  labels:
    release: monitoring  # Must match Prometheus selector
spec:
  namespaceSelector:
    matchNames:
      - dev
  selector:
    matchLabels:
      stack: spring-boot  # Must match service labels in Helm chart
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
      scrapeTimeout: 10s
---
# ServiceMonitor for Node.js notification-service
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: pharma-nodejs-services
  namespace: monitoring
  labels:
    release: monitoring
spec:
  namespaceSelector:
    matchNames:
      - dev
  selector:
    matchLabels:
      stack: nodejs
  endpoints:
    - port: http
      path: /metrics
      interval: 30s
```

Apply:
```bash
kubectl apply -f ~/pharma-devops/k8s/monitoring/servicemonitors.yaml
```

### 28.2 Verify Prometheus is discovering targets

```bash
kubectl port-forward svc/monitoring-kube-prometheus-stack-prometheus -n monitoring 9090:9090
```

Open `http://localhost:9090` → **Status → Targets**

You should see all your services listed with `State: UP`.

If targets are missing, check:
1. ServiceMonitor label `release: monitoring` matches the Prometheus `serviceMonitorSelector`
2. Service labels in Helm chart include `stack: spring-boot`

---

## 29. Import Grafana Dashboards

### 29.1 Access Grafana

```bash
# Port-forward Grafana
kubectl port-forward svc/monitoring-grafana -n monitoring 3000:80
```

Get admin password:

**Mac / WSL2:**
```bash
kubectl get secret -n monitoring monitoring-grafana \
  -o jsonpath="{.data.admin-password}" | base64 -d
echo ""
```

**Windows PowerShell:**
```powershell
$encoded = kubectl get secret -n monitoring monitoring-grafana `
  -o jsonpath="{.data.admin-password}"
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($encoded))
```

Open `http://localhost:3000` — Username: `admin`, Password: from above.

### 29.2 Import community dashboards

Grafana has a dashboard library at grafana.com/grafana/dashboards. Import by ID:

| Dashboard | ID | What it shows |
|-----------|-----|----------------|
| **Kubernetes cluster overview** | `315` | Nodes, pods, CPU, memory |
| **Kubernetes pods** | `6417` | Per-pod resource usage |
| **JVM (Spring Boot)** | `4701` | Heap, GC, threads, HTTP requests |
| **Node.js** | `11159` | Event loop, HTTP, memory |
| **NGINX Ingress** | `9614` | Request rate, latency, error rate |

**How to import:**
1. Grafana → **+** (plus icon) → **Import dashboard**
2. Enter the dashboard ID → **Load**
3. Select **Prometheus** as the data source → **Import**

### 29.3 Create a custom Pharma Services dashboard via ConfigMap

The Grafana sidecar in `prometheus-values.yaml` watches for ConfigMaps with label `grafana_dashboard: "1"` in all namespaces.

Create `k8s/monitoring/pharma-dashboard.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: pharma-services-dashboard
  namespace: monitoring
  labels:
    grafana_dashboard: "1"
data:
  pharma-services.json: |
    {
      "title": "Pharma Services Overview",
      "panels": [
        {
          "title": "HTTP Request Rate (all services)",
          "type": "graph",
          "targets": [{
            "expr": "sum(rate(http_server_requests_seconds_count{namespace=\"dev\"}[5m])) by (job)"
          }]
        },
        {
          "title": "HTTP Error Rate",
          "type": "graph",
          "targets": [{
            "expr": "sum(rate(http_server_requests_seconds_count{namespace=\"dev\",status=~\"5..\"}[5m])) by (job)"
          }]
        },
        {
          "title": "JVM Heap Used",
          "type": "graph",
          "targets": [{
            "expr": "jvm_memory_used_bytes{namespace=\"dev\",area=\"heap\"}"
          }]
        },
        {
          "title": "Pod Restarts",
          "type": "stat",
          "targets": [{
            "expr": "sum(kube_pod_container_status_restarts_total{namespace=\"dev\"}) by (pod)"
          }]
        }
      ]
    }
```

Apply:
```bash
kubectl apply -f ~/pharma-devops/k8s/monitoring/pharma-dashboard.yaml
```

Grafana auto-loads it within 30 seconds (no restart needed).

---

## 30. Set Up Custom Alert Rules

### 30.1 Create alert rules for pharma services

Create `k8s/monitoring/pharma-alert-rules.yaml`:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: pharma-service-alerts
  namespace: monitoring
  labels:
    release: monitoring
spec:
  groups:
    - name: pharma.services
      interval: 1m
      rules:

        # Pod crash loop
        - alert: PharmaServiceCrashLooping
          expr: |
            rate(kube_pod_container_status_restarts_total{
              namespace="dev"
            }[15m]) * 60 > 1
          for: 5m
          labels:
            severity: critical
          annotations:
            summary: "Service {{ $labels.pod }} is crash-looping"
            description: "Pod {{ $labels.pod }} in namespace {{ $labels.namespace }} is restarting more than once per minute."

        # High HTTP error rate
        - alert: PharmaHighErrorRate
          expr: |
            sum(rate(http_server_requests_seconds_count{
              namespace="dev", status=~"5.."
            }[5m])) by (job)
            /
            sum(rate(http_server_requests_seconds_count{
              namespace="dev"
            }[5m])) by (job)
            > 0.05
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "High error rate on {{ $labels.job }}"
            description: "{{ $labels.job }} has more than 5% HTTP 5xx errors for 5 minutes."

        # Pod not ready
        - alert: PharmaPodNotReady
          expr: |
            kube_pod_status_ready{
              namespace="dev", condition="true"
            } == 0
          for: 3m
          labels:
            severity: critical
          annotations:
            summary: "Pod {{ $labels.pod }} is not ready"
            description: "Pod {{ $labels.pod }} has been not ready for more than 3 minutes."

        # High JVM heap usage
        - alert: PharmaHighJvmHeap
          expr: |
            jvm_memory_used_bytes{namespace="dev", area="heap"}
            /
            jvm_memory_max_bytes{namespace="dev", area="heap"}
            > 0.85
          for: 10m
          labels:
            severity: warning
          annotations:
            summary: "High JVM heap on {{ $labels.job }}"
            description: "{{ $labels.job }} JVM heap is above 85% for 10 minutes. Consider increasing memory limits."
```

Apply:
```bash
kubectl apply -f ~/pharma-devops/k8s/monitoring/pharma-alert-rules.yaml
```

### 30.2 Verify rules are loaded in Prometheus

```bash
kubectl port-forward svc/monitoring-kube-prometheus-stack-prometheus \
  -n monitoring 9090:9090
```

Open `http://localhost:9090` → **Alerts** — you should see `pharma.services` group with all four rules in `inactive` state (green).

---

## 31. Verify Monitoring End-to-End

### 31.1 Confirm all components are healthy

```bash
kubectl get pods -n monitoring
kubectl get servicemonitors -n monitoring
kubectl get prometheusrules -n monitoring
```

### 31.2 Check Prometheus scrape targets

```bash
kubectl port-forward svc/monitoring-kube-prometheus-stack-prometheus \
  -n monitoring 9090:9090
```

Open `http://localhost:9090` → **Status → Targets**

All services should show `State: UP`.

### 31.3 Run a test PromQL query

In Prometheus UI → **Graph** tab, try these queries:

```promql
# Request rate per service
sum(rate(http_server_requests_seconds_count{namespace="dev"}[5m])) by (job)

# JVM heap usage
jvm_memory_used_bytes{namespace="dev", area="heap"}

# Pod CPU usage
rate(container_cpu_usage_seconds_total{namespace="dev"}[5m])

# Pods not ready
kube_pod_status_ready{namespace="dev", condition="true"} == 0
```

### 31.4 Trigger a test alert

Scale down auth-service to 0 replicas to trigger `PharmaPodNotReady`:

```bash
kubectl scale deployment auth-service -n dev --replicas=0
```

Wait 3 minutes, then check:
- **Prometheus UI → Alerts**: `PharmaPodNotReady` should change from `inactive` → `pending` → `firing`
- **AlertManager UI** (`localhost:9093`): Alert should appear
- **Email**: Check `devops@pharma.com` for the notification

Restore after testing:
```bash
kubectl scale deployment auth-service -n dev --replicas=1
```

### 31.5 Access Grafana and verify dashboards

```bash
kubectl port-forward svc/monitoring-grafana -n monitoring 3000:80
# Open: http://localhost:3000
```

Navigate to:
- **Dashboards → Kubernetes cluster overview** → verify node CPU/memory graphs
- **Dashboards → JVM** → select a Spring Boot service → verify heap and GC metrics
- **Dashboards → Pharma Services Overview** → verify custom dashboard loaded

---

## 32. Troubleshooting Monitoring

### Prometheus targets show "0/X up"

```bash
# Check ServiceMonitor selectors match Service labels
kubectl get svc -n dev --show-labels
kubectl get servicemonitor -n monitoring -o yaml | grep -A5 selector
```

The `matchLabels` in the ServiceMonitor must match labels on the K8s Service objects.

---

### Grafana shows "No data"

1. Verify Prometheus datasource:
   **Grafana → Configuration → Data Sources → Prometheus** → click **Save & test** → should say "Data source is working"

2. Check Prometheus is scraping your services:
   ```promql
   up{namespace="dev"}
   ```
   In Prometheus UI → if empty, targets are not being scraped.

3. Check ServiceMonitor `release` label matches:
   ```bash
   kubectl get prometheus -n monitoring -o yaml | grep serviceMonitorSelector -A5
   ```

---

### AlertManager not sending emails

1. Check AlertManager config was applied:
   ```bash
   kubectl port-forward svc/monitoring-kube-prometheus-alertmanager \
     -n monitoring 9093:9093
   # Open http://localhost:9093/api/v2/status
   ```

2. Check SMTP credentials:
   ```bash
   kubectl get secret alertmanager-smtp -n monitoring
   ```

3. Send a test alert manually:
   ```bash
   curl -X POST http://localhost:9093/api/v2/alerts \
     -H "Content-Type: application/json" \
     -d '[{
       "labels": {"alertname": "TestAlert", "severity": "warning"},
       "annotations": {"summary": "Manual test alert"}
     }]'
   ```

---

### Prometheus OOMKilled — running out of memory

The default config allocates 2Gi–4Gi memory. If the cluster is large, increase:

```yaml
# In k8s/monitoring/prometheus-values.yaml
prometheus:
  prometheusSpec:
    resources:
      requests:
        memory: 4Gi
      limits:
        memory: 8Gi
```

Re-apply:
```bash
helm upgrade monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --values ~/pharma-devops/k8s/monitoring/prometheus-values.yaml \
  --reuse-values
```

---

### PVC not binding — storage class not found

The values file uses `storageClassName: gp2`. Verify it exists:

```bash
kubectl get storageclass
```

If `gp2` is missing (EKS sometimes uses `gp3` by default):

```bash
# Check available storage classes
kubectl get storageclass

# Update prometheus-values.yaml to use the available class
# e.g., change gp2 → gp3 in all three PVC specs
```

---

*Pharma DevOps Learning Project | AWS: us-east-1 | Phase 1: EKS + ArgoCD | Phase 2: GitHub Actions CI/CD | Phase 3: Prometheus + Grafana*
