package com.personal.fakeapps

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView

/** Pantalla "segura" (feed / bandeja de entrada) con una pestaña seleccionada y un botón al visor. */
class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            id = R.id.home_root
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply { text = "Pantalla segura"; textSize = 28f; gravity = Gravity.CENTER })
        root.addView(Button(this).apply {
            text = "Abrir visor"
            setOnClickListener { startActivity(Intent(this@HomeActivity, ViewerActivity::class.java)) }
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.safe_tab)
            contentDescription = getString(R.string.safe_tab)
            isSelected = true
            textSize = 18f
            gravity = Gravity.CENTER
        })
        setContentView(root)
    }
}

/** Visor de videos cortos: lista vertical paginada a pantalla completa. */
class ViewerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pager = RecyclerView(this).apply {
            id = R.id.clips_viewer_view_pager
            layoutManager = LinearLayoutManager(this@ViewerActivity)
            adapter = PageAdapter()
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        PagerSnapHelper().attachToRecyclerView(pager)
        setContentView(pager)
    }

    private class PageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val colors = intArrayOf(0xFF8E24AA.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFF4511E.toInt())
        override fun getItemCount() = 30
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                textSize = 40f
                setTextColor(Color.WHITE)
            }
            return object : RecyclerView.ViewHolder(tv) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder.itemView as TextView).apply {
                text = "Video ${position + 1}"
                setBackgroundColor(colors[position % colors.size])
            }
        }
    }
}
