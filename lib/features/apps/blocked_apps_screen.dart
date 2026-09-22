import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/state/app_state.dart';
import '../../shared/widgets/app_icon_avatar.dart';
import '../../shared/widgets/empty_state.dart';
import '../../shared/widgets/unlock_password_dialog.dart';

class BlockedAppsScreen extends StatelessWidget {
  const BlockedAppsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    final blocked = state.blockedApps.where((a) => a.enabled).toList();
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Blocked Apps')),
      body: blocked.isEmpty
          ? const EmptyState(
              icon: Icons.block,
              title: 'No blocked apps',
              message: 'Block an installed app from the Installed Apps screen.',
            )
          : ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: blocked.length,
              itemBuilder: (context, index) {
                final app = blocked[index];
                return Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Material(
                    color: theme.cardTheme.color ?? theme.colorScheme.surface,
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
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  app.appName,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: theme.textTheme.titleMedium,
                                ),
                                Text(
                                  app.packageName,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: theme.textTheme.bodySmall?.copyWith(
                                    color: theme.colorScheme.onSurface
                                        .withValues(alpha: 0.6),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          TextButton(
                            onPressed: () => _allowApp(context, state, app.appName, app.packageName),
                            child: const Text('ALLOW'),
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              },
            ),
    );
  }

  Future<void> _allowApp(
    BuildContext context,
    AppState state,
    String appName,
    String packageName,
  ) async {
    final unlocked = await confirmUnlockPassword(
      context,
      title: 'Allow app',
      message: 'Enter the password to allow $appName.',
    );
    if (!unlocked || !context.mounted) return;
    await state.unblockApp(packageName);
  }
}
