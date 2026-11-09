package com.andrerinas.openheadunit.phoenix

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Small translucent button in the top-right corner that opens the system HOME (the Phoenix
 * launcher's mode switcher). Top-right because portrait Android Auto stacks its own controls
 * (settings, mute, compass) down the left edge from the top-left, and the receiver's menu screen
 * keeps its status icons on the left too. Only shown once the app has been started by the Phoenix launcher.
 */
object PhoenixHomeButton {
    private const val SIZE_PX = 56
    private const val MARGIN_PX = 16

    fun attach(activity: Activity) {
        if (!PhoenixLaunch.isActive(activity)) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return
        content.findViewWithTag<ImageButton>(TAG)?.let { it.bringToFront(); return }
        val button = ImageButton(activity).apply {
            tag = TAG
            contentDescription = "Home"
            setImageResource(R.drawable.phoenix_ic_home)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66000000)
            }
            alpha = 0.7f
            setPadding(12, 12, 12, 12)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            elevation = 100f
            setOnClickListener { goHome(activity) }
        }
        content.addView(button, FrameLayout.LayoutParams(SIZE_PX, SIZE_PX, Gravity.TOP or Gravity.END).apply {
            rightMargin = MARGIN_PX
            topMargin = MARGIN_PX
        })
    }

    fun goHome(activity: Activity) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            AppLog.w("Phoenix: no HOME activity: ${e.message}")
        }
    }

    private const val TAG = "phoenix_home_button"
}
