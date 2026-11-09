package com.quizmaster.app.data

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun stringListToJson(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter
    fun jsonToStringList(value: String): List<String> {
        val array = JSONArray(value)
        return List(array.length()) { array.getString(it) }
    }

    @TypeConverter
    fun intListToJson(value: List<Int>): String = JSONArray(value).toString()

    @TypeConverter
    fun jsonToIntList(value: String): List<Int> {
        val array = JSONArray(value)
        return List(array.length()) { array.getInt(it) }
    }
}
