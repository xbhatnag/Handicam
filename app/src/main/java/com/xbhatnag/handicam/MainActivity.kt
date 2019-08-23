package com.xbhatnag.handicam

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val connectButton = findViewById<Button>(R.id.connect)

        connectButton.setOnClickListener {
            val dialog = AlertDialog.Builder(this)
                .setTitle("Connecting to Sony a6000")
                .setMessage("Establishing WiFi Connection...")
                .setCancelable(false)
                .create()
            dialog.show()
            GlobalScope.launch(Dispatchers.IO) {
                Connection.connectToWifi(application)
                GlobalScope.launch(Dispatchers.Main) {
                    dialog.setMessage("Enabling Shooting Mode...")
                }
                Connection.startRecMode()
                GlobalScope.launch(Dispatchers.Main) {
                    dialog.dismiss()
                    startActivity(Intent(this@MainActivity, ShootingActivity::class.java))
                }
            }
        }

    }

}
