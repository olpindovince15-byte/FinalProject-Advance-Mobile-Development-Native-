package com.example.weatherapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

//third app project

class MainActivity : AppCompatActivity() {

    private lateinit var cityInput: EditText
    private lateinit var loading: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var cityText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherIcon: TextView
    private lateinit var tempText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var feelsText: TextView
    private lateinit var humidityText: TextView
    private lateinit var windText: TextView
    private lateinit var forecastContainer: LinearLayout

    private val apiKey: String
        get() = BuildConfig.OPENWEATHER_API_KEY.trim()

    private val refreshInterval = 10.minutes
    private var selectedLat: Double? = null
    private var selectedLon: Double? = null
    private var selectedDisplayName = ""
    private var useGpsLocation = false

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

        val allowed = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (allowed) {
            loadGpsWeather()
        } else {
            showError(
                "Location permission denied. Search for a city instead."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cityInput         = findViewById(R.id.cityInput)
        loading           = findViewById(R.id.loading)
        statusText        = findViewById(R.id.statusText)
        cityText          = findViewById(R.id.cityText)
        dateText          = findViewById(R.id.dateText)
        weatherIcon       = findViewById(R.id.weatherIcon)
        tempText          = findViewById(R.id.tempText)
        descriptionText   = findViewById(R.id.descriptionText)
        feelsText         = findViewById(R.id.feelsText)
        humidityText      = findViewById(R.id.humidityText)
        windText          = findViewById(R.id.windText)
        forecastContainer = findViewById(R.id.forecastContainer)

        findViewById<Button>(R.id.searchButton).setOnClickListener {
            searchCity()
        }

        findViewById<Button>(R.id.gpsButton).setOnClickListener {
            requestGpsWeather()
        }

        cityInput.setOnEditorActionListener { _, actionId, _ ->
            if (
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE
            ) {
                searchCity()
                true
            } else {
                false
            }
        }

        if (!checkApiKey()) return

        searchWeatherByCity("Manila")

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(refreshInterval)
                    refreshWeatherAutomatically()
                }
            }
        }
    }

    private fun searchCity() {
        val city = cityInput.text.toString().trim()

        if (city.isBlank()) {
            cityInput.error = "Enter a city name"
            return
        }

        searchWeatherByCity(city)
    }

    private fun searchWeatherByCity(city: String) {
        if (!checkApiKey()) return

        lifecycleScope.launch {
            setLoading(true)

            try {
                // Correct OpenWeatherMap Geocoding API URL.
                val geoUrl = "https://api.openweathermap.org/geo/1.0/direct" + "?q=${encode(city)}&limit=1&appid=${encode(apiKey)}"

                val results = withContext(Dispatchers.IO) {
                    JSONArray(getJson(geoUrl))
                }

                if (results.length() == 0) {
                    throw IllegalStateException("City not found. Check the spelling and try again.")
                }

                val place = results.getJSONObject(0)

                val lat = place.getDouble("lat")
                val lon = place.getDouble("lon")
                val name = place.optString("name", city)
                val country = place.optString("country", "")
                val displayName = if (country.isNotBlank()) {
                    "$name, $country"
                } else {
                    name
                }

                useGpsLocation = false
                selectedLat = lat
                selectedLon = lon
                selectedDisplayName = displayName

                fetchWeather(lat, lon, displayName)

            } catch (e: Exception) {
                showError(e.message ?: "Unable to load weather.")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun requestGpsWeather() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (fine || coarse) {
            loadGpsWeather()
        } else {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    private fun loadGpsWeather() {
        if (!checkApiKey()) return

        lifecycleScope.launch {
            setLoading(true)

            try {
                val location = withContext(Dispatchers.IO) {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY,null).await()
                } ?: throw IllegalStateException("Could not get location. Turn on Location and try again.")

                useGpsLocation = true
                selectedLat = location.latitude
                selectedLon = location.longitude
                selectedDisplayName = getString(R.string.current_location)

                fetchWeather(location.latitude,location.longitude,selectedDisplayName)

            } catch (_: SecurityException) {
                showError("Location permission is required.")
            } catch (e: Exception) {
                showError(
                    e.message ?: "Unable to get device location."
                )
            } finally {
                setLoading(false)
            }
        }
    }

    private suspend fun refreshWeatherAutomatically() {
        if (!checkApiKey()) return

        var lat = selectedLat ?: return
        var lon = selectedLon ?: return

        setLoading(true)

        try {
            if (useGpsLocation) {
                val location = withContext(Dispatchers.IO) {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        null
                    ).await()
                }

                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude

                    selectedLat = lat
                    selectedLon = lon
                }
            }

            fetchWeather(lat, lon, selectedDisplayName)

        } catch (_: SecurityException) {
            statusText.text =
                getString(R.string.status_location_unavailable)
        } catch (e: Exception) {
            val errorMsg = e.message
                ?: getString(R.string.check_connection)

            statusText.text = getString(
                R.string.status_refresh_failed,
                errorMsg
            )
        } finally {
            loading.visibility = View.GONE
        }
    }

    private suspend fun fetchWeather(
        lat: Double,
        lon: Double,
        displayName: String
    ) {
        val currentUrl =
            "https://api.openweathermap.org/data/2.5/weather" +
                    "?lat=$lat&lon=$lon&units=metric&appid=${encode(apiKey)}"

        val forecastUrl =
            "https://api.openweathermap.org/data/2.5/forecast" +
                    "?lat=$lat&lon=$lon&units=metric&appid=${encode(apiKey)}"

        val (currentJson, forecastJson) = withContext(Dispatchers.IO) {
            getJson(currentUrl) to getJson(forecastUrl)
        }

        renderCurrentWeather(
            JSONObject(currentJson),
            displayName
        )

        renderForecast(JSONObject(forecastJson))

        val updatedTime = SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        statusText.text = getString(
            R.string.status_last_updated,
            updatedTime
        )
    }

    private fun renderCurrentWeather(
        json: JSONObject,
        displayName: String
    ) {
        val main = json.getJSONObject("main")
        val weather = json.getJSONArray("weather").getJSONObject(0)
        val wind = json.optJSONObject("wind") ?: JSONObject()

        cityText.text = displayName

        dateText.text = SimpleDateFormat(
            "EEEE, dd MMMM yyyy",
            Locale.getDefault()
        ).format(Date())

        tempText.text = getString(
            R.string.temp_celsius,
            main.getDouble("temp").toInt()
        )

        descriptionText.text =
            weather.optString("description", "Unknown")
                .replaceFirstChar { it.uppercase() }

        weatherIcon.text = weatherEmoji(
            weather.optInt("id", 800)
        )

        feelsText.text = getString(
            R.string.feels_like_celsius,
            main.optDouble("feels_like").toInt()
        )

        humidityText.text = getString(
            R.string.humidity_format,
            main.optInt("humidity")
        )

        windText.text = getString(
            R.string.wind_format,
            wind.optDouble("speed")
        )
    }

    private fun renderForecast(json: JSONObject) {
        forecastContainer.removeAllViews()

        val list = json.getJSONArray("list")
        val byDate = linkedMapOf<String, JSONObject>()

        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            val dateTime = item.optString("dt_txt")
            val date = dateTime.take(10)

            if (!byDate.containsKey(date)) {
                byDate[date] = item
            }

            // Prefer the forecast around midday.
            if (dateTime.contains("12:00:00")) {
                byDate[date] = item
            }
        }

        byDate.entries.take(5).forEach { (date, item) ->
            val main = item.getJSONObject("main")
            val weather = item.getJSONArray("weather")
                .getJSONObject(0)

            val parsedDate = SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            ).parse(date) ?: Date()

            val day = SimpleDateFormat(
                "EEE",
                Locale.getDefault()
            ).format(parsedDate)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 16)
                setBackgroundColor(0x30FFFFFF)
            }

            fun addText(value: String, size: Float) {
                card.addView(
                    TextView(this).apply {
                        text = value
                        textSize = size
                        gravity = Gravity.CENTER
                        setTextColor(android.graphics.Color.WHITE)
                        setPadding(0, 5, 0, 5)
                    }
                )
            }

            addText(day, 14f)
            addText(
                weatherEmoji(weather.optInt("id", 800)),
                28f
            )
            addText(
                getString(
                    R.string.temp_celsius,
                    main.getDouble("temp").toInt()
                ),
                18f
            )
            addText(
                weather.optString("description", "Unknown")
                    .replaceFirstChar { it.uppercase() },
                12f
            )

            val params = LinearLayout.LayoutParams(
                (120 * resources.displayMetrics.density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            params.marginEnd =
                (8 * resources.displayMetrics.density).toInt()

            forecastContainer.addView(card, params)
        }
    }

    // OpenWeatherMap condition codes.
    private fun weatherEmoji(id: Int): String = when (id) {
        in 200..232 -> "⛈️"
        in 300..321 -> "🌦️"
        in 500..531 -> "🌧️"
        in 600..622 -> "❄️"
        in 701..781 -> "🌫️"
        800 -> "☀️"
        801 -> "🌤️"
        802 -> "⛅"
        803, 804 -> "☁️"
        else -> "🌡️"
    }

    private suspend fun getJson(urlString: String): String =
        withContext(Dispatchers.IO) {
            val connection =
                URL(urlString).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val code = connection.responseCode

                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val body = stream?.bufferedReader()?.use {
                    it.readText()
                }.orEmpty()

                if (code !in 200..299) {
                    val message = try {
                        JSONObject(body).optString(
                            "message",
                            "HTTP $code"
                        )
                    } catch (_: Exception) {
                        "HTTP $code"
                    }

                    throw IllegalStateException(
                        when (code) {
                            401 -> "Invalid API key or key not activated."
                            404 -> "Weather data not found."
                            else -> "OpenWeatherMap error ($code): $message"
                        }
                    )
                }

                body
            } finally {
                connection.disconnect()
            }
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private fun checkApiKey(): Boolean {
        if (
            apiKey.isBlank() ||
            apiKey == "YOUR_ACTUAL_API_KEY"
        ) {
            showError(
                "Set OPENWEATHER_API_KEY in local.properties."
            )
            return false
        }

        return true
    }

    private fun setLoading(isLoading: Boolean) {
        loading.visibility =
            if (isLoading) View.VISIBLE else View.GONE

        if (isLoading) {
            statusText.text =
                getString(R.string.status_loading_weather)
        }
    }

    private fun showError(message: String) {
        statusText.text = message

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
