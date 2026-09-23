import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../core/app_theme.dart';

class OnboardingPage extends StatefulWidget {
  const OnboardingPage({super.key});

  @override
  State<OnboardingPage> createState() => _OnboardingPageState();
}

class _OnboardingPageState extends State<OnboardingPage> {
  final PageController _controller = PageController();
  int _currentPage = 0;
  bool _finishing = false;

  static const _slides = [
    _OnboardingSlide(
      icon: Icons.local_shipping_outlined,
      label: 'VOTRE ESPACE CHAUFFEUR',
      title: 'Vos tournées,\nau même endroit',
      subtitle:
          'Retrouvez les tournées qui vous sont affectées, leurs points de collecte et votre progression enregistrée.',
    ),
    _OnboardingSlide(
      icon: Icons.route_outlined,
      label: 'UN PARCOURS À SUIVRE',
      title: 'Le prochain arrêt,\ntoujours à portée de main',
      subtitle:
          'Suivez l’ordre prévu, ouvrez la navigation vers le prochain point, puis retrouvez votre tournée jusqu’au retour au dépôt.',
    ),
    _OnboardingSlide(
      icon: Icons.add_a_photo_outlined,
      label: 'LE SUIVI SUR LE TERRAIN',
      title: 'Chaque collecte\ngarde une trace',
      subtitle:
          'Ajoutez une photo après la collecte. Si un point est inaccessible ou dangereux, signalez le problème et poursuivez votre suivi.',
    ),
    _OnboardingSlide(
      icon: Icons.forum_outlined,
      label: 'VOTRE ASSISTANT',
      title: 'Des réponses\navec leurs sources',
      subtitle:
          'Posez vos questions sur les procédures. L’assistant s’appuie sur les documents approuvés et indique quand les sources ne suffisent pas.',
    ),
  ];

  Future<void> _finishOnboarding() async {
    if (_finishing) return;
    setState(() => _finishing = true);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('onboarding_completed', true);
    if (!mounted) return;
    Navigator.pushReplacementNamed(context, '/login');
  }

  void _nextPage() {
    if (_currentPage < _slides.length - 1) {
      _controller.nextPage(
        duration: const Duration(milliseconds: 250),
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
    final text = Theme.of(context).textTheme;
    return Scaffold(
      backgroundColor: GestiTheme.bg,
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 680),
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(24, 12, 12, 0),
                  child: Row(children: [
                    const Icon(Icons.local_shipping_rounded,
                        color: GestiTheme.primaryDark, size: 26),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text('GestiCollecte',
                          style: text.titleMedium
                              ?.copyWith(color: GestiTheme.navy)),
                    ),
                    TextButton(
                      onPressed: _finishing ? null : _finishOnboarding,
                      child: const Text('Passer'),
                    ),
                  ]),
                ),
                Expanded(
                  child: PageView.builder(
                    controller: _controller,
                    onPageChanged: (i) => setState(() => _currentPage = i),
                    itemCount: _slides.length,
                    itemBuilder: (context, index) =>
                        _SlideContent(slide: _slides[index]),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(24, 12, 24, 24),
                  child: Column(children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Semantics(
                          liveRegion: true,
                          child: Text(
                            'Étape ${_currentPage + 1} sur ${_slides.length}',
                            style: text.bodySmall,
                          ),
                        ),
                        ExcludeSemantics(
                          child: Row(
                            children: List.generate(
                              _slides.length,
                              (i) => AnimatedContainer(
                                duration: const Duration(milliseconds: 200),
                                margin: const EdgeInsets.only(left: 6),
                                width: i == _currentPage ? 24 : 8,
                                height: 6,
                                decoration: BoxDecoration(
                                  color: i == _currentPage
                                      ? GestiTheme.primaryDark
                                      : GestiTheme.border,
                                  borderRadius: BorderRadius.circular(4),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 20),
                    Row(children: [
                      if (_currentPage > 0) ...[
                        OutlinedButton(
                          onPressed: _finishing
                              ? null
                              : () => _controller.previousPage(
                                    duration: const Duration(milliseconds: 250),
                                    curve: Curves.easeInOut,
                                  ),
                          child: const Text('Retour'),
                        ),
                        const SizedBox(width: 12),
                      ],
                      Expanded(
                        child: SizedBox(
                          height: 52,
                          child: FilledButton.icon(
                            onPressed: _finishing ? null : _nextPage,
                            icon: const Icon(Icons.arrow_forward_rounded,
                                size: 20),
                            label: Text(
                              _currentPage == _slides.length - 1
                                  ? 'Commencer'
                                  : 'Suivant',
                            ),
                          ),
                        ),
                      ),
                    ]),
                  ]),
                ),
              ],
            ),
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
    final text = Theme.of(context).textTheme;
    return LayoutBuilder(builder: (context, constraints) {
      return SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 28),
        child: ConstrainedBox(
          constraints: BoxConstraints(
              minHeight:
                  (constraints.maxHeight - 56).clamp(0, double.infinity)),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 104,
                height: 104,
                decoration: BoxDecoration(
                  color: GestiTheme.soft,
                  borderRadius: BorderRadius.circular(28),
                  border: Border.all(color: GestiTheme.border),
                ),
                child:
                    Icon(slide.icon, size: 46, color: GestiTheme.primaryDark),
              ),
              const SizedBox(height: 32),
              Text(slide.label,
                  style: text.labelSmall?.copyWith(
                    color: GestiTheme.primaryDark,
                    letterSpacing: 1.2,
                    fontWeight: FontWeight.w700,
                  )),
              const SizedBox(height: 12),
              Text(slide.title,
                  style: text.headlineLarge?.copyWith(color: GestiTheme.navy)),
              const SizedBox(height: 20),
              Text(slide.subtitle,
                  style: text.bodyLarge?.copyWith(color: GestiTheme.muted)),
            ],
          ),
        ),
      );
    });
  }
}

class _OnboardingSlide {
  final IconData icon;
  final String label;
  final String title;
  final String subtitle;

  const _OnboardingSlide({
    required this.icon,
    required this.label,
    required this.title,
    required this.subtitle,
  });
}
