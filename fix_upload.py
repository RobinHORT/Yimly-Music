import re

with open('app/src/main/java/com/example/data/api/YimlyApiService.kt', 'r') as f:
    content = f.read()

content = content.replace(
'''    @retrofit2.http.Multipart
    @POST("api/playlists/{id}/cover")
    suspend fun uploadPlaylistCover(
        @Path("id") id: String,
        @retrofit2.http.Part cover: okhttp3.MultipartBody.Part
    ): Playlist''',
'''    @POST("api/playlists/{id}/cover")
    suspend fun uploadPlaylistCover(
        @Path("id") id: String,
        @retrofit2.http.Body cover: okhttp3.RequestBody
    ): Playlist'''
)

with open('app/src/main/java/com/example/data/api/YimlyApiService.kt', 'w') as f:
    f.write(content)


with open('app/src/main/java/com/example/data/repository/MusicRepository.kt', 'r') as f:
    content2 = f.read()

content2 = content2.replace(
'''                        val requestFile = okhttp3.RequestBody.create("image/*".toMediaTypeOrNull(), file)
                        val body = okhttp3.MultipartBody.Part.createFormData("cover", file.name, requestFile)
                        val uploaded = try {
                            apiService.uploadPlaylistCover(playlistId, body)
                        } catch (_: Exception) {
                            val artworkBody = okhttp3.MultipartBody.Part.createFormData("artwork", file.name, requestFile)
                            apiService.uploadPlaylistArtwork(playlistId, artworkBody)
                        }''',
'''                        val requestFile = okhttp3.RequestBody.create("image/jpeg".toMediaTypeOrNull(), file)
                        val uploaded = try {
                            apiService.uploadPlaylistCover(playlistId, requestFile)
                        } catch (_: Exception) {
                            val artworkBody = okhttp3.MultipartBody.Part.createFormData("artwork", file.name, requestFile)
                            apiService.uploadPlaylistArtwork(playlistId, artworkBody)
                        }'''
)

with open('app/src/main/java/com/example/data/repository/MusicRepository.kt', 'w') as f:
    f.write(content2)


with open('app/src/test/java/com/example/PlaylistCoverVerificationTest.kt', 'r') as f:
    content3 = f.read()

content3 = content3.replace(
'''        val reqFile1 = RequestBody.create("image/*".toMediaTypeOrNull(), privateImageFile)
        val body1 = MultipartBody.Part.createFormData("cover", privateImageFile.name, reqFile1)
        try {
            apiService.uploadPlaylistCover(privatePlaylistId, body1)
        } catch (e: retrofit2.HttpException) {''',
'''        val reqFile1 = RequestBody.create("image/jpeg".toMediaTypeOrNull(), privateImageFile)
        try {
            apiService.uploadPlaylistCover(privatePlaylistId, reqFile1)
        } catch (e: retrofit2.HttpException) {'''
)
content3 = content3.replace(
'''        val reqFile2 = RequestBody.create("image/*".toMediaTypeOrNull(), publicImageFile)
        val body2 = MultipartBody.Part.createFormData("cover", publicImageFile.name, reqFile2)
        try {
            apiService.uploadPlaylistCover(publicPlaylistId, body2)
        } catch (e: retrofit2.HttpException) {''',
'''        val reqFile2 = RequestBody.create("image/jpeg".toMediaTypeOrNull(), publicImageFile)
        try {
            apiService.uploadPlaylistCover(publicPlaylistId, reqFile2)
        } catch (e: retrofit2.HttpException) {'''
)

with open('app/src/test/java/com/example/PlaylistCoverVerificationTest.kt', 'w') as f:
    f.write(content3)

