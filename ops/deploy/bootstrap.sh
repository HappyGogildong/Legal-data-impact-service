#!/usr/bin/env bash
# EC2 최초 준비(D63, cloud-infra §3) — Amazon Linux 2023 arm64. SSM 세션에서 sudo 로 1회 실행.
set -euo pipefail

COMPOSE_VERSION="v2.39.4"

dnf install -y docker git cronie logrotate
systemctl enable --now docker crond

mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-aarch64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

timedatectl set-timezone Asia/Seoul

if ! swapon --show | grep -q /swapfile; then    # 메모리 안전판 2GB
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap defaults 0 0' >> /etc/fstab
fi

[[ -d /opt/lia ]] || git clone https://github.com/HappyGogildong/Legal-data-impact-service.git /opt/lia
mkdir -p /var/log/lia
install -m 644 /opt/lia/ops/deploy/logrotate.conf /etc/logrotate.d/lia

echo "준비 끝 — 다음: Parameter Store 등록 확인 후 'cd /opt/lia && ops/deploy/lia up <커밋 SHA>', 그리고 'crontab ops/deploy/crontab'"
