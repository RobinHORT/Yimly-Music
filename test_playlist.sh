#!/bin/bash
TOKEN=$(curl -s -X POST "https://yimly.robinhort.link/api/auth/login" -H "Content-Type: application/json" -d '{"username":"test2","password":"1234"}' | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")
curl -s -X GET "https://yimly.robinhort.link/api/playlists/46" -H "Authorization: Bearer $TOKEN" | python3 -c "import sys, json; print(json.dumps(json.load(sys.stdin), indent=2))"
