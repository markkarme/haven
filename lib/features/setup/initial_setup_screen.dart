import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/constants/app_constants.dart';
import '../../core/routing/route_names.dart';
import '../../core/state/app_state.dart';
import '../../shared/widgets/section_header.dart';

class InitialSetupScreen extends StatelessWidget {
  const InitialSetupScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 32, 24, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(AppConstants.appName, style: theme.textTheme.headlineLarge),
              const SizedBox(height: 8),
              Text(
                'Private, local blocking for websites and apps. '
                'Nothing leaves your device — no browsing history is stored or uploaded.',
                style: theme.textTheme.bodyLarge?.copyWith(
                  color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
                ),
              ),
              const SizedBox(height: 32),
              const SectionHeader(
                title: 'What Haven does',
                subtitle:
                    'Core protection runs as Android services, even when this UI is closed.',
              ),
              const SizedBox(height: 16),
              const _SetupPoint(
                icon: Icons.language,
                title: 'Website blocking',
                body:
                    'A local VPN filters website names on your phone — works in every '
                    'browser, and banking apps keep working.',
              ),
              const _SetupPoint(
                icon: Icons.apps,
                title: 'App blocking',
                body:
                    'Detect blocked apps with Usage access and show a full-screen block.',
              ),
              const _SetupPoint(
                icon: Icons.lock_outline,
                title: 'Uninstall protection',
                body:
                    'Optional Device Admin makes removal less accidental (can still be disabled).',
              ),
              const Spacer(),
              FilledButton(
                onPressed: () async {
                  await context.read<AppState>().completeSetup();
                  if (context.mounted) context.go(RouteNames.permissions);
                },
                child: const Text('Continue to permissions'),
              ),
              const SizedBox(height: 12),
              OutlinedButton(
                onPressed: () async {
                  await context.read<AppState>().completeSetup();
                  if (context.mounted) context.go(RouteNames.home);
                },
                child: const Text('Skip for now'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SetupPoint extends StatelessWidget {
  const _SetupPoint({
    required this.icon,
    required this.title,
    required this.body,
  });

  final IconData icon;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: theme.colorScheme.primary),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: theme.textTheme.titleMedium),
                const SizedBox(height: 2),
                Text(
                  body,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurface.withValues(alpha: 0.65),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
