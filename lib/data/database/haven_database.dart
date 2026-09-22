import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import '../models/models.dart';

class HavenDatabase {
  HavenDatabase._();

  static Database? _db;

  static Future<Database> instance() async {
    if (_db != null) return _db!;
    final dbPath = await getDatabasesPath();
    _db = await openDatabase(
      p.join(dbPath, 'haven.db'),
      version: 3,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE blocked_websites (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            domain TEXT NOT NULL UNIQUE,
            enabled INTEGER NOT NULL DEFAULT 1,
            createdAt TEXT NOT NULL,
            isAdultSeed INTEGER NOT NULL DEFAULT 0
          )
        ''');
        await db.execute('''
          CREATE TABLE blocked_apps (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            packageName TEXT NOT NULL UNIQUE,
            appName TEXT NOT NULL,
            enabled INTEGER NOT NULL DEFAULT 1,
            createdAt TEXT NOT NULL
          )
        ''');
        await db.execute('''
          CREATE TABLE settings (
            id INTEGER PRIMARY KEY,
            protectionEnabled INTEGER NOT NULL DEFAULT 1,
            uninstallProtectionEnabled INTEGER NOT NULL DEFAULT 0,
            adultProtectionEnabled INTEGER NOT NULL DEFAULT 1,
            setupCompleted INTEGER NOT NULL DEFAULT 0,
            themeMode TEXT NOT NULL DEFAULT 'system'
          )
        ''');
        await db.insert('settings', AppSettings.defaults().toMap());
        await _seedAdultDomains(db);
      },
      onUpgrade: (db, oldVersion, newVersion) async {
        if (oldVersion < 2) {
          await db.update(
            'settings',
            {
              'protectionEnabled': 1,
              'adultProtectionEnabled': 1,
            },
            where: 'id = ?',
            whereArgs: [1],
          );
        }
        if (oldVersion < 3) {
          await db.update(
            'settings',
            {'uninstallProtectionEnabled': 0},
            where: 'id = ?',
            whereArgs: [1],
          );
        }
      },
    );
    return _db!;
  }

  static Future<void> _seedAdultDomains(Database db) async {
    final raw =
        await rootBundle.loadString('assets/blocklists/adult_domains.json');
    final list = (jsonDecode(raw) as List).cast<String>();
    final now = DateTime.now().toIso8601String();
    final batch = db.batch();
    for (final domain in list) {
      batch.insert(
        'blocked_websites',
        {
          'domain': domain.toLowerCase(),
          'enabled': 1,
          'createdAt': now,
          'isAdultSeed': 1,
        },
        conflictAlgorithm: ConflictAlgorithm.ignore,
      );
    }
    await batch.commit(noResult: true);
  }

  static Future<void> close() async {
    await _db?.close();
    _db = null;
  }
}
