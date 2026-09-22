import 'package:flutter/material.dart';

import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';
import '../../services/native/blocker_native_service.dart';
import '../utils/domain_utils.dart';

/// App-level state: SQLite is source of truth; native services get synced rules.
class AppState extends ChangeNotifier {
  AppState({
    BlockerNativeService? nativeService,
    WebsiteRepository? websites,
    AppRepository? apps,
    SettingsRepository? settings,
  })  : _native = nativeService ?? BlockerNativeService(),
        _websites = websites ?? WebsiteRepository(),
        _apps = apps ?? AppRepository(),
        _settingsRepo = settings ?? SettingsRepository();

  final BlockerNativeService _native;
  final WebsiteRepository _websites;
  final AppRepository _apps;
  final SettingsRepository _settingsRepo;

  BlockerNativeService get native => _native;

  bool _ready = false;
  bool get ready => _ready;

  ThemeMode themeMode = ThemeMode.system;
  bool setupCompleted = false;

  List<BlockedWebsite> websites = const [];
  List<BlockedApp> blockedApps = const [];

  int blockedWebsiteCount = 0;
  int blockedAppCount = 0;
  bool protectionEnabled = false;
  bool uninstallProtectionEnabled = false;
  bool adultProtectionEnabled = true;

  bool accessibilityEnabled = false;
  bool vpnEnabled = false;
  bool deviceAdminEnabled = false;
  bool notificationEnabled = false;
  bool nativeProtectionActive = false;

  Future<void> initialize() async {
    final settings = await _settingsRepo.get();
    _applySettings(settings);
    await refreshLists();
    await refreshPermissions();
    if (protectionEnabled) {
      await _syncAndStart();
    }
    _ready = true;
    notifyListeners();
  }

  void _applySettings(AppSettings settings) {
    protectionEnabled = settings.protectionEnabled;
    uninstallProtectionEnabled = settings.uninstallProtectionEnabled;
    adultProtectionEnabled = settings.adultProtectionEnabled;
    setupCompleted = settings.setupCompleted;
    themeMode = switch (settings.themeMode) {
      'light' => ThemeMode.light,
      'dark' => ThemeMode.dark,
      _ => ThemeMode.system,
    };
  }

  Future<void> refreshLists() async {
    // UI only shows user-added sites; adult seed list stays hidden but still blocks.
    websites = await _websites.getAll(customOnly: true);
    blockedApps = await _apps.getAll();
    blockedWebsiteCount = await _websites.countEnabled(includeAdult: false);
    blockedAppCount = await _apps.countEnabled();
    notifyListeners();
  }

  Future<void> refreshPermissions() async {
    final status = await _native.getProtectionStatus();
    accessibilityEnabled = status.accessibilityEnabled;
    vpnEnabled = false;
    deviceAdminEnabled = status.deviceAdminEnabled;
    notificationEnabled = status.notificationEnabled;
    nativeProtectionActive = status.isActive;
    if (!accessibilityEnabled) {
      accessibilityEnabled = await _native.getAccessibilityStatus();
    }
    if (!deviceAdminEnabled) {
      deviceAdminEnabled = await _native.getDeviceAdminStatus();
    }
    if (!notificationEnabled) {
      notificationEnabled = await _native.getNotificationStatus();
    }
    notifyListeners();
  }

  Future<AppSettings> _currentSettings() async {
    final existing = await _settingsRepo.get();
    return existing.copyWith(
      protectionEnabled: protectionEnabled,
      uninstallProtectionEnabled: uninstallProtectionEnabled,
      adultProtectionEnabled: adultProtectionEnabled,
      setupCompleted: setupCompleted,
      themeMode: switch (themeMode) {
        ThemeMode.light => 'light',
        ThemeMode.dark => 'dark',
        ThemeMode.system => 'system',
      },
    );
  }

  Future<void> _persistSettings() async {
    await _settingsRepo.save(await _currentSettings());
  }

  Future<void> _syncRulesToNative() async {
    final domains = await _websites.getEnabledForBlocking(
      includeAdult: adultProtectionEnabled,
    );
    final packages = await _apps.getEnabled();
    await _native.syncRules(
      domains: domains.map((d) => d.domain).toList(),
      packages: packages.map((a) => a.packageName).toList(),
      protectionEnabled: protectionEnabled,
      adultProtectionEnabled: adultProtectionEnabled,
      uninstallProtectionEnabled: uninstallProtectionEnabled,
    );
  }

