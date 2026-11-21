package com.raikaai.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the conversation tree (branching) logic.
 * Mirrors exactly what ChatViewModel does when sending, editing
 * (forking), and regenerating messages.
 */
class ConversationTreeTest {

    private val gson = Gson()

    private fun node(
        role: String,
        content: String,
        ts: Long,
        parent: ChatMessageEntry? = null,
    ) = ChatMessageEntry(
        role = role,
        content = content,
        timestamp = ts,
        parentId = parent?.id,
    )

    @Test
    fun `linear conversation resolves in insertion order`() {
        val g = node("assistant", "hi", 1)
        val u = node("user", "hello", 2, g)
        val a = node("assistant", "hey", 3, u)
        val conv = Conversation(messages = listOf(g, u, a))

        val path = ChatRepository.resolveActivePath(conv)

        assertEquals(listOf(g.id, u.id, a.id), path.map { it.id })
    }

    @Test
    fun `editing last user message forks and preserves old branch`() {
        val g = node("assistant", "hi", 1)
        val oldU = node("user", "original question", 2, g)
        val oldA = node("assistant", "original answer", 3, oldU)
        val conv =
            Conversation(
                messages = mutableListOf(g, oldU, oldA),
                branchActive = mutableMapOf(),
            )

        // ChatViewModel.editUserMessageAndRegenerate at index of oldU:
        val forked = oldU.copy(content = "edited question", timestamp = 100, id = "fork-u")
        conv.messages = conv.messages + forked
        (conv.branchActive ?: mutableMapOf())[forked.parentId ?: ChatRepository.ROOT_KEY] = forked.id!!
        // old tail stays in nodes; new assistant reply under the fork:
        val newA = node("assistant", "edited answer", 101, forked)
        conv.messages = conv.messages + newA
        (conv.branchActive ?: mutableMapOf())[forked.id!!] = newA.id!!

        // Old branch data must be preserved:
        assertTrue(conv.messages.any { it.id == oldU.id && it.content == "original question" })
        assertTrue(conv.messages.any { it.id == oldA.id })

        // Active path follows the fork:
        val path = ChatRepository.resolveActivePath(conv)
        assertEquals(listOf(g.id, forked.id, newA.id), path.map { it.id })

        // Pill "<n/m>": edited message must report exactly 2 siblings.
        val sibs = ChatRepository.siblingsOf(conv, forked)
        assertEquals(2, sibs.size)
        assertEquals(setOf(oldU.id, forked.id), sibs.map { it.id }.toSet())
    }

    @Test
    fun `regenerate creates assistant siblings and switching restores old branch`() {
        val u = node("user", "q", 1)
        val a1 = node("assistant", "answer v1", 2, u)
        val conv = Conversation(messages = mutableListOf(u, a1), branchActive = mutableMapOf())

        // Regenerate #1: new node under same parent, becomes active child.
        val a2 = node("assistant", "answer v2", 3, u)
        conv.messages = conv.messages + a2
        (conv.branchActive ?: mutableMapOf())[u.id!!] = a2.id!!

        assertEquals(2, ChatRepository.siblingsOf(conv, a2).size)
        assertEquals("answer v2", ChatRepository.resolveActivePath(conv).last().content)

        // Switch branch back to v1 via prev arrow:
        (conv.branchActive ?: mutableMapOf())[u.id!!] = a1.id!!
        assertEquals("answer v1", ChatRepository.resolveActivePath(conv).last().content)
    }

    @Test
    fun `legacy json without ids still loads linearly`() {
        val legacyJson =
            """
            {
              "id": "c1",
              "title": "Old Chat",
              "createdAt": 1,
              "updatedAt": 2,
              "messages": [
                {"role":"assistant","content":"greeting","timestamp":10,"stats":"P"},
                {"role":"user","content":"question","timestamp":11},
                {"role":"assistant","content":"answer","timestamp":12}
              ]
            }
            """.trimIndent()

        val conv = gson.fromJson(legacyJson, Conversation::class.java)
        assertNotNull(conv)

        ChatRepository.normalize(conv)

        val path = ChatRepository.resolveActivePath(conv)
        assertEquals(3, path.size)
        assertEquals(listOf("greeting", "question", "answer"), path.map { it.content })
        // Every node now has a stable id and chained parent:
        assertTrue(path.all { !it.id.isNullOrBlank() })
        assertEquals(path[0].id, path[1].parentId)
        assertEquals(path[1].id, path[2].parentId)
    }

    @Test
    fun `branch state survives gson round trip`() {
        val u = node("user", "q", 1)
        val a1 = node("assistant", "v1", 2, u)
        val a2 = node("assistant", "v2", 3, u)
        val conv =
            Conversation(
                title = "t",
                messages = listOf(u, a1, a2),
                branchActive = mutableMapOf(u.id!! to a2.id!!),
            )
        val conv2 = gson.fromJson(gson.toJson(conv), Conversation::class.java)

        val path = ChatRepository.resolveActivePath(conv2)
        assertEquals("v2", path.last().content)

        // And switching after reload works:
        conv2.branchActive?.set(u.id!!, a1.id!!)
        assertEquals("v1", ChatRepository.resolveActivePath(conv2).last().content)
    }

    @Test
    fun `pruneUnreachableNodes preserves multi-turn inactive branches and drops orphans`() {
        val g = node("assistant", "hi", 1)
        val oldU1 = node("user", "old q1", 2, g)
        val oldA1 = node("assistant", "old a1", 3, oldU1)
        val oldU2 = node("user", "old q2", 4, oldA1)
        val oldA2 = node("assistant", "old a2", 5, oldU2)
        val forkU1 = node("user", "new q1", 10, g)
        val newA1 = node("assistant", "new a1", 11, forkU1)
        val orphan = ChatMessageEntry(role = "assistant", content = "detached", timestamp = 99, parentId = "missing-parent")

        val kept = ChatRepository.pruneUnreachableNodes(listOf(g, oldU1, oldA1, oldU2, oldA2, forkU1, newA1, orphan))
        val keptIds = kept.map { it.id }.toSet()

        assertEquals(7, kept.size)
        assertTrue(oldA1.id in keptIds)
        assertTrue(oldU2.id in keptIds)
        assertTrue(oldA2.id in keptIds)
        assertTrue(newA1.id in keptIds)
        assertTrue(orphan.id !in keptIds)

        // Switching back to oldU1 restores the entire multi-turn old branch:
        val conv = Conversation(messages = kept, branchActive = mutableMapOf(g.id!! to oldU1.id!!))
        assertEquals(listOf(g.id, oldU1.id, oldA1.id, oldU2.id, oldA2.id), ChatRepository.resolveActivePath(conv).map { it.id })
    }
}
