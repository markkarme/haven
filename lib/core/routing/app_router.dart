import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../features/apps/blocked_apps_screen.dart';
import '../../features/apps/installed_apps_screen.dart';
import '../../features/home/home_screen.dart';
import '../../features/permissions/permissions_screen.dart';
import '../../features/settings/protection_settings_screen.dart';
import '../../features/settings/vpn_exceptions_screen.dart';
import '../../features/setup/initial_setup_screen.dart';
import '../../features/splash/splash_screen.dart';
import '../../features/websites/add_website_screen.dart';
import '../../features/websites/blocked_websites_screen.dart';
import 'route_names.dart';

GoRouter createAppRouter() {
  return GoRouter(
    initialLocation: RouteNames.splash,
    routes: [
      GoRoute(
        path: RouteNames.splash,
        name: 'splash',
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: RouteNames.initialSetup,
        name: 'initialSetup',
        builder: (context, state) => const InitialSetupScreen(),
      ),
      GoRoute(
        path: RouteNames.home,
        name: 'home',
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: RouteNames.blockedWebsites,
        name: 'blockedWebsites',
        builder: (context, state) => const BlockedWebsitesScreen(),
      ),
      GoRoute(
        path: RouteNames.addWebsite,
        name: 'addWebsite',
        builder: (context, state) => const AddWebsiteScreen(),
      ),
      GoRoute(
        path: RouteNames.installedApps,
        name: 'installedApps',
        builder: (context, state) => const InstalledAppsScreen(),
      ),
      GoRoute(
        path: RouteNames.blockedApps,
        name: 'blockedApps',
        builder: (context, state) => const BlockedAppsScreen(),
      ),
      GoRoute(
        path: RouteNames.permissions,
        name: 'permissions',
        builder: (context, state) => const PermissionsScreen(),
      ),
      GoRoute(
        path: RouteNames.protectionSettings,
        name: 'protectionSettings',
        builder: (context, state) => const ProtectionSettingsScreen(),
      ),
      GoRoute(
        path: RouteNames.vpnExceptions,
        name: 'vpnExceptions',
        builder: (context, state) => const VpnExceptionsScreen(),
      ),
    ],
    errorBuilder: (context, state) => Scaffold(
      body: Center(
        child: Text('Page not found: ${state.uri}'),
      ),
    ),
  );
}
