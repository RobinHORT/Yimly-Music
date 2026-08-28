#!/bin/bash
TOKEN=$(curl -s -X POST "https://yimly.robinhort.link/api/auth/login" -H "Content-Type: application/json" -d '{"username":"test","password":"1234"}' | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")
curl -s -I "https://yimly.robinhort.link/api/playlists/55/cover" -H "Authorization: Bearer $TOKEN"
