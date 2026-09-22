import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/constants/app_constants.dart';
import '../../core/routing/route_names.dart';
import '../../core/state/app_state.dart';
import '../../core/theme/app_colors.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  @override
  void initState() {
    super.initState();
    Future<void>.delayed(const Duration(milliseconds: 900), _navigate);
  }

  void _navigate() {
    if (!mounted) return;
    final setupCompleted = context.read<AppState>().setupCompleted;
    context.go(
      setupCompleted ? RouteNames.home : RouteNames.initialSetup,
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: isDark
                ? const [AppColors.darkBackground, AppColors.slate800]
                : const [AppColors.lightBackground, Color(0xFFDCEEEA)],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Container(
                  width: 88,
                  height: 88,
                  decoration: BoxDecoration(
                    color: AppColors.teal.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Icon(
                    Icons.shield_outlined,
                    size: 48,
                    color: isDark ? AppColors.tealMuted : AppColors.teal,
                  ),
                ),
                const SizedBox(height: 24),
                Text(
                  AppConstants.appName,
                  style: theme.textTheme.displayMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  AppConstants.appTagline,
                  style: theme.textTheme.bodyLarge?.copyWith(
                    color: theme.colorScheme.onSurface.withValues(alpha: 0.65),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
