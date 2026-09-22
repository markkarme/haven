import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import 'core/constants/app_constants.dart';
import 'core/routing/app_router.dart';
import 'core/state/app_state.dart';
import 'core/theme/app_theme.dart';

class HavenApp extends StatefulWidget {
  const HavenApp({super.key, this.appState});

  final AppState? appState;

  @override
  State<HavenApp> createState() => _HavenAppState();
}

class _HavenAppState extends State<HavenApp> with WidgetsBindingObserver {
  late final AppState _appState = widget.appState ?? AppState();
  late final GoRouter _router = createAppRouter();
  final bool _ownsState = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    if (_ownsState) {
      _appState.dispose();
    }
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _appState.refreshPermissions();
    }
  }

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: _appState,
      child: Consumer<AppState>(
        builder: (context, state, _) {
          return MaterialApp.router(
            title: AppConstants.appName,
            debugShowCheckedModeBanner: false,
            theme: AppTheme.light(),
            darkTheme: AppTheme.dark(),
            themeMode: state.themeMode,
            routerConfig: _router,
          );
        },
      ),
    );
  }
}
