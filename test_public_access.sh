#!/bin/bash
TOKEN2=$(curl -s -X POST "https://yimly.robinhort.link/api/auth/login" -H "Content-Type: application/json" -d '{"username":"test2","password":"1234"}' | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")

echo "Public playlist access with test2:"
curl -s -I "https://yimly.robinhort.link/api/playlists/55/cover" -H "Authorization: Bearer $TOKEN2"

