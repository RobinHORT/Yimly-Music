import sys, json

data = json.load(sys.stdin)
if len(sys.argv) > 1:
    key = sys.argv[1]
    if key == "songs":
        print(len(data.get("songs", [])))
    elif key == "song1":
        print(data[0]["id"])
    elif key == "song2":
        print(data[1]["id"])
    elif key == "cover":
        print(data.get("coverUrl") or data.get("coverImageUrl") or data.get("coverPath") or data.get("cover_url") or "null")
    else:
        print(data.get(key, "null"))
