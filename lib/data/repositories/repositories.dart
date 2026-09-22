import 'package:sqflite/sqflite.dart';

import '../database/haven_database.dart';
import '../models/models.dart';

class WebsiteRepository {
  Future<Database> get _db => HavenDatabase.instance();

  Future<List<BlockedWebsite>> getAll({bool customOnly = false}) async {
    final db = await _db;
    final rows = await db.query(
      'blocked_websites',
      where: customOnly ? 'isAdultSeed = 0' : null,
      orderBy: 'domain COLLATE NOCASE ASC',
    );
    return rows.map(BlockedWebsite.fromMap).toList();
  }

  Future<List<BlockedWebsite>> getEnabledForBlocking({
    required bool includeAdult,
  }) async {
    final db = await _db;
    final rows = includeAdult
        ? await db.query(
            'blocked_websites',
            where: 'enabled = 1',
          )
        : await db.query(
            'blocked_websites',
            where: 'enabled = 1 AND isAdultSeed = 0',
          );
    return rows.map(BlockedWebsite.fromMap).toList();
  }

  Future<int> countEnabled({required bool includeAdult}) async {
    final db = await _db;
    final result = includeAdult
        ? await db.rawQuery(
            'SELECT COUNT(*) AS c FROM blocked_websites WHERE enabled = 1',
          )
        : await db.rawQuery(
            'SELECT COUNT(*) AS c FROM blocked_websites WHERE enabled = 1 AND isAdultSeed = 0',
          );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<BlockedWebsite> insert(String domain) async {
    final db = await _db;
    final now = DateTime.now();
    final id = await db.insert(
      'blocked_websites',
      {
        'domain': domain,
        'enabled': 1,
        'createdAt': now.toIso8601String(),
        'isAdultSeed': 0,
      },
      conflictAlgorithm: ConflictAlgorithm.abort,
    );
    return BlockedWebsite(
      id: id,
      domain: domain,
      enabled: true,
      createdAt: now,
    );
  }

  Future<void> update(BlockedWebsite website) async {
    final db = await _db;
    await db.update(
      'blocked_websites',
      website.toMap()..remove('id'),
      where: 'id = ?',
      whereArgs: [website.id],
    );
  }

  Future<void> delete(int id) async {
    final db = await _db;
    await db.delete('blocked_websites', where: 'id = ?', whereArgs: [id]);
  }

  Future<bool> exists(String domain) async {
    final db = await _db;
    final rows = await db.query(
      'blocked_websites',
      where: 'domain = ?',
      whereArgs: [domain],
      limit: 1,
    );
    return rows.isNotEmpty;
  }
}

class AppRepository {
  Future<Database> get _db => HavenDatabase.instance();

  Future<List<BlockedApp>> getAll() async {
    final db = await _db;
    final rows = await db.query(
      'blocked_apps',
      orderBy: 'appName COLLATE NOCASE ASC',
    );
    return rows.map(BlockedApp.fromMap).toList();
  }

  Future<List<BlockedApp>> getEnabled() async {
    final db = await _db;
    final rows = await db.query('blocked_apps', where: 'enabled = 1');
    return rows.map(BlockedApp.fromMap).toList();
  }

  Future<int> countEnabled() async {
    final db = await _db;
    final result = await db.rawQuery(
      'SELECT COUNT(*) AS c FROM blocked_apps WHERE enabled = 1',
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<BlockedApp> upsert({
    required String packageName,
    required String appName,
    bool enabled = true,
  }) async {
    final db = await _db;
    final existing = await db.query(
      'blocked_apps',
      where: 'packageName = ?',
      whereArgs: [packageName],
      limit: 1,
    );
    if (existing.isNotEmpty) {
      final app = BlockedApp.fromMap(existing.first).copyWith(
        enabled: enabled,
        appName: appName,
      );
      await db.update(
        'blocked_apps',
        app.toMap()..remove('id'),
        where: 'id = ?',
        whereArgs: [app.id],
      );
      return app;
    }
    final now = DateTime.now();
    final id = await db.insert('blocked_apps', {
      'packageName': packageName,
      'appName': appName,
      'enabled': enabled ? 1 : 0,
      'createdAt': now.toIso8601String(),
    });
    return BlockedApp(
      id: id,
      packageName: packageName,
      appName: appName,
      enabled: enabled,
      createdAt: now,
    );
  }

  Future<void> setEnabled(String packageName, bool enabled) async {
    final db = await _db;
    await db.update(
      'blocked_apps',
      {'enabled': enabled ? 1 : 0},
      where: 'packageName = ?',
      whereArgs: [packageName],
    );
  }

  Future<void> deleteByPackage(String packageName) async {
    final db = await _db;
    await db.delete(
      'blocked_apps',
      where: 'packageName = ?',
      whereArgs: [packageName],
    );
  }

  Future<Set<String>> enabledPackageNames() async {
    final apps = await getEnabled();
    return apps.map((a) => a.packageName).toSet();
  }
}

class SettingsRepository {
  Future<Database> get _db => HavenDatabase.instance();

  Future<AppSettings> get() async {
    final db = await _db;
    final rows = await db.query('settings', where: 'id = 1', limit: 1);
    if (rows.isEmpty) {
      final defaults = AppSettings.defaults();
      await db.insert('settings', defaults.toMap());
      return defaults;
    }
    return AppSettings.fromMap(rows.first);
  }

  Future<void> save(AppSettings settings) async {
    final db = await _db;
    await db.update(
      'settings',
      settings.toMap()..remove('id'),
      where: 'id = ?',
      whereArgs: [settings.id],
    );
  }
}
