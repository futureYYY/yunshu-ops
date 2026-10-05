package com.rackexcel.mobile

import kotlin.test.Test
import kotlin.test.assertEquals

class GalleryIndicatorPagesTest {
    @Test
    fun galleryIndicatorPages_showsEveryPageForGalleriesWithAtMostEightImages() {
        assertEquals((0 until 8).toList(), galleryIndicatorPages(totalPages = 8, currentPage = 5))
    }

    @Test
    fun galleryIndicatorPages_keepsTheCurrentPageInsideAFivePageWindowForLongGalleries() {
        assertEquals(listOf(3, 4, 5, 6, 7), galleryIndicatorPages(totalPages = 12, currentPage = 5))
        assertEquals(listOf(0, 1, 2, 3, 4), galleryIndicatorPages(totalPages = 12, currentPage = 0))
        assertEquals(listOf(7, 8, 9, 10, 11), galleryIndicatorPages(totalPages = 12, currentPage = 11))
    }
}
