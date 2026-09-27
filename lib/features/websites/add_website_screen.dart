import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/state/app_state.dart';
import '../../core/utils/domain_utils.dart';

class AddWebsiteScreen extends StatefulWidget {
  const AddWebsiteScreen({super.key});

  @override
  State<AddWebsiteScreen> createState() => _AddWebsiteScreenState();
}

class _AddWebsiteScreenState extends State<AddWebsiteScreen> {
  final _controller = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _saving = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();

    return Scaffold(
      appBar: AppBar(title: const Text('Add Website')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Website', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 8),
                TextFormField(
                  controller: _controller,
                  autofocus: true,
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  decoration: const InputDecoration(
                    hintText: 'example.com or *.example.com',
                    helperText:
                        'Blocked in every browser and app through Haven’s local VPN. '
                        'Subdomains are blocked too — other sites are untouched.\n'
                        'Also block the site’s app (e.g. X/Twitter) from Blocked Apps if needed.',
                    helperMaxLines: 6,
                  ),
                  validator: DomainUtils.validate,
                ),
                const SizedBox(height: 12),
                Text(
                  state.isProtectionActive
                      ? 'Website filter: running'
                      : 'Allow the VPN and turn on Protection so blocked sites are enforced.',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: state.isProtectionActive
                            ? Theme.of(context).colorScheme.primary
                            : Theme.of(context).colorScheme.error,
                      ),
                ),
                const Spacer(),
                FilledButton(
                  onPressed: _saving ? null : _submit,
                  child: Text(_saving ? 'Saving…' : 'Block Website'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    final state = context.read<AppState>();
    final message = await state.addWebsite(_controller.text);
    if (!mounted) return;
    setState(() => _saving = false);

    if (message != null && message.startsWith('This domain is already')) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            state.isProtectionActive
                ? message
                : '$message Allow the VPN so Haven can enforce it.',
          ),
        ),
      );
      context.pop();
      return;
    }
    if (message != null) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
      return;
    }

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          state.isProtectionActive
              ? 'Saved. The site will no longer load (a browser may need a moment '
                  'to forget cached pages).'
              : 'Saved. Allow the VPN so website blocking can run.',
        ),
      ),
    );
    context.pop();
  }
}
