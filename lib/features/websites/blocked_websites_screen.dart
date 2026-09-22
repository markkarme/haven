import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/routing/route_names.dart';
import '../../core/state/app_state.dart';
import '../../core/utils/domain_utils.dart';
import '../../data/models/models.dart';
import '../../shared/widgets/empty_state.dart';
import '../../shared/widgets/unlock_password_dialog.dart';

class BlockedWebsitesScreen extends StatefulWidget {
  const BlockedWebsitesScreen({super.key});

  @override
  State<BlockedWebsitesScreen> createState() => _BlockedWebsitesScreenState();
}

class _BlockedWebsitesScreenState extends State<BlockedWebsitesScreen> {
  final _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    final query = _searchController.text.trim().toLowerCase();
    // Adult seed domains are never shown — only user-added websites.
    final filtered = state.websites
        .where((rule) => rule.domain.toLowerCase().contains(query))
        .toList();

    return Scaffold(
      appBar: AppBar(title: const Text('Blocked Websites')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push(RouteNames.addWebsite),
        icon: const Icon(Icons.add),
        label: const Text('Add Website'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: TextField(
              controller: _searchController,
              onChanged: (_) => setState(() {}),
              decoration: const InputDecoration(
                hintText: 'Search blocked websites',
                prefixIcon: Icon(Icons.search),
              ),
            ),
          ),
          Expanded(
            child: filtered.isEmpty
                ? const EmptyState(
                    icon: Icons.language,
                    title: 'No websites found',
                    message: 'Add a domain to your personal blocklist.',
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 88),
                    itemCount: filtered.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 8),
                    itemBuilder: (context, index) {
                      final rule = filtered[index];
                      return Card(
                        child: ListTile(
                          title: Text(rule.domain),
                          subtitle: Text(rule.enabled ? 'ON' : 'OFF'),
                          trailing: Switch(
                            value: rule.enabled,
                            onChanged: (value) =>
                                _onToggleEnabled(context, state, rule, value),
                          ),
                          onTap: () => _editWebsite(context, state, rule),
                          onLongPress: () =>
                              _confirmDelete(context, state, rule),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Future<void> _onToggleEnabled(
    BuildContext context,
    AppState state,
    BlockedWebsite rule,
    bool enabled,
  ) async {
    if (enabled) {
      await state.setWebsiteEnabled(rule, true);
      return;
    }

    final unlocked = await confirmUnlockPassword(
      context,
      title: 'Allow website',
      message:
          'Enter the password to turn off blocking for ${rule.domain}.',
    );
    if (!unlocked || !context.mounted) return;
    await state.setWebsiteEnabled(rule, false);
  }

  Future<void> _editWebsite(
    BuildContext context,
    AppState state,
    BlockedWebsite rule,
  ) async {
    final unlocked = await confirmUnlockPassword(
      context,
      title: 'Edit website',
      message: 'Enter the password to edit ${rule.domain}.',
    );
    if (!unlocked || !context.mounted) return;

    final controller = TextEditingController(text: rule.domain);
    final formKey = GlobalKey<FormState>();
    final saved = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Edit website'),
        content: Form(
          key: formKey,
          child: TextFormField(
            controller: controller,
            decoration: const InputDecoration(hintText: 'example.com'),
            validator: DomainUtils.validate,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              if (!formKey.currentState!.validate()) return;
              Navigator.pop(context, DomainUtils.normalize(controller.text));
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
    if (saved == null || saved == rule.domain) return;
    await state.updateWebsite(rule.copyWith(domain: saved));
  }

  Future<void> _confirmDelete(
    BuildContext context,
    AppState state,
    BlockedWebsite rule,
  ) async {
    final unlocked = await confirmUnlockPassword(
      context,
      title: 'Remove website',
      message:
          'Enter the password to remove ${rule.domain} from the blocklist.',
    );
    if (!unlocked || !context.mounted) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Remove website?'),
        content: Text('Remove ${rule.domain} from the blocklist?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await state.deleteWebsite(rule);
    }
  }
}
