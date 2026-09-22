import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/routing/route_names.dart';
import '../../core/state/app_state.dart';
import '../../services/native/blocker_native_service.dart';
import '../../shared/widgets/app_icon_avatar.dart';
import '../../shared/widgets/empty_state.dart';
import '../../shared/widgets/unlock_password_dialog.dart';

class InstalledAppsScreen extends StatefulWidget {
  const InstalledAppsScreen({super.key});

  @override
  State<InstalledAppsScreen> createState() => _InstalledAppsScreenState();
}

class _InstalledAppsScreenState extends State<InstalledAppsScreen> {
  final _searchController = TextEditingController();
  List<InstalledAppInfo> _apps = const [];
  String _query = '';
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    SchedulerBinding.instance.addPostFrameCallback((_) {
      Future<void>.delayed(const Duration(milliseconds: 50), _load);
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    if (!mounted) return;
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final state = context.read<AppState>();
      final apps = await state.native.getInstalledApps();
      final blocked = {
        for (final a in state.blockedApps)
          if (a.enabled) a.packageName,
      };

      if (!mounted) return;
      setState(() {
        _apps = [
          for (final app in apps)
            InstalledAppInfo(
              packageName: app.packageName,
              appName: app.appName,
              isBlocked: blocked.contains(app.packageName),
            ),
        ];
        _loading = false;
        if (_apps.isEmpty) {
          _error =
              'No launchable apps were returned. Try Refresh, or check app permissions.';
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = 'Failed to load apps: $e';
        _apps = const [];
      });
    }
  }

  List<InstalledAppInfo> get _filtered {
    final q = _query.trim().toLowerCase();
    if (q.isEmpty) return _apps;
    return [
      for (final app in _apps)
        if (app.appName.toLowerCase().contains(q) ||
            app.packageName.toLowerCase().contains(q))
          app,
    ];
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final filtered = _filtered;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Installed Apps'),
        actions: [
          IconButton(
            onPressed: _loading ? null : _load,
            icon: const Icon(Icons.refresh),
          ),
          TextButton(
            onPressed: () => context.push(RouteNames.blockedApps),
            child: const Text('Blocked'),
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: TextField(
              controller: _searchController,
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
                : filtered.isEmpty
                    ? EmptyState(
                        icon: Icons.apps,
                        title: 'No apps found',
                        message: _error ?? 'Try a different search term.',
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                        itemCount: filtered.length,
                        itemBuilder: (context, index) {
                          final app = filtered[index];
                          return Padding(
                            key: ValueKey(app.packageName),
                            padding: const EdgeInsets.only(bottom: 8),
                            child: Material(
                              color: theme.cardTheme.color ??
                                  theme.colorScheme.surface,
                              borderRadius: BorderRadius.circular(16),
                              child: Padding(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 12,
                                  vertical: 10,
                                ),
                                child: Row(
                                  children: [
                                    AppIconAvatar(
                                      packageName: app.packageName,
                                      appName: app.appName,
                                    ),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            _displayName(app),
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                            style: theme.textTheme.titleMedium,
                                          ),
                                          if (_displayName(app) != app.packageName)
                                            Text(
                                              app.packageName,
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                              style: theme.textTheme.bodySmall
                                                  ?.copyWith(
                                                color: theme.colorScheme.onSurface
                                                    .withValues(alpha: 0.6),
                                              ),
                                            ),
                                        ],
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    TextButton(
                                      onPressed: () => _toggle(app),
                                      child: Text(
                                        app.isBlocked ? 'ALLOW' : 'BLOCK',
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          );
                        },
                      ),
          ),
        ],
      ),
    );
  }

  Future<void> _toggle(InstalledAppInfo app) async {
    final state = context.read<AppState>();
    if (app.isBlocked) {
      final unlocked = await confirmUnlockPassword(
        context,
        title: 'Allow app',
        message: 'Enter the password to allow ${_displayName(app)}.',
      );
      if (!unlocked || !mounted) return;
      await state.unblockApp(app.packageName);
    } else {
      await state.blockApp(
        packageName: app.packageName,
        appName: _displayName(app),
      );
    }
    if (!mounted) return;
    setState(() {
      _apps = [
        for (final item in _apps)
          if (item.packageName == app.packageName)
            InstalledAppInfo(
              packageName: item.packageName,
              appName: item.appName,
              isBlocked: !app.isBlocked,
            )
          else
            item,
      ];
    });
  }

  String _displayName(InstalledAppInfo app) {
    final name = app.appName.trim();
    if (name.isEmpty) return app.packageName;
    return name;
  }
}
