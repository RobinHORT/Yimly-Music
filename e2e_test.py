import requests
import json
import uuid

BASE_URL = "https://yimly.robinhort.link/api"

# 1. Login
def login(username, password):
    resp = requests.post(f"{BASE_URL}/auth/login", json={"username": username, "password": password})
    if resp.status_code == 200:
        return resp.json().get("token")
    else:
        raise Exception(f"Failed to login {username}: {resp.text}")

token_test = login("test", "test")
token_test2 = login("test2", "test2")

headers_test = {"Authorization": f"Bearer {token_test}"}
headers_test2 = {"Authorization": f"Bearer {token_test2}"}

# 2. Get Songs
songs_resp = requests.get(f"{BASE_URL}/songs", headers=headers_test)
songs = songs_resp.json()
song_1 = songs[0]["id"]
song_2 = songs[1]["id"]

# Create dummy images
with open("private_cover.jpg", "wb") as f:
    f.write(b"\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x01\x00H\x00H\x00\x00\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\t\t\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.' \",#\x1c\x1c(7),01444\x1f'9=82<.342\xff\xd9")

with open("public_cover.jpg", "wb") as f:
    f.write(b"\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x01\x00H\x00H\x00\x00\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\t\t\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.' \",#\x1c\x1c(7),01444\x1f'9=82<.342\xff\xd9")

# 3. Create Private Playlist
private_payload = {"name": "Private E2E Test", "description": "Test", "isPublic": False}
resp = requests.post(f"{BASE_URL}/playlists", json=private_payload, headers=headers_test)
private_pl = resp.json()
private_id = private_pl["id"]

# Upload cover
with open("private_cover.jpg", "rb") as f:
    files = {"cover": ("private_cover.jpg", f, "image/jpeg")}
    up_resp = requests.post(f"{BASE_URL}/playlists/{private_id}/cover", headers=headers_test, files=files)
    priv_cover_result = up_resp.json().get("coverUrl", up_resp.json().get("coverPath", up_resp.json().get("cover")))

# Add songs
requests.post(f"{BASE_URL}/playlists/{private_id}/songs", json={"songId": song_1}, headers=headers_test)
requests.post(f"{BASE_URL}/playlists/{private_id}/songs", json={"songId": song_2}, headers=headers_test)

# 4. Create Public Playlist
public_payload = {"name": "Public E2E Test", "description": "Test", "isPublic": True}
resp = requests.post(f"{BASE_URL}/playlists", json=public_payload, headers=headers_test)
public_pl = resp.json()
public_id = public_pl["id"]

# Upload cover
with open("public_cover.jpg", "rb") as f:
    files = {"cover": ("public_cover.jpg", f, "image/jpeg")}
    up_resp = requests.post(f"{BASE_URL}/playlists/{public_id}/cover", headers=headers_test, files=files)
    pub_cover_result = up_resp.json().get("coverUrl", up_resp.json().get("coverPath", up_resp.json().get("cover")))

# Add songs
requests.post(f"{BASE_URL}/playlists/{public_id}/songs", json={"songId": song_1}, headers=headers_test)
requests.post(f"{BASE_URL}/playlists/{public_id}/songs", json={"songId": song_2}, headers=headers_test)

# 5. Fetch and verify as test
fetched_priv = requests.get(f"{BASE_URL}/playlists/{private_id}", headers=headers_test).json()
fetched_pub = requests.get(f"{BASE_URL}/playlists/{public_id}", headers=headers_test).json()

priv_cover_url = fetched_priv.get("coverUrl") or fetched_priv.get("coverPath") or fetched_priv.get("cover")
pub_cover_url = fetched_pub.get("coverUrl") or fetched_pub.get("coverPath") or fetched_pub.get("cover")

# 6. Fetch and verify as test2
test2_priv_status = requests.get(f"{BASE_URL}/playlists/{private_id}", headers=headers_test2).status_code
test2_pub_status = requests.get(f"{BASE_URL}/playlists/{public_id}", headers=headers_test2).status_code

print("=== VERIFICATION REPORT ===")
print(f"1. Private playlist: Name='{fetched_priv['name']}', ID='{private_id}'")
print(f"2. Public playlist: Name='{fetched_pub['name']}', ID='{public_id}'")
print(f"3. Private Songs: {fetched_priv.get('songCount', 0)}, Public Songs: {fetched_pub.get('songCount', 0)}")
print(f"4. Private cover upload success: {priv_cover_result is not None}, Public cover upload success: {pub_cover_result is not None}")
print(f"5. Real server-side cover paths:\n   Private: {priv_cover_url}\n   Public: {pub_cover_url}")
print(f"6. Cover reloaded from server successfully: Private: {priv_cover_url is not None}, Public: {pub_cover_url is not None}")
print(f"7. test2 Visibility:\n   Private Playlist Status: {test2_priv_status} (Expected 403 or 404)\n   Public Playlist Status: {test2_pub_status} (Expected 200)")

