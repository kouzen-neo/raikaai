package com.raikaai.app.core.image

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageRequestTest {
    private fun parse(json: String) = ImageRequest.fromArgs(JsonParser.parseString(json).asJsonObject)

    @Test
    fun singlePrompt() {
        val r = parse("""{"prompt": "a cat", "caption": "Here!"}""")!!
        assertEquals(listOf("a cat"), r.prompts)
        assertEquals("Here!", r.caption)
        assertNull(r.self)
        assertEquals(false, parse("""{"prompt": "a cat", "self": false}""")!!.self)
    }

    @Test
    fun countRepeatsPromptAndIsCapped() {
        assertEquals(3, parse("""{"prompt": "a cat", "count": 3}""")!!.prompts.size)
        assertEquals(ImageRequest.MAX_IMAGES, parse("""{"prompt": "a cat", "count": 20}""")!!.prompts.size)
        assertEquals(1, parse("""{"prompt": "a cat", "count": 0}""")!!.prompts.size)
        assertEquals(2, parse("""{"prompt": "a cat", "count": "2"}""")!!.prompts.size)
    }

    @Test
    fun promptsListWins() {
        val r = parse("""{"prompts": ["beach", " ", "park", "a", "b", "c"], "prompt": "x", "self": true}""")!!
        assertEquals(listOf("beach", "park", "a", "b"), r.prompts)
        assertEquals(true, r.self)
    }

    @Test
    fun fallbackKeysAndEmpty() {
        assertEquals(listOf("dog"), parse("""{"description": "dog"}""")!!.prompts)
        assertNull(parse("""{"caption": "hi"}"""))
        assertNull(parse("""{"prompt": "   "}"""))
    }

    @Test
    fun toolBlockMentionsSelfOnlyForCharacters() {
        assertTrue(ImagePrompt.toolBlock("Colette").contains("\"self\""))
        assertFalse(ImagePrompt.toolBlock(null).contains("\"self\""))
    }
}

class ImageResponseTest {
    @Test
    fun imagesEndpoint() {
        assertEquals("data:image/png;base64,QUJD", ImageResponse.find("""{"data":[{"b64_json":"QUJD"}]}"""))
        assertEquals("https://x/y.png", ImageResponse.find("""{"data":[{"url":"https://x/y.png"}]}"""))
    }

    @Test
    fun chatImagesField() {
        val body = """{"choices":[{"message":{"content":"here","images":[{"type":"image_url","image_url":{"url":"data:image/png;base64,QUJD"}}]}}]}"""
        assertEquals("data:image/png;base64,QUJD", ImageResponse.find(body))
    }

    @Test
    fun chatContentPartsAndText() {
        val parts = """{"choices":[{"message":{"content":[{"type":"text","text":"ok"},{"type":"image_url","image_url":{"url":"https://h/i.png"}}]}}]}"""
        assertEquals("https://h/i.png", ImageResponse.find(parts))
        val md = """{"choices":[{"message":{"content":"Sure ![img](https://h/a.png)"}}]}"""
        assertEquals("https://h/a.png", ImageResponse.find(md))
        val inline = """{"choices":[{"message":{"content":"![x](data:image/jpeg;base64,QUJD)"}}]}"""
        assertEquals("data:image/jpeg;base64,QUJD", ImageResponse.find(inline))
    }

    @Test
    fun textOnlyReplyHasNoImage() {
        assertNull(ImageResponse.find("""{"choices":[{"message":{"content":"I can't draw"}}]}"""))
        assertNull(ImageResponse.find("not json"))
    }
}
