package com.droidrun.portal.mcp

import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP Protocol Models
 * Based on Model Context Protocol specification
 */

// JSON-RPC 2.0 Request
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: Any, // can be string or number
    val method: String,
    val params: JSONObject? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("jsonrpc", jsonrpc)
            put("id", id)
            put("method", method)
            params?.let { put("params", it) }
        }
    }
}

// JSON-RPC 2.0 Response
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: Any,
    val result: JSONObject? = null,
    val error: McpError? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("jsonrpc", jsonrpc)
            put("id", id)
            result?.let { put("result", it) }
            error?.let { put("error", it.toJson()) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): McpResponse {
            return McpResponse(
                jsonrpc = json.optString("jsonrpc", "2.0"),
                id = json.get("id"),
                result = json.optJSONObject("result"),
                error = json.optJSONObject("error")?.let { McpError.fromJson(it) }
            )
        }
    }
}

data class McpError(
    val code: Int,
    val message: String,
    val data: Any? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("code", code)
            put("message", message)
            data?.let { put("data", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): McpError {
            return McpError(
                code = json.getInt("code"),
                message = json.getString("message"),
                data = json.opt("data")
            )
        }
    }
}

// MCP Tool Definition
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JSONObject
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("inputSchema", inputSchema)
        }
    }
}

// MCP Initialize Request Parameters
data class McpInitializeParams(
    val protocolVersion: String = "2024-11-05",
    val capabilities: McpClientCapabilities = McpClientCapabilities(),
    val clientInfo: McpClientInfo
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("protocolVersion", protocolVersion)
            put("capabilities", capabilities.toJson())
            put("clientInfo", clientInfo.toJson())
        }
    }
}

data class McpClientCapabilities(
    val experimental: JSONObject? = null,
    val sampling: JSONObject? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            experimental?.let { put("experimental", it) }
            sampling?.let { put("sampling", it) }
        }
    }
}

data class McpClientInfo(
    val name: String,
    val version: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("version", version)
        }
    }
}

// MCP Initialize Result
data class McpInitializeResult(
    val protocolVersion: String,
    val capabilities: McpServerCapabilities,
    val serverInfo: McpServerInfo
) {
    companion object {
        fun fromJson(json: JSONObject): McpInitializeResult {
            return McpInitializeResult(
                protocolVersion = json.getString("protocolVersion"),
                capabilities = McpServerCapabilities.fromJson(json.getJSONObject("capabilities")),
                serverInfo = McpServerInfo.fromJson(json.getJSONObject("serverInfo"))
            )
        }
    }
}

data class McpServerCapabilities(
    val tools: JSONObject? = null,
    val experimental: JSONObject? = null
) {
    companion object {
        fun fromJson(json: JSONObject): McpServerCapabilities {
            return McpServerCapabilities(
                tools = json.optJSONObject("tools"),
                experimental = json.optJSONObject("experimental")
            )
        }
    }
}

data class McpServerInfo(
    val name: String,
    val version: String
) {
    companion object {
        fun fromJson(json: JSONObject): McpServerInfo {
            return McpServerInfo(
                name = json.getString("name"),
                version = json.getString("version")
            )
        }
    }
}

// Tool Call Request
data class McpToolCallParams(
    val name: String,
    val arguments: JSONObject?
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            arguments?.let { put("arguments", it) }
        }
    }
}

// Tool List Result
data class McpToolListResult(
    val tools: List<McpTool>
) {
    companion object {
        fun fromJson(json: JSONObject): McpToolListResult {
            val toolsArray = json.getJSONArray("tools")
            val tools = mutableListOf<McpTool>()
            for (i in 0 until toolsArray.length()) {
                val toolJson = toolsArray.getJSONObject(i)
                tools.add(McpTool(
                    name = toolJson.getString("name"),
                    description = toolJson.getString("description"),
                    inputSchema = toolJson.getJSONObject("inputSchema")
                ))
            }
            return McpToolListResult(tools)
        }
    }
}