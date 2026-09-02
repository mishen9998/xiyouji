# Kubernetes 草案

这些清单只部署应用层，MySQL 和 Redis 假设由集群外部的托管服务或已有 Service 提供；本阶段不包含数据库/缓存高可用。

1. 复制 `secret.example.yaml` 为环境专用 Secret，并替换所有占位值。
2. 将 Deployment 中的镜像改为实际镜像仓库地址。
3. 修改 ConfigMap 中的域名和 MySQL/Redis Service 地址。
4. 有可用 Kubernetes API Server 时校验：`kubectl apply --dry-run=client -f k8s/`。
   Docker Desktop 的 Kubernetes 未启用时，使用下面的离线 schema 校验，不需要 API Server：
   `docker run --rm -v "${PWD}/k8s:/work:ro" ghcr.io/yannh/kubeconform:v0.6.7 -strict -summary /work`
5. 部署：`kubectl apply -f k8s/`。

Deployment 默认 2 个副本，Service 使用 ClusterIP，Ingress 负责 HTTP 和 WebSocket 转发；HPA 为可选清单。
