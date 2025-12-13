package com.droidrun.portal.mcp.tools

import android.util.Log
import com.droidrun.portal.mcp.McpTool
import org.json.JSONObject
import kotlin.math.*

/**
 * Calculator MCP Tool
 * Evaluates mathematical expressions
 */
class CalculatorTool : McpToolHandler {
    
    companion object {
        private const val TAG = "CalculatorTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("python_expression", JSONObject().apply {
                        put("type", "string")
                        put("description", "Mathematical expression to calculate. Supports basic operations (+, -, *, /, ^), math functions (sin, cos, tan, sqrt, pow, abs, floor, ceil, round), and constants (pi, e).")
                    })
                })
                put("required", org.json.JSONArray().put("python_expression"))
            }
            
            return McpTool(
                name = "calculator",
                description = "For mathematical calculation, use this tool to calculate the result of a mathematical expression. Supports: +, -, *, /, ^, sin, cos, tan, sqrt, pow, abs, floor, ceil, round, pi, e, random.",
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val expression = arguments?.getString("python_expression")
                ?: throw IllegalArgumentException("Missing python_expression parameter")
            
            Log.i(TAG, "Calculating expression: $expression")
            
            val result = evaluateExpression(expression)
            Log.i(TAG, "Result: $result")
            
            JSONObject().apply {
                put("success", true)
                put("result", result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating expression", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
    
    private fun evaluateExpression(expression: String): Double {
        // Use Kotlin's ScriptEngine-like approach with eval
        // For simplicity, we'll parse and evaluate manually
        var expr = expression.trim()
        
        // Replace constants
        expr = expr.replace("pi", PI.toString())
        expr = expr.replace("e", E.toString())
        
        return parseAdditionSubtraction(expr)
    }
    
    private fun parseAdditionSubtraction(expr: String): Double {
        val tokens = tokenize(expr, listOf('+', '-'))
        if (tokens.size == 1) return parseMultiplicationDivision(tokens[0])
        
        var result = parseMultiplicationDivision(tokens[0])
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i]
            val value = parseMultiplicationDivision(tokens[i + 1])
            result = when (op) {
                "+" -> result + value
                "-" -> result - value
                else -> result
            }
            i += 2
        }
        return result
    }
    
    private fun parseMultiplicationDivision(expr: String): Double {
        val tokens = tokenize(expr, listOf('*', '/'))
        if (tokens.size == 1) return parsePower(tokens[0])
        
        var result = parsePower(tokens[0])
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i]
            val value = parsePower(tokens[i + 1])
            result = when (op) {
                "*" -> result * value
                "/" -> result / value
                else -> result
            }
            i += 2
        }
        return result
    }
    
    private fun parsePower(expr: String): Double {
        val tokens = tokenize(expr, listOf('^'))
        if (tokens.size == 1) return parseUnary(tokens[0])
        
        // Right associative
        var result = parseUnary(tokens.last())
        for (i in tokens.size - 3 downTo 0 step 2) {
            result = parseUnary(tokens[i]).pow(result)
        }
        return result
    }
    
    private fun parseUnary(expr: String): Double {
        val trimmed = expr.trim()
        
        // Handle parentheses
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return parseAdditionSubtraction(trimmed.substring(1, trimmed.length - 1))
        }
        
        // Handle functions
        val funcMatch = Regex("([a-z]+)\\((.+)\\)").matchEntire(trimmed)
        if (funcMatch != null) {
            val funcName = funcMatch.groupValues[1]
            val arg = funcMatch.groupValues[2]
            return evaluateFunction(funcName, arg)
        }
        
        // Handle numbers
        return trimmed.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid expression: $trimmed")
    }
    
    private fun evaluateFunction(name: String, arg: String): Double {
        val argValue = if (arg.contains(",")) {
            // For functions with multiple args
            val args = arg.split(",").map { parseAdditionSubtraction(it.trim()) }
            return when (name) {
                "pow" -> args[0].pow(args[1])
                else -> throw IllegalArgumentException("Unknown function: $name")
            }
        } else {
            parseAdditionSubtraction(arg)
        }
        
        return when (name) {
            "sin" -> sin(argValue)
            "cos" -> cos(argValue)
            "tan" -> tan(argValue)
            "sqrt" -> sqrt(argValue)
            "abs" -> abs(argValue)
            "floor" -> floor(argValue)
            "ceil" -> ceil(argValue)
            "round" -> round(argValue)
            "random" -> kotlin.random.Random.nextDouble()
            else -> throw IllegalArgumentException("Unknown function: $name")
        }
    }
    
    private fun tokenize(expr: String, operators: List<Char>): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0
        
        for (char in expr) {
            when {
                char == '(' -> {
                    depth++
                    current.append(char)
                }
                char == ')' -> {
                    depth--
                    current.append(char)
                }
                depth == 0 && char in operators -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString().trim())
                        current = StringBuilder()
                    }
                    tokens.add(char.toString())
                }
                else -> current.append(char)
            }
        }
        
        if (current.isNotEmpty()) {
            tokens.add(current.toString().trim())
        }
        
        return tokens
    }
}

/**
 * Interface for MCP Tool Handlers
 */
interface McpToolHandler {
    fun execute(arguments: JSONObject?): JSONObject
}