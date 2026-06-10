package dev.stw.blocking

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class BlockOverlayHandles(
    val root: View,
    val countdownText: TextView,
    val continueButton: TextView,
)

/**
 * Shared raw-View UI for the block overlay, used by both trigger paths
 * (AppMonitorAccessibilityService and FloatingReminderService) so they always
 * look and behave identically.
 *
 * The palette mirrors the Compose theme in dev.stw.ui.Theme.kt; keep both in
 * sync when changing colors.
 */
object BlockOverlayUi {
    private val scrim = 0xCC1B1B22.toInt()
    private val primary = 0xFF536AA3.toInt()
    private val primaryContainer = 0xFFE2E7FA.toInt()
    private val onPrimaryContainer = 0xFF394B73.toInt()
    private val textStrong = 0xFF1B1B1F.toInt()
    private val textBody = 0xFF44464E.toInt()
    private val textMuted = 0xFF5F5D66.toInt()
    private val fieldBg = 0xFFF0EDF2.toInt()
    private val danger = 0xFFB3261E.toInt()
    private val miniBg = 0xDD536AA3.toInt()

    private const val COUNTDOWN_SECONDS = 3
    private const val PRIVACY_NOTE = "仅检测前台 App 包名/窗口身份，不读取文字、输入或聊天内容。"

