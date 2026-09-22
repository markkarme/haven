import 'package:flutter/services.dart';

import '../../core/constants/app_constants.dart';

/// Status snapshot returned by native protection APIs.
class ProtectionStatus {
  const ProtectionStatus({
    required this.isActive,
    required this.accessibilityEnabled,
    required this.vpnEnabled,
    required this.deviceAdminEnabled,
    this.notificationEnabled = false,
  });

  final bool isActive;
  final bool accessibilityEnabled;
  final bool vpnEnabled;
  final bool deviceAdminEnabled;
  final bool notificationEnabled;

  factory ProtectionStatus.inactive() => const ProtectionStatus(
        isActive: false,
        accessibilityEnabled: false,
        vpnEnabled: false,
        deviceAdminEnabled: false,
      );
}

/// Installed app info from the Android package manager.
class InstalledAppInfo {
  const InstalledAppInfo({
    required this.packageName,
    required this.appName,
    this.isBlocked = false,
    this.iconBase64,
  });

  final String packageName;
  final String appName;
  final bool isBlocked;
  final String? iconBase64;
}

/// Flutter ↔ Kotlin bridge for native blocking features.
class BlockerNativeService {
  BlockerNativeService({MethodChannel? channel})
      : _channel =
            channel ?? const MethodChannel(AppConstants.methodChannel);

  final MethodChannel _channel;

  Future<List<InstalledAppInfo>> getInstalledApps() async {
    try {
      final result =
          await _channel.invokeMethod<List<dynamic>>('getInstalledApps');
      if (result == null) return const [];
      final apps = <InstalledAppInfo>[];
      for (final item in result) {
        if (item is! Map) continue;
        final raw = Map<Object?, Object?>.from(item);
        final packageName = raw['packageName']?.toString() ?? '';
        final appName = raw['appName']?.toString() ?? packageName;
        if (packageName.isEmpty) continue;
        apps.add(
          InstalledAppInfo(
            packageName: packageName,
            appName: appName,
            isBlocked: raw['isBlocked'] == true,
          ),
        );
      }
      return apps;
    } on MissingPluginException {
      return const [];
    } on PlatformException {
      return const [];
    }
  }

  /// Lazily fetch a small JPEG icon for [packageName]. Returns null if unavailable.
  Future<String?> getAppIcon(String packageName) async {
    try {
      return await _channel.invokeMethod<String>('getAppIcon', {
        'packageName': packageName,
      });
    } on MissingPluginException {
      return null;
    } on PlatformException {
      return null;
    }
  }

  Future<bool> getAccessibilityStatus() =>
      _invokeBool('getAccessibilityStatus');

  Future<void> requestAccessibility() => _invokeVoid('requestAccessibility');

  Future<bool> getVpnStatus() => _invokeBool('getVpnStatus');

  /// Returns true if the VPN permission was granted (or already held).
  Future<bool> requestVpnPermission() async {
    try {
      final result = await _channel.invokeMethod<bool>('requestVpnPermission');
      return result ?? false;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }

  Future<bool> getDeviceAdminStatus() => _invokeBool('getDeviceAdminStatus');

  Future<void> requestDeviceAdmin() => _invokeVoid('requestDeviceAdmin');

  Future<bool> getNotificationStatus() =>
      _invokeBool('getNotificationStatus');

  Future<void> requestNotificationPermission() =>
      _invokeVoid('requestNotificationPermission');

  Future<ProtectionStatus> getProtectionStatus() async {
    try {
      final result = await _channel
          .invokeMethod<Map<dynamic, dynamic>>('getProtectionStatus');
      if (result == null) return ProtectionStatus.inactive();
      return ProtectionStatus(
        isActive: result['isActive'] as bool? ?? false,
        accessibilityEnabled: result['accessibilityEnabled'] as bool? ?? false,
        vpnEnabled: result['vpnEnabled'] as bool? ?? false,
        deviceAdminEnabled: result['deviceAdminEnabled'] as bool? ?? false,
        notificationEnabled: result['notificationEnabled'] as bool? ?? false,
      );
    } on MissingPluginException {
      return ProtectionStatus.inactive();
    } on PlatformException {
      return ProtectionStatus.inactive();
    }
  }

  Future<void> startProtection() => _invokeVoid('startProtection');

  Future<void> stopProtection() => _invokeVoid('stopProtection');

  /// Push local block rules to native SharedPreferences for offline services.
  Future<void> syncRules({
    required List<String> domains,
    required List<String> packages,
    required bool protectionEnabled,
    required bool adultProtectionEnabled,
    required bool uninstallProtectionEnabled,
  }) async {
    try {
      await _channel.invokeMethod<void>('syncRules', {
        'domains': domains,
        'packages': packages,
        'protectionEnabled': protectionEnabled,
        'adultProtectionEnabled': adultProtectionEnabled,
        'uninstallProtectionEnabled': uninstallProtectionEnabled,
      });
    } on MissingPluginException {
      // Running on non-Android or before plugin registration.
    } on PlatformException {
      // Never crash the UI on sync failure.
    }
  }

  Future<bool> _invokeBool(String method) async {
    try {
      final result = await _channel.invokeMethod<bool>(method);
      return result ?? false;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }

  Future<void> _invokeVoid(String method) async {
    try {
      await _channel.invokeMethod<void>(method);
    } on MissingPluginException {
      // Native handler unavailable.
    } on PlatformException {
      // Permission or OEM limitation — never crash the UI.
    }
  }
}
