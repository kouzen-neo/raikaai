package com.raikaai.app.rag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RagScopeTest {
    @Test
    fun memoryStaysInItsOwnChat() {
        assertTrue(chunkInScope(isMemory = true, docId = "mem_a", includeMemory = true, chatId = "a"))
        assertFalse(chunkInScope(isMemory = true, docId = "mem_b", includeMemory = true, chatId = "a"))
    }

    @Test
    fun noChatIdMeansNoMemory() {
        assertFalse(chunkInScope(isMemory = true, docId = "mem_a", includeMemory = true, chatId = null))
        assertFalse(chunkInScope(isMemory = true, docId = "mem_a", includeMemory = true, chatId = " "))
    }

    @Test
    fun documentsAndMemoryNeverMix() {
        assertTrue(chunkInScope(isMemory = false, docId = "doc1", includeMemory = false, chatId = "a"))
        assertTrue(chunkInScope(isMemory = false, docId = "doc1", includeMemory = false, chatId = null))
        assertFalse(chunkInScope(isMemory = true, docId = "mem_a", includeMemory = false, chatId = "a"))
        assertFalse(chunkInScope(isMemory = false, docId = "doc1", includeMemory = true, chatId = "a"))
    }
}
