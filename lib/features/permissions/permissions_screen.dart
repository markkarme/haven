import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/routing/route_names.dart';
import '../../core/state/app_state.dart';
import '../../shared/widgets/permission_tile.dart';
import '../../shared/widgets/section_header.dart';

class PermissionsScreen extends StatelessWidget {
  const PermissionsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();

    return Scaffold(
      appBar: AppBar(title: const Text('Permissions')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
        children: [
          const SectionHeader(
            title: 'Permission setup',
            subtitle:
                'Haven blocks apps and websites with Accessibility only — no VPN. '
                'Grant Accessibility so blocking works while the UI is closed.',
          ),
          const SizedBox(height: 20),
          PermissionTile(
            title: 'Accessibility',
            reason:
                'Required to detect blocked apps and blocked website addresses in browsers.',
            enabled: state.accessibilityEnabled,
            actionLabel: 'Enable Accessibility',
            onEnable: () => state.requestAccessibility(),
          ),
          PermissionTile(
            title: 'Device Administrator',
            reason:
                'Optional. Makes uninstalling harder while protection is on. '
                'You can always deactivate Device Admin in system settings.',
            enabled: state.deviceAdminEnabled,
            actionLabel: 'Enable Device Admin',
            onEnable: () => state.requestDeviceAdmin(),
          ),
          PermissionTile(
            title: 'Notifications',
            reason: 'Optional status notices.',
            enabled: state.notificationEnabled,
            actionLabel: 'Enable Notifications',
            onEnable: () => state.requestNotifications(),
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: () => context.go(RouteNames.home),
            child: const Text('Go to Home'),
          ),
        ],
      ),
    );
  }
}
