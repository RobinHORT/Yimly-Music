import base64
# Base64 string for a small 1x1 valid JPEG
b64 = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA="

with open('private_cover.jpg', 'wb') as f:
    f.write(base64.b64decode(b64))
