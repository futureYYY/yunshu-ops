#!/usr/bin/env bash
# 发布前泄密闸门 / Pre-release secret gate.
# 扫描源码 + 构建产物（APK / EXE / JAR），命中任何一条即失败退出（exit 1）。
# Usage: bash tools/scan-secrets.sh
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FAIL=0

PATTERNS=(
  'sk-[A-Za-z0-9]{16,}'
  'ark-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'
  'AKIA[0-9A-Z]{16}'
  'AIza[0-9A-Za-z_-]{35}'
  'Bearer [A-Za-z0-9._-]{20,}'
  'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY'
)

# 自用额外否决词（如自有中转服务域名、内部站点名）：每行一条写入 .scan-extra-patterns，该文件不入库。
if [ -f "$ROOT/.scan-extra-patterns" ]; then
  while IFS= read -r extra; do
    [ -n "$extra" ] && PATTERNS+=("$extra")
  done < "$ROOT/.scan-extra-patterns"
fi
EXCLUDE=(--exclude-dir=.git --exclude-dir=build --exclude-dir=bin --exclude-dir=obj --exclude-dir=node_modules --exclude=LICENSE --exclude=scan-secrets.sh --exclude=.scan-extra-patterns)

echo "== [1/3] 扫描源码与文本资源 (source & text assets) =="
for p in "${PATTERNS[@]}"; do
  hits="$(grep -rInE "${EXCLUDE[@]}" -- "$p" "$ROOT" 2>/dev/null | head -5)"
  if [ -n "$hits" ]; then
    echo "  [FAIL] 命中 /$p/"
    echo "$hits" | sed 's/^/      /'
    FAIL=1
  fi
done
[ "$FAIL" -eq 0 ] && echo "  [OK] 源码无命中"

echo "== [2/3] 扫描构建产物 APK / AAB =="
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
while IFS= read -r f; do
  d="$TMP/$(basename "$f").d"; mkdir -p "$d"
  unzip -o -q "$f" -d "$d" 2>/dev/null || continue
  for p in "${PATTERNS[@]}"; do
    if grep -ralE -- "$p" "$d" >/dev/null 2>&1; then
      echo "  [FAIL] $(basename "$f") 命中 /$p/"
      FAIL=1
    fi
  done
done < <(find "$ROOT" -type f \( -name '*.apk' -o -name '*.aab' \) 2>/dev/null)
echo "  (APK 扫描完成)"

echo "== [3/3] 扫描 Windows 产物 EXE / DLL / JAR =="
while IFS= read -r f; do
  for p in "${PATTERNS[@]}"; do
    if grep -aqE -- "$p" "$f" 2>/dev/null; then
      echo "  [FAIL] $(basename "$f") 命中 /$p/"
      FAIL=1
    fi
  done
done < <(find "$ROOT" -type f \( -name '*.exe' -o -name '*.dll' -o -name '*.jar' \) 2>/dev/null)
echo "  (二进制扫描完成；注：压缩/加密载荷无法完全覆盖，发布前请人工复核 Release 附件)"

if [ "$FAIL" -ne 0 ]; then
  echo
  echo "结果：未通过，禁止发布 / BLOCKED"
  exit 1
fi
echo
echo "结果：通过 / PASS"
