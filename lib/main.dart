import 'package:flutter/material.dart';

import 'app.dart';
import 'core/state/app_state.dart';
import 'data/database/haven_database.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await HavenDatabase.instance();
  final appState = AppState();
  await appState.initialize();
  runApp(HavenApp(appState: appState));
}
