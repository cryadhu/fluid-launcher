package com.example.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

data class WeatherHour(val time: String, val temperature: Double)
data class HomeWeather(val city: String, val temperature: Double, val condition: String, val hours: List<WeatherHour>, val code: Int = -1, val isDay: Boolean = true, val windSpeed: Double = 0.0, val windDirection: Int = 0, val uv: Double = 0.0, val sunrise: String? = null, val sunset: String? = null, val utcOffset: Int? = null)
data class MarketQuote(val name: String, val price: Double, val change: Double, val points: List<Double>, val timestamp: Long)
internal fun weatherCondition(code: Int): String = when (code) { 0 -> "Clear sky"; 1,2,3 -> "Cloudy"; 45,48 -> "Fog"; in 51..67 -> "Rain"; in 71..77 -> "Snow"; in 80..82 -> "Showers"; in 85..86 -> "Snow showers"; in 95..99 -> "Thunderstorms"; else -> "Weather" }
class WidgetDataRepository {
    suspend fun weather(city: String): HomeWeather = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(city, "UTF-8")
        val place = JSONObject(getHttps("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json")).optJSONArray("results")?.optJSONObject(0) ?: error("City not found")
        weather(place.getDouble("latitude"), place.getDouble("longitude"), "${place.getString("name")}, ${place.optString("country_code")}")
    }
    suspend fun weather(latitude: Double, longitude: Double, label: String): HomeWeather = withContext(Dispatchers.IO) {
        val data = JSONObject(getHttps("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m,uv_index&hourly=temperature_2m&daily=sunrise,sunset&forecast_days=2&wind_speed_unit=kmh&timezone=auto"))
        parseCurrentWeather(data,label)
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

internal fun parseCurrentWeather(data: JSONObject,label: String): HomeWeather {
    val current=data.getJSONObject("current")
    val hourly=data.getJSONObject("hourly");val times=hourly.getJSONArray("time");val values=hourly.getJSONArray("temperature_2m")
    val now=current.getString("time")
    val hours=(0 until times.length()).filter{times.getString(it)>=now}.take(4).map{WeatherHour(times.getString(it).substringAfter('T'),values.getDouble(it))}
    val code=current.getInt("weather_code");val day=current.getInt("is_day")==1;val wind=current.getDouble("wind_speed_10m")
    val condition=when {
        code==0 -> if(wind>=20) "Windy" else if(day) "Sunny" else "Clear night"
        code in 1..3 -> if(wind>=20) "Windy" else if(day) "Cloudy" else "Cloudy night"
        else -> weatherCondition(code)
    }
    val daily=data.optJSONObject("daily")
    fun dailyTime(key:String)=daily?.optJSONArray(key)?.optString(0)?.takeIf{it.contains('T')}?.substringAfter('T')
    return HomeWeather(label,current.getDouble("temperature_2m"),condition,hours,code,day,wind,current.optDouble("wind_direction_10m",0.0).toInt(),current.optDouble("uv_index",0.0),dailyTime("sunrise"),dailyTime("sunset"),if(data.has("utc_offset_seconds")) data.optInt("utc_offset_seconds") else null)
}
