import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';


class PermissionTile extends StatelessWidget {
  const PermissionTile({
    super.key,
    required this.title,
    required this.reason,
    required this.enabled,
    required this.actionLabel,
    required this.onEnable,
  });

  final String title;
  final String reason;
  final bool enabled;
  final String actionLabel;
  final VoidCallback onEnable;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final statusColor = enabled ? AppColors.success : AppColors.warning;

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(title, style: theme.textTheme.titleMedium),
                ),
                Text(
                  enabled ? 'Enabled' : 'Disabled',
                  style: theme.textTheme.labelLarge?.copyWith(
                    color: statusColor,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              reason,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
              ),
            ),
            if (!enabled) ...[
              const SizedBox(height: 14),
              FilledButton(
                onPressed: onEnable,
                child: Text(actionLabel),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
