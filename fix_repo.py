import re
with open('app/src/main/java/com/example/data/repository/MusicRepository.kt', 'r') as f:
    content = f.read()

old_func = """    suspend fun updatePlaylistArtwork(playlistId: String, localUri: String?) = withContext(Dispatchers.IO) {
        musicDao.updatePlaylistCoverUrl(playlistId, localUri)
        if (!localUri.isNullOrBlank()) {
            try {
                val localPl = musicDao.getPlaylistById(playlistId)
                val currentName = localPl?.name ?: "Playlist"
                val currentDesc = localPl?.description
                val currentIsPublic = localPl?.isPublic
                val req = UpdatePlaylistRequest(
                    name = currentName,
                    description = currentDesc,
                    isPublic = currentIsPublic,
                    coverUrl = localUri,
                    coverUrlSnake = localUri,
                    cover = localUri
                )
                val updatedRemote = apiService.updatePlaylist(playlistId, req)
                if (!updatedRemote.coverUrl.isNullOrBlank()) {
                    musicDao.insertPlaylist(updatedRemote.toEntity())
                }
            } catch (_: Exception) {
                if (localUri.startsWith("file://") || localUri.startsWith("/")) {
                    try {
                        val filePath = localUri.removePrefix("file://")
                        val file = java.io.File(filePath)
                        if (file.exists()) {
                            val requestFile = okhttp3.RequestBody.create("image/*".toMediaTypeOrNull(), file)
                            val body = okhttp3.MultipartBody.Part.createFormData("cover", file.name, requestFile)
                            val uploaded = try {
                                apiService.uploadPlaylistCover(playlistId, body)
                            } catch (_: Exception) {
                                val artworkBody = okhttp3.MultipartBody.Part.createFormData("artwork", file.name, requestFile)
                                apiService.uploadPlaylistArtwork(playlistId, artworkBody)
                            }
                            if (!uploaded.coverUrl.isNullOrBlank()) {
                                musicDao.insertPlaylist(uploaded.toEntity())
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }"""

new_func = """    suspend fun updatePlaylistArtwork(playlistId: String, localUri: String?) = withContext(Dispatchers.IO) {
        musicDao.updatePlaylistCoverUrl(playlistId, localUri)
        if (!localUri.isNullOrBlank()) {
            if (localUri.startsWith("file://") || localUri.startsWith("/")) {
                try {
                    val filePath = localUri.removePrefix("file://")
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        val requestFile = okhttp3.RequestBody.create("image/*".toMediaTypeOrNull(), file)
                        val body = okhttp3.MultipartBody.Part.createFormData("cover", file.name, requestFile)
                        val uploaded = try {
                            apiService.uploadPlaylistCover(playlistId, body)
                        } catch (_: Exception) {
                            val artworkBody = okhttp3.MultipartBody.Part.createFormData("artwork", file.name, requestFile)
                            apiService.uploadPlaylistArtwork(playlistId, artworkBody)
                        }
                        if (!uploaded.coverUrl.isNullOrBlank()) {
                            musicDao.insertPlaylist(uploaded.toEntity())
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    val localPl = musicDao.getPlaylistById(playlistId)
                    val req = UpdatePlaylistRequest(
                        name = localPl?.name ?: "Playlist",
                        description = localPl?.description,
                        isPublic = localPl?.isPublic,
                        coverUrl = localUri,
                        coverUrlSnake = localUri,
                        cover = localUri
                    )
                    val updatedRemote = apiService.updatePlaylist(playlistId, req)
                    if (!updatedRemote.coverUrl.isNullOrBlank()) {
                        musicDao.insertPlaylist(updatedRemote.toEntity())
                    }
                } catch (_: Exception) {}
            }
        }
    }"""

if old_func in content:
    content = content.replace(old_func, new_func)
    with open('app/src/main/java/com/example/data/repository/MusicRepository.kt', 'w') as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Failed to find the function")
