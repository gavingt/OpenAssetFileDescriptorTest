package com.gavinsappcreations.usbtest

import android.content.*
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.system.Os
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.documentfile.provider.DocumentFile
import com.gavinsappcreations.usbtest.databinding.ActivityMainBinding
import kotlin.system.measureTimeMillis

@RequiresApi(Build.VERSION_CODES.Q)
class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        prefs = getSharedPreferences("prefs", 0)

        binding.permissionButton.setOnClickListener {
            promptSAFPermissions()
        }

        registerBroadcastReceivers()

        binding.measureMethodButton.setOnClickListener {
            if (prefs.getString(PREFS_PERSISTABLE_URI, "")!!.isEmpty()) {
                Toast.makeText(this, "USB permission not yet granted", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            var freeDriveSpace: Long
            val timeInMillis = measureTimeMillis {
                val persistedUri = Uri.parse(prefs.getString(PREFS_PERSISTABLE_URI, ""))
                val rootUri = DocumentFile.fromTreeUri(this, persistedUri)!!.uri
                val pfd = contentResolver.openAssetFileDescriptor(rootUri, "r")
                val stats = Os.fstatvfs(pfd!!.fileDescriptor)
                freeDriveSpace = stats.f_bavail * stats.f_bsize
            }


            binding.resultsTextView.text = "Time to execute method: $timeInMillis ms\n\n" +
                    "Free drive space: $freeDriveSpace bytes"
        }
    }


    private fun promptSAFPermissions() {
        var intent: Intent? = null
        val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumes = storageManager.storageVolumes
        storageVolumes.forEach {
            if (it.isRemovable) {
                intent = it.createOpenDocumentTreeIntent()
                return@forEach
            }
        }
        if (intent == null) {
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        }
        startActivityForResult(intent, SAF_PERMISSION_REQUEST_CODE)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SAF_PERMISSION_REQUEST_CODE) {
            if (data?.data == null) {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = data.data!!
            saveDrivePermission(uri, data.flags)
        }
    }


    private fun saveDrivePermission(uri: Uri, flags: Int) {
        grantUriPermission(packageName, uri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putString(PREFS_PERSISTABLE_URI, uri.toString()).apply()

        val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        contentResolver.takePersistableUriPermission(uri, takeFlags)
    }


    private fun registerBroadcastReceivers() {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction("android.hardware.usb.action.USB_STATE")
        registerReceiver(MyBroadcastReceiver(), filter)
    }


    inner class MyBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Log.d("onReceiveUsbMountEvent", "onReceiveUsbMountEvent action received = $action")
            if (action == "android.hardware.usb.action.USB_STATE") {
                val hostConnected = intent.extras?.getBoolean("host_connected")
                if (hostConnected == true) {
                    // USB_STATE CONNECTED
                    binding.measureMethodButton.apply {
                        isEnabled = true
                        text = "Measure method time"
                    }
                } else {
                    // USB_STATE DISCONNECTED
                    binding.measureMethodButton.apply {
                        isEnabled = false
                        text = "USB drive not attached"
                    }
                }
            }
        }
    }
}


const val SAF_PERMISSION_REQUEST_CODE = 2000
const val PREFS_PERSISTABLE_URI = "URI"
const val ACTION_USB_PERMISSION = "com.simplifieditproducts.picturekeeper.USB_PERMISSION"
