package com.cue.reply.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.cue.reply.MainActivity
import com.cue.reply.domain.model.ReplyDraft

/**
 * Floating draft card drawn over Tinder/Hinge/WhatsApp (Stitch: "Draft Overlay").
 * Programmatic views styled to the Sentience Core palette — an overlay window
 * can't host the Compose theme without a full lifecycle owner, so the design
 * tokens are inlined here.
 */
class OverlayController(private val context: Context) {

    private object Palette {
        val surface = Color.parseColor("#FFFFFF")
        val surfaceLow = Color.parseColor("#F3F4F5")
        val outlineVariant = Color.parseColor("#C1C6D6")
        val onSurface = Color.parseColor("#191C1D")
        val onSurfaceVariant = Color.parseColor("#414754")
        val primary = Color.parseColor("#005BBF")
        val primaryFixed = Color.parseColor("#D8E2FF")
        val secondary = Color.parseColor("#006972")
    }

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var countdownView: TextView? = null

    fun canDraw(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /** Views and window operations must run on the main thread; callers may be on any dispatcher. */
    fun show(
        title: String,
        drafts: List<ReplyDraft>,
        onChoose: (ReplyDraft) -> Unit,
        onRegenerate: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        if (!canDraw()) return
        mainHandler.post { showOnMain(title, drafts, onChoose, onRegenerate, onDismiss) }
    }

    private fun showOnMain(
        title: String,
        drafts: List<ReplyDraft>,
        onChoose: (ReplyDraft) -> Unit,
        onRegenerate: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        hideOnMain()

        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Palette.surface)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Palette.outlineVariant)
            }
            elevation = dp(8).toFloat()
        }

        // Header: pulsing status + title + actions
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(8), dp(12))
            background = GradientDrawable().apply {
                setColor(Palette.surfaceLow)
                cornerRadii = floatArrayOf(
                    dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(),
                    0f, 0f, 0f, 0f,
                )
            }
        }
        header.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Palette.secondary)
            }
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                rightMargin = dp(8)
            }
            animate().alpha(0.4f).setDuration(900).withEndAction {
                animate().alpha(1f).setDuration(900).start()
            }.start()
        })
        header.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = "SUGGEST MODE"
                setTextColor(Palette.secondary)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                letterSpacing = 0.15f
            })
            addView(TextView(context).apply {
                text = title
                setTextColor(Palette.onSurfaceVariant)
                textSize = 11f
                maxLines = 1
            })
        })
        fun headerButton(label: String, onClick: () -> Unit) = TextView(context).apply {
            text = label
            setTextColor(Palette.onSurfaceVariant)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }
        header.addView(headerButton("↻") { onRegenerate() })
        header.addView(headerButton("✕") { onDismiss(); hide() })
        root.addView(header)

        // Draft chips
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        body.addView(TextView(context).apply {
            text = "DRAFT REPLIES DETECTED"
            setTextColor(Palette.onSurfaceVariant)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.1f
            setPadding(0, 0, 0, dp(8))
        })
        drafts.forEachIndexed { index, draft ->
            body.addView(TextView(context).apply {
                text = draft.text
                setTextColor(Palette.onSurface)
                textSize = 14f
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(if (index == 0) Palette.primaryFixed else Palette.surface)
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), if (index == 0) Palette.primary else Palette.outlineVariant)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(8) }
                setOnClickListener { onChoose(draft) }
            })
        }
        root.addView(body)

        // Footer: open full thread in the app
        root.addView(TextView(context).apply {
            text = "VIEW FULL THREAD →"
            setTextColor(Palette.primary)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
            setPadding(dp(16), dp(8), dp(16), dp(14))
            setOnClickListener {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                onDismiss()
                hide()
            }
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(96)
            horizontalMargin = 0.04f
        }

        try {
            windowManager.addView(root, params)
            view = root
        } catch (e: Exception) {
            view = null
        }
    }

    /**
     * Pending auto-send card: shows the reply that is about to go out and the
     * seconds left, so an automated send is never a surprise and can be stopped.
     */
    fun showCountdown(
        title: String,
        draftText: String,
        onSendNow: () -> Unit,
        onCancel: () -> Unit,
    ) {
        if (!canDraw()) return
        mainHandler.post {
            hideOnMain()
            val density = context.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Palette.surface)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Palette.outlineVariant)
                }
                elevation = dp(8).toFloat()
                setPadding(dp(16), dp(14), dp(16), dp(12))
            }

            root.addView(TextView(context).apply {
                text = "AUTO-SENDING TO ${title.uppercase()}"
                setTextColor(Palette.onSurfaceVariant)
                textSize = 10f
                typeface = Typeface.MONOSPACE
                letterSpacing = 0.1f
            })

            countdownView = TextView(context).apply {
                setTextColor(Palette.primary)
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(4), 0, dp(8))
            }
            root.addView(countdownView)

            root.addView(TextView(context).apply {
                text = draftText
                setTextColor(Palette.onSurface)
                textSize = 14f
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(Palette.primaryFixed)
                    cornerRadius = dp(8).toFloat()
                }
            })

            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            }
            fun actionButton(label: String, color: Int, onClick: () -> Unit) =
                TextView(context).apply {
                    text = label
                    setTextColor(color)
                    textSize = 13f
                    typeface = Typeface.MONOSPACE
                    gravity = Gravity.CENTER
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { onClick() }
                }
            actions.addView(actionButton("CANCEL", Palette.onSurfaceVariant) { onCancel(); hide() })
            actions.addView(actionButton("SEND NOW", Palette.primary) { onSendNow() })
            root.addView(actions)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dp(96)
                horizontalMargin = 0.04f
            }
            try {
                windowManager.addView(root, params)
                view = root
            } catch (e: Exception) {
                view = null
            }
        }
    }

    /** Updates the pending-send card; safe to call from any thread. */
    fun updateCountdown(secondsLeft: Int) {
        mainHandler.post {
            countdownView?.text = if (secondsLeft <= 0) "Sending…" else "Sending in ${secondsLeft}s"
        }
    }

    fun hide() {
        mainHandler.post { hideOnMain() }
    }

    private fun hideOnMain() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        countdownView = null
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
}
