import 'package:flutter/material.dart';

import '../../core/constants/app_constants.dart';

/// Asks for the unlock password before allowing/removing a blocked website.
Future<bool> confirmUnlockPassword(
  BuildContext context, {
  required String title,
  String message =
      'Enter the password to allow or change this blocked website.',
}) async {
  final ok = await showDialog<bool>(
    context: context,
    barrierDismissible: false,
    builder: (context) => _UnlockPasswordDialog(
      title: title,
      message: message,
    ),
  );
  return ok == true;
}

class _UnlockPasswordDialog extends StatefulWidget {
  const _UnlockPasswordDialog({
    required this.title,
    required this.message,
  });

  final String title;
  final String message;

  @override
  State<_UnlockPasswordDialog> createState() => _UnlockPasswordDialogState();
}

class _UnlockPasswordDialogState extends State<_UnlockPasswordDialog> {
  final _controller = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  var _obscure = true;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    Navigator.of(context).pop(true);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.message),
            const SizedBox(height: 16),
            TextFormField(
              controller: _controller,
              obscureText: _obscure,
              autofocus: true,
              textInputAction: TextInputAction.done,
              onFieldSubmitted: (_) => _submit(),
              decoration: InputDecoration(
                labelText: 'Password',
                suffixIcon: IconButton(
                  onPressed: () => setState(() => _obscure = !_obscure),
                  icon: Icon(
                    _obscure
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                  ),
                ),
              ),
              validator: (value) {
                if (value == null || value.isEmpty) {
                  return 'Enter the password';
                }
                if (value != AppConstants.unlockPassword) {
                  return 'Incorrect password';
                }
                return null;
              },
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: _submit,
          child: const Text('Confirm'),
        ),
      ],
    );
  }
}
