package com.Safa.VPN

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import java.io.*
import java.lang.StringBuilder
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.log
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Base64


class MainActivity : AppCompatActivity() {

    private var state = State.STOPPED
    var configContent = """[General]
loglevel = info
dns-server = 114.114.114.114, 223.5.5.5
routing-domain-resolve = true
tun-fd = REPLACE-ME-WITH-THE-FD

[Proxy]
Direct = direct
Reject = reject


[Proxy Group]
Proxy = failover, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, health-check=true, check-interval=600, fail-timeout=3

[Rule]
EXTERNAL, site:category-ads-all, Reject
FINAL, Proxy"""
    private enum class State {
        STARTING, STARTED, STOPPING, STOPPED
    }

    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "vpn_stopped" -> {
                    state = State.STOPPED
                    findViewById<Button>(R.id.go).text = "Go"
                }
                "vpn_started", "vpn_pong" -> {
                    state = State.STARTED
                    findViewById<Button>(R.id.go).text = "Stop"
                }
            }
        }
    }
    fun get_sub(){
        val url: URL = URL("https://safasafari.ir")
        val con: HttpsURLConnection = url.openConnection() as HttpsURLConnection
        val inputStream : InputStream = con.inputStream
        val text = inputStream.bufferedReader().use(BufferedReader::readText)
        update_ss(text)
    }
    fun update_ss(base64: String){
        var final: String = ""
        var configFile = File(filesDir, "config.conf")
        val str = Base64.decode(base64, Base64.DEFAULT).decodeToString().split("\n")
        var i = 1
        for (row in str) {
            val parse = parse_ss(row)
            final = final + "p" + i++ + " = ss, " + parse[0] + ", " + parse[1] + ", encrypt-method=" + parse[2] + ", password=" + parse[3] + "\n"
        }
        Log.d("SAFA", final)
        var configContent: String = FileInputStream(configFile).bufferedReader().use(BufferedReader::readText)
        val replace: String = configContent.substring(configContent.indexOf("Reject = reject") + "Reject = reject".length + 2, configContent.indexOf("[Proxy Group]") - 1)
        configContent = configContent.replace(replace, final)
        FileOutputStream(configFile).use {
            it.write(configContent.toByteArray())
        }

    }
    private fun parse_ss(ss_url: String): Array<String> {
        if (ss_url.substring(0, 5) != "ss://") return arrayOf()
        val ss: String = ss_url.split("#")[0].substring(5)
        var base: String
        if (!ss.contains("@")) {
            base = String(Base64.decode(ss, Base64.DEFAULT))
        } else {
            val part = ss.split("@")
            base = String(Base64.decode(part[0], Base64.DEFAULT))+"@"+part[1]
        }
        val part2 = base.split("@")
        val server = part2[1].split(":")
        val auth = part2[0].split(":")
        val ip = server[0]
        val port = server[1]
        val enc = auth[0]
        val password = auth[1]
        return arrayOf(ip, port, enc, password)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        findViewById<Button>(R.id.go).text = "Go"
        findViewById<Button>(R.id.Update).setOnClickListener {
            get_sub()
        }

        findViewById<Button>(R.id.go).setOnClickListener { _ ->
            get_sub()
            if (state == State.STARTED) {
                state = State.STOPPING
                findViewById<Button>(R.id.go).text = "Disconnecting"
                sendBroadcast(Intent("stop_vpn"))
            } else if (state == State.STOPPED) {
                state = State.STARTING
                findViewById<Button>(R.id.go).text = "Connecting"
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 1)
                } else {
                    onActivityResult(1, Activity.RESULT_OK, null);
                }
            }
        }

        registerReceiver(broadcastReceiver, IntentFilter("vpn_pong"))
        registerReceiver(broadcastReceiver, IntentFilter("vpn_started"))
        registerReceiver(broadcastReceiver, IntentFilter("vpn_stopped"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            val intent = Intent(this, SimpleVpnService::class.java)
            startService(intent)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onResume() {
        super.onResume()
        sendBroadcast(Intent("vpn_ping"))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }
}
