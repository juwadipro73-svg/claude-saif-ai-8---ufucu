package com.saif.ai.builder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.saif.ai.builder.data.AdGate
import com.saif.ai.builder.databinding.ActivityMainBinding
import com.saif.ai.builder.databinding.DialogSettingsBinding
import com.saif.ai.builder.ui.BuildViewModel
import com.saif.ai.builder.ui.UiState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BuildViewModel by viewModels()

    /** Build complete hone par set hota hai — download button isi ko use karta hai. */
    private var downloadUrl: String? = null
    private var builtAppName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.btnBuild.setOnClickListener { onBuildClicked() }
        binding.btnDownload.setOnClickListener { openDownloadPage() }
        binding.btnCancel.setOnClickListener { viewModel.cancelBuild() }
        binding.btnOpenRun.setOnClickListener { openRunLogs() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(state) }
            }
        }

        if (!viewModel.hasToken) {
            showSettingsDialog(firstRun = true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> {
            showSettingsDialog(firstRun = false)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ------------------------------------------------------------------ build

    private fun onBuildClicked() {
        hideKeyboard()
        viewModel.startBuild(binding.etPrompt.text?.toString().orEmpty())
    }

    private fun render(state: UiState) {
        when (state) {
            is UiState.Idle -> {
                binding.progress.isVisible = false
                binding.btnCancel.isVisible = false
                binding.btnBuild.isEnabled = true
                binding.btnDownload.isVisible = false
                binding.btnOpenRun.isVisible = false
                binding.tvStatus.text = getString(R.string.status_idle)
                binding.tvLog.text = ""
                downloadUrl = null
            }

            is UiState.Running -> {
                binding.progress.isVisible = true
                binding.btnCancel.isVisible = true
                binding.btnBuild.isEnabled = false
                binding.btnDownload.isVisible = false
                binding.btnOpenRun.isVisible = false
                binding.tvStatus.text = state.step
                binding.tvLog.text = state.log.joinToString("\n")
                downloadUrl = null
            }

            is UiState.Ready -> {
                binding.progress.isVisible = false
                binding.btnCancel.isVisible = false
                binding.btnBuild.isEnabled = true
                binding.tvStatus.text = getString(R.string.status_ready, state.appName)
                binding.tvLog.text = state.log.joinToString("\n")
                downloadUrl = state.downloadUrl
                builtAppName = state.appName
                // 👇 Build complete → download button dikhao
                binding.btnDownload.isVisible = true
                binding.btnOpenRun.isVisible = true
            }

            is UiState.Failed -> {
                binding.progress.isVisible = false
                binding.btnCancel.isVisible = false
                binding.btnBuild.isEnabled = true
                binding.btnDownload.isVisible = false
                binding.btnOpenRun.isVisible = true
                binding.tvStatus.text = getString(R.string.status_failed, state.message)
                binding.tvLog.text = state.log.joinToString("\n")
                downloadUrl = null
            }
        }
        binding.nestedScroll.post { binding.nestedScroll.fullScroll(View.FOCUS_DOWN) }
    }

    // --------------------------------------------------------------- download

    private fun openDownloadPage() {
        val url = downloadUrl ?: return
        val browserUrl = AdGate.resolve(url, builtAppName, viewModel.adGateUrl)
        // Browser me khulega → (AD_GATE_URL set hai to) ad page → auto download
        openInBrowser(browserUrl)
    }

    private fun openRunLogs() {
        val runUrl = (viewModel.state.value as? UiState.Ready)?.runUrl
            ?: (viewModel.state.value as? UiState.Failed)?.let { extractUrl(it.message) }
            ?: return
        openInBrowser(runUrl)
    }

    private fun openInBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (error: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.no_browser, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun extractUrl(text: String): String? =
        Regex("https?://\\S+").find(text)?.value

    // --------------------------------------------------------------- settings

    private fun showSettingsDialog(firstRun: Boolean) {
        val dialogBinding = DialogSettingsBinding.inflate(layoutInflater)
        dialogBinding.etToken.setText(viewModel.githubToken)
        dialogBinding.etFirebaseUrl.setText(viewModel.firebaseDbUrl)
        dialogBinding.etAdGateUrl.setText(viewModel.adGateUrl)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                viewModel.saveSettings(
                    token = dialogBinding.etToken.text?.toString().orEmpty(),
                    firebaseDbUrl = dialogBinding.etFirebaseUrl.text?.toString().orEmpty(),
                    adGateUrl = dialogBinding.etAdGateUrl.text?.toString().orEmpty()
                )
                if (!viewModel.hasToken) {
                    Snackbar.make(binding.root, R.string.token_required, Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(!firstRun)
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etPrompt.windowToken, 0)
    }
}
