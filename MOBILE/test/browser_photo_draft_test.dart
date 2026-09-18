@TestOn('browser')
library;

import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:gesticollecte_mobile/core/constants.dart';
import 'package:gesticollecte_mobile/services/auth_service.dart';
import 'package:gesticollecte_mobile/services/photo_draft_service.dart';

Future<void> useDriver(String id) async {
  final prefs = await SharedPreferences.getInstance();
  final claims = base64Url.encode(utf8.encode('{"exp":4102444800}'));
  await prefs.setString(AppConstants.tokenKey, 'header.$claims.signature');
  await prefs.setString(
      AppConstants.userKey, jsonEncode({'id': id, 'role': 'CHAUFFEUR'}));
  expect(await AuthService.isLoggedIn(), isTrue);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await useDriver('browser-storage-test-a');
    await PhotoDraftService.clear();
    await useDriver('browser-storage-test-b');
    await PhotoDraftService.clear();
    await useDriver('browser-storage-test-a');
  });
  tearDown(() async {
    await useDriver('browser-storage-test-a');
    await PhotoDraftService.clear();
    await useDriver('browser-storage-test-b');
    await PhotoDraftService.clear();
    await AuthService.logout();
  });

  test('large browser draft survives closing and reopening its storage',
      () async {
    // Binary storage fixture, not an image-quality or decoder test.
    final bytes = Uint8List(6 * 1024 * 1024);
    bytes.setAll(0, [0xff, 0xd8, 0xff]);
    bytes[bytes.length - 1] = 42;
    await PhotoDraftService.save(
        XFile.fromData(bytes, name: 'large.jpg'), 'tour-a', 'point-a', 'AFTER');
    final restored = await PhotoDraftService.load();
    expect(restored?.tourId, 'tour-a');
    expect(restored?.pointId, 'point-a');
    expect(restored?.stage, 'AFTER');
    final restoredBytes = await restored!.bytes();
    expect(restoredBytes.length, bytes.length);
    expect(restoredBytes.last, 42);
    expect(PhotoDraft.imageMimeType(restoredBytes), 'image/jpeg');
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('photo_draft_browser-storage-test-a'), isNull);
    await PhotoDraftService.clear();
    expect(await PhotoDraftService.load(), isNull);
  });

  test('browser drafts stay separated between driver sessions', () async {
    final png =
        Uint8List.fromList([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
    await PhotoDraftService.save(
        XFile.fromData(png, name: 'before.png'), 'tour-a', 'point-a', 'BEFORE');
    await useDriver('browser-storage-test-b');
    expect(await PhotoDraftService.load(), isNull);
    await PhotoDraftService.save(
        XFile.fromData(png, name: 'after.png'), 'tour-b', 'point-b', 'AFTER');
    await useDriver('browser-storage-test-a');
    final restored = await PhotoDraftService.load();
    expect(restored?.tourId, 'tour-a');
    expect(restored?.stage, 'BEFORE');
    expect(PhotoDraft.imageMimeType(await restored!.bytes()), 'image/png');
  });

  test('previous base64 browser drafts remain available for retry', () async {
    final bytes = Uint8List.fromList([0xff, 0xd8, 0xff, 0xe0]);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
        'photo_draft_browser-storage-test-a',
        jsonEncode({
          'tourId': 'old-tour',
          'pointId': 'old-point',
          'stage': 'AFTER',
          'path': null,
          'bytes': base64Encode(bytes),
        }));
    final restored = await PhotoDraftService.load();
    expect(restored?.tourId, 'old-tour');
    expect(await restored!.bytes(), bytes);
  });
}
