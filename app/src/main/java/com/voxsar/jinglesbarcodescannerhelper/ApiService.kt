package com.voxsar.jinglesbarcodescannerhelper

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class SubmissionResult(
    val isSuccessful: Boolean,
    val message: String,
)

class ApiService {
    fun fetchLocations(url: String): List<LocationNode> {
        val connection = openConnection(url, "GET")
        return try {
            val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONTokener(response).nextValue()
            LocationTreeParser.parse(JsonTreeConverter.toGeneric(json))
        } finally {
            connection.disconnect()
        }
    }

    fun submit(url: String, payload: SubmissionPayload): SubmissionResult {
        val connection = openConnection(url, "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            val body = JSONObject(payload.toJsonMap()).toString()
            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            val responseStream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseBody = responseStream?.let { stream ->
                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use(BufferedReader::readText)
            }.orEmpty()
            SubmissionResult(
                isSuccessful = connection.responseCode in 200..299,
                message = responseBody.ifBlank { "HTTP ${connection.responseCode}" },
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
    }
}

private object JsonTreeConverter {
    fun toGeneric(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { key ->
            toGeneric(value.opt(key))
        }
        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                add(toGeneric(value.opt(index)))
            }
        }
        else -> value
    }
}

private fun SubmissionPayload.toJsonMap(): Map<String, Any> = buildMap {
    put("barcode", barcode)
    expireDate?.let { put("expireDate", it) }
    manufactureDate?.let { put("manufactureDate", it) }
    itemQuantity?.let { put("itemQuantity", it) }
    location?.let { put("location", it) }
}
