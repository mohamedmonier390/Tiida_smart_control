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

    private val sppUuid = UUID.fromString(
        "00001101-0000-1000-8000-00805F9B34FB"
    )

    private var socket: BluetoothSocket? = null
    private var input: java.io.InputStream? = null
    private var output: java.io.OutputStream? = null

    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var command: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestBluetoothPermission()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        root.addView(TextView(this).apply {
            text = "Tiida Smart Control"
            textSize = 24f
        })

        status = TextView(this).apply {
            text = "Not connected"
            textSize = 16f
        }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "CHOOSE PAIRED ELM327"
            setOnClickListener { showPairedDevices() }
        })

        root.addView(Button(this).apply {
            text = "INITIALIZE ELM327"
            setOnClickListener { sendInit() }
        })

        command = EditText(this).apply {
            hint = "AT command (e.g. ATDP)"
        }
        root.addView(command)

        root.addView(Button(this).apply {
            text = "SEND COMMAND"
            setOnClickListener {
                sendAt(command.text.toString().trim())
            }
        })

        root.addView(TextView(this).apply {
            text = "Vehicle controls (disabled until verified)"
            textSize = 18f
        })

        listOf("LOCK", "UNLOCK", "LIGHTS").forEach { label ->
            root.addView(Button(this).apply {
                text = label
                isEnabled = false
            })
        }

        log = TextView(this).apply {
            text = "Log:\n"
            textSize = 14f
        }

        root.addView(
            ScrollView(this).apply {
                addView(log)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun requestBluetoothPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

        val permissions = mutableListOf<String>()

        if (checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (checkSelfPermission(
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        if (permissions.isNotEmpty()) {
            requestPermissions(
                permissions.toTypedArray(),
                100
            )
        }
    }
}

    private fun showPairedDevices() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestBluetoothPermission()
                append("Bluetooth permission required")
                return
            }

            val adapter = BluetoothAdapter.getDefaultAdapter()

            if (adapter == null) {
                append("Bluetooth not available")
                return
            }

            val devices = adapter.bondedDevices.toList()

            if (devices.isEmpty()) {
                append(
                    "No paired devices. Pair ELM327 from Android Bluetooth settings first."
                )
                return
            }

            AlertDialog.Builder(this)
                .setTitle("Select paired device")
                .setItems(
                    devices.map {
                        "${it.name ?: "Unknown"} — ${it.address}"
                    }.toTypedArray()
                ) { _, which ->
                    connect(devices[which])
                }
                .show()

        } catch (e: SecurityException) {
            append("Bluetooth permission denied")
        }
    }

    private fun connect(device: BluetoothDevice) {
        status.text = "Connecting..."

        thread {
            try {
                val s = device.createRfcommSocketToServiceRecord(sppUuid)
                s.connect()

                socket = s
                input = s.inputStream
                output = s.outputStream

                runOnUiThread {
                    status.text = "Connected"
                    append("Connected successfully")
                }

            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Connection failed"
                    append(e.message ?: "Connection error")
                }
            }
        }
    }

    private fun sendInit() {
        val commands = listOf(
            "ATZ",
            "ATE0",
            "ATL0",
            "ATS0",
            "ATH1",
            "ATSP5",
            "ATDP"
        )

        thread {
            commands.forEach { cmd ->
                sendCommandInternal(cmd)
                Thread.sleep(500)
            }
        }
    }

    private fun sendAt(cmd: String) {
        val clean = cmd.uppercase().trim()

        if (!clean.startsWith("AT") ||
            clean.contains("\n") ||
            clean.contains("\r")
        ) {
            append("Only single AT commands are accepted")
            return
        }

        thread {
            sendCommandInternal(clean)
        }
    }

    private fun sendCommandInternal(cmd: String) {
        try {
            val stream = output
            val inputStream = input

            if (stream == null || inputStream == null) {
                runOnUiThread {
                    append("Not connected")
                }
                return
            }

            stream.write((cmd + "\r").toByteArray())
            stream.flush()

            Thread.sleep(1000)

            val bytes = ByteArray(1024)
            val available = inputStream.available()

            val count = if (available > 0) {
                inputStream.read(
                    bytes,
                    0,
                    minOf(available, bytes.size)
                )
            } else {
                0
            }

            val response = if (count > 0) {
                String(bytes, 0, count)
            } else {
                "(no response)"
            }

            runOnUiThread {
                append("$cmd → $response")
            }

        } catch (e: IOException) {
            runOnUiThread {
                append("I/O error: ${e.message}")
            }
        } catch (e: Exception) {
            runOnUiThread {
                append("Error: ${e.message}")
            }
        }
    }

    private fun append(message: String) {
        runOnUiThread {
            log.append("\n$message")
        }
    }

    override fun onDestroy() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
