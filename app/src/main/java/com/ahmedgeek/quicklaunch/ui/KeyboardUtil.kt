package com.ahmedgeek.quicklaunch.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import android.view.inputmethod.InputMethodManager

object KeyboardUtil {
    /** True when a physical keyboard is attached and usable, so the IME should stay out of the way. */
    fun hasHardwareKeyboard(config: Configuration): Boolean =
        config.keyboard != Configuration.KEYBOARD_NOKEYS &&
            config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO

    fun hideIme(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun showIme(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * Show the soft keyboard without the system's 275 ms slide-in animation.
     * On API 30+ we take control of the IME insets animation and jump straight to the shown state.
     * Falls back to a plain showSoftInput if control is refused.
     */
    fun showImeInstantly(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            showIme(view)
            return
        }
        val controller = view.windowInsetsController
        if (controller == null) {
            showIme(view)
            return
        }
        controller.controlWindowInsetsAnimation(
            WindowInsets.Type.ime(),
            0L,
            null,
            null,
            object : WindowInsetsAnimationControlListener {
                override fun onReady(animationController: WindowInsetsAnimationController, types: Int) {
                    animationController.setInsetsAndAlpha(animationController.shownStateInsets, 1f, 1f)
                    animationController.finish(true)
                }

                override fun onFinished(animationController: WindowInsetsAnimationController) {}

                override fun onCancelled(animationController: WindowInsetsAnimationController?) {
                    showIme(view)
                }
            },
        )
    }
}
