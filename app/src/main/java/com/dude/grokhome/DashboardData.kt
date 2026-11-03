package com.dude.grokhome

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object DashboardData {
    fun weather(city: String): String {
        return try {
            val query = URLEncoder.encode(city, "UTF-8")
            val geoText = URL("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=1&language=en&format=json").readText()
            val place = JSONObject(geoText).optJSONArray("results")?.optJSONObject(0) ?: return "Weather: city not found"
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val text = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code&temperature_unit=fahrenheit").readText()
            val now = JSONObject(text).getJSONObject("current")
            val code = now.optInt("weather_code")
            val sky = when (code) { 0 -> "Clear"; 1, 2 -> "Partly cloudy"; 3 -> "Cloudy"; 45, 48 -> "Fog"; 51, 53, 55 -> "Drizzle"; 61, 63, 65, 80, 81, 82 -> "Rain"; 71, 73, 75, 77, 85, 86 -> "Snow"; 95, 96, 99 -> "Thunderstorms"; else -> "Current conditions" }
            "${place.optString("name", city)}\n${now.optDouble("temperature_2m").toInt()}°F • $sky\nFeels ${now.optDouble("apparent_temperature").toInt()}° • Humidity ${now.optInt("relative_humidity_2m")}%"
        } catch (_: Exception) { "Weather unavailable — check internet" }
    }

    fun headlines(): String {
        return try {
            val c = URL("https://feeds.bbci.co.uk/news/rss.xml").openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 12000
            val xml = c.inputStream.bufferedReader().use { it.readText() }; c.disconnect()
            val titles = Regex("<item>.*?<title><!\\[CDATA\\[(.*?)]]></title>", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(xml).take(4).map { it.groupValues[1].replace("&amp;", "&").trim() }.toList()
            if (titles.isEmpty()) "No headlines available" else titles.mapIndexed { i, title -> "${i + 1}. $title" }.joinToString("\n")
        } catch (_: Exception) { "Headlines unavailable — check internet" }
    }
}