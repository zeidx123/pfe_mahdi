import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'services/api_service.dart';
import 'services/auth_service.dart';
import 'pages/login_page.dart';
import 'pages/home_page.dart';
import 'pages/tournee_detail_page.dart';
import 'pages/reclamations_page.dart';
import 'pages/assistant_page.dart';
import 'pages/onboarding_page.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  ApiService.init();
  runApp(const GestiCollecteApp());
}

class GestiCollecteApp extends StatelessWidget {
  const GestiCollecteApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'GestiCollecte',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF0369a1),
          primary: const Color(0xFF0369a1),
        ),
        fontFamily: 'Roboto',
        appBarTheme: const AppBarTheme(centerTitle: false),
      ),
      home: const _SplashRouter(),
      routes: {
        '/onboarding': (_) => const OnboardingPage(),
        '/login': (_) => const LoginPage(),
        '/home': (_) => const HomePage(),
        '/tournee-detail': (_) => const TourneeDetailPage(),
        '/reclamations': (_) => const ReclamationsPage(),
        '/assistant': (_) => const AssistantPage(),
      },
    );
  }
}

class _SplashRouter extends StatefulWidget {
  const _SplashRouter();

  @override
  State<_SplashRouter> createState() => _SplashRouterState();
}

class _SplashRouterState extends State<_SplashRouter> {
  @override
  void initState() {
    super.initState();
    _redirect();
  }

  Future<void> _redirect() async {
    await Future.delayed(const Duration(milliseconds: 1500));
    if (!mounted) return;

    final prefs = await SharedPreferences.getInstance();
    final onboardingDone = prefs.getBool('onboarding_completed') ?? false;

    if (!mounted) return;
    if (!onboardingDone) {
      Navigator.pushReplacementNamed(context, '/onboarding');
      return;
    }

    final loggedIn = await AuthService.isLoggedIn();
    if (!mounted) return;
    Navigator.pushReplacementNamed(context, loggedIn ? '/home' : '/login');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF0369a1), Color(0xFF0c4a6e)],
          ),
        ),
        child: const Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.local_shipping_rounded, size: 80, color: Colors.white),
              SizedBox(height: 20),
              Text('GestiCollecte', style: TextStyle(fontSize: 32, fontWeight: FontWeight.w800, color: Colors.white, letterSpacing: -1)),
              SizedBox(height: 8),
              Text('Espace Chauffeur', style: TextStyle(fontSize: 16, color: Colors.white70)),
              SizedBox(height: 48),
              CircularProgressIndicator(color: Colors.white54, strokeWidth: 2),
            ],
          ),
        ),
      ),
    );
  }
}
