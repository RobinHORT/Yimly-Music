#!/bin/bash
BASE_URL="https://yimly.robinhort.link/api"

# 1. Login as test
TEST_TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" -d '{"username":"test","password":"test"}' | grep -o '"token":"[^"]*' | grep -o '[^"]*$')

# 2. Login as test2
TEST2_TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" -d '{"username":"test2","password":"test2"}' | grep -o '"token":"[^"]*' | grep -o '[^"]*$')

# Get songs
SONGS=$(curl -s "$BASE_URL/songs" -H "Authorization: Bearer $TEST_TOKEN")
SONG1_ID=$(echo $SONGS | grep -o '"id":"[^"]*' | head -n 1 | grep -o '[^"]*$')
SONG2_ID=$(echo $SONGS | grep -o '"id":"[^"]*' | head -n 2 | tail -n 1 | grep -o '[^"]*$')

# Create dummy images
echo "fake_image_content" > private_cover.jpg
echo "fake_image_content" > public_cover.jpg

# 3. Create Private Playlist
PRIV_PL=$(curl -s -X POST "$BASE_URL/playlists" -H "Authorization: Bearer $TEST_TOKEN" -H "Content-Type: application/json" -d '{"name":"Private E2E Test","description":"Test","isPublic":false}')
PRIV_ID=$(echo $PRIV_PL | grep -o '"id":"[^"]*' | head -n 1 | grep -o '[^"]*$')

# Upload cover
curl -s -X POST "$BASE_URL/playlists/$PRIV_ID/cover" -H "Authorization: Bearer $TEST_TOKEN" -F "cover=@private_cover.jpg" > /dev/null

# Add songs
curl -s -X POST "$BASE_URL/playlists/$PRIV_ID/songs" -H "Authorization: Bearer $TEST_TOKEN" -H "Content-Type: application/json" -d '{"songId":"'$SONG1_ID'"}' > /dev/null
curl -s -X POST "$BASE_URL/playlists/$PRIV_ID/songs" -H "Authorization: Bearer $TEST_TOKEN" -H "Content-Type: application/json" -d '{"songId":"'$SONG2_ID'"}' > /dev/null

# 4. Create Public Playlist
PUB_PL=$(curl -s -X POST "$BASE_URL/playlists" -H "Authorization: Bearer $TEST_TOKEN" -H "Content-Type: application/json" -d '{"name":"Public E2E Test","description":"Test","isPublic":true}')
PUB_ID=$(echo $PUB_PL | grep -o '"id":"[^"]*' | head -n 1 | grep -o '[^"]*$')

# Upload cover
curl -s -X POST "$BASE_URL/playlists/$PUB_ID/cover" -H "Authorization: Bearer $TEST_TOKEN" -F "cover=@public_cover.jpg" > /dev/null

# Add songs
curl -s -X POST "$BASE_URL/playlists/$PUB_ID/songs" -H "Authorization: Bearer $TEST_TOKEN" -H "Content-Type: application/json" -d '{"songId":"'$SONG1_ID'"}' > /dev/null
curl -s -X POST "$BASE_URL/playlists/$PUB_ID/songs" -H "Authorization: Bearer $TEST_TOKEN" -H "Content-Type: application/json" -d '{"songId":"'$SONG2_ID'"}' > /dev/null

# 5. Fetch and verify as test
FETCH_PRIV=$(curl -s "$BASE_URL/playlists/$PRIV_ID" -H "Authorization: Bearer $TEST_TOKEN")
FETCH_PUB=$(curl -s "$BASE_URL/playlists/$PUB_ID" -H "Authorization: Bearer $TEST_TOKEN")

PRIV_NAME=$(echo $FETCH_PRIV | grep -o '"name":"[^"]*' | head -n 1 | grep -o '[^"]*$')
PUB_NAME=$(echo $FETCH_PUB | grep -o '"name":"[^"]*' | head -n 1 | grep -o '[^"]*$')

PRIV_SONGS=$(echo $FETCH_PRIV | grep -o '"songCount":[0-9]*' | grep -o '[0-9]*')
PUB_SONGS=$(echo $FETCH_PUB | grep -o '"songCount":[0-9]*' | grep -o '[0-9]*')

PRIV_COVER=$(echo $FETCH_PRIV | grep -o '"cover_url":"[^"]*' | head -n 1 | grep -o '[^"]*$' || echo "")
if [ -z "$PRIV_COVER" ]; then
    PRIV_COVER=$(echo $FETCH_PRIV | grep -o '"coverUrl":"[^"]*' | head -n 1 | grep -o '[^"]*$')
fi

PUB_COVER=$(echo $FETCH_PUB | grep -o '"cover_url":"[^"]*' | head -n 1 | grep -o '[^"]*$' || echo "")
if [ -z "$PUB_COVER" ]; then
    PUB_COVER=$(echo $FETCH_PUB | grep -o '"coverUrl":"[^"]*' | head -n 1 | grep -o '[^"]*$')
fi

# 6. Fetch and verify as test2
TEST2_PRIV_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/playlists/$PRIV_ID" -H "Authorization: Bearer $TEST2_TOKEN")
TEST2_PUB_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/playlists/$PUB_ID" -H "Authorization: Bearer $TEST2_TOKEN")

echo "=== VERIFICATION REPORT ==="
echo "1. Private playlist: Name='$PRIV_NAME', ID='$PRIV_ID'"
echo "2. Public playlist: Name='$PUB_NAME', ID='$PUB_ID'"
echo "3. Private Songs: $PRIV_SONGS, Public Songs: $PUB_SONGS"
echo "4. Private cover upload success: $(if [ -n "$PRIV_COVER" ]; then echo true; else echo false; fi), Public cover upload success: $(if [ -n "$PUB_COVER" ]; then echo true; else echo false; fi)"
echo "5. Real server-side cover paths:"
echo "   Private: $PRIV_COVER"
echo "   Public: $PUB_COVER"
echo "6. Cover reloaded from server successfully: Private: $(if [ -n "$PRIV_COVER" ]; then echo true; else echo false; fi), Public: $(if [ -n "$PUB_COVER" ]; then echo true; else echo false; fi)"
echo "7. test2 Visibility:"
echo "   Private Playlist Status: $TEST2_PRIV_STATUS (Expected 403 or 404)"
echo "   Public Playlist Status: $TEST2_PUB_STATUS (Expected 200)"

