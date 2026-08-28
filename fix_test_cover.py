with open('app/src/test/java/com/example/PlaylistCoverVerificationTest.kt', 'r') as f:
    content = f.read()
# Replace the catch block
content = content.replace(
'''        try {
            apiService.uploadPlaylistCover(privatePlaylistId, body1)
        } catch (e: Exception) {
            val body1Alt = MultipartBody.Part.createFormData("artwork", privateImageFile.name, reqFile1)
            apiService.uploadPlaylistArtwork(privatePlaylistId, body1Alt)
        }''',
'''        try {
            apiService.uploadPlaylistCover(privatePlaylistId, body1)
        } catch (e: retrofit2.HttpException) {
            println("PRIVATE UPLOAD ERROR: ${e.response()?.errorBody()?.string()}")
        }'''
)
content = content.replace(
'''        try {
            apiService.uploadPlaylistCover(publicPlaylistId, body2)
        } catch (e: Exception) {
            val body2Alt = MultipartBody.Part.createFormData("artwork", publicImageFile.name, reqFile2)
            apiService.uploadPlaylistArtwork(publicPlaylistId, body2Alt)
        }''',
'''        try {
            apiService.uploadPlaylistCover(publicPlaylistId, body2)
        } catch (e: retrofit2.HttpException) {
            println("PUBLIC UPLOAD ERROR: ${e.response()?.errorBody()?.string()}")
        }'''
)
with open('app/src/test/java/com/example/PlaylistCoverVerificationTest.kt', 'w') as f:
    f.write(content)
