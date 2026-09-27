import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/constants/app_constants.dart';
import '../../core/routing/route_names.dart';
import '../../core/state/app_state.dart';
import '../../shared/widgets/section_header.dart';
import '../../shared/widgets/status_badge.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text(AppConstants.appName),
        actions: [
          IconButton(
            tooltip: 'Permissions',
            onPressed: () => context.push(RouteNames.permissions),
            icon: const Icon(Icons.verified_user_outlined),
          ),
          IconButton(
            tooltip: 'Settings',
            onPressed: () => context.push(RouteNames.protectionSettings),
            icon: const Icon(Icons.settings_outlined),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          await state.refreshLists();
          await state.refreshPermissions();
        },
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Protection Status',
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
                      ),
                    ),
                    const SizedBox(height: 12),
                    StatusBadge(
                      label: state.protectionLabel,
                      active: state.isProtectionActive,
                    ),
                    const SizedBox(height: 20),
                    Row(
                      children: [
                        Expanded(
                          child: _StatTile(
                            label: 'Blocked Websites',
                            value: '${state.blockedWebsiteCount}',
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: _StatTile(
                            label: 'Blocked Apps',
                            value: '${state.blockedAppCount}',
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),
            const SectionHeader(
              title: 'Quick Actions',
              subtitle: 'Manage blocklists and protection.',
            ),
            const SizedBox(height: 12),
            _ActionButton(
              icon: Icons.language,
              label: 'Block Website',
              onTap: () => context.push(RouteNames.addWebsite),
            ),
            _ActionButton(
              icon: Icons.apps,
              label: 'Block App',
              onTap: () => context.push(RouteNames.installedApps),
            ),
            _ActionButton(
              icon: Icons.shield_outlined,
              label: 'Protection Settings',
              onTap: () => context.push(RouteNames.protectionSettings),
            ),
            const SizedBox(height: 24),
            const SectionHeader(
              title: 'Required permissions',
              subtitle: 'Native blocking needs these Android capabilities.',
            ),
            const SizedBox(height: 12),
            _PermissionStatusRow(
              label: 'VPN website filter',
              enabled: state.vpnRunning,
            ),
            _PermissionStatusRow(
              label: 'App blocking (usage access + overlay)',
              enabled: state.appMonitorReady,
            ),
            _PermissionStatusRow(
              label: 'Device Admin',
              enabled: state.deviceAdminEnabled,
            ),
            _PermissionStatusRow(
              label: 'Notifications',
              enabled: state.notificationEnabled,
            ),
            const SizedBox(height: 8),
            TextButton(
              onPressed: () => context.push(RouteNames.permissions),
              child: const Text('Open permission setup'),
            ),
            TextButton(
              onPressed: () => context.push(RouteNames.blockedWebsites),
              child: const Text('View blocked websites'),
            ),
            TextButton(
              onPressed: () => context.push(RouteNames.blockedApps),
              child: const Text('View blocked apps'),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatTile extends StatelessWidget {
  const _StatTile({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: theme.colorScheme.primary.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            value,
            style: theme.textTheme.headlineSmall?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurface.withValues(alpha: 0.65),
            ),
          ),
        ],
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: ListTile(
        leading: Icon(icon),
        title: Text(label),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}

class _PermissionStatusRow extends StatelessWidget {
  const _PermissionStatusRow({
    required this.label,
    required this.enabled,
  });

  final String label;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      dense: true,
      title: Text(label),
      trailing: Text(
        enabled ? 'Enabled' : 'Missing',
        style: TextStyle(
          color: enabled
              ? Theme.of(context).colorScheme.primary
              : Theme.of(context).colorScheme.error,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}
