# ─────────────────────────────────────────────────────────────
#  Kubernetes Deploy Script — Order Service
#  Kullanim: .\deploy.ps1 [-Build] [-Delete]
#
#  Parametreler:
#    -Build   : Docker image'i yeniden build eder
#    -Delete  : Tum kaynaklari siler (cleanup)
# ─────────────────────────────────────────────────────────────
param(
    [switch]$Build,
    [switch]$Delete
)

$ErrorActionPreference = "Stop"
$K8S_DIR = $PSScriptRoot
$PROJECT_ROOT = Split-Path $K8S_DIR -Parent
$NAMESPACE = "foodapp"
$IMAGE_NAME = "order-service"
$IMAGE_TAG = "latest"

# Renklendirme
function Write-Step($msg) { Write-Host "`n>>> $msg" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Err($msg) { Write-Host "  [HATA] $msg" -ForegroundColor Red }

# ── DELETE MODU ──
if ($Delete) {
    Write-Step "Tum Kubernetes kaynaklari siliniyor..."
    kubectl delete namespace $NAMESPACE --ignore-not-found=true
    Write-Ok "Namespace '$NAMESPACE' silindi."
    exit 0
}

# ── DOCKER BUILD ──
if ($Build) {
    Write-Step "Docker image build ediliyor: ${IMAGE_NAME}:${IMAGE_TAG}"
    Push-Location $PROJECT_ROOT
    docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" .
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Docker build basarisiz!"
        Pop-Location
        exit 1
    }
    Write-Ok "Image build edildi: ${IMAGE_NAME}:${IMAGE_TAG}"

    # Minikube kullaniyorsaniz asagidaki satiri aktif edin:
    # minikube image load "${IMAGE_NAME}:${IMAGE_TAG}"
    # Write-Ok "Image Minikube'a yuklendi."

    Pop-Location
}

# ── KUBERNETES DEPLOY ──
Write-Step "1/7 — Namespace olusturuluyor..."
kubectl apply -f "$K8S_DIR\namespace.yaml"
Write-Ok "Namespace '$NAMESPACE' hazir."

Write-Step "2/7 — ConfigMap ve Secret uygulaniyor..."
kubectl apply -f "$K8S_DIR\configmap.yaml"
kubectl apply -f "$K8S_DIR\secret.yaml"
Write-Ok "ConfigMap ve Secret uygulandi."

Write-Step "3/7 — PostgreSQL deploy ediliyor..."
kubectl apply -f "$K8S_DIR\postgres-pvc.yaml"
kubectl apply -f "$K8S_DIR\postgres-deployment.yaml"
Write-Host "  PostgreSQL pod'unun hazir olmasi bekleniyor..." -ForegroundColor Yellow
kubectl rollout status deployment/postgres -n $NAMESPACE --timeout=120s
Write-Ok "PostgreSQL hazir."

Write-Step "4/7 — RabbitMQ deploy ediliyor..."
kubectl apply -f "$K8S_DIR\rabbitmq-deployment.yaml"
Write-Ok "RabbitMQ hazir."

Write-Step "5/7 — Order Service deploy ediliyor..."
kubectl apply -f "$K8S_DIR\order-service-deployment.yaml"
Write-Host "  Order Service pod'larinin hazir olmasi bekleniyor..." -ForegroundColor Yellow
kubectl rollout status deployment/order-service -n $NAMESPACE --timeout=300s
Write-Ok "Order Service hazir."

Write-Step "6/7 — Ingress, HPA ve NetworkPolicy uygulaniyor..."
kubectl apply -f "$K8S_DIR\ingress.yaml"
kubectl apply -f "$K8S_DIR\hpa.yaml"
kubectl apply -f "$K8S_DIR\network-policy.yaml"
Write-Ok "Ingress, HPA ve NetworkPolicy uygulandi."

# ── SONUC ──
Write-Host "`n" -NoNewline
Write-Host "=============================================" -ForegroundColor Green
Write-Host "  DEPLOY TAMAMLANDI!" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green

Write-Host "`nKaynak Durumu:" -ForegroundColor Cyan
kubectl get all -n $NAMESPACE

Write-Host "`nErisim:" -ForegroundColor Cyan
Write-Host "  Port-Forward : kubectl port-forward -n $NAMESPACE svc/order-service 8082:8082"
Write-Host "  Swagger UI   : http://localhost:8082/swagger-ui.html"
Write-Host "  Health Check : http://localhost:8082/actuator/health"
Write-Host "  HPA Durumu   : kubectl get hpa -n $NAMESPACE"
