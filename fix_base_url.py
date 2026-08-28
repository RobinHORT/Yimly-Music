with open('app/src/test/java/com/example/PlaylistCoverVerificationTest.kt', 'r') as f:
    content = f.read()
content = content.replace('baseUrl("https://yimly.robinhort.link/api/")', 'baseUrl("https://yimly.robinhort.link/")')
with open('app/src/test/java/com/example/PlaylistCoverVerificationTest.kt', 'w') as f:
    f.write(content)
