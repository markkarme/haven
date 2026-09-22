class BlockedWebsite {
  const BlockedWebsite({
    required this.id,
    required this.domain,
    required this.enabled,
    required this.createdAt,
    this.isAdultSeed = false,
  });

  final int id;
  final String domain;
  final bool enabled;
  final DateTime createdAt;
  final bool isAdultSeed;

  BlockedWebsite copyWith({
    int? id,
    String? domain,
    bool? enabled,
    DateTime? createdAt,
    bool? isAdultSeed,
  }) {
    return BlockedWebsite(
      id: id ?? this.id,
      domain: domain ?? this.domain,
      enabled: enabled ?? this.enabled,
      createdAt: createdAt ?? this.createdAt,
      isAdultSeed: isAdultSeed ?? this.isAdultSeed,
    );
  }

  Map<String, Object?> toMap() => {
        'id': id,
        'domain': domain,
        'enabled': enabled ? 1 : 0,
        'createdAt': createdAt.toIso8601String(),
        'isAdultSeed': isAdultSeed ? 1 : 0,
      };

  factory BlockedWebsite.fromMap(Map<String, Object?> map) {
    return BlockedWebsite(
      id: map['id'] as int,
      domain: map['domain'] as String,
      enabled: (map['enabled'] as int) == 1,
      createdAt: DateTime.parse(map['createdAt'] as String),
      isAdultSeed: (map['isAdultSeed'] as int? ?? 0) == 1,
    );
  }
}

class BlockedApp {
  const BlockedApp({
    required this.id,
    required this.packageName,
    required this.appName,
    required this.enabled,
    required this.createdAt,
  });

  final int id;
  final String packageName;
  final String appName;
  final bool enabled;
  final DateTime createdAt;

  BlockedApp copyWith({
    int? id,
    String? packageName,
    String? appName,
    bool? enabled,
    DateTime? createdAt,
  }) {
    return BlockedApp(
      id: id ?? this.id,
      packageName: packageName ?? this.packageName,
      appName: appName ?? this.appName,
      enabled: enabled ?? this.enabled,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  Map<String, Object?> toMap() => {
        'id': id,
        'packageName': packageName,
        'appName': appName,
        'enabled': enabled ? 1 : 0,
        'createdAt': createdAt.toIso8601String(),
      };

  factory BlockedApp.fromMap(Map<String, Object?> map) {
    return BlockedApp(
      id: map['id'] as int,
      packageName: map['packageName'] as String,
      appName: map['appName'] as String,
      enabled: (map['enabled'] as int) == 1,
      createdAt: DateTime.parse(map['createdAt'] as String),
    );
  }
}

class AppSettings {
  const AppSettings({
    required this.id,
    required this.protectionEnabled,
    required this.uninstallProtectionEnabled,
    required this.adultProtectionEnabled,
    required this.setupCompleted,
    required this.themeMode,
  });

  final int id;
  final bool protectionEnabled;
  final bool uninstallProtectionEnabled;
  final bool adultProtectionEnabled;
  final bool setupCompleted;
  final String themeMode;

  factory AppSettings.defaults() => const AppSettings(
        id: 1,
        protectionEnabled: true,
        uninstallProtectionEnabled: false,
        adultProtectionEnabled: true,
        setupCompleted: false,
        themeMode: 'system',
      );

  AppSettings copyWith({
    bool? protectionEnabled,
    bool? uninstallProtectionEnabled,
    bool? adultProtectionEnabled,
    bool? setupCompleted,
    String? themeMode,
  }) {
    return AppSettings(
      id: id,
      protectionEnabled: protectionEnabled ?? this.protectionEnabled,
      uninstallProtectionEnabled:
          uninstallProtectionEnabled ?? this.uninstallProtectionEnabled,
      adultProtectionEnabled:
          adultProtectionEnabled ?? this.adultProtectionEnabled,
      setupCompleted: setupCompleted ?? this.setupCompleted,
      themeMode: themeMode ?? this.themeMode,
    );
  }

  Map<String, Object?> toMap() => {
        'id': id,
        'protectionEnabled': protectionEnabled ? 1 : 0,
        'uninstallProtectionEnabled': uninstallProtectionEnabled ? 1 : 0,
        'adultProtectionEnabled': adultProtectionEnabled ? 1 : 0,
        'setupCompleted': setupCompleted ? 1 : 0,
        'themeMode': themeMode,
      };

  factory AppSettings.fromMap(Map<String, Object?> map) {
    return AppSettings(
      id: map['id'] as int,
      protectionEnabled: (map['protectionEnabled'] as int) == 1,
      uninstallProtectionEnabled:
          (map['uninstallProtectionEnabled'] as int) == 1,
      adultProtectionEnabled: (map['adultProtectionEnabled'] as int) == 1,
      setupCompleted: (map['setupCompleted'] as int? ?? 0) == 1,
      themeMode: map['themeMode'] as String? ?? 'system',
    );
  }
}
