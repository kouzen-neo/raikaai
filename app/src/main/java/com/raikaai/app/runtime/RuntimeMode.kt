package com.raikaai.app.runtime

/**
 * Selects which product a user is running. The mode chooses the agent loop and
 * the tool set exposed to the UI; it never changes the LLM backend — the local
 * Ktor `/v1` server remains the single inference path in every mode.
 */
enum class RuntimeMode {
    /**
     * Default. No guest, no Pi, no terminal. Chat uses the on-device engine
     * directly and only Linux-free tools are available: web search, RAG,
     * image generation and HTTP MCP.
     */
    ON_DEVICE,

    /**
     * Opt-in. A KazeTerm guest runs Node + Pi, Pi owns the agent loop, and
     * file/shell/daemon tools become available. The guest is installed on
     * demand, never bundled in the base APK.
     */
    KAZETERM_PI,

    /**
     * Transition only. The legacy PRoot `ContainerManager`, to be deleted
     * once the KazeTerm path has passed device tests.
     */
    LEGACY_PROOT,
}
