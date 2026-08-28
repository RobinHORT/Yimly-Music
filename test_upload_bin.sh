#!/bin/bash
TOKEN=$(curl -s -X POST "https://yimly.robinhort.link/api/auth/login" -H "Content-Type: application/json" -d '{"username":"test","password":"1234"}' | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")
PID=$(curl -s -X POST "https://yimly.robinhort.link/api/playlists" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"Upload Test BIN","description":"test","isPublic":true}' | python3 -c "import sys, json; print(json.load(sys.stdin)['id'])")
curl -s -X POST "https://yimly.robinhort.link/api/playlists/$PID/cover" -H "Authorization: Bearer $TOKEN" -H "Content-Type: image/jpeg" --data-binary "@pic.jpg"
