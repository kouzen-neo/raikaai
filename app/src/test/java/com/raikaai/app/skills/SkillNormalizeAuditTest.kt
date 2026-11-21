package com.raikaai.app.skills

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit regression tests: legacy skill JSON (missing keys, explicit nulls)
 * must normalize to safe values — previously instructions.isNotBlank()
 * could NPE and crash the app at startup.
 */
class SkillNormalizeAuditTest {

    private val gson = Gson()

    @Test
    fun `legacy json without new fields normalizes safely`() {
        val list: List<Skill> =
            gson.fromJson(
                """[{"id":"s1","name":"Lama","instructions":"do x"}]""",
                object : TypeToken<List<Skill>>() {}.type,
            )
        val out = list.map { it.normalize() }

        assertEquals("s1", out[0].id)
        assertEquals("Lama", out[0].name)
        assertEquals("do x", out[0].instructions)
        assertEquals("code", out[0].iconCategory)
        assertEquals("General", out[0].category)
        assertEquals("", out[0].description)
    }

    @Test
    fun `explicit nulls fall back to safe defaults`() {
        val parsed = gson.fromJson(
            """{"id":null,"name":null,"description":null,"instructions":null,"iconCategory":null,"category":null}""",
            Skill::class.java,
        ).normalize()

        assertTrue(parsed.id.isNotBlank())
        assertEquals("Custom Skill", parsed.name)
        assertEquals("", parsed.instructions)
        assertFalse(parsed.isEnabled)
        assertFalse(parsed.isBuiltIn)
    }

    @Test
    fun `toMarkdown escapes quotes and newlines in YAML frontmatter`() {
        val skill = Skill(
            id = "s2",
            name = "My \"Quoted\" Skill",
            description = "Line 1\nLine \"2\"",
            instructions = "Do something.",
        )
        val md = skill.toMarkdown()
        assertTrue(md.contains("""name: "My \"Quoted\" Skill""""))
        assertTrue(md.contains("""description: "Line 1 Line \"2\"""""))
    }
}
