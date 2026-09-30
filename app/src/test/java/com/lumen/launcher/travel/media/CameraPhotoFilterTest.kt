package com.lumen.launcher.travel.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPhotoFilterTest {
    @Test fun picturesRequiresCameraOwnership() {
        assertTrue(MediaStorePhotoRepository.isCameraPhoto("Pictures/", "IMG_123.jpg", "image/jpeg", true))
        assertFalse(MediaStorePhotoRepository.isCameraPhoto("Pictures/", "IMG_123.jpg", "image/jpeg"))
        assertFalse(MediaStorePhotoRepository.isCameraPhoto("Pictures/", "Screenshot_123.png", "image/png", true))
    }
    @Test fun cameraFoldersAndNestedLensesAreIncluded() {
        listOf("DCIM/Camera/", "DCIM/Camera/Telephoto/", "DCIM/100ANDRO/", "DCIM/OpenCamera/").forEach {
            assertTrue(it, MediaStorePhotoRepository.isCameraPhoto(it, "IMG_123.jpg", "image/jpeg"))
        }
    }

    @Test fun similarlyNamedFoldersAreNotCameraCaptures() {
        listOf("DCIM/CameraBackup/", "DCIM/Camera-edited/", "DCIM/100android/").forEach {
            assertFalse(it, MediaStorePhotoRepository.isCameraPhoto(it, "IMG_123.jpg", "image/jpeg"))
        }
    }

    @Test fun screenshotsDownloadsAndVideosStayOutOfTrips() {
        assertFalse(MediaStorePhotoRepository.isCameraPhoto("DCIM/Camera", "Screenshot_123.png", "image/png"))
        assertFalse(MediaStorePhotoRepository.isCameraPhoto("Download", "IMG_123.jpg", "image/jpeg"))
        assertFalse(MediaStorePhotoRepository.isCameraPhoto("DCIM/Camera", "VID_123.mp4", "video/mp4"))
        assertFalse(MediaStorePhotoRepository.isCameraPhoto(null, "IMG_123.jpg", "image/jpeg"))
    }
}
