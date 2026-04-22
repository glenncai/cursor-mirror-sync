package com.glenncai.cursormirrorsync.core.models

import com.google.gson.Gson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Verifies the wire-level compatibility of EditorState with the VSCode side,
 * which uses the `sourceIde` field name while JetBrains keeps the Kotlin
 * property `source` for internal use.
 */
class EditorStateSerializationTest {

    private val gson = Gson()

    @Test
    fun `deserializes sourceIde from VSCode into source property`() {
        val json = """
            {
              "filePath": "/workspace/project/src/main.kt",
              "line": 10,
              "column": 5,
              "sourceIde": "vscode",
              "isActive": true,
              "hasSelection": false
            }
        """.trimIndent()

        val state = gson.fromJson(json, EditorState::class.java)

        assertNotNull(state)
        assertEquals("vscode", state.source, "sourceIde on the wire must bind to the source property")
        assertEquals("/workspace/project/src/main.kt", state.filePath)
        assertEquals(10, state.line)
        assertEquals(5, state.column)
        assertTrue(state.isActive)
    }

    @Test
    fun `serializes source property to sourceIde on the wire`() {
        val state = EditorState(
            filePath = "/workspace/project/src/main.kt",
            line = 3,
            column = 7,
            source = "jetbrains",
            isActive = true
        )

        val json = gson.toJson(state)

        assertTrue(json.contains("\"sourceIde\":\"jetbrains\""), "Serialized JSON must use sourceIde, got: $json")
        assertFalse(json.contains("\"source\":"), "Serialized JSON must not emit the internal source field name, got: $json")
    }

    @Test
    fun `source stays null when wire payload omits sourceIde`() {
        val json = """
            {
              "filePath": "/workspace/project/src/main.kt",
              "line": 0,
              "column": 0,
              "isActive": true
            }
        """.trimIndent()

        val state = gson.fromJson(json, EditorState::class.java)

        assertNotNull(state)
        assertNull(state.source, "Missing sourceIde should not be filled by the Kotlin default when Gson constructs via Unsafe")
    }

    @Test
    fun `legacy source field on the wire is ignored`() {
        val json = """
            {
              "filePath": "/workspace/project/src/main.kt",
              "line": 1,
              "column": 2,
              "source": "vscode",
              "isActive": true
            }
        """.trimIndent()

        val state = gson.fromJson(json, EditorState::class.java)

        assertNotNull(state)
        assertNull(state.source, "Only sourceIde is honoured; the legacy source key must be ignored")
    }

    @Test
    fun `echo filter treats VSCode messages as non-JetBrains`() {
        val json = """
            {
              "filePath": "/workspace/project/src/main.kt",
              "line": 0,
              "column": 0,
              "sourceIde": "vscode",
              "isActive": true
            }
        """.trimIndent()

        val state = gson.fromJson(json, EditorState::class.java)

        assertNotEquals("jetbrains", state.source, "MessageHandlerImpl echo filter must pass VSCode messages through")
    }
}
