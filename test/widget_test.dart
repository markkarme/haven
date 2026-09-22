import 'package:flutter_test/flutter_test.dart';
import 'package:haven/app.dart';
import 'package:haven/core/state/app_state.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  testWidgets('Haven splash shows app name', (tester) async {
    final state = AppState();
    // Skip full DB seed in widget test — use empty ready state.
    await tester.pumpWidget(HavenApp(appState: state));
    expect(find.text('Haven'), findsWidgets);
    await tester.pump(const Duration(milliseconds: 1000));
  });
}
