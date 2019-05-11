package com.example.android.uamp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.GsonBuilder
import kotlinx.android.synthetic.main.fragment_mediaitem_list.*
import okhttp3.*
import java.io.IOException

class YearActivity : AppCompatActivity() {

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.fragment_mediaitem_list)

    list.layoutManager = LinearLayoutManager(this)

    fetchJson()
}

fun fetchJson() {
    println("Attempting to Fetch JSON")

    val url = "https://phish.in/api/v1/years?include_show_counts=true"
    val apiKey =
            "Bearer bb2286b37f9df4df7c33d79bd2479925c5ec35531feab05e4375a20fad4369f3fc5128194360d9296d39c7f6bde839f9"

    val request = Request.Builder().url(url).header("Authorization", apiKey).build()

    val client = OkHttpClient()
    client.newCall(request).enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            val body = response.body()?.string()
            println(body)

            val gson = GsonBuilder().create()

            val yearFeed = gson.fromJson(body, YearFeed::class.java)


            runOnUiThread {
                list.adapter = YearAdapter(yearFeed)
            }
        }

        override fun onFailure(call: Call, e: IOException) {
            println("Failed to connect to phish.in")
        }
    })


}
}

class YearFeed(val data: List<PhishYears>)

class PhishYears(val date: String, val show_count: String)