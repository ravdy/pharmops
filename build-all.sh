#!/bin/bash
set -e

TAG="v1.0.0"
SERVICES=(
  auth-service
  api-gateway
  notification-service
  pharma-ui
)

for svc in "${SERVICES[@]}"; do
  echo "========== Building: $svc =========="
  docker build -t "${REGISTRY}/${svc}:${TAG}" "services/${svc}"
  docker push "${REGISTRY}/${svc}:${TAG}"
  echo "Pushed: ${REGISTRY}/${svc}:${TAG}"
done

# catalog-service directory is named drug-catalog-service
echo "========== Building: catalog-service =========="
docker build -t "${REGISTRY}/catalog-service:${TAG}" "services/drug-catalog-service"
docker push "${REGISTRY}/catalog-service:${TAG}"
echo "Pushed: ${REGISTRY}/catalog-service:${TAG}"

echo "All 5 images pushed to ECR successfully!"
