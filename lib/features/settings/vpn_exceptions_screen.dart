import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:provider/provider.dart';

import '../../core/state/app_state.dart';
import '../../services/native/blocker_native_service.dart';
import '../../shared/widgets/app_icon_avatar.dart';
import '../../shared/widgets/empty_state.dart';
import '../../shared/widgets/unlock_password_dialog.dart';

/// Apps that bypass Haven's VPN because they refuse to work behind any VPN.
class VpnExceptionsScreen extends StatefulWidget {
  const VpnExceptionsScreen({super.key});

  @override
  State<VpnExceptionsScreen> createState() => _VpnExceptionsScreenState();
}

class _VpnExceptionsScreenState extends State<VpnExceptionsScreen> {
  List<InstalledAppInfo> _apps = const [];
  String _query = '';
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    SchedulerBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    final apps = await context.read<AppState>().native.getInstalledApps();
    if (!mounted) return;
    setState(() {
      _apps = apps;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    final theme = Theme.of(context);
    final excluded = state.vpnExcludedPackages;

    final q = _query.trim().toLowerCase();
    final visible = [
      for (final app in _apps)
        if (q.isEmpty ||
            app.appName.toLowerCase().contains(q) ||
            app.packageName.toLowerCase().contains(q))
          app,
    ]..sort((a, b) {
        final ae = excluded.contains(a.packageName);
        final be = excluded.contains(b.packageName);
        if (ae != be) return ae ? -1 : 1;
        return a.appName.toLowerCase().compareTo(b.appName.toLowerCase());
      });

    return Scaffold(
      appBar: AppBar(title: const Text('VPN exceptions')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Text(
              'Some apps (banks, coupon and payment apps) show "No internet" '
              'whenever a VPN is on. Excluded apps skip Haven’s VPN, so they '
              'work normally — but websites opened inside them are not filtered. '
              'Never exclude a browser.',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: TextField(
              onChanged: (value) => setState(() => _query = value),
              decoration: const InputDecoration(
                hintText: 'Search installed applications',
                prefixIcon: Icon(Icons.search),
              ),
            ),
          ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : visible.isEmpty
                    ? const EmptyState(
                        icon: Icons.apps,
                        title: 'No apps found',
                        message: 'Try a different search term.',
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                        itemCount: visible.length,
                        itemBuilder: (context, index) {
                          final app = visible[index];
                          final isExcluded = excluded.contains(app.packageName);
                          return SwitchListTile(
                            key: ValueKey(app.packageName),
                            contentPadding: EdgeInsets.zero,
                            secondary: AppIconAvatar(
                              packageName: app.packageName,
                              appName: app.appName,
                            ),
                            title: Text(
                              app.appName,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            subtitle: Text(
                              isExcluded ? 'Bypasses VPN' : 'Filtered',
                            ),
                            value: isExcluded,
                            onChanged: (value) => _toggle(state, app, value),
                          );
                        },
                      ),
          ),
        ],
      ),
    );
  }

  Future<void> _toggle(
    AppState state,
    InstalledAppInfo app,
    bool exclude,
  ) async {
    if (exclude) {
      final ok = await confirmUnlockPassword(
        context,
        title: 'Exclude from VPN',
        message:
            'Enter the password to let ${app.appName} bypass website blocking.',
      );
      if (!ok || !mounted) return;
    }
    await state.setVpnExcluded(app.packageName, exclude);
  }
}
