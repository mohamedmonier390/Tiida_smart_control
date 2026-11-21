package com.tiida.smartcontrol

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var input: java.io.InputStream? = null
    private var output: java.io.OutputStream? = null
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var command: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestBtPermissionIfNeeded()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply { text = "Tiida Smart Control"; textSize = 24f })
        status = TextView(this).apply { text = "Not connected"; textSize = 16f }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Choose paired ELM327"
            setOnClickListener { showPairedDevices() }
        })
        root.addView(Button(this).apply {
            text = "Initialize ELM327"
            setOnClickListener { sendInit() }
        })
        command = EditText(this).apply { hint = "AT command (e.g. ATDP)" }
        root.addView(command)
        root.addView(Button(this).apply {
            text = "Send command"
            setOnClickListener { sendAt(command.text.toString().trim()) }
        })
        root.addView(TextView(this).apply {
            text = "Vehicle controls (disabled until verified)"
            textSize = 18f
        })
        listOf("LOCK", "UNLOCK", "LIGHTS").forEach { label ->
            root.addView(Button(this).apply { text = label; isEnabled = false })
        }
        log = TextView(this).apply { text = "Log:\n" }
        root.addView(ScrollView(this).apply { addView(log) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun requestBtPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 100)
        }
    }

    private fun showPairedDevices() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) { append("Bluetooth not available"); return }
            val devices = adapter.bondedDevices.toList()
            if (devices.isEmpty()) {
                append("No paired devices. Pair the ELM327 in Android Bluetooth settings first.")
                return
            }
            AlertDialog.Builder(this)
                .setTitle("Select paired device")
                .setItems(devices.map { "${it.name ?: "Unknown"} — ${it.address}" }.toTypedArray()) { _, which ->
                    connect(devices[which])
                }.show()
        } catch (_: SecurityException) {
            append("Bluetooth permission denied")
        }
    }

    private fun connect(device: BluetoothDevice) {
        status.text = "Connecting…"
        thread {
            try {
                BluetoothAdapter.getDefaultAdapter()?
                val s = device.createRfcommSocketToServiceRecord(sppUuid)
                s.connect()
                socket = s
                input = s.inputStream
                output = s.outputStream
                runOnUiThread {
                    status.text = "Connected: ${device.name ?: device.address}"
                    append("Connected")
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Connection failed"; append(e.message ?: "Connection error") }
            }
        }
    }

    private fun sendInit() {
        listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATSP5", "ATDP").forEach { sendAt(it) }
    }

    private fun sendAt(cmd: String) {
        if (!cmd.uppercase().startsWith("AT") || cmd.contains("\n") || cmd.contains("\r")) {
            append("Only single AT commands are accepted here")
            return
        }
        thread {
            try {
                val stream = output
                if (stream == null) {
                    runOnUiThread { append("Not connected") }
                    return@thread
                }
                stream.write((cmd.uppercase() + "\r").toByteArray())
                stream.flush()
                Thread.sleep(900)
                val available = input?.available() ?: 0
                val bytes = ByteArray(1024)
                val count = if (available > 0) input!!.read(bytes, 0, minOf(available, bytes.size)) else 0
                val response = if (count > 0) String(bytes, 0, count) else "(no response read)"
                runOnUiThread { append("$cmd → $response") }
            } catch (e: IOException) {
                runOnUiThread { append("I/O error: ${e.message}") }
            } catch (e: InterruptedException) {
                runOnUiThread { append("Command interrupted") }
            }
        }
    }

    private fun append(message: String) {
        log.append("\n$message")
    }

    override fun onDestroy() {
        try { socket?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
