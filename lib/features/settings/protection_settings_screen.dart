import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/routing/route_names.dart';
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
                    'Website blocking uses a local VPN that only filters DNS — '
                    'your traffic never leaves the phone through Haven. '
                    'App blocking and the uninstall lock use Usage access and '
                    'Display over other apps.',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurface.withValues(alpha: 0.65),
                    ),
                  ),
                  if (state.privateDnsBypass) ...[
                    const SizedBox(height: 12),
                    Text(
                      'Private DNS is set to a custom provider, so websites skip '
                      'Haven’s filter. Set Settings › Network › Private DNS to '
                      '"Off" or "Automatic".',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.error,
                      ),
                    ),
                  ],
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
              'Blocks listed websites in every browser and app using a local VPN.',
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
              'Android refuses to uninstall Haven while it is a Device Admin. '
              'Uninstall and deactivate screens ask for your password. '
              'To uninstall, turn this off here first.',
            ),
            value: state.uninstallProtectionEnabled,
            onChanged: (value) => _onUninstallChanged(context, state, value),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.vpn_lock_outlined),
            title: const Text('Keep protection always on'),
            subtitle: const Text(
              'In VPN settings, tap the gear next to Haven and turn on '
              '"Always-on VPN". Leave "Block connections without VPN" OFF, '
              'or the internet will stop working.',
            ),
            trailing: const Icon(Icons.open_in_new),
            onTap: state.openVpnSettings,
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.alt_route_outlined),
            title: const Text('Apps that say "No internet"'),
            subtitle: Text(
              state.vpnExcludedPackages.isEmpty
                  ? 'Some apps refuse to work while any VPN is on. '
                      'Let them bypass Haven’s VPN.'
                  : '${state.vpnExcludedPackages.length} app(s) bypass the VPN.',
            ),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push(RouteNames.vpnExceptions),
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
    }
    await state.setProtectionEnabled(value);
    if (value && !state.vpnEnabled && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Allow the VPN connection so Haven can block websites.'),
        ),
      );
    }
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
