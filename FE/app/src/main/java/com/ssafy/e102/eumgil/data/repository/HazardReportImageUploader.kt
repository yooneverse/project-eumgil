package com.ssafy.e102.eumgil.data.repository

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.ssafy.e102.eumgil.data.remote.datasource.HazardReportImagesRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.HazardReportsApiException
import com.ssafy.e102.eumgil.data.remote.dto.PresignedUploadBatchRequestDto
import com.ssafy.e102.eumgil.data.remote.dto.PresignedUploadRequestDto
import com.ssafy.e102.eumgil.data.remote.dto.PresignedUploadResponseDto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

fun interface HazardReportImageUploader {
    suspend fun uploadAll(
        accessToken: String,
        photos: List<ReportOutboxPhotoData>,
        alreadyUploadedCount: Int,
    ): HazardReportImageUploadResult
}

data class HazardReportImageUploadResult(
    val newlyUploadedObjectKeys: List<String>,
    val newlyUploadedThumbnailObjectKeys: List<String>,
    val allSucceeded: Boolean,
)

data class HazardReportPreparedImage(
    val fileName: String,
    val original: HazardReportPreparedImageVariant,
    val thumbnail: HazardReportPreparedImageVariant,
)

data class HazardReportPreparedImageVariant(
    val contentType: String,
    val body: ByteArray,
)

fun interface HazardReportImageProcessor {
    suspend fun prepare(
        photo: ReportOutboxPhotoData,
        index: Int,
    ): HazardReportPreparedImage?
}

object NoOpHazardReportImageUploader : HazardReportImageUploader {
    override suspend fun uploadAll(
        accessToken: String,
        photos: List<ReportOutboxPhotoData>,
        alreadyUploadedCount: Int,
    ): HazardReportImageUploadResult =
        HazardReportImageUploadResult(
            newlyUploadedObjectKeys = emptyList(),
            newlyUploadedThumbnailObjectKeys = emptyList(),
            allSucceeded = photos.size <= alreadyUploadedCount,
        )
}

