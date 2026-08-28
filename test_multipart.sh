#!/bin/bash
TOKEN=$(curl -s -X POST "https://yimly.robinhort.link/api/auth/login" -H "Content-Type: application/json" -d '{"username":"test","password":"1234"}' | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")
PID=$(curl -s -X POST "https://yimly.robinhort.link/api/playlists" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"Multipart Test","description":"test","isPublic":true}' | python3 -c "import sys, json; print(json.load(sys.stdin)['id'])")
echo "Playlist ID: $PID"

echo -e "\n--- Test Multipart with pic.jpg (-F cover=@pic.jpg) ---"
curl -s -X POST "https://yimly.robinhort.link/api/playlists/$PID/cover" -H "Authorization: Bearer $TOKEN" -F "cover=@pic.jpg;type=image/jpeg"

echo -e "\n--- Test Raw Binary with pic.jpg ---"
curl -s -X POST "https://yimly.robinhort.link/api/playlists/$PID/cover" -H "Authorization: Bearer $TOKEN" -H "Content-Type: image/jpeg" --data-binary "@pic.jpg"
