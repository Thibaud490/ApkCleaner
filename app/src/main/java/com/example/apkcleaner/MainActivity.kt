package com.example.apkcleaner

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import android.os.Bundle
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val found = mutableListOf<File>()
    private val labels = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var listView: ListView
    private lateinit var status: TextView
    private lateinit var btnScan: Button
    private lateinit var btnAll: Button
    private lateinit var btnDelete: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        status = TextView(this).apply {
            text = "Appuie sur « Scanner » pour chercher les fichiers .apk."
            textSize = 16f
        }
        btnScan = Button(this).apply { text = "Scanner" }
        btnAll = Button(this).apply { text = "Tout sélectionner" }
        btnDelete = Button(this).apply { text = "Supprimer la sélection" }

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, labels)
        listView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            adapter = this@MainActivity.adapter
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        root.addView(status)
        root.addView(btnScan)
        root.addView(btnAll)
        root.addView(listView)
        root.addView(btnDelete)
        setContentView(root)

        btnScan.setOnClickListener { if (ensurePermission()) scan() }
        btnAll.setOnClickListener {
            for (i in 0 until adapter.count) listView.setItemChecked(i, true)
        }
        btnDelete.setOnClickListener { confirmDelete() }
    }

    private fun hasPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun ensurePermission(): Boolean {
        if (hasPermission()) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Toast.makeText(
                this,
                "Active « Accès à tous les fichiers » pour cette appli, puis reviens.",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 1
            )
        }
        return false
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPermission()) scan()
    }

    private fun scan() {
        status.text = "Scan en cours…"
        btnScan.isEnabled = false
        thread {
            val result = Environment.getExternalStorageDirectory()
                .walkTopDown()
                .onFail { _, _ -> }
                .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                .toList()
                .sortedByDescending { it.length() }

            runOnUiThread {
                found.clear()
                found.addAll(result)
                labels.clear()
                result.forEach {
                    labels.add(
                        "${it.name}\n${it.parent} — " +
                            Formatter.formatShortFileSize(this, it.length())
                    )
                }
                adapter.notifyDataSetChanged()
                val total = result.sumOf { it.length() }
                status.text = if (result.isEmpty()) {
                    "Aucun fichier .apk trouvé."
                } else {
                    "${result.size} fichier(s) .apk trouvé(s) — " +
                        Formatter.formatShortFileSize(this, total)
                }
                btnScan.isEnabled = true
            }
        }
    }

    private fun confirmDelete() {
        val checked = (0 until adapter.count).filter { listView.isItemChecked(it) }
        if (checked.isEmpty()) {
            Toast.makeText(this, "Rien n'est sélectionné.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Suppression définitive")
            .setMessage("Supprimer ${checked.size} fichier(s) .apk ? Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                var ok = 0
                checked.forEach { if (found[it].delete()) ok++ }
                Toast.makeText(
                    this, "$ok/${checked.size} fichier(s) supprimé(s).", Toast.LENGTH_LONG
                ).show()
                scan()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
