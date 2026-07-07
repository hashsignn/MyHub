package com.contentreg.app.feature4_retention.logs

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.contentreg.app.R
import com.contentreg.app.core.util.CrashReporter
import com.contentreg.app.databinding.ActivityLogsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Views the local crash log and lets the user share it (e.g. to email a bug report) or clear it.
 * All contents stay on-device until the user explicitly shares them.
 */
class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.logs_title)

        binding.shareLogsButton.setOnClickListener { shareLog() }
        binding.clearLogsButton.setOnClickListener {
            CrashReporter.clear(this)
            refresh()
        }
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { CrashReporter.read(this@LogsActivity) }
            binding.logsText.text = text.ifBlank { getString(R.string.logs_empty) }
        }
    }

    private fun shareLog() {
        val file = CrashReporter.logFile(this)
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, R.string.logs_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.logs_share_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.logs_share)))
    }
}