  Future<void> _syncAndStart() async {
    await _syncRulesToNative();
    if (protectionEnabled) {
      await _native.startProtection();
    }
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    themeMode = mode;
    await _persistSettings();
    notifyListeners();
  }

  Future<void> completeSetup() async {
    setupCompleted = true;
    await _persistSettings();
    notifyListeners();
  }

  Future<void> setProtectionEnabled(bool value) async {
    protectionEnabled = value;
    await _persistSettings();
    await _syncRulesToNative();
    if (value) {
      await _native.startProtection();
    } else {
      await _native.stopProtection();
    }
    await refreshPermissions();
    notifyListeners();
  }

  Future<void> setUninstallProtectionEnabled(bool value) async {
    uninstallProtectionEnabled = value;
    await _persistSettings();
    await _syncRulesToNative();
    if (value && !deviceAdminEnabled) {
      await _native.requestDeviceAdmin();
      await refreshPermissions();
    }
    notifyListeners();
  }

  Future<void> setAdultProtectionEnabled(bool value) async {
    adultProtectionEnabled = value;
    await _persistSettings();
    await refreshLists();
    await _syncAndStart();
    notifyListeners();
  }

  Future<String?> addWebsite(String rawDomain) async {
    final domain = DomainUtils.normalize(rawDomain);
    final error = DomainUtils.validate(domain);
    if (error != null) return error;
    if (await _websites.exists(domain)) {
      // Re-enable + re-sync so an existing rule actually blocks again.
      BlockedWebsite? existing;
      for (final w in websites) {
        if (w.domain == domain) {
          existing = w;
          break;
        }
      }
      if (existing != null && !existing.enabled) {
        await updateWebsite(existing.copyWith(enabled: true));
      }
      await ensureWebsiteBlockingActive();
      return 'This domain is already on the blocklist — protection was refreshed.';
    }
    await _websites.insert(domain);
    await refreshLists();
    await ensureWebsiteBlockingActive();
    return null;
  }

  /// Turns protection on and syncs rules for Accessibility-based blocking.
  Future<void> ensureWebsiteBlockingActive() async {
    if (!protectionEnabled) {
      protectionEnabled = true;
      await _persistSettings();
    }
    await _syncRulesToNative();
    await _native.startProtection();
    await refreshPermissions();
    if (!accessibilityEnabled) {
      await _native.requestAccessibility();
      await refreshPermissions();
    }
    notifyListeners();
  }

  Future<void> updateWebsite(BlockedWebsite website) async {
    await _websites.update(website);
    await refreshLists();
    await _syncAndStart();
  }

  Future<void> deleteWebsite(BlockedWebsite website) async {
    await _websites.delete(website.id);
    await refreshLists();
    await _syncAndStart();
  }

  Future<void> setWebsiteEnabled(BlockedWebsite website, bool enabled) async {
    await updateWebsite(website.copyWith(enabled: enabled));
  }

  Future<void> blockApp({
    required String packageName,
    required String appName,
  }) async {
    await _apps.upsert(
      packageName: packageName,
      appName: appName,
      enabled: true,
    );
    await refreshLists();
    await _syncAndStart();
  }

  Future<void> unblockApp(String packageName) async {
    await _apps.deleteByPackage(packageName);
    await refreshLists();
    await _syncAndStart();
  }

  Future<void> requestAccessibility() async {
    await _native.requestAccessibility();
    await refreshPermissions();
  }

  Future<void> requestDeviceAdmin() async {
    await _native.requestDeviceAdmin();
    await refreshPermissions();
  }

  Future<void> requestNotifications() async {
    await _native.requestNotificationPermission();
    await refreshPermissions();
  }

  bool get allRequiredPermissionsReady => accessibilityEnabled;

  String get protectionLabel =>
      protectionEnabled && (nativeProtectionActive || accessibilityEnabled)
          ? 'ACTIVE'
          : 'INACTIVE';

  bool get isProtectionActive =>
      protectionEnabled && (nativeProtectionActive || accessibilityEnabled);
}
