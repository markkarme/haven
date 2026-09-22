import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../../services/native/blocker_native_service.dart';

/// In-memory cache so scrolling does not re-fetch the same icon.
final Map<String, Uint8List?> _iconCache = {};

class AppIconAvatar extends StatefulWidget {
  const AppIconAvatar({
    super.key,
    required this.packageName,
    required this.appName,
    this.size = 40,
  });

  final String packageName;
  final String appName;
  final double size;

  @override
  State<AppIconAvatar> createState() => _AppIconAvatarState();
}

class _AppIconAvatarState extends State<AppIconAvatar> {
  Uint8List? _bytes;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _resolve();
  }

  @override
  void didUpdateWidget(covariant AppIconAvatar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.packageName != widget.packageName) {
      _resolve();
    }
  }

  Future<void> _resolve() async {
    final cached = _iconCache[widget.packageName];
    if (cached != null || _iconCache.containsKey(widget.packageName)) {
      if (!mounted) return;
      setState(() {
        _bytes = cached;
        _loading = false;
      });
      return;
    }

    setState(() => _loading = true);
    final b64 = await BlockerNativeService().getAppIcon(widget.packageName);
    Uint8List? bytes;
    if (b64 != null && b64.isNotEmpty) {
      try {
        bytes = base64Decode(b64);
      } catch (_) {
        bytes = null;
      }
    }
    _iconCache[widget.packageName] = bytes;
    if (!mounted) return;
    setState(() {
      _bytes = bytes;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final letter = widget.appName.trim().isNotEmpty
        ? widget.appName.trim().characters.first.toUpperCase()
        : '?';

    if (_bytes != null) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(10),
        child: Image.memory(
          _bytes!,
          width: widget.size,
          height: widget.size,
          fit: BoxFit.cover,
          gaplessPlayback: true,
        ),
      );
    }

    return CircleAvatar(
      radius: widget.size / 2,
      child: _loading
          ? SizedBox(
              width: widget.size * 0.35,
              height: widget.size * 0.35,
              child: const CircularProgressIndicator(strokeWidth: 2),
            )
          : Text(letter),
    );
  }
}
