import 'dart:io';
import 'package:flutter/services.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

Future<void> openSourceFile(
    Uint8List bytes, String filename, String mimeType) async {
  try {
    final cache = await getTemporaryDirectory();
    final directory = await Directory(
            '${cache.path}${Platform.pathSeparator}rag-sources')
        .create(recursive: true);
    final download = await directory.createTemp('source-');
    final file = File('${download.path}${Platform.pathSeparator}$filename');
    await file.writeAsBytes(bytes, flush: true);
    // open_filex shares Android files through a content URI/FileProvider.
    final result = await OpenFilex.open(file.path, type: mimeType);
    switch (result.type) {
      case ResultType.done:
        return;
      case ResultType.noAppToOpen:
        throw const FormatException(
            'Aucune application ne peut ouvrir ce document. Installez un lecteur PDF ou bureautique, puis réessayez.');
      case ResultType.permissionDenied:
        throw const FormatException(
            'L’accès au document a été refusé par l’appareil. Vérifiez les autorisations puis réessayez.');
      case ResultType.fileNotFound:
        throw const FormatException(
            'Le fichier temporaire est introuvable. Téléchargez de nouveau la source.');
      case ResultType.error:
        throw const FormatException(
            'Le document a été téléchargé, mais son ouverture a échoué. Réessayez avec un lecteur compatible.');
    }
  } on FileSystemException {
    throw const FormatException(
        'Le document n’a pas pu être enregistré sur cet appareil. Vérifiez l’espace disponible puis réessayez.');
  } on PlatformException {
    throw const FormatException(
        'L’appareil n’a pas pu ouvrir le document. Vérifiez qu’un lecteur compatible est installé.');
  } on MissingPluginException {
    throw const FormatException(
        'L’ouverture de documents nécessite une nouvelle installation de cette version de l’application.');
  }
}
