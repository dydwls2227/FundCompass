#!/usr/bin/env bash
# 로컬 DB를 덤프한다. 1,447건의 추출 결과를 RDS로 옮기기 위한 것.
# 재수집·재추출하면 며칠과 API 비용이 다시 드니 반드시 옮긴다.
set -euo pipefail

OUT="${1:-fundcompass-$(date +%Y%m%d).sql}"
docker exec fundcompass-postgres pg_dump -U fundcompass -d fundcompass \
  --no-owner --no-privileges > "$OUT"
echo "덤프 완료: $OUT ($(du -h "$OUT" | cut -f1))"
echo
echo "RDS로 복원:"
echo "  psql -h <rds-endpoint> -U fundcompass -d fundcompass -f $OUT"
