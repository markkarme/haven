import 'package:flutter/services.dart';

import '../../core/constants/app_constants.dart';

/// Status snapshot returned by native protection APIs.
class ProtectionStatus {
  const ProtectionStatus({
    required this.isActive,
    required this.vpnEnabled,
    required this.deviceAdminEnabled,
    this.vpnRunning = false,
    this.privateDnsBypass = false,
    this.usageAccessEnabled = false,
    this.overlayEnabled = false,
    this.notificationEnabled = false,
  });

  /// Usage Access: lets Haven see which app is in front (app blocking, uninstall gate).
  final bool usageAccessEnabled;

  /// "Display over other apps": lets Haven show block screens from the background.
  final bool overlayEnabled;

  final bool isActive;

  /// VPN consent granted.
  final bool vpnEnabled;

  /// Website filter tunnel is up right now.
  final bool vpnRunning;

  /// Strict Private DNS is set, so lookups skip the website filter.
  final bool privateDnsBypass;
  final bool deviceAdminEnabled;
  final bool notificationEnabled;

  factory ProtectionStatus.inactive() => const ProtectionStatus(
        isActive: false,
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

  /// Packages that bypass Haven's VPN (for apps that refuse to run behind a VPN).
  Future<List<String>> getVpnExcludedApps() async {
    try {
      final result =
          await _channel.invokeMethod<List<dynamic>>('getVpnExcludedApps');
      return result?.map((e) => e.toString()).toList() ?? const [];
    } on MissingPluginException {
      return const [];
    } on PlatformException {
      return const [];
    }
  }

  Future<void> setVpnExcludedApps(List<String> packages) async {
    try {
      await _channel.invokeMethod<void>('setVpnExcludedApps', {
        'packages': packages,
      });
    } on MissingPluginException {
      // Native handler unavailable.
    } on PlatformException {
      // Never crash the UI on sync failure.
    }
  }

  Future<void> requestUsageAccess() => _invokeVoid('requestUsageAccess');

  Future<void> requestOverlay() => _invokeVoid('requestOverlay');

  /// Opens Android VPN settings (for Always-on VPN).
  Future<void> openVpnSettings() => _invokeVoid('openVpnSettings');

  Future<bool> getDeviceAdminStatus() => _invokeBool('getDeviceAdminStatus');

  Future<void> requestDeviceAdmin() => _invokeVoid('requestDeviceAdmin');

  /// Haven removes its own Device Admin so it can be uninstalled normally.
  Future<void> removeDeviceAdmin() => _invokeVoid('removeDeviceAdmin');

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
        vpnEnabled: result['vpnEnabled'] as bool? ?? false,
        vpnRunning: result['vpnRunning'] as bool? ?? false,
        privateDnsBypass: result['privateDnsBypass'] as bool? ?? false,
        usageAccessEnabled: result['usageAccessEnabled'] as bool? ?? false,
        overlayEnabled: result['overlayEnabled'] as bool? ?? false,
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