class DefaultHazardReportImageUploader(
    private val remoteDataSource: HazardReportImagesRemoteDataSource,
    private val imageProcessor: HazardReportImageProcessor,
    private val uploadParallelism: Int = DEFAULT_UPLOAD_PARALLELISM,
) : HazardReportImageUploader {
    constructor(
        contentResolver: ContentResolver,
        remoteDataSource: HazardReportImagesRemoteDataSource,
        uploadParallelism: Int = DEFAULT_UPLOAD_PARALLELISM,
    ) : this(
        remoteDataSource = remoteDataSource,
        imageProcessor = AndroidHazardReportImageProcessor(contentResolver),
        uploadParallelism = uploadParallelism,
    )

    override suspend fun uploadAll(
        accessToken: String,
        photos: List<ReportOutboxPhotoData>,
        alreadyUploadedCount: Int,
    ): HazardReportImageUploadResult {
        if (photos.isEmpty()) {
            return HazardReportImageUploadResult(
                newlyUploadedObjectKeys = emptyList(),
                newlyUploadedThumbnailObjectKeys = emptyList(),
                allSucceeded = true,
            )
        }
        if (alreadyUploadedCount >= photos.size) {
            return HazardReportImageUploadResult(
                newlyUploadedObjectKeys = emptyList(),
                newlyUploadedThumbnailObjectKeys = emptyList(),
                allSucceeded = true,
            )
        }

        val pendingPhotos = photos.drop(alreadyUploadedCount)
        val preparedImages = buildList {
            pendingPhotos.forEachIndexed { index, photo ->
                val prepared =
                    try {
                        imageProcessor.prepare(photo = photo, index = alreadyUploadedCount + index)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (apiException: HazardReportsApiException) {
                        if (apiException.httpStatusCode == HTTP_UNAUTHORIZED) throw apiException
                        Log.w(IMAGE_UPLOADER_LOG_TAG, "Image preparation failed at index ${alreadyUploadedCount + index}", apiException)
                        null
                    } catch (other: Throwable) {
                        Log.w(IMAGE_UPLOADER_LOG_TAG, "Image preparation failed at index ${alreadyUploadedCount + index}", other)
                        null
                    }
                if (prepared == null) {
                    return HazardReportImageUploadResult(
                        newlyUploadedObjectKeys = emptyList(),
                        newlyUploadedThumbnailObjectKeys = emptyList(),
                        allSucceeded = false,
                    )
                }
                add(prepared)
            }
        }

        val originalUploads =
            remoteDataSource.requestPresignedUploadBatch(
                accessToken = accessToken,
                request =
                    PresignedUploadBatchRequestDto(
                        files =
                            preparedImages.map { prepared ->
                                prepared.original.toPresignedRequest(prepared.fileName)
                            },
                    ),
            ).uploads
        val thumbnailUploads =
            remoteDataSource.requestPresignedUploadBatch(
                accessToken = accessToken,
                request =
                    PresignedUploadBatchRequestDto(
                        files =
                            preparedImages.map { prepared ->
                                prepared.thumbnail.toPresignedRequest(prepared.fileName.thumbnailFileName())
                            },
                    ),
            ).uploads

        val uploadResults =
            uploadPreparedImages(
                preparedImages = preparedImages,
                originalUploads = originalUploads,
                thumbnailUploads = thumbnailUploads,
            )

        val uploadedObjectKeys = mutableListOf<String>()
        val uploadedThumbnailObjectKeys = mutableListOf<String>()
        var allSucceeded = true
        for (result in uploadResults) {
            if (result == null) {
                allSucceeded = false
                break
            }
            uploadedObjectKeys += result.originalObjectKey
            uploadedThumbnailObjectKeys += result.thumbnailObjectKey
        }

        return HazardReportImageUploadResult(
            newlyUploadedObjectKeys = uploadedObjectKeys,
            newlyUploadedThumbnailObjectKeys = uploadedThumbnailObjectKeys,
            allSucceeded = allSucceeded,
        )
    }

    private suspend fun uploadPreparedImages(
        preparedImages: List<HazardReportPreparedImage>,
        originalUploads: List<PresignedUploadResponseDto>,
        thumbnailUploads: List<PresignedUploadResponseDto>,
    ): List<UploadedImagePair?> = coroutineScope {
        val semaphore = Semaphore(uploadParallelism.coerceAtLeast(1))
        preparedImages.mapIndexed { index, prepared ->
            async {
                semaphore.withPermit {
                    uploadPreparedImagePair(
                        prepared = prepared,
                        originalUpload = originalUploads.getOrNull(index),
                        thumbnailUpload = thumbnailUploads.getOrNull(index),
                    )
                }
            }
        }.awaitAll()
    }

    private suspend fun uploadPreparedImagePair(
        prepared: HazardReportPreparedImage,
        originalUpload: PresignedUploadResponseDto?,
        thumbnailUpload: PresignedUploadResponseDto?,
    ): UploadedImagePair? {
        val originalTarget = originalUpload ?: return null
        val thumbnailTarget = thumbnailUpload ?: return null
        val originalUploaded =
            remoteDataSource.uploadBinary(
                uploadUrl = originalTarget.uploadUrl,
                contentType = prepared.original.contentType,
                body = prepared.original.body,
            )
        if (!originalUploaded) return null

        val thumbnailUploaded =
            remoteDataSource.uploadBinary(
                uploadUrl = thumbnailTarget.uploadUrl,
                contentType = prepared.thumbnail.contentType,
                body = prepared.thumbnail.body,
            )
        if (!thumbnailUploaded) return null

        return UploadedImagePair(
            originalObjectKey = originalTarget.objectKey,
            thumbnailObjectKey = thumbnailTarget.objectKey,
        )
    }

    private fun HazardReportPreparedImageVariant.toPresignedRequest(fileName: String) =
        PresignedUploadRequestDto(
            fileName = fileName,
            contentType = contentType,
            contentLength = body.size.toLong(),
        )

    private data class UploadedImagePair(
        val originalObjectKey: String,
        val thumbnailObjectKey: String,
    )

    private companion object {
        private const val IMAGE_UPLOADER_LOG_TAG = "HazardReportImageUploader"
        private const val DEFAULT_UPLOAD_PARALLELISM = 3
        private const val HTTP_UNAUTHORIZED = 401
    }
}

class AndroidHazardReportImageProcessor(
    private val contentResolver: ContentResolver,
) : HazardReportImageProcessor {
    override suspend fun prepare(
        photo: ReportOutboxPhotoData,
        index: Int,
    ): HazardReportPreparedImage? {
        val uri = Uri.parse(photo.localUri)
        val sourceBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val sourceMimeType = photo.mimeType ?: contentResolver.getType(uri) ?: DEFAULT_MIME_TYPE
        val sourceFileName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "hazard_report_$index.jpg"
        val bitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
        if (bitmap == null) {
            return HazardReportPreparedImage(
                fileName = sourceFileName.ensureJpegFileName(index),
                original = HazardReportPreparedImageVariant(contentType = sourceMimeType, body = sourceBytes),
                thumbnail = HazardReportPreparedImageVariant(contentType = sourceMimeType, body = sourceBytes),
            )
        }

        val rotatedBitmap = bitmap.rotateIfNeeded(exifOrientation(sourceBytes))
        val originalBitmap = rotatedBitmap.scaleDown(ORIGINAL_MAX_DIMENSION_PX)
        val thumbnailBitmap = rotatedBitmap.scaleDown(THUMBNAIL_MAX_DIMENSION_PX)
        val originalBytes = originalBitmap.toJpegBytes(ORIGINAL_JPEG_QUALITY)
        val thumbnailBytes = thumbnailBitmap.toJpegBytes(THUMBNAIL_JPEG_QUALITY)

        if (bitmap !== rotatedBitmap) bitmap.recycle()
        if (rotatedBitmap !== originalBitmap && rotatedBitmap !== thumbnailBitmap) rotatedBitmap.recycle()
        if (originalBitmap !== thumbnailBitmap) originalBitmap.recycle()
        thumbnailBitmap.recycle()

        return HazardReportPreparedImage(
            fileName = sourceFileName.ensureJpegFileName(index),
            original = HazardReportPreparedImageVariant(contentType = JPEG_MIME_TYPE, body = originalBytes),
            thumbnail = HazardReportPreparedImageVariant(contentType = JPEG_MIME_TYPE, body = thumbnailBytes),
        )
    }

    private fun exifOrientation(sourceBytes: ByteArray): Int =
        runCatching {
            ExifInterface(ByteArrayInputStream(sourceBytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun Bitmap.rotateIfNeeded(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.scaleDown(maxDimensionPx: Int): Bitmap {
        val largestDimension = maxOf(width, height)
        if (largestDimension <= maxDimensionPx) return this
        val scale = maxDimensionPx.toFloat() / largestDimension.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun Bitmap.toJpegBytes(quality: Int): ByteArray =
        ByteArrayOutputStream().use { output ->
            compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        }

    private companion object {
        private const val DEFAULT_MIME_TYPE = "image/jpeg"
        private const val JPEG_MIME_TYPE = "image/jpeg"
        private const val ORIGINAL_MAX_DIMENSION_PX = 1600
        private const val THUMBNAIL_MAX_DIMENSION_PX = 512
        private const val ORIGINAL_JPEG_QUALITY = 82
        private const val THUMBNAIL_JPEG_QUALITY = 72
    }
}

private fun String.ensureJpegFileName(index: Int): String {
    val baseName = substringBeforeLast('.').takeIf { it.isNotBlank() } ?: "hazard_report_$index"
    return "$baseName.jpg"
}

private fun String.thumbnailFileName(): String = "${substringBeforeLast('.')}_thumb.jpg"
