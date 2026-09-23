import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../core/app_theme.dart';
import '../services/auth_service.dart';
import '../services/api_service.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _isLoading = false;
  bool _obscurePassword = true;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    if (_isLoading || !_formKey.currentState!.validate()) return;
    setState(() => _isLoading = true);
    HapticFeedback.lightImpact();

    final success = await AuthService.login(
      _emailController.text.trim(),
      _passwordController.text,
    );

    if (!mounted) return;
    setState(() => _isLoading = false);
    if (success) {
      TextInput.finishAutofillContext();
      Navigator.pushReplacementNamed(context, '/home');
    } else {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(AuthService.lastError ?? 'Connexion impossible'),
        backgroundColor: Theme.of(context).colorScheme.error,
      ));
    }
  }

  Future<void> _configureServer() async {
    final controller =
        TextEditingController(text: ApiService.instance.options.baseUrl);
    final formKey = GlobalKey<FormState>();
    final value = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Serveur GestiCollecte'),
        content: SizedBox(
          width: 400,
          child: Form(
            key: formKey,
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              const Text(
                'Sur un téléphone, utilisez l’adresse réseau du serveur accessible depuis cet appareil.',
              ),
              const SizedBox(height: 20),
              TextFormField(
                controller: controller,
                keyboardType: TextInputType.url,
                autocorrect: false,
                decoration: const InputDecoration(
                  labelText: 'Adresse de l’API',
                  hintText: 'http://192.168.1.10:8080/api',
                ),
                validator: (v) =>
                    (v == null || v.trim().isEmpty) ? 'Adresse requise' : null,
              ),
            ]),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Annuler'),
          ),
          FilledButton(
            onPressed: () {
              if (formKey.currentState!.validate()) {
                Navigator.pop(ctx, controller.text);
              }
            },
            child: const Text('Enregistrer'),
          ),
        ],
      ),
    );
    if (value == null) return;
    try {
      await AuthService.logout();
      await ApiService.configureServer(value);
      if (mounted) setState(() {});
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(ApiService.errorMessage(error))),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: GestiTheme.bg,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 32),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 1000),
              child: LayoutBuilder(builder: (context, constraints) {
                final wide = constraints.maxWidth >= 820;
                if (wide) {
                  return Row(
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: [
                      const Expanded(child: _LoginWelcome()),
                      const SizedBox(width: 56),
                      Expanded(child: _buildForm(context)),
                    ],
                  );
                }
                return ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 460),
                  child: Column(
                    children: [
                      const _LoginBrand(),
                      const SizedBox(height: 28),
                      _buildForm(context),
                    ],
                  ),
                );
              }),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildForm(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: GestiTheme.border),
        borderRadius: BorderRadius.circular(20),
      ),
      child: AutofillGroup(
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Connexion', style: text.headlineMedium),
              const SizedBox(height: 8),
              Text(
                'Retrouvez vos tournées avec vos identifiants de chauffeur.',
                style: text.bodyMedium?.copyWith(color: GestiTheme.muted),
              ),
              const SizedBox(height: 28),
              TextFormField(
                controller: _emailController,
                enabled: !_isLoading,
                keyboardType: TextInputType.emailAddress,
                textInputAction: TextInputAction.next,
                autofillHints: const [AutofillHints.username],
                autocorrect: false,
                decoration: const InputDecoration(
                  labelText: 'Identifiant ou e-mail',
                  prefixIcon: Icon(Icons.person_outline_rounded),
                ),
                validator: (v) => v == null || v.trim().isEmpty
                    ? 'Saisissez votre identifiant ou votre e-mail'
                    : null,
              ),
              const SizedBox(height: 18),
              TextFormField(
                controller: _passwordController,
                enabled: !_isLoading,
                obscureText: _obscurePassword,
                textInputAction: TextInputAction.done,
                autofillHints: const [AutofillHints.password],
                enableSuggestions: false,
                autocorrect: false,
                onFieldSubmitted: (_) => _handleLogin(),
                decoration: InputDecoration(
                  labelText: 'Mot de passe',
                  prefixIcon: const Icon(Icons.lock_outline_rounded),
                  suffixIcon: IconButton(
                    tooltip: _obscurePassword
                        ? 'Afficher le mot de passe'
                        : 'Masquer le mot de passe',
                    onPressed: _isLoading
                        ? null
                        : () => setState(
                            () => _obscurePassword = !_obscurePassword),
                    icon: Icon(_obscurePassword
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined),
                  ),
                ),
                validator: (v) => v == null || v.isEmpty
                    ? 'Saisissez votre mot de passe'
                    : null,
              ),
              const SizedBox(height: 26),
              SizedBox(
                width: double.infinity,
                height: 52,
                child: FilledButton(
                  onPressed: _isLoading ? null : _handleLogin,
                  child: _isLoading
                      ? const Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            ),
                            SizedBox(width: 12),
                            Text('Connexion en cours…'),
                          ],
                        )
                      : const Text('Se connecter'),
                ),
              ),
              const SizedBox(height: 20),
              const Divider(),
              const SizedBox(height: 8),
              Center(
                child: TextButton.icon(
                  onPressed: _isLoading ? null : _configureServer,
                  icon: const Icon(Icons.settings_ethernet_rounded, size: 18),
                  label: const Text('Configurer le serveur'),
                ),
              ),
              Text(
                'Un problème d’accès ? Contactez votre superviseur.',
                textAlign: TextAlign.center,
                style: text.bodySmall,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LoginBrand extends StatelessWidget {
  const _LoginBrand();

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 52,
          height: 52,
          decoration: BoxDecoration(
            color: GestiTheme.primaryDark,
            borderRadius: BorderRadius.circular(14),
          ),
          child: const Icon(Icons.local_shipping_rounded,
              color: Colors.white, size: 28),
        ),
        const SizedBox(width: 14),
        Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('GestiCollecte',
              style: text.titleLarge?.copyWith(color: GestiTheme.navy)),
          Text('Espace Chauffeur', style: text.bodySmall),
        ]),
      ],
    );
  }
}

class _LoginWelcome extends StatelessWidget {
  const _LoginWelcome();

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _LoginBrand(),
        const SizedBox(height: 48),
        Text('Votre tournée.\nUne étape à la fois.',
            style: text.headlineLarge?.copyWith(color: GestiTheme.navy)),
        const SizedBox(height: 16),
        Text(
          'Du départ au retour au dépôt, gardez un parcours clair et le suivi de chaque point de collecte.',
          style: text.bodyLarge?.copyWith(color: GestiTheme.muted),
        ),
        const SizedBox(height: 28),
        ...[
          (
            Icons.route_outlined,
            'Retrouver votre parcours et le prochain arrêt'
          ),
          (
            Icons.camera_alt_outlined,
            'Enregistrer la collecte ou signaler un obstacle'
          ),
          (Icons.forum_outlined, 'Consulter les procédures avec leurs sources'),
        ].map((item) => Padding(
              padding: const EdgeInsets.only(bottom: 18),
              child:
                  Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Icon(item.$1, color: GestiTheme.primaryDark, size: 22),
                const SizedBox(width: 12),
                Expanded(child: Text(item.$2, style: text.bodyMedium)),
              ]),
            )),
      ],
    );
  }
}
