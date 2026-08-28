#!/bin/bash
BASE_URL="https://yimly.robinhort.link/api"

TEST_TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" -d '{"username":"test","password":"1234"}' | python3 parse_json.py token)
TEST2_TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" -d '{"username":"test2","password":"1234"}' | python3 parse_json.py token)

if [ "$TEST_TOKEN" == "null" ] || [ "$TEST2_TOKEN" == "null" ]; then
    echo "Login failed. tokens are empty or null"
    exit 1
fi

SONGS=$(curl -s "$BASE_URL/songs" -H "Authorization: Bearer $TEST_TOKEN")
SONG1_ID=$(echo "$SONGS" | python3 parse_json.py song1)
SONG2_ID=$(echo "$SONGS" | python3 parse_json.py song2)

echo -e -n "\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x01\x00H\x00H\x00\x00\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\x09\x09\x08\x0a\x0c\x14\x0d\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.' \",#\x1c\x1c(7),01444\x1f'9=82<.342\xff\xd9" > private_cover.jpg
echo -e -n "\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x01\x00H\x00H\x00\x00\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\x09\x09\x08\x0a\x0c\x14\x0d\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.' \",#\x1c\x1c(7),01444\x1f'9=82<.342\xff\xd9" > public_cover.jpg

PRIV_PL=$(curl -s -X POST "$BASE_URL/playlists" -H "Authorization: Bearer $TEST_TOKEN" -H "Content-Type: application/json" -d '{"name":"Private E2E Test","description":"Test","isPublic":false}')
PRIV_ID=$(echo "$PRIV_PL" | python3 parse_json.py id)
echo "Private ID: $PRIV_ID"

curl -s -X POST "$BASE_URL/playlists/$PRIV_ID/cover" -H "Authorization: Bearer $TEST_TOKEN" -F "cover=@private_cover.jpg;type=image/jpeg" > /dev/null

curl -s -X POST "$BASE_URL/playlists/$PRIV_ID/songs" -H "Authorization: Bearer $TEST_TOKEN" -H "Content-Type: application/json" -d '{"songId":"'$SONG1_ID'"}' > /dev/null
curl -s -X POST "$BASE_URL/playlists/$PRIV_ID/songs" -H "Authorization: Bearer $TEST_TOKEN" -H "Content-Type: application/json" -d '{"songId":"'$SONG2_ID'"}' > /dev/null

PUB_PL=$(curl -s -X POST "$BASE_URL/playlists" -H "Authorization: Bearer $TEST_TOKEN" -H "Content-Type: application/json" -d '{"name":"Public E2E Test","description":"Test","isPublic":true}')
PUB_ID=$(echo "$PUB_PL" | python3 parse_json.py id)
echo "Public ID: $PUB_ID"

curl -s -X POST "$BASE_URL/playlists/$PUB_ID/cover" -H "Authorization: Bearer $TEST_TOKEN" -F "cover=@public_cover.jpg;type=image/jpeg" > /dev/null

curl -s -X POST "$BASE_URL/playlists/$PUB_ID/songs" -H "Authorization: Bearer $TEST_TOKEN" -H "Content-Type: application/json" -d '{"songId":"'$SONG1_ID'"}' > /dev/null
curl -s -X POST "$BASE_URL/playlists/$PUB_ID/songs" -H "Authorization: Bearer $TEST_TOKEN" -H "Content-Type: application/json" -d '{"songId":"'$SONG2_ID'"}' > /dev/null

FETCH_PRIV=$(curl -s "$BASE_URL/playlists/$PRIV_ID" -H "Authorization: Bearer $TEST_TOKEN")
FETCH_PUB=$(curl -s "$BASE_URL/playlists/$PUB_ID" -H "Authorization: Bearer $TEST_TOKEN")

PRIV_NAME=$(echo "$FETCH_PRIV" | python3 parse_json.py name)
PUB_NAME=$(echo "$FETCH_PUB" | python3 parse_json.py name)

PRIV_SONGS=$(echo "$FETCH_PRIV" | python3 parse_json.py songs)
PUB_SONGS=$(echo "$FETCH_PUB" | python3 parse_json.py songs)

PRIV_COVER=$(echo "$FETCH_PRIV" | python3 parse_json.py cover)
PUB_COVER=$(echo "$FETCH_PUB" | python3 parse_json.py cover)

TEST2_PRIV_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/playlists/$PRIV_ID" -H "Authorization: Bearer $TEST2_TOKEN")
TEST2_PUB_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/playlists/$PUB_ID" -H "Authorization: Bearer $TEST2_TOKEN")

echo "=== VERIFICATION REPORT ==="
echo "1. Private playlist: Name='$PRIV_NAME', ID='$PRIV_ID'"
echo "2. Public playlist: Name='$PUB_NAME', ID='$PUB_ID'"
echo "3. Private Songs: $PRIV_SONGS, Public Songs: $PUB_SONGS"
echo "4. Private cover upload success: $(if [ "$PRIV_COVER" != "null" ]; then echo true; else echo false; fi), Public cover upload success: $(if [ "$PUB_COVER" != "null" ]; then echo true; else echo false; fi)"
echo "5. Real server-side cover paths:"
echo "   Private: $PRIV_COVER"
echo "   Public: $PUB_COVER"
echo "6. Cover reloaded from server successfully: Private: $(if [ "$PRIV_COVER" != "null" ]; then echo true; else echo false; fi), Public: $(if [ "$PUB_COVER" != "null" ]; then echo true; else echo false; fi)"
echo "7. test2 Visibility:"
echo "   Private Playlist Status: $TEST2_PRIV_STATUS (Expected 403 or 404)"
echo "   Public Playlist Status: $TEST2_PUB_STATUS (Expected 200)"

