#!/usr/bin/env bash
set -euo pipefail
v2_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
seed_customers=false
if [[ "${1:-}" == "--seed-customers" && $# -eq 1 ]]; then
  seed_customers=true
elif [[ $# -gt 0 ]]; then
  echo "Usage: $0 [--seed-customers]" >&2
  exit 2
fi
if [[ ! -f "$v2_root/.env.backend.local" ]]; then
  python3 - "$v2_root/.env.backend.local" <<'PY'
import secrets,sys,os
path=sys.argv[1]
fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as f:
 f.write('HIVEAPP_ADMIN_BOOTSTRAP_ENABLED=true\nHIVEAPP_ADMIN_BOOTSTRAP_EMAIL=admin@hive.local\nHIVEAPP_ADMIN_BOOTSTRAP_PASSWORD='+secrets.token_urlsafe(24)+'\nHIVEAPP_ADMIN_BOOTSTRAP_FIRST_NAME=Platform\nHIVEAPP_ADMIN_BOOTSTRAP_LAST_NAME=Administrator\n')
PY
fi
if [[ "$seed_customers" == true ]]; then
  python3 - "$v2_root/.env.backend.local" <<'PY'
import os,secrets,sys
path=sys.argv[1]
with open(path) as f:
 content=f.read()
if 'HIVEAPP_CUSTOMER_SEED_PASSWORD=' not in content:
 with open(path,'a') as f:
  f.write(('' if content.endswith('\n') else '\n')+'HIVEAPP_CUSTOMER_SEED_PASSWORD='+secrets.token_urlsafe(24)+'\n')
os.chmod(path,0o600)
PY
fi
set -a
source "$v2_root/.env.backend.local"
set +a
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
if [[ "$seed_customers" == true ]]; then
  export HIVEAPP_CUSTOMER_SEED_ENABLED=true
fi
export SPRING_JPA_HIBERNATE_DDL_AUTO="${SPRING_JPA_HIBERNATE_DDL_AUTO:-update}"
# A supplied datasource takes precedence. The local default is durable across restarts.
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:h2:file:$v2_root/.data.local/hiveapp;DB_CLOSE_ON_EXIT=FALSE}"
export SPRING_DATASOURCE_DRIVER_CLASS_NAME="${SPRING_DATASOURCE_DRIVER_CLASS_NAME:-org.h2.Driver}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-sa}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"
mkdir -p "$v2_root/.data.local"
cd "$v2_root/../backend"
exec mvn -Dmaven.test.skip=true spring-boot:run -Dspring-boot.run.arguments="--hiveapp.activation.base-url=${HIVEAPP_FRONTEND_BASE_URL:-http://127.0.0.1:5173}"
