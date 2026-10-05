package com.leasingdocument.app.capture

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.leasingdocument.app.network.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Shows only encrypted drafts belonging to the currently signed-in agent. */
class CaptureDraftsActivity : ComponentActivity() {
    private lateinit var content: LinearLayout
    private lateinit var store: EncryptedCaptureStore
    private var agentId = 0L
    private var selectedId: String? = null
    private var displayed: Bitmap? = null
    private var loading = false
    private var page = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        agentId = AuthSession.userId ?: 0L
        if (!authorized()) { finish(); return }
        try { store = EncryptedCaptureStore(applicationContext, agentId) }
        catch (e: Exception) {
            Toast.makeText(this, "Cannot access local draft storage.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        val pad = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            insets
        }
        setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (loading) return
                if (selectedId != null) showList() else finish()
            }
        })
        showList()
    }

    override fun onResume() {
        super.onResume()
        if (!authorized()) finish()
    }

    private fun authorized() = agentId > 0 && AuthSession.userId == agentId &&
        AuthSession.role == "AGENT" && !AuthSession.token.isNullOrBlank()

    private fun reset() {
        content.removeAllViews()
        displayed?.recycle(); displayed = null
    }

    private fun label(text: String, size: Float = 16f) {
        content.addView(TextView(this).apply { this.text = text; textSize = size; setPadding(0, 8, 0, 8) })
    }

    private fun button(text: String, action: () -> Unit) {
        content.addView(Button(this).apply { this.text = text; setOnClickListener { if (!loading) action() } })
    }

    private fun showList() {
        if (!authorized()) { finish(); return }
        selectedId = null; reset(); loading = true
        label("Local Capture Drafts", 24f)
        label("Encrypted on this phone. These documents have not been submitted to the backend.")
        lifecycleScope.launch {
            try {
                val ids = withContext(Dispatchers.IO) { store.ids() }
                if (page * 20 >= ids.size) page = 0
                if (ids.isEmpty()) label("No saved drafts for this agent.")
                for (id in ids.drop(page * 20).take(20)) {
                    val summary = try {
                        withContext(Dispatchers.IO) {
                            val draft = store.load(id)
                            try {
                                "${draft.metadata.optString("documentTypeName")} — ${draft.metadata.optString("customerName")}\n${draft.metadata.optString("capturedAtOffset")}"
                            } finally { draft.jpeg.fill(0) }
                        }
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { "Unreadable draft — $id" }
                    button(summary) { showDraft(id) }
                }
                if (page > 0) button("Previous page") { page--; showList() }
                if ((page + 1) * 20 < ids.size) button("Next page") { page++; showList() }
                button("Refresh") { showList() }
                button("Back") { finish() }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                label("Cannot read local draft storage.")
                button("Retry") { showList() }
                button("Back") { finish() }
            } finally { loading = false }
        }
    }

    private fun showDraft(id: String) {
        if (!authorized()) { finish(); return }
        reset(); selectedId = id; loading = true
        label("Local draft — not submitted", 24f)
        lifecycleScope.launch {
            var readyBitmap: Bitmap? = null
            try {
                val meta = withContext(Dispatchers.IO) {
                    val draft = store.load(id)
                    try {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(draft.jpeg, 0, draft.jpeg.size, opts)
                        require(opts.outWidth > 0 && opts.outHeight > 0 &&
                            opts.outWidth.toLong() * opts.outHeight <= 12_000_000L)
                        var sample = 1
                        while (maxOf(opts.outWidth, opts.outHeight) / sample > 2048) sample *= 2
                        val decoded = requireNotNull(BitmapFactory.decodeByteArray(draft.jpeg, 0, draft.jpeg.size,
                            BitmapFactory.Options().apply { inSampleSize = sample }))
                        val rotation = draft.metadata.optInt("rotationDegrees", 0)
                        if (rotation == 0) readyBitmap = decoded
                        else {
                            readyBitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                                Matrix().apply { postRotate(rotation.toFloat()) }, true)
                            if (readyBitmap !== decoded) decoded.recycle()
                        }
                        draft.metadata
                    } finally { draft.jpeg.fill(0) }
                }
                if (!authorized()) { finish(); return@launch }
                displayed = readyBitmap; readyBitmap = null
                content.addView(ImageView(this@CaptureDraftsActivity).apply {
                    setImageBitmap(displayed); adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, LinearLayout.LayoutParams(-1, (360 * resources.displayMetrics.density).toInt()))
                showMetadata(meta)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                label("This draft could not be decrypted or failed its integrity check. It cannot be used.")
            } finally {
                readyBitmap?.recycle()
                loading = false
            }
            button("Delete local draft") { confirmDelete(id) }
            button("Back to drafts") { showList() }
        }
    }

    private fun showMetadata(meta: JSONObject) {
        label("Customer: ${meta.optString("customerName")}\nDocument: ${meta.optString("documentTypeName")}")
        label("Captured: ${meta.optString("capturedAtOffset")}\nResolution: ${meta.optInt("imageWidth")} × ${meta.optInt("imageHeight")}")
        label("Blur score: ${meta.optDouble("blurScore")}\nBrightness: ${meta.optDouble("brightnessScore")}")
        label("Local encryption authentication and image hash verified. No server verification has occurred.")
        label("SHA-256: ${meta.optString("mobileSha256")}", 12f)
    }

    private fun confirmDelete(id: String) {
        AlertDialog.Builder(this)
            .setMessage("Permanently delete this local draft? It has not been submitted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (!authorized()) { finish(); return@setPositiveButton }
                loading = true
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { store.delete(id) }
                        loading = false; showList()
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) {
                        loading = false
                        Toast.makeText(this@CaptureDraftsActivity, "Could not delete draft.", Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
    }

    override fun onDestroy() {
        if (::content.isInitialized) content.removeAllViews()
        displayed?.recycle(); displayed = null
        super.onDestroy()
    }
}
