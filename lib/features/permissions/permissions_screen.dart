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
                'Grant VPN, Usage access, Display over other apps, and Device Admin '
                'for full protection.',
          ),
          const SizedBox(height: 20),
          PermissionTile(
            title: 'VPN (website filter)',
            reason:
                'Required. Haven runs a local VPN on this phone that only checks '
                'website names (DNS) against your blocklist. Your traffic is not '
                'sent to any Haven server.',
            enabled: state.vpnEnabled,
            actionLabel: 'Allow VPN',
            onEnable: () => state.requestVpn(),
          ),
          PermissionTile(
            title: 'Usage access',
            reason:
                'Required for app blocking and uninstall protection. Lets Haven '
                'see which app is open — it never reads what is on the screen.',
            enabled: state.usageAccessEnabled,
            actionLabel: 'Allow usage access',
            onEnable: () => state.requestUsageAccess(),
          ),
          PermissionTile(
            title: 'Display over other apps',
            reason:
                'Required so Haven can show the block screen and the password '
                'lock when a blocked app or the uninstall screen opens.',
            enabled: state.overlayEnabled,
            actionLabel: 'Allow display over apps',
            onEnable: () => state.requestOverlay(),
          ),
          PermissionTile(
            title: 'Device Administrator',
            reason:
                'Required for uninstall protection. Android will not uninstall '
                'Haven while it is a Device Admin, and turning it off asks for '
                'your Haven password.',
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
