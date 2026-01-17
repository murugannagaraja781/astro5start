package com.astro5star.app.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.astro5star.app.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class ThemeManagerTest {

    @Mock
    lateinit var context: Context

    @Mock
    lateinit var activity: Activity

    @Mock
    lateinit var sharedPrefs: SharedPreferences

    @Mock
    lateinit var editor: SharedPreferences.Editor

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPrefs)
        `when`(activity.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPrefs)
        `when`(sharedPrefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(editor.putInt(anyString(), anyInt())).thenReturn(editor)
    }

    @Test
    fun `test setTheme saves theme to SharedPreferences`() {
        ThemeManager.setTheme(context, AppThemeID.COSMIC)

        verify(editor).putString("selected_theme", "COSMIC")
        verify(editor).apply()
    }

    @Test
    fun `test applyTheme applies correct style for VEDIC`() {
        // Arrange
        `when`(sharedPrefs.getString("selected_theme", "LUXURY")).thenReturn("VEDIC")

        // Act
        ThemeManager.applyTheme(activity)

        // Assert
        verify(activity).setTheme(R.style.Theme_FCMCallApp_Vedic)
    }

    @Test
    fun `test applyTheme applies correct style for COSMIC`() {
        // Arrange
        `when`(sharedPrefs.getString("selected_theme", "LUXURY")).thenReturn("COSMIC")

        // Act
        ThemeManager.applyTheme(activity)

        // Assert
        verify(activity).setTheme(R.style.Theme_FCMCallApp_Cosmic)
    }

    @Test
    fun `test applyTheme defaults to LUXURY on error or missing`() {
        // Arrange
        `when`(sharedPrefs.getString("selected_theme", "LUXURY")).thenReturn(null)

        // Act
        ThemeManager.applyTheme(activity)

        // Assert
        verify(activity).setTheme(R.style.Theme_FCMCallApp_Luxury)
    }
}
