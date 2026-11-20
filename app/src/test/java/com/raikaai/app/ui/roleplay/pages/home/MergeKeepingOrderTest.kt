package com.raikaai.app.ui.roleplay.pages.home

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeKeepingOrderTest {
    private fun c(id: Int, avatar: String = "a$id") =
        RoleplayCharacter(id, "n$id", "s$id", null, null, null, avatar, 1)

    @Test
    fun keepsCachedOrderAndTakesFreshFields() {
        val cached = listOf(c(3), c(1), c(2))
        val fresh = listOf(c(1, "new"), c(2), c(3))
        val merged = RoleplayHomeViewModel.mergeKeepingOrder(cached, fresh)
        assertEquals(listOf(3, 1, 2), merged.map { it.id })
        assertEquals("new", merged[1].avatarUrl)
    }

    @Test
    fun dropsRemovedAndAppendsNew() {
        val merged = RoleplayHomeViewModel.mergeKeepingOrder(listOf(c(2), c(1)), listOf(c(1), c(4)))
        assertEquals(listOf(1, 4), merged.map { it.id })
    }
}
