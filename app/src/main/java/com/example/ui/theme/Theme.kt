package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
      primary = PrimaryBlue,
      secondary = PrimaryBlue,
      tertiary = PrimaryBlue,
      background = BackgroundLight,
      surface = BackgroundLight,
      surfaceVariant = CardGlassLight,
      onPrimary = Color.White,
      onSecondary = Color.White,
      onTertiary = Color.White,
      onBackground = TextPrimary,
      onSurface = TextPrimary,
      onSurfaceVariant = TextPrimary
  )

private val LightColorScheme =
  lightColorScheme(
      primary = PrimaryBlue,
      secondary = PrimaryBlue,
      tertiary = PrimaryBlue,
      background = BackgroundLight,
      surface = BackgroundLight,
      surfaceVariant = CardGlassLight,
      onPrimary = Color.White,
      onSecondary = Color.White,
      onTertiary = Color.White,
      onBackground = TextPrimary,
      onSurface = TextPrimary,
      onSurfaceVariant = TextPrimary
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled for theme consistency
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
