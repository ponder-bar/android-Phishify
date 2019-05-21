package com.example.android.uamp

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.android.uamp.media.MusicService
import kotlinx.android.synthetic.main.fragment_mediaitem.view.*

class YearAdapter(val yearFeed: YearFeed) : RecyclerView.Adapter<CustomViewHolder>() {

    //numberOfItems
    override fun getItemCount(): Int {
        return yearFeed.data.count()
    }

    override fun onCreateViewHolder(p0: ViewGroup, p1: Int): CustomViewHolder {
        val layoutInflator = LayoutInflater.from(p0.context)
        val cellForRow = layoutInflator.inflate(R.layout.fragment_yearitem, p0, false)
        return CustomViewHolder(cellForRow)
    }

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        //val videoTitle = videoTitles.get(p1)
        val theYear = yearFeed.data[position]
        holder.view.title?.text = theYear.date
        holder.view.subtitle?.text = theYear.show_count
        holder.holderYear = theYear
    }

}

class CustomViewHolder(val view: View, var holderYear: PhishYears? = null) : RecyclerView.ViewHolder(view) {

    companion object {
        val YEAR = "YEAR_TITLE"

    }

    init {
        view.setOnClickListener {
            val intent = Intent(view.context, MainActivity::class.java)
            intent.putExtra(YEAR, holderYear?.date)
            view.context.startActivity(intent)
            view.context.startService(Intent(view.context, MusicService::class.java))
        }
    }

}
