package com.raikaai.app.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PiAgentRuntimeTest {

    @Test
    fun assistantStreamMapsToAssistantDelta() {
        val event = JSONObject()
            .put("type", "assistant-stream")
            .put("at", 1_700_000_000_000L)
            .put("delta", "Hello\u2028world!")
            .put("contentIndex", 0)

        assertEquals(RuntimeEvent.AssistantDelta("Hello\u2028world!"), translateEvent(event))
    }

    @Test
    fun toolCallMapsToToolCallWithJsonArguments() {
        val event = JSONObject()
            .put("type", "tool/call")
            .put("id", "call_fake_1")
            .put("name", "web_search")
            .put("args", JSONObject().put("query", "pi rpc"))

        assertEquals(
            RuntimeEvent.ToolCall("web_search", """{"query":"pi rpc"}"""),
            translateEvent(event),
        )
    }

    @Test
    fun approvalRequestMapsToApprovalRequestFromNestedRecord() {
        val event = JSONObject()
            .put("type", "approval/request")
            .put("id", "approval_fake_1")
            .put(
                "request",
                JSONObject()
                    .put("type", "approval_request")
                    .put("toolName", "bash")
                    .put("args", JSONObject().put("command", "echo hi"))
                    .put("reason", "shell access"),
            )

        val translated = translateEvent(event)
        assertTrue(translated is RuntimeEvent.ApprovalRequest)
        val approval = translated as RuntimeEvent.ApprovalRequest
        assertEquals("bash", approval.name)
        assertEquals("""{"command":"echo hi"}""", approval.argumentsJson)
        assertEquals("shell access", approval.reason)
    }

    @Test
    fun turnEndMapsToTurnEndFromMessageContent() {
        val event = JSONObject()
            .put("type", "turn/end")
            .put(
                "message",
                JSONObject()
                    .put("role", "assistant")
                    .put("content", "Hello world!"),
            )

        assertEquals(RuntimeEvent.TurnEnd("Hello world!"), translateEvent(event))
    }

    @Test
    fun turnEndJoinsStructuredContentBlocks() {
        val event = JSONObject()
            .put("type", "turn/end")
            .put(
                "message",
                JSONObject().put(
                    "content",
                    JSONArray()
                        .put(JSONObject().put("type", "text").put("text", "Hello "))
                        .put(JSONObject().put("type", "text").put("text", "world")),
                ),
            )

        assertEquals(RuntimeEvent.TurnEnd("Hello world"), translateEvent(event))
    }

    @Test
    fun unknownAndTypelessEventsAreIgnored() {
        assertNull(
            translateEvent(
                JSONObject()
                    .put("type", "settled")
                    .put("aborted", false),
            ),
        )
        assertNull(translateEvent(JSONObject().put("delta", "no type here")))
    }

    @Test
    fun promptBodyHasOpenAiMessageShape() {
        val body = JSONObject(
            buildPromptBody(
                listOf(
                    AgentMessage("system", "Be brief."),
                    AgentMessage("user", "Hi"),
                ),
            ),
        )
        val messages = body.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("Be brief.", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("Hi", messages.getJSONObject(1).getString("content"))
    }
}
