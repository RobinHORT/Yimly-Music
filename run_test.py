import re

with open('app/src/test/java/com/example/PlaylistCoverVerificationTest.kt', 'r') as f:
    content = f.read()

# Let's just catch all Retrofit exceptions and print them
content = content.replace(
    'fun verifyPlaylistCoversAndVisibility() = runBlocking {',
    'fun verifyPlaylistCoversAndVisibility() = runBlocking {\n        try {'
)
content = content.replace(
    'println("REPORT_END")',
    'println("REPORT_END")\n        } catch(e: retrofit2.HttpException) {\n            println("HTTP EXCEPTION: ${e.code()} ${e.message()} ${e.response()?.errorBody()?.string()}")\n        } catch(e: Exception) {\n            println("EXCEPTION: ${e.message}")\n        }'
)

with open('app/src/test/java/com/example/PlaylistCoverVerificationTest.kt', 'w') as f:
    f.write(content)
