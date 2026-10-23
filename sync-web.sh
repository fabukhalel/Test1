#!/bin/sh
# يولّد app/src/main/assets/index.html من web/index.html (نفس صفحة الويب) ثم ابنِ التطبيق.
set -e
cd "$(dirname "$0")"
mkdir -p app/src/main/assets
{
  printf '%s\n' '<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
  cat web/index.html
  printf '\n</html>\n'
} > app/src/main/assets/index.html
echo "تم تحديث app/src/main/assets/index.html"
