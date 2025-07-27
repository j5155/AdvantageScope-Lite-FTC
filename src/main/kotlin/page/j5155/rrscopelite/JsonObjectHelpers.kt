package page.j5155.rrscopelite

import com.google.gson.JsonObject

fun JsonObject.append(other: JsonObject) {
    other.entrySet().forEach {
        this.add(it.key,it.value)
    }
}

operator fun JsonObject.plus(other: JsonObject): JsonObject {
    val new = JsonObject()
    new.append(this)
    new.append(other)
    return new
}