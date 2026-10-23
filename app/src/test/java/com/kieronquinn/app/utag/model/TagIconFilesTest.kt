package com.kieronquinn.app.utag.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TagIconFilesTest {

    @Test
    fun keepsSafeCharacters() {
        assertEquals("tag-01_a.b.png", TagIconFiles.fileName("tag-01_a.b"))
    }

    @Test
    fun replacesPathCharacters() {
        assertEquals("abc_.._x_y.png", TagIconFiles.fileName("abc/../x y"))
    }

    @Test
    fun emptyIdStillHasAFileName() {
        assertEquals("tag.png", TagIconFiles.fileName(""))
    }

}
