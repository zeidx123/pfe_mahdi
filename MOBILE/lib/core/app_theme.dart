import 'package:flutter/material.dart';

/// Shared visual language with the supervisor web application.
class GestiTheme {
  static const primary = Color(0xFF209B75);
  static const primaryDark = Color(0xFF187D5E);
  static const ink = Color(0xFF22313E);
  static const muted = Color(0xFF687783);
  static const bg = Color(0xFFF4F6F7);
  static const border = Color(0xFFE2E8E7);
  static const navy = Color(0xFF15222E);
  static const soft = Color(0xFFEDF8F3);
  static const amber = Color(0xFF976619);
  static const amberSoft = Color(0xFFFFF7E7);

  static ThemeData get light {
    final scheme = ColorScheme.fromSeed(
      seedColor: primary,
      primary: primaryDark,
      secondary: const Color(0xFF234669),
      surface: Colors.white,
      onSurface: ink,
      outline: border,
      error: const Color(0xFFB6453E),
    );
    final base = ThemeData(useMaterial3: true, colorScheme: scheme);
    final text = base.textTheme
        .apply(bodyColor: ink, displayColor: ink)
        .copyWith(
          headlineLarge: const TextStyle(
              fontSize: 32,
              fontWeight: FontWeight.w700,
              letterSpacing: -0.9,
              height: 1.2,
              color: ink),
          headlineMedium: const TextStyle(
              fontSize: 26,
              fontWeight: FontWeight.w700,
              letterSpacing: -0.6,
              height: 1.25,
              color: ink),
          titleLarge: const TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w700,
              letterSpacing: -0.35,
              height: 1.3,
              color: ink),
          titleMedium: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              height: 1.4,
              color: ink),
          bodyLarge: const TextStyle(fontSize: 15, height: 1.55, color: ink),
          bodyMedium: const TextStyle(fontSize: 14, height: 1.5, color: ink),
          bodySmall: const TextStyle(fontSize: 12, height: 1.45, color: muted),
          labelLarge: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              height: 1.3,
              color: ink),
        );
    final shape =
        RoundedRectangleBorder(borderRadius: BorderRadius.circular(12));
    return base.copyWith(
      scaffoldBackgroundColor: bg,
      textTheme: text,
      appBarTheme: AppBarTheme(
        backgroundColor: Colors.white,
        foregroundColor: ink,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        toolbarHeight: 72,
        titleTextStyle: text.titleLarge,
        shape: const Border(bottom: BorderSide(color: border)),
      ),
      cardTheme: CardTheme(
        color: Colors.white,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: shape.copyWith(side: const BorderSide(color: border)),
      ),
      filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(
              backgroundColor: primaryDark,
              foregroundColor: Colors.white,
              minimumSize: const Size(48, 48),
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
              textStyle: text.labelLarge,
              shape: shape)),
      elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
              backgroundColor: primaryDark,
              foregroundColor: Colors.white,
              minimumSize: const Size(48, 48),
              elevation: 0,
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
              textStyle: text.labelLarge,
              shape: shape)),
      outlinedButtonTheme: OutlinedButtonThemeData(
          style: OutlinedButton.styleFrom(
              foregroundColor: ink,
              minimumSize: const Size(48, 48),
              side: const BorderSide(color: border),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              textStyle: text.labelLarge,
              shape: shape)),
      textButtonTheme: TextButtonThemeData(
          style: TextButton.styleFrom(
              foregroundColor: primaryDark,
              minimumSize: const Size(48, 48),
              textStyle: text.labelLarge,
              shape: shape)),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        hintStyle: text.bodyMedium?.copyWith(color: muted),
        labelStyle: text.bodyMedium?.copyWith(color: muted),
        border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: border)),
        enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: border)),
        focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: primaryDark, width: 1.5)),
      ),
      dividerTheme:
          const DividerThemeData(color: border, thickness: 1, space: 1),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
          color: primaryDark, linearTrackColor: soft),
      drawerTheme: const DrawerThemeData(
          backgroundColor: Colors.white, surfaceTintColor: Colors.transparent),
      dialogTheme: DialogTheme(
          backgroundColor: Colors.white,
          surfaceTintColor: Colors.transparent,
          shape: shape),
      bottomSheetTheme: const BottomSheetThemeData(
          backgroundColor: Colors.white,
          surfaceTintColor: Colors.transparent,
          showDragHandle: true),
      snackBarTheme: SnackBarThemeData(
          backgroundColor: navy,
          behavior: SnackBarBehavior.floating,
          shape: shape),
      navigationBarTheme: const NavigationBarThemeData(
          backgroundColor: Colors.white,
          indicatorColor: soft,
          surfaceTintColor: Colors.transparent),
    );
  }
}
