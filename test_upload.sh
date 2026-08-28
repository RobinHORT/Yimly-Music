#!/bin/bash
TOKEN=$(curl -s -X POST "https://yimly.robinhort.link/api/auth/login" -H "Content-Type: application/json" -d '{"username":"test","password":"1234"}' | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")
echo "Token: $TOKEN"

PID=$(curl -s -X POST "https://yimly.robinhort.link/api/playlists" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"Upload Test","description":"test","isPublic":true}' | python3 -c "import sys, json; print(json.load(sys.stdin)['id'])")
echo "Playlist ID: $PID"

echo "Testing field 'cover':"
curl -s -X POST "https://yimly.robinhort.link/api/playlists/$PID/cover" -H "Authorization: Bearer $TOKEN" -F "cover=@dummy.jpg;type=image/jpeg"

echo -e "\nTesting field 'image':"
curl -s -X POST "https://yimly.robinhort.link/api/playlists/$PID/cover" -H "Authorization: Bearer $TOKEN" -F "image=@dummy.jpg;type=image/jpeg"

echo -e "\nTesting field 'file':"
curl -s -X POST "https://yimly.robinhort.link/api/playlists/$PID/cover" -H "Authorization: Bearer $TOKEN" -F "file=@dummy.jpg;type=image/jpeg"

echo -e "\nTesting field 'photo':"
curl -s -X POST "https://yimly.robinhort.link/api/playlists/$PID/cover" -H "Authorization: Bearer $TOKEN" -F "photo=@dummy.jpg;type=image/jpeg"

echo -e "\nTesting field 'artwork':"
curl -s -X POST "https://yimly.robinhort.link/api/playlists/$PID/cover" -H "Authorization: Bearer $TOKEN" -F "artwork=@dummy.jpg;type=image/jpeg"

