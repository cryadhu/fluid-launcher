package com.example.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

data class WeatherHour(val time: String, val temperature: Double)
data class HomeWeather(val city: String, val temperature: Double, val condition: String, val hours: List<WeatherHour>)
data class MarketQuote(val name: String, val price: Double, val change: Double, val points: List<Double>, val timestamp: Long)
internal fun weatherCondition(code: Int): String = when (code) { 0 -> "Clear sky"; 1,2,3 -> "Cloudy"; 45,48 -> "Fog"; in 51..67 -> "Rain"; in 71..77 -> "Snow"; in 80..82 -> "Showers"; in 95..99 -> "Thunderstorms"; else -> "Weather" }
class WidgetDataRepository {
    suspend fun weather(city: String): HomeWeather = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(city, "UTF-8")
        val place = JSONObject(getHttps("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json")).optJSONArray("results")?.optJSONObject(0) ?: error("City not found")
        val data = JSONObject(getHttps("https://api.open-meteo.com/v1/forecast?latitude=${place.getDouble("latitude")}&longitude=${place.getDouble("longitude")}&current=temperature_2m,weather_code&hourly=temperature_2m&forecast_days=2&timezone=auto"))
        val current = data.getJSONObject("current")
        val hourly = data.getJSONObject("hourly"); val times = hourly.getJSONArray("time"); val values = hourly.getJSONArray("temperature_2m")
        val now = current.getString("time")
        val hours = (0 until times.length()).filter { times.getString(it) >= now }.take(4).map { WeatherHour(times.getString(it).substringAfter('T'), values.getDouble(it)) }
        HomeWeather("${place.getString("name")}, ${place.optString("country_code")}", current.getDouble("temperature_2m"), weatherCondition(current.getInt("weather_code")), hours)
    }
    suspend fun markets(): List<MarketQuote> = withContext(Dispatchers.IO) {
        listOf("NIFTY 50" to "%5ENSEI", "S&P 500" to "%5EGSPC").mapNotNull { (name, symbol) -> runCatching {
            val result = JSONObject(getHttps("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1d&range=1mo")).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
            val meta = result.getJSONObject("meta"); val price = meta.getDouble("regularMarketPrice")
            val previous = meta.optDouble("chartPreviousClose", price)
            val quotes = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
            MarketQuote(name, price, if (previous > 0) (price - previous) / previous * 100 else 0.0,
                (0 until quotes.length()).mapNotNull { if (quotes.isNull(it)) null else quotes.getDouble(it) }, meta.optLong("regularMarketTime") * 1000)
        }.getOrNull() }
    }
}
