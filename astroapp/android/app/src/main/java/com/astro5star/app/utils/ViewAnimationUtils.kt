package com.astro5star.app.utils

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Performance-safe animation utilities using ViewPropertyAnimator.
 * Optimized for 60fps on low-end devices.
 */
object ViewAnimationUtils {

    /**
     * Entry animation for list items or views.
     * Fade + Slide up once.
     */
    fun applyEntryAnimation(view: View, delay: Long = 0) {
        view.alpha = 0f
        view.translationY = 50f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(delay)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Premium Card/Button tap animation.
     * Scale down on press, snap back on release.
     */
    fun View.applyTapAnimation(onAction: () -> Unit) {
        this.setOnClickListener {
            this.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    this.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .withEndAction { onAction() }
                        .start()
                }
                .start()
        }
    }

    /**
     * Subtle pulse highlight.
     * Runs once, no infinite loop.
     */
    fun pulseOnce(view: View) {
        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start()
            }
            .start()
    }

    private class DecelerateInterpolator : android.view.animation.DecelerateInterpolator()
}
