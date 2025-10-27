package com.example.runningtasks2

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button
import android.widget.TextView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var r: RecyclerView
    private lateinit var b: Button
    private lateinit var h: TextView
    private lateinit var s: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        r = findViewById(R.id.rv)
        b = findViewById(R.id.btnRefresh)
        h = findViewById(R.id.h)
        s = findViewById(R.id.s)
        h.text = getString(R.string.title_running_tasks)
        s.text = getString(R.string.subtitle_recent)
        r.layoutManager = LinearLayoutManager(this)
        b.setOnClickListener { refreshList() }
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        if (!hasUsageAccess()) {
            s.text = getString(R.string.grant_usage)
            r.adapter = SimpleAdapter(emptyList())
            b.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            return
        } else {
            s.text = getString(R.string.instructions)
            val l = getRecentForegroundApps()
            r.adapter = SimpleAdapter(l)
        }
    }

    private fun hasUsageAccess(): Boolean {
        try {
            val aom = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = aom.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            if (mode == android.app.AppOpsManager.MODE_ALLOWED) return true
        } catch (_: Exception) { }

        return try {
            val um = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val agg = um.queryAndAggregateUsageStats(0, System.currentTimeMillis())
            agg != null && agg.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }


    @Suppress("DEPRECATION")
    private fun getRecentForegroundApps(): List<AppInfo> {
        val um = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val to = System.currentTimeMillis()
        val from = to - 1000L * 60 * 60 * 24 // last 24 hours
        val ev = um.queryEvents(from, to)
        val e = UsageEvents.Event()
        val set = LinkedHashMap<String, Long>()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) set[e.packageName] = e.timeStamp
        }
        val out = ArrayList<AppInfo>()
        for ((k, v) in set.entries) out.add(AppInfo(k, v))
        out.sortByDescending { it.ts }
        return out
    }


    private fun killBackgroundProcess(pkg: String) {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(pkg)
        } catch (_: Exception) { }
    }

    private fun openAppInfo(pkg: String) {
        try {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:$pkg")
            startActivity(i)
        } catch (_: Exception) { }
    }

    data class AppInfo(val pkg: String, val ts: Long)

    inner class SimpleAdapter(private val a: List<AppInfo>) : RecyclerView.Adapter<SimpleAdapter.VH>() {
        override fun onCreateViewHolder(p: ViewGroup, vType: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_task, p, false))
        override fun getItemCount() = a.size
        override fun onBindViewHolder(h: VH, i: Int) {
            val app = a[i]
            h.t1.text = app.pkg
            val timeStr = DateFormat.getTimeInstance().format(Date(app.ts))
            h.t2.text = getString(R.string.last_seen, timeStr)
            h.btn.setOnClickListener {
                killBackgroundProcess(app.pkg)
                openAppInfo(app.pkg)
            }
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val t1: TextView = v.findViewById(R.id.t1)
            val t2: TextView = v.findViewById(R.id.t2)
            val btn: Button = v.findViewById(R.id.btnKill)
        }
    }
}
