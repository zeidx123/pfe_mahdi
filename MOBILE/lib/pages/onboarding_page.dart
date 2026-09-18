import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class OnboardingPage extends StatefulWidget {
  const OnboardingPage({super.key});

  @override
  State<OnboardingPage> createState() => _OnboardingPageState();
}

class _OnboardingPageState extends State<OnboardingPage> {
  final PageController _controller = PageController();
  int _currentPage = 0;

  List<_OnboardingSlide> get _slides => const [
        _OnboardingSlide(
          icon: Icons.local_shipping_rounded,
          iconColor: Color(0xFF0369a1),
          bgGradient: [Color(0xFFe0f2fe), Color(0xFFbae6fd)],
          title: 'Bienvenue dans GestiCollecte',
          subtitle:
              'Application de gestion des tournees de collecte pour les chauffeurs municipaux.',
        ),
        _OnboardingSlide(
          icon: Icons.map_rounded,
          iconColor: Color(0xFF059669),
          bgGradient: [Color(0xFFd1fae5), Color(0xFFa7f3d0)],
          title: 'Suivez vos Tournees',
          subtitle:
              'Consultez vos tournees du jour, visualisez les points de collecte et mettez a jour le statut en temps reel.',
        ),
        _OnboardingSlide(
          icon: Icons.report_problem_rounded,
          iconColor: Color(0xFFd97706),
          bgGradient: [Color(0xFFfef3c7), Color(0xFFfde68a)],
          title: 'Signalez les Incidents',
          subtitle:
              'Declarez tout probleme rencontre sur le terrain — obstacles, bacs manquants ou situations dangereuses.',
        ),
        _OnboardingSlide(
          icon: Icons.smart_toy_rounded,
          iconColor: Color(0xFF7c3aed),
          bgGradient: [Color(0xFFede9fe), Color(0xFFddd6fe)],
          title: 'Assistant Intelligent',
          subtitle:
              'Posez vos questions a l assistant qui vous guide grace aux documents et procedures de votre municipalite.',
        ),
      ];

  Future<void> _finishOnboarding() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('onboarding_completed', true);
    if (!mounted) return;
    Navigator.pushReplacementNamed(context, '/login');
  }

  void _nextPage() {
    final slides = _slides;
    if (_currentPage < slides.length - 1) {
      _controller.nextPage(
        duration: const Duration(milliseconds: 400),
        curve: Curves.easeInOut,
      );
    } else {
      _finishOnboarding();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final slides = _slides;
    final slide = slides[_currentPage];
    return Scaffold(
      body: AnimatedContainer(
        duration: const Duration(milliseconds: 400),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: slide.bgGradient,
          ),
        ),
        child: SafeArea(
          child: Column(
            children: [
              // Skip button
              Align(
                alignment: Alignment.topRight,
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: TextButton(
                    onPressed: _finishOnboarding,
                    child: const Text(
                      'Passer',
                      style: TextStyle(
                        color: Colors.black54,
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                  ),
                ),
              ),

              // PageView
              Expanded(
                child: PageView.builder(
                  controller: _controller,
                  onPageChanged: (i) => setState(() => _currentPage = i),
                  itemCount: slides.length,
                  itemBuilder: (context, index) =>
                      _SlideContent(slide: slides[index]),
                ),
              ),

              // Indicators + buttons
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 0, 24, 36),
                child: Column(
                  children: [
                    // Dot indicators
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: List.generate(
                        slides.length,
                        (i) => AnimatedContainer(
                          duration: const Duration(milliseconds: 300),
                          margin: const EdgeInsets.symmetric(horizontal: 4),
                          width: i == _currentPage ? 24 : 8,
                          height: 8,
                          decoration: BoxDecoration(
                            color: i == _currentPage
                                ? slide.iconColor
                                : Colors.black26,
                            borderRadius: BorderRadius.circular(4),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 32),

                    // Action button
                    SizedBox(
                      width: double.infinity,
                      height: 56,
                      child: ElevatedButton(
                        onPressed: _nextPage,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: slide.iconColor,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(16),
                          ),
                          elevation: 0,
                        ),
                        child: Text(
                          _currentPage == slides.length - 1
                              ? 'Commencer'
                              : 'Suivant',
                          style: const TextStyle(
                            fontSize: 17,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 0.3,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SlideContent extends StatelessWidget {
  final _OnboardingSlide slide;
  const _SlideContent({required this.slide});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 200,
            height: 200,
            decoration: BoxDecoration(
              color: Colors.white.withAlpha(179),
              shape: BoxShape.circle,
              boxShadow: [
                BoxShadow(
                  color: slide.iconColor.withAlpha(51),
                  blurRadius: 40,
                  spreadRadius: 10,
                ),
              ],
            ),
            child: Icon(
              slide.icon,
              size: 96,
              color: slide.iconColor,
            ),
          ),
          const SizedBox(height: 48),
          Text(
            slide.title,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 28,
              fontWeight: FontWeight.w800,
              color: Color(0xFF0f172a),
              height: 1.2,
              letterSpacing: -0.5,
            ),
          ),
          const SizedBox(height: 20),
          Text(
            slide.subtitle,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 16,
              color: Color(0xFF475569),
              height: 1.6,
            ),
          ),
        ],
      ),
    );
  }
}

class _OnboardingSlide {
  final IconData icon;
  final Color iconColor;
  final List<Color> bgGradient;
  final String title;
  final String subtitle;

  const _OnboardingSlide({
    required this.icon,
    required this.iconColor,
    required this.bgGradient,
    required this.title,
    required this.subtitle,
  });
}
