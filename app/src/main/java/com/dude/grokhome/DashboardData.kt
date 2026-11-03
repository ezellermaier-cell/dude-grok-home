package com.dude.grokhome

import android.util.Xml
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Public, key-free dashboard feeds. Network calls run on the app IO executor. */
object DashboardData {
    data class NewsItem(val title: String, val imageUrl: String, val source: String, val link: String = "")

    private fun get(url: String, timeout: Int = 9000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeout
            c.readTimeout = timeout
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "DUDE-Home-Dashboard/2.0 Android")
            c.setRequestProperty("Accept", "application/json, application/rss+xml, application/xml, text/xml, text/html")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    fun weather(city: String): String {
        if (city.isBlank()) return ""
        return try {
            val q = URLEncoder.encode(city.trim(), "UTF-8")
            val geo = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=en&format=json"))
            val place = geo.optJSONArray("results")?.optJSONObject(0) ?: return "Weather: city not found"
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val data = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto"))
            val now = data.getJSONObject("current")
            val daily = data.optJSONObject("daily")
            val condition = when (now.optInt("weather_code")) {
                0 -> "Clear sky"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
                45, 48 -> "Fog"; 51, 53, 55 -> "Drizzle"; 56, 57 -> "Freezing drizzle"
                61, 63, 65, 80, 81, 82 -> "Rain"; 66, 67 -> "Freezing rain"
                71, 73, 75, 77, 85, 86 -> "Snow"; 95, 96, 99 -> "Thunderstorms"
                else -> "Current conditions"
            }
            val high = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.toInt()
            val low = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.toInt()
            val rain = daily?.optJSONArray("precipitation_probability_max")?.optInt(0)
            buildString {
                append(place.optString("name", city)).append("\n")
                append(now.optDouble("temperature_2m").toInt()).append("°F • ").append(condition).append("\n")
                append("Feels ").append(now.optDouble("apparent_temperature").toInt()).append("°F")
                append(" • Humidity ").append(now.optInt("relative_humidity_2m")).append("%")
                append(" • Wind ").append(now.optDouble("wind_speed_10m").toInt()).append(" mph")
                if (high != null && low != null) append("\nToday: high ").append(high).append("° / low ").append(low).append("°")
                if (rain != null) append(" • Rain chance ").append(rain).append("%")
            }
        } catch (_: Exception) { "Weather unavailable — check internet or city" }
    }

    fun newsItems(): List<NewsItem> {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(get("https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en", 12000).reader())
            val result = ArrayList<NewsItem>()
            var event = parser.eventType
            var inside = false
            var tag = ""
            var title = ""
            var source = ""
            var link = ""
            var image = ""
            while (event != XmlPullParser.END_DOCUMENT && result.size < 8) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        tag = parser.name
                        if (tag == "item") { inside = true; title = ""; source = ""; link = ""; image = "" }
                        val localTag = parser.name.substringAfter(':')
                        if (inside && (localTag == "content" || localTag == "thumbnail" || localTag == "enclosure")) {
                            image = parser.getAttributeValue(null, "url")
                                ?: parser.getAttributeValue(null, "href")
                                ?: parser.getAttributeValue(0)
                                ?: image
                        }
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inside) {
                        val value = parser.text.orEmpty().trim()
                        when (tag) {
                            "title" -> title += value
                            "source" -> source += value
                            "link" -> link += value
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "item") {
                        if (title.isNotBlank()) {
                            val clean = title.substringBefore(" - ").trim()
                            result.add(NewsItem(clean, if (image.isNotBlank()) image else articleImage(link), source.ifBlank { "Google News" }, link))
                        }
                        inside = false
                    }
                }
                event = parser.next()
            }
            result
        } catch (_: Exception) { emptyList() }
    }

    private fun articleImage(link: String): String {
        if (link.isBlank()) return ""
        return try {
            val connection = URL(link).openConnection() as HttpURLConnection
            connection.connectTimeout = 6000
            connection.readTimeout = 7000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            val html = connection.inputStream.bufferedReader().use { it.readText().take(450000) }
            val finalUrl = connection.url.toString()
            connection.disconnect()
            val tags = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)
            for (match in tags) {
                val tag = match.value
                val property = Regex("""(?:property|name)\s*=\\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
                    .find(tag)?.groupValues?.get(1)?.lowercase().orEmpty()
                if (property == "og:image" || property == "og:image:url" || property == "twitter:image" || property == "twitter:image:src") {
                    val value = Regex("""content\s*=\\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
                        .find(tag)?.groupValues?.get(1).orEmpty()
                    if (value.isNotBlank()) {
                        val decoded = value.replace("&amp;", "&").replace("&#38;", "&").replace("&quot;", "\"")
                        val resolved = URL(URL(finalUrl), decoded).toString()
                        if (resolved.startsWith("https://") || resolved.startsWith("http://")) return resolved
                    }
                }
            }
            ""
        } catch (_: Exception) { "" }
    }
}
