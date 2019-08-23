package com.xbhatnag.handicam

import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ShootingActivity : AppCompatActivity() {
    var backPressedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_shooting)

        val imageView = findViewById<ImageView>(R.id.frame)
        val zoomInButton = findViewById<Button>(R.id.zoomIn)
        val zoomOutButton = findViewById<Button>(R.id.zoomOut)
        val focusButton = findViewById<Button>(R.id.focus)

        GlobalScope.launch(Dispatchers.IO) {
            val liveviewURL = Connection.startLiveview()
            try {
                Connection.stream(liveviewURL, imageView)
            } catch (e : Exception) {
                GlobalScope.launch(Dispatchers.Main) {
                    imageView.setImageResource(R.drawable.ic_error_outline_red_24dp)
                }
                e.printStackTrace()
            }
        }

        imageView.setOnClickListener(takePicture)
        zoomInButton.setOnClickListener {
            GlobalScope.launch(Dispatchers.IO) {
                Connection.zoomIn()
            }
        }

        zoomOutButton.setOnClickListener {
            GlobalScope.launch(Dispatchers.IO) {
                Connection.zoomOut()
            }
        }

        focusButton.setOnClickListener {
            GlobalScope.launch(Dispatchers.IO) {
                Connection.halfPressShutter()
            }
        }
    }

    val takePicture = View.OnClickListener {
        Toast.makeText(this@ShootingActivity, "Click!", Toast.LENGTH_SHORT).show()
        GlobalScope.launch(Dispatchers.IO) {
            Connection.takePicture()
        }
    }

    override fun onBackPressed() {
        if (backPressedOnce) {
            val dialog = AlertDialog.Builder(this)
                .setTitle("Disconnecting from Sony a6000")
                .setMessage("Disabling LiveView...")
                .setCancelable(false)
                .create()
            dialog.show()
            GlobalScope.launch(Dispatchers.IO) {
                Connection.stopLiveview()
                GlobalScope.launch(Dispatchers.Main) {
                    dialog.setMessage("Disabling Shooting Mode...")
                }
                Connection.stopRecMode()
                GlobalScope.launch(Dispatchers.Main) {
                    dialog.setMessage("Disconnecting from WiFi network...")
                }
                Connection.disconnectFromWiFi(application)
                GlobalScope.launch(Dispatchers.Main) {
                    dialog.dismiss()
                    super.onBackPressed()
                }
            }
            return
        }

        backPressedOnce = true
        Toast.makeText(this, "Press the back button again to exit", Toast.LENGTH_SHORT).show()

        Handler().postDelayed(kotlinx.coroutines.Runnable { backPressedOnce = false }, 2000)
    }
}
