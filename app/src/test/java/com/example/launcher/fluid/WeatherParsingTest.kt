package com.example.launcher.fluid

import com.example.launcher.data.parseCurrentWeather
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WeatherParsingTest {
    private fun forecast(code:Int,day:Int,wind:Double=4.0)=parseCurrentWeather(JSONObject("""{"current":{"time":"2026-11-02T01:00","temperature_2m":24.5,"weather_code":$code,"is_day":$day,"wind_speed_10m":$wind},"hourly":{"time":["2026-11-02T00:00","2026-11-02T01:00","2026-11-02T02:00"],"temperature_2m":[25,24.5,24]}}"""),"Current location")
    @Test fun actualDayNightWindAndPrecipitationSurviveParsing() {
        assertEquals("Sunny",forecast(0,1).condition)
        assertEquals("Clear night",forecast(0,0).condition)
        assertFalse(forecast(0,0).isDay)
        assertEquals("Cloudy night",forecast(3,0).condition)
        assertEquals("Windy",forecast(0,1,25.0).condition)
        assertEquals(25.0,forecast(0,1,25.0).windSpeed,0.0)
        assertEquals("Rain",forecast(63,0,30.0).condition)
        assertEquals("Thunderstorms",forecast(95,0).condition)
        assertEquals("Snow showers",forecast(85,1).condition)
        assertEquals("Fog",forecast(45,1).condition)
        assertEquals(2,forecast(0,1).hours.size)
    }
    @Test fun automaticAndManualLocationModesPersist() {
        assertTrue(decodeFluid("{}" ).autoWeather)
        val manual=FluidConfig(city="Kochi",autoWeather=false)
        assertEquals(manual,decodeFluid(encodeFluid(manual)))
    }
}
