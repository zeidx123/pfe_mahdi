import 'package:flutter/material.dart';
import 'active_tracking_page.dart';

class TourneeDetailPage extends StatelessWidget {
  const TourneeDetailPage({super.key});

  @override
  Widget build(BuildContext context) {
    final args = ModalRoute.of(context)?.settings.arguments;
    if (args is! Map || args['id'] == null) {
      return Scaffold(
          appBar: AppBar(title: const Text('Tournée')),
          body: const Center(
              child: Text('Tournée introuvable. Revenez à vos affectations.')));
    }
    return ActiveTrackingPage(tournee: Map<String, dynamic>.from(args));
  }
}
