package com.pharma.evisualaid.data.repository

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Serializes the tag list to/from the JSON string stored in the [tags] DB column. */
object TagCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    fun encode(tags: List<String>): String = json.encodeToString(serializer, tags)

    fun decode(raw: String?): List<String> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
}
