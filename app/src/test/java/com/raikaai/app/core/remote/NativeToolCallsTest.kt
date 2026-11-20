package com.raikaai.app.core.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeToolCallsTest {
    /** All `<tool_call>` blocks in [text], parsed. */
    private fun calls(text: String): List<JsonObject> =
        Regex("""<tool_call>([\s\S]*?)</tool_call>""").findAll(text).map { JsonParser.parseString(it.groupValues[1]).asJsonObject }.toList()

    private fun assertOneCall(text: String, name: String, key: String, value: String) {
        val c = calls(NativeToolCalls.convert(text))
        assertEquals("expected one call in: $text", 1, c.size)
        assertEquals(name, c[0].get("name").asString)
        assertEquals(value, c[0].getAsJsonObject("arguments").get(key).asString)
    }

    private val dsml =
        "<｜DSML｜function_calls>\n<｜DSML｜invoke name=\"execute_in_container\">\n" +
            "<｜DSML｜parameter name=\"command\" string=\"true\">cd /workspace && git clone https://x/y.git 2>&1 | tail -20</｜DSML｜parameter>\n" +
            "</｜DSML｜invoke>\n</｜DSML｜function_calls>"

    @Test
    fun deepseekDsml() {
        assertOneCall(dsml, "execute_in_container", "command", "cd /workspace && git clone https://x/y.git 2>&1 | tail -20")
        assertFalse(NativeToolCalls.convert(dsml).contains("DSML"))
    }

    @Test
    fun dsmlNonStringParametersKeepTheirType() {
        val t = "<｜DSML｜invoke name=\"f\"><｜DSML｜parameter name=\"n\" string=\"false\">5</｜DSML｜parameter></｜DSML｜invoke>"
        assertEquals(5, calls(NativeToolCalls.convert(t))[0].getAsJsonObject("arguments").get("n").asInt)
    }

    @Test
    fun dsmlWithAsciiPipes() {
        assertOneCall("<|DSML|invoke name=\"f\"><|DSML|parameter name=\"a\" string=\"true\">x</|DSML|parameter></|DSML|invoke>", "f", "a", "x")
    }

    @Test
    fun deepseekV3Original() {
        val t = "<｜tool▁calls▁begin｜><｜tool▁call▁begin｜>function<｜tool▁sep｜>get_weather\n```json\n{\"city\": \"Paris\"}\n```<｜tool▁call▁end｜><｜tool▁calls▁end｜>"
        assertOneCall(t, "get_weather", "city", "Paris")
        assertFalse(NativeToolCalls.convert(t).contains("tool▁"))
    }

    @Test
    fun claudeStyleInvoke() {
        assertOneCall("<function_calls><invoke name=\"f\"><parameter name=\"a\">x</parameter></invoke></function_calls>", "f", "a", "x")
    }

    @Test
    fun qwen3CoderXml() {
        val t = "<tool_call>\n<function=execute_in_container>\n<parameter=command>\nls -la\n</parameter>\n</function>\n</tool_call>"
        assertOneCall(t, "execute_in_container", "command", "ls -la")
    }

    @Test
    fun llamaFunctionTagWithJsonBody() {
        assertOneCall("<function=get_time>{\"tz\": \"UTC\"}</function>", "get_time", "tz", "UTC")
    }

    @Test
    fun glmArgKeyValue() {
        val t = "<tool_call>execute_in_container\n<arg_key>command</arg_key>\n<arg_value>ls -la</arg_value>\n</tool_call>"
        assertOneCall(t, "execute_in_container", "command", "ls -la")
    }

    @Test
    fun llamaPythonTag() {
        val t = "Let me check. <|python_tag|>{\"name\": \"execute_in_container\", \"parameters\": {\"command\": \"ls\"}}<|eom_id|>"
        val out = NativeToolCalls.convert(t)
        assertTrue(out.startsWith("Let me check."))
        assertOneCall(t, "execute_in_container", "command", "ls")
        assertFalse(out.contains("eom_id"))
    }

    @Test
    fun llamaBareJsonReply() {
        assertOneCall("{\"name\": \"execute_in_container\", \"parameters\": {\"command\": \"ls\"}}", "execute_in_container", "command", "ls")
    }

    @Test
    fun mistralArrayAndNamedForms() {
        assertOneCall("[TOOL_CALLS] [{\"name\": \"f\", \"arguments\": {\"a\": \"1\"}}]", "f", "a", "1")
        assertOneCall("[TOOL_CALLS]f[ARGS]{\"a\": \"1\"}", "f", "a", "1")
    }

    @Test
    fun kimiK2() {
        val t = "<|tool_calls_section_begin|><|tool_call_begin|>functions.f:0<|tool_call_argument_begin|>{\"a\":\"1\"}<|tool_call_end|><|tool_calls_section_end|>"
        assertOneCall(t, "f", "a", "1")
        assertFalse(NativeToolCalls.convert(t).contains("section"))
    }

    @Test
    fun gptOssHarmony() {
        assertOneCall("<|channel|>commentary to=functions.f <|constrain|>json<|message|>{\"a\":\"1\"}<|call|>", "f", "a", "1")
    }

    @Test
    fun multipleCallsInOneReply() {
        val t = "<|python_tag|>{\"name\": \"a\", \"parameters\": {}}; {\"name\": \"b\", \"parameters\": {}}"
        assertEquals(listOf("a", "b"), calls(NativeToolCalls.convert(t)).map { it.get("name").asString })
    }

    @Test
    fun hermesJsonAndPlainTextAreUntouched() {
        val hermes = "<tool_call>{\"name\":\"f\",\"arguments\":{}}</tool_call>"
        assertEquals(hermes, NativeToolCalls.convert(hermes))
        assertEquals("hello <b>world</b>", NativeToolCalls.convert("hello <b>world</b>"))
        assertEquals("{\"a\": 1}", NativeToolCalls.convert("{\"a\": 1}"))
    }

    private fun stream(text: String, chunk: Int): String {
        val f = NativeToolCalls.Filter()
        val sb = StringBuilder()
        text.chunked(chunk).forEach { sb.append(f.push(it)) }
        sb.append(f.finish())
        return sb.toString()
    }

    @Test
    fun streamingConvertsAtAnyChunkSize() {
        for (size in listOf(1, 2, 3, 7, 50)) {
            val out = stream("Baik, saya jalankan. $dsml", size)
            assertTrue("chunk $size", out.startsWith("Baik, saya jalankan. "))
            assertEquals("chunk $size", 1, calls(out).size)
            assertFalse("chunk $size", out.contains("DSML"))
        }
    }

    @Test
    fun streamingLlamaAndBareJson() {
        assertEquals(1, calls(stream("<|python_tag|>{\"name\": \"f\", \"parameters\": {\"a\": \"1\"}}", 4)).size)
        assertEquals(1, calls(stream("{\"name\": \"f\", \"parameters\": {\"a\": \"1\"}}", 3)).size)
    }

    @Test
    fun streamingLeavesOrdinaryTextAlone() {
        for (size in listOf(1, 3, 9)) {
            assertEquals("a < b and <tag> ok [1] <function x", stream("a < b and <tag> ok [1] <function x", size))
            assertEquals("{\"a\": 1} is json", stream("{\"a\": 1} is json", size))
            val hermes = "<tool_call>{\"name\":\"f\",\"arguments\":{}}</tool_call>"
            assertEquals(hermes, stream(hermes, size))
        }
    }
}
