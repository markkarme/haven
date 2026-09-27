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
  Set<String> vpnExcludedPackages = const {};

  int blockedWebsiteCount = 0;
  int blockedAppCount = 0;
  bool protectionEnabled = false;
  bool uninstallProtectionEnabled = false;
  bool adultProtectionEnabled = true;

  bool vpnEnabled = false;
  bool vpnRunning = false;
  bool privateDnsBypass = false;
  bool usageAccessEnabled = false;
  bool overlayEnabled = false;
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
    vpnExcludedPackages = (await _native.getVpnExcludedApps()).toSet();
    notifyListeners();
  }

  /// Excluded apps skip website blocking entirely — callers must confirm the password.
  Future<void> setVpnExcluded(String packageName, bool excluded) async {
    final next = {...vpnExcludedPackages};
    if (excluded) {
      next.add(packageName);
    } else {
      next.remove(packageName);
    }
    vpnExcludedPackages = next;
    await _native.setVpnExcludedApps(next.toList());
    notifyListeners();
  }

  Future<void> refreshPermissions() async {
    final status = await _native.getProtectionStatus();
    vpnEnabled = status.vpnEnabled;
    vpnRunning = status.vpnRunning;
    privateDnsBypass = status.privateDnsBypass;
    usageAccessEnabled = status.usageAccessEnabled;
    overlayEnabled = status.overlayEnabled;
    deviceAdminEnabled = status.deviceAdminEnabled;
    notificationEnabled = status.notificationEnabled;
    nativeProtectionActive = status.isActive;
    if (!vpnEnabled) {
      vpnEnabled = await _native.getVpnStatus();
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
      await _native.requestVpnPermission();
      await _native.startProtection();
    } else {
      await _native.stopProtection();
    }
    await _awaitVpnRunning(value);
    notifyListeners();
  }

  /// The VPN service starts/stops asynchronously; poll briefly so the UI shows the real state.
  Future<void> _awaitVpnRunning(bool expected) async {
    for (var i = 0; i < 6; i++) {
      await refreshPermissions();
      if (vpnRunning == expected || !vpnEnabled) return;
      await Future<void>.delayed(const Duration(milliseconds: 250));
    }
  }

  Future<void> setUninstallProtectionEnabled(bool value) async {
    uninstallProtectionEnabled = value;
    await _persistSettings();
    await _syncRulesToNative();
    if (value && !deviceAdminEnabled) {
      await _native.requestDeviceAdmin();
      await refreshPermissions();
    } else if (!value && deviceAdminEnabled) {
      // The UI already confirmed the password; this is the supported uninstall path.
      await _native.removeDeviceAdmin();
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

  /// Turns protection on and makes sure the VPN website filter is running.
  Future<void> ensureWebsiteBlockingActive() async {
    if (!protectionEnabled) {
      protectionEnabled = true;
      await _persistSettings();
    }
    await _syncRulesToNative();
    if (!vpnEnabled) {
      await _native.requestVpnPermission();
    }
    await _native.startProtection();
    await _awaitVpnRunning(true);
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

  Future<void> requestVpn() async {
    final granted = await _native.requestVpnPermission();
    if (granted && protectionEnabled) {
      await _native.startProtection();
      await _awaitVpnRunning(true);
    } else {
      await refreshPermissions();
    }
  }

  Future<void> openVpnSettings() => _native.openVpnSettings();

  /// Status refreshes when the user returns from Settings (app lifecycle resume).
  Future<void> requestUsageAccess() => _native.requestUsageAccess();

  Future<void> requestOverlay() => _native.requestOverlay();

  /// App blocking and the uninstall gate need Usage access and overlay permission.
  bool get appMonitorReady => usageAccessEnabled && overlayEnabled;

  Future<void> requestDeviceAdmin() async {
    await _native.requestDeviceAdmin();
    await refreshPermissions();
  }

  Future<void> requestNotifications() async {
    await _native.requestNotificationPermission();
    await refreshPermissions();
  }

  bool get allRequiredPermissionsReady => vpnEnabled;

  String get protectionLabel => isProtectionActive ? 'ACTIVE' : 'INACTIVE';

  bool get isProtectionActive => protectionEnabled && vpnRunning;
}
