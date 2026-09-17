#!/bin/bash
# =====================================================
# Jade Grafana Dashboard 一键导入
# =====================================================
# 用法：
#   ./import-dashboard.sh
#   GRAFANA_URL=http://my-grafana:3000 ./import-dashboard.sh
# =====================================================

set -e

GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASS="${GRAFANA_PASS:-admin}"
DASHBOARD_FILE="$(dirname "$0")/dashboards/jade-business.json"

echo "📊 Importing Jade dashboard to $GRAFANA_URL ..."

# 包成 Grafana 接受的格式
WRAPPED=$(python3 -c "
import json
with open('$DASHBOARD_FILE') as f:
    dash = json.load(f)
if 'id' in dash:
    del dash['id']
print(json.dumps({'dashboard': dash, 'message': 'Jade business dashboard', 'overwrite': True}))
")

RESP=$(curl -s -X POST -H "Content-Type: application/json" \
    -u "$GRAFANA_USER:$GRAFANA_PASS" \
    -d "$WRAPPED" \
    "$GRAFANA_URL/api/dashboards/db")

# 检查成功
if echo "$RESP" | grep -q '"status":"success"'; then
    DASH_UID=$(echo "$RESP" | python3 -c "import json, sys; print(json.load(sys.stdin)['uid'])")
    DASH_URL="$GRAFANA_URL/d/$DASH_UID"
    echo "✅ Dashboard imported!"
    echo "   URL: $DASH_URL"
else
    echo "❌ Import failed: $RESP"
    exit 1
fi
