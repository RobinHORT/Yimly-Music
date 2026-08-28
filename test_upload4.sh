#!/bin/bash
TOKEN=$(curl -s -X POST "https://yimly.robinhort.link/api/auth/login" -H "Content-Type: application/json" -d '{"username":"test","password":"1234"}' | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")
PID=$(curl -s -X POST "https://yimly.robinhort.link/api/playlists" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"Upload Test 4","description":"test","isPublic":true}' | python3 -c "import sys, json; print(json.load(sys.stdin)['id'])")

for field in cover image file photo artwork picture avatar upload; do
  echo "Testing field '$field':"
  curl -s -X POST "https://yimly.robinhort.link/api/playlists/$PID/cover" -H "Authorization: Bearer $TOKEN" -F "$field=@pic.jpg;type=image/jpeg"
  echo ""
done
