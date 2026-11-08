package io.github.nexfusion.nexfusion.linkedin

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Lightweight protobuf wire-format editor for runtime payload modifications.
 */
internal data class ProtoField(
    val number: Int,
    val wireType: Int,
    var data: ByteArray
) {
    fun copy(): ProtoField = ProtoField(number, wireType, data.clone())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtoField) return false
        return number == other.number && wireType == other.wireType && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = number
        result = 31 * result + wireType
        result = 31 * result + data.contentHashCode()
        return result
    }
}

internal object ProtoEditor {
    fun parse(data: ByteArray): ArrayList<ProtoField>? {
        val fields = ArrayList<ProtoField>()
        val pos = intArrayOf(0)
        try {
            while (pos[0] < data.size) {
                val key = readVarint(data, pos)
                val number = (key ushr 3).toInt()
                val wireType = (key and 7L).toInt()
                if (number == 0) return null
                var start = pos[0]
                when (wireType) {
                    0 -> readVarint(data, pos)
                    1 -> pos[0] += 8
                    5 -> pos[0] += 4
                    2 -> {
                        val length = readVarint(data, pos)
                        if (length < 0 || pos[0] + length > data.size) return null
                        start = pos[0]
                        pos[0] += length.toInt()
                    }
                    else -> return null
                }
                if (pos[0] > data.size) return null
                fields.add(ProtoField(number, wireType, Arrays.copyOfRange(data, start, pos[0])))
            }
            return fields
        } catch (_: Throwable) {
            return null
        }
    }

    fun field(number: Int, wireType: Int, data: ByteArray): ProtoField = ProtoField(number, wireType, data)

    fun write(fields: List<ProtoField>): ByteArray {
        val out = ByteArrayOutputStream()
        for (field in fields) {
            writeVarint(out, ((field.number.toLong() shl 3) or field.wireType.toLong()))
            if (field.wireType == 2) {
                writeVarint(out, field.data.size.toLong())
            }
            out.write(field.data)
        }
        return out.toByteArray()
    }

    fun stringField(number: Int, value: String): ProtoField =
        ProtoField(number, 2, value.toByteArray(StandardCharsets.UTF_8))

    fun varintField(number: Int, value: Long): ProtoField {
        val out = ByteArrayOutputStream()
        writeVarint(out, value)
        return ProtoField(number, 0, out.toByteArray())
    }

    fun messageField(number: Int, vararg fields: ProtoField): ProtoField {
        return ProtoField(number, 2, write(fields.toList()))
    }

    fun nested(data: ByteArray, vararg path: Int): ByteArray? {
        var current: ByteArray? = data
        for (number in path) {
            val cur = current ?: return null
            val fields = parse(cur) ?: return null
            current = null
            for (field in fields) {
                if (field.number == number && field.wireType == 2) {
                    current = field.data
                    break
                }
            }
            if (current == null) return null
        }
        return current
    }

    fun replaceString(data: ByteArray, from: String, to: String): ByteArray {
        if (!containsAscii(data, from)) return data
        val fields = parse(data) ?: return data
        val fromBytes = from.toByteArray(StandardCharsets.UTF_8)
        val toBytes = to.toByteArray(StandardCharsets.UTF_8)
        for (field in fields) {
            if (field.wireType != 2) continue
            if (field.data.contentEquals(fromBytes)) {
                field.data = toBytes
            } else if (containsAscii(field.data, from)) {
                field.data = replaceString(field.data, from, to)
            }
        }
        return write(fields)
    }

    private fun readVarint(data: ByteArray, pos: IntArray): Long {
        var value = 0L
        var shift = 0
        while (shift < 64) {
            val b = data[pos[0]++]
            value = value or ((b.toLong() and 0x7FL) shl shift)
            if (b >= 0) return value
            shift += 7
        }
        throw IllegalArgumentException("Malformed varint")
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while ((v and 0x7FL.inv()) != 0L) {
            out.write(((v and 0x7FL) or 0x80L).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }
}
