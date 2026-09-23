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
import 'core/app_theme.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await ApiService.init();
  ApiService.sessionExpired.addListener(() async {
    if (ApiService.sessionExpired.value) {
      await AuthService.logout();
      appNavigator.currentState
          ?.pushNamedAndRemoveUntil('/login', (_) => false);
    }
  });
  runApp(const GestiCollecteApp());
}

final appNavigator = GlobalKey<NavigatorState>();

class GestiCollecteApp extends StatelessWidget {
  const GestiCollecteApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: appNavigator,
      title: 'GestiCollecte',
      debugShowCheckedModeBanner: false,
      theme: GestiTheme.light,
      // A browser refresh has no Navigator arguments. Restore the driver
      // session first, then reopen tours from their persisted assignments.
      onGenerateInitialRoutes: (_) => [
        MaterialPageRoute<void>(
          settings: const RouteSettings(name: '/'),
          builder: (_) => const _SplashRouter(),
        ),
      ],
      routes: {
        '/': (_) => const _SplashRouter(),
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
            colors: [GestiTheme.navy, Color(0xFF234638)],
          ),
        ),
        child: const Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.local_shipping_rounded, size: 80, color: Colors.white),
              SizedBox(height: 20),
              Text('GestiCollecte',
                  style: TextStyle(
                      fontSize: 32,
                      fontWeight: FontWeight.w800,
                      color: Colors.white,
                      letterSpacing: -1)),
              SizedBox(height: 8),
              Text('Espace Chauffeur',
                  style: TextStyle(fontSize: 16, color: Colors.white70)),
              SizedBox(height: 48),
              CircularProgressIndicator(color: Colors.white54, strokeWidth: 2),
            ],
          ),
        ),
      ),
    );
  }
}