    fun build(
        context: Context,
        appLabel: String,
        groupName: String?,
        limitSnapshot: GroupLimitSnapshot,
        requireTypedPurpose: Boolean,
        intents: List<String>,
        onCancel: () -> Unit,
        onContinue: (purpose: String?, addToPreset: Boolean) -> Unit,
    ): BlockOverlayHandles {
        fun Int.dp(): Int = (this * context.resources.displayMetrics.density).toInt()
        fun rounded(color: Int, radiusDp: Int): GradientDrawable =
            GradientDrawable().apply { setColor(color); cornerRadius = radiusDp.dp().toFloat() }

        val overLimit = limitSnapshot.overLimit
        var selectedIntent: String? = intents.firstOrNull()
        var typedPurpose: EditText? = null
        var customPurpose: EditText? = null
        var addPresetCheck: CheckBox? = null
        var customMode = requireTypedPurpose

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp(), 20.dp(), 22.dp(), 20.dp())
            background = rounded(Color.WHITE, 24)
            elevation = 12f
        }

        fun addText(textValue: String, sizeSp: Float, bold: Boolean, color: Int, topDp: Int) {
            card.addView(TextView(context).apply {
                text = textValue
                textSize = sizeSp
                setTextColor(color)
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, topDp.dp(), 0, 2.dp())
            })
        }

        fun button(text: String, bg: Int, fg: Int, onClick: () -> Unit) = TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            minHeight = 0
            setPadding(10.dp(), 0, 10.dp(), 0)
            setTextColor(fg)
            background = rounded(bg, 18)
            stateListAnimator = null
            elevation = 0f
            setOnClickListener { if (isEnabled) onClick() }
        }

        addText("时停 · Stop the World", 12f, true, if (overLimit) danger else primary, 0)
        addText(if (overLimit) "已经超时" else "先停一下", 30f, true, textStrong, 10)
        addText("你正在打开 $appLabel", 17f, true, textBody, 4)
        groupName?.let { addText("分组：$it", 14f, true, onPrimaryContainer, 4) }
        if (overLimit) {
            addText(
                "当前应用今日已用 ${DemoBlockPrefs.compactDuration(limitSnapshot.appUsedMillis)} · 分组今日已用 ${DemoBlockPrefs.compactDuration(limitSnapshot.groupUsedMillis)}",
                14f,
                false,
                textBody,
                8,
            )
            val limitLabel = if (limitSnapshot.source == LimitSource.APP) "单应用限时" else "分组限时"
            addText(
                "$limitLabel ${DemoBlockPrefs.compactDuration(limitSnapshot.limitMillis)} · 已超 ${DemoBlockPrefs.compactDuration(limitSnapshot.overMillis)}",
                16f,
                true,
                danger,
                4,
            )
            addText("建议先回到桌面，或确认这次继续打开的必要性。", 14f, false, textMuted, 4)
        }
        addText(
            if (requireTypedPurpose) "请输入这次打开的具体目的" else if (overLimit) "如果仍要打开，这次是为了什么？" else "这次打开是为了什么？",
            16f,
            false,
            textBody,
            8,
        )

        fun newPurposeEditor(): EditText = EditText(context).apply {
            hint = "输入这次的具体目的"
            textSize = 14f
            setSingleLine(false)
            minLines = 2
            setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
            background = rounded(fieldBg, 16)
        }

        addPresetCheck = CheckBox(context).apply {
            text = "加入目的预设，下次优先显示"
            textSize = 13f
            setTextColor(textMuted)
            isChecked = false
            setPadding(0, 2.dp(), 0, 0)
        }

        if (requireTypedPurpose) {
            typedPurpose = newPurposeEditor().apply { hint = "例如：查某个资料 / 回复某个人 / 完成一个任务" }
            card.addView(typedPurpose)
            card.addView(addPresetCheck)
        } else {
            val featured = intents.take(4)
            val hidden = intents.drop(4)
            val chipViews = mutableListOf<TextView>()
            fun refreshChips() {
                chipViews.forEach { view ->
                    val raw = view.tag as String
                    val selected = !customMode && raw == selectedIntent
                    view.text = if (selected) "✓ $raw" else raw
                    view.setTextColor(if (selected) onPrimaryContainer else textBody)
                    view.background = rounded(if (selected) primaryContainer else fieldBg, 999)
                }
            }
            fun chip(item: String): TextView = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
                tag = item
                setOnClickListener {
                    customMode = false
                    selectedIntent = item
                    refreshChips()
                    customPurpose?.clearFocus()
                }
            }
            fun addChipRows(parent: LinearLayout, values: List<String>) {
                values.chunked(2).forEach { rowItems ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 2.dp(), 0, 2.dp())
                        clipToPadding = false
                    }
                    rowItems.forEach { item ->
                        val chip = chip(item)
                        chipViews += chip
                        row.addView(chip, LinearLayout.LayoutParams(0, 42.dp(), 1f).apply { setMargins(3.dp(), 4.dp(), 3.dp(), 4.dp()) })
                    }
                    if (rowItems.size == 1) row.addView(View(context), LinearLayout.LayoutParams(0, 42.dp(), 1f).apply { setMargins(3.dp(), 4.dp(), 3.dp(), 4.dp()) })
                    parent.addView(row)
                }
            }

            val chipBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addChipRows(chipBox, featured)
            card.addView(chipBox)

            val secondaryPanel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(0, 4.dp(), 0, 0)
            }
            val secondaryTabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val moreBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val customBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            customPurpose = newPurposeEditor()
            customBox.addView(customPurpose)
            customBox.addView(addPresetCheck)

            fun setSecondaryMode(showCustom: Boolean) {
                customMode = showCustom
                moreBox.visibility = if (showCustom) View.GONE else View.VISIBLE
                customBox.visibility = if (showCustom) View.VISIBLE else View.GONE
                refreshChips()
            }
            val moreTab = button("更多预设", fieldBg, textBody) { setSecondaryMode(false) }
            val customTab = button("新目的", primaryContainer, onPrimaryContainer) { setSecondaryMode(true) }
            secondaryTabs.addView(moreTab, LinearLayout.LayoutParams(0, 38.dp(), 1f).apply { rightMargin = 5.dp() })
            secondaryTabs.addView(customTab, LinearLayout.LayoutParams(0, 38.dp(), 1f).apply { leftMargin = 5.dp() })
            secondaryPanel.addView(secondaryTabs)
            if (hidden.isNotEmpty()) {
                addChipRows(moreBox, hidden)
            } else {
                moreBox.addView(TextView(context).apply {
                    text = "没有更多预设，可以切换到新目的。"
                    textSize = 12f
                    setTextColor(textMuted)
                    setPadding(4.dp(), 8.dp(), 4.dp(), 6.dp())
                })
            }
            secondaryPanel.addView(moreBox)
            secondaryPanel.addView(customBox)

            val toggleSecondary = TextView(context).apply {
                text = "没有想要的？展开更多预设 / 新目的"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(onPrimaryContainer)
                gravity = Gravity.CENTER
                setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
                background = rounded(primaryContainer, 999)
                setOnClickListener {
                    secondaryPanel.visibility = if (secondaryPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    text = if (secondaryPanel.visibility == View.VISIBLE) "收起更多目的" else "没有想要的？展开更多预设 / 新目的"
                    if (secondaryPanel.visibility == View.VISIBLE && hidden.isEmpty()) setSecondaryMode(true)
                }
            }
            card.addView(toggleSecondary, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 40.dp()).apply { setMargins(0, 4.dp(), 0, 2.dp()) })
            card.addView(secondaryPanel)
            refreshChips()
        }

        val countdownText = TextView(context).apply {
            text = "还需等待 $COUNTDOWN_SECONDS 秒"
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(onPrimaryContainer)
            setPadding(0, 14.dp(), 0, 12.dp())
        }
        card.addView(countdownText)

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 2.dp(), 0, 2.dp())
            clipToPadding = false
        }
        val cancel = button("不打开了", primaryContainer, onPrimaryContainer) { onCancel() }
        val cont = button("继续 5 分钟", primary, Color.WHITE) {
            val customText = if (requireTypedPurpose) {
                typedPurpose?.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            } else {
                customPurpose?.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            }
            val chosen = if (customMode || requireTypedPurpose) customText else selectedIntent
            val addPreset = (customMode || requireTypedPurpose) && !customText.isNullOrBlank() && addPresetCheck?.isChecked == true
            onContinue(chosen, addPreset)
        }.apply { isEnabled = false; alpha = 0.45f }
        buttonRow.addView(cancel, LinearLayout.LayoutParams(0, 48.dp(), 1f).apply { rightMargin = 6.dp() })
        buttonRow.addView(cont, LinearLayout.LayoutParams(0, 48.dp(), 1f).apply { leftMargin = 6.dp() })
        card.addView(buttonRow)

        addText(PRIVACY_NOTE, 12f, false, textMuted, 10)

        val scroller = ScrollView(context).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        scroller.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20.dp(), 20.dp(), 20.dp(), 20.dp())
            setBackgroundColor(scrim)
        }
        root.addView(scroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))

        return BlockOverlayHandles(root, countdownText, cont)
    }

    fun buildMini(context: Context, intentText: String, onTap: () -> Unit): TextView {
        fun Int.dp(): Int = (this * context.resources.displayMetrics.density).toInt()
        return TextView(context).apply {
            text = "时停：$intentText"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
            background = GradientDrawable().apply { setColor(miniBg); cornerRadius = 999.dp().toFloat() }
            setOnClickListener { onTap() }
        }
    }

    /** Starts the shared 3-second friction countdown; returns the runnable so the caller can cancel it. */
    fun startCountdown(handler: Handler, handles: BlockOverlayHandles): Runnable {
        var remaining = COUNTDOWN_SECONDS
        val runnable = object : Runnable {
            override fun run() {
                if (remaining > 0) {
                    handles.countdownText.text = "还需等待 $remaining 秒"
                    remaining -= 1
                    handler.postDelayed(this, 1_000L)
                } else {
                    handles.countdownText.text = "可以继续，也可以选择不打开。"
                    handles.continueButton.isEnabled = true
                    handles.continueButton.alpha = 1f
                }
            }
        }
        handler.post(runnable)
        return runnable
    }
}
