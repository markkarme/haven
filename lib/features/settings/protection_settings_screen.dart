import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/state/app_state.dart';
import '../../shared/widgets/section_header.dart';
import '../../shared/widgets/status_badge.dart';
import '../../shared/widgets/unlock_password_dialog.dart';

class ProtectionSettingsScreen extends StatelessWidget {
  const ProtectionSettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Protection Settings')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Protection',
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
                    ),
                  ),
                  const SizedBox(height: 12),
                  StatusBadge(
                    label: state.protectionLabel,
                    active: state.isProtectionActive,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Website and app blocking use Accessibility. '
                    'Uninstall protection locks Haven App info and uses Device Admin when enabled.',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurface.withValues(alpha: 0.65),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          const SectionHeader(
            title: 'Controls',
            subtitle:
                'All settings stay on-device. No browsing history is uploaded. '
                'Turning a protection switch off requires your unlock password.',
          ),
          const SizedBox(height: 8),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Enable protection'),
            subtitle: const Text(
              'Uses Accessibility to block apps and listed websites in browsers.',
            ),
            value: state.protectionEnabled,
            onChanged: (value) => _onProtectionChanged(context, state, value),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Adult website protection'),
            subtitle: const Text('Apply the local adult-domain blocklist.'),
            value: state.adultProtectionEnabled,
            onChanged: (value) => _onAdultChanged(context, state, value),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Uninstall protection'),
            subtitle: const Text(
              'Locks Haven App info in Settings / App management. Enable Device Admin when prompted.',
            ),
            value: state.uninstallProtectionEnabled,
            onChanged: (value) => _onUninstallChanged(context, state, value),
          ),
          const Divider(height: 32),
          const SectionHeader(title: 'Appearance'),
          const SizedBox(height: 8),
          SegmentedButton<ThemeMode>(
            segments: const [
              ButtonSegment(value: ThemeMode.system, label: Text('System')),
              ButtonSegment(value: ThemeMode.light, label: Text('Light')),
              ButtonSegment(value: ThemeMode.dark, label: Text('Dark')),
            ],
            selected: {state.themeMode},
            onSelectionChanged: (modes) {
              state.setThemeMode(modes.first);
            },
          ),
        ],
      ),
    );
  }

  Future<bool> _confirmTurnOff(
    BuildContext context, {
    required String title,
    required String message,
  }) {
    return confirmUnlockPassword(
      context,
      title: title,
      message: message,
    );
  }

  Future<void> _onProtectionChanged(
    BuildContext context,
    AppState state,
    bool value,
  ) async {
    if (!value) {
      final ok = await _confirmTurnOff(
        context,
        title: 'Disable protection',
        message: 'Enter the password to turn off protection.',
      );
      if (!ok || !context.mounted) return;
    } else if (!state.accessibilityEnabled) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Enable Accessibility permission first.')),
      );
    }
    await state.setProtectionEnabled(value);
  }

  Future<void> _onAdultChanged(
    BuildContext context,
    AppState state,
    bool value,
  ) async {
    if (!value) {
      final ok = await _confirmTurnOff(
        context,
        title: 'Disable adult protection',
        message: 'Enter the password to turn off adult website protection.',
      );
      if (!ok || !context.mounted) return;
    }
    await state.setAdultProtectionEnabled(value);
  }

  Future<void> _onUninstallChanged(
    BuildContext context,
    AppState state,
    bool value,
  ) async {
    if (!value) {
      final ok = await _confirmTurnOff(
        context,
        title: 'Disable uninstall protection',
        message: 'Enter the password to turn off uninstall protection.',
      );
      if (!ok || !context.mounted) return;
    }
    await state.setUninstallProtectionEnabled(value);
  }
}
