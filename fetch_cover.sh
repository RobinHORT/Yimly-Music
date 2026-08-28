#!/bin/bash
TOKEN=$(curl -s -X POST "https://yimly.robinhort.link/api/auth/login" -H "Content-Type: application/json" -d '{"username":"test","password":"1234"}' | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")
curl -s -X GET "https://yimly.robinhort.link/api/playlists/38/cover" -H "Authorization: Bearer $TOKEN" --output downloaded.jpg
ls -la downloaded.jpg
file downloaded.jpg
