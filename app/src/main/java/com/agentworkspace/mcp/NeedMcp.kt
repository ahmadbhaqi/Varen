package com.agentworkspace.mcp

/**
 * Connection configuration for NeedMCP - the hosted MCP server marketplace
 * that powers the agent's external tool surface.
 *
 * Endpoint discovered via https://needmcp.com (MCP server "need-mcp" v1.0.0).
 * Authentication uses a personal API key sent as a Bearer token, matching the
 * site's /api-keys flow.
 */
object NeedMcp {
    /** Streamable-HTTP MCP endpoint. */
    const val ENDPOINT = "https://needmcp.com/mcp"

    /** Stable identifier used to store the API key in the encrypted vault. */
    const val CONNECTION_ID = "needmcp"
    const val UI_STUDIO_TITLE = "UI Studio"

    const val DISPLAY_NAME = "NeedMCP"
    const val TAGLINE = "Frontend & UI design tools"

    /** Protocol versions we are willing to negotiate during initialize. */
    val PROTOCOL_VERSIONS = listOf("2024-11-05", "2025-03-26", "2025-06-18")

    const val CLIENT_NAME = "Varen"
    const val CLIENT_VERSION = "1.0.0"
}
