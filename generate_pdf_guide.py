import os
from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, HRFlowable
)
from reportlab.pdfgen import canvas

class NumberedCanvas(canvas.Canvas):
    def __init__(self, *args, **kwargs):
        super(NumberedCanvas, self).__init__(*args, **kwargs)
        self._saved_page_states = []

    def showPage(self):
        self._saved_page_states.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        num_pages = len(self._saved_page_states)
        for state in self._saved_page_states:
            self.__dict__.update(state)
            self.draw_page_decorations(num_pages)
            super(NumberedCanvas, self).showPage()
        super(NumberedCanvas, self).save()

    def draw_page_decorations(self, page_count):
        self.saveState()
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#64748B"))
        
        # Header (pages > 1)
        if self._pageNumber > 1:
            self.drawString(45, 810, "CleanFlow — Guide Opérationnel du Chauffeur & Base RAG de l'Assistant IA")
            self.setStrokeColor(colors.HexColor("#CBD5E1"))
            self.setLineWidth(0.5)
            self.line(45, 803, 550, 803)
            
        # Footer
        self.setStrokeColor(colors.HexColor("#CBD5E1"))
        self.setLineWidth(0.5)
        self.line(45, 42, 550, 42)
        self.drawString(45, 30, "CleanFlow Municipalité — Référentiel des Opérations & Assistant Intelligent")
        self.drawRightString(550, 30, f"Page {self._pageNumber} sur {page_count}")
        self.restoreState()

def build_pdf(filename="GUIDE_CHAUFFEUR_CLEANFLOW.pdf"):
    doc = SimpleDocTemplate(
        filename,
        pagesize=A4,
        leftMargin=45,
        rightMargin=45,
        topMargin=48,
        bottomMargin=48
    )

    styles = getSampleStyleSheet()
    
    primary_color = colors.HexColor("#0F766E")   # Emerald/Teal
    secondary_color = colors.HexColor("#0369A1") # Sky/Blue
    dark_text = colors.HexColor("#1E293B")
    muted_text = colors.HexColor("#475569")
    light_bg = colors.HexColor("#F8FAFC")
    border_color = colors.HexColor("#CBD5E1")

    title_style = ParagraphStyle(
        'DocTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=18,
        leading=22,
        textColor=primary_color,
        alignment=1,
        spaceAfter=4
    )

    subtitle_style = ParagraphStyle(
        'DocSubtitle',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=10,
        leading=14,
        textColor=muted_text,
        alignment=1,
        spaceAfter=10
    )

    h1_style = ParagraphStyle(
        'H1',
        parent=styles['Heading1'],
        fontName='Helvetica-Bold',
        fontSize=11.5,
        leading=15,
        textColor=primary_color,
        spaceBefore=8,
        spaceAfter=4,
        keepWithNext=True
    )

    h2_style = ParagraphStyle(
        'H2',
        parent=styles['Heading2'],
        fontName='Helvetica-Bold',
        fontSize=9.5,
        leading=13,
        textColor=secondary_color,
        spaceBefore=5,
        spaceAfter=3,
        keepWithNext=True
    )

    body_style = ParagraphStyle(
        'Body',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8.5,
        leading=12.5,
        textColor=dark_text,
        spaceAfter=3
    )

    bullet_style = ParagraphStyle(
        'Bullet',
        parent=body_style,
        leftIndent=12,
        firstLineIndent=-8,
        spaceAfter=2.5
    )

    badge_style = ParagraphStyle(
        'Badge',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=8.5,
        leading=11,
        textColor=colors.white,
        alignment=1
    )

    table_header_style = ParagraphStyle(
        'TableHeader',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=8,
        leading=11,
        textColor=colors.white
    )

    table_cell_style = ParagraphStyle(
        'TableCell',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=7.8,
        leading=11,
        textColor=dark_text
    )

    table_cell_bold = ParagraphStyle(
        'TableCellBold',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=7.8,
        leading=11,
        textColor=primary_color
    )

    story = []

    # ================= PAGE 1 =================
    title_data = [
        [Paragraph("CLEANFLOW &bull; GESTION MUNICIPALE DES TOURNÉES", badge_style)],
        [Paragraph("Guide Opérationnel du Chauffeur & Base RAG de l'Assistant IA", title_style)],
        [Paragraph("Manuel de procédures de collecte, gestion des incidents et référentiel de questions de l'IA", subtitle_style)]
    ]
    title_table = Table(title_data, colWidths=[505])
    title_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), light_bg),
        ('ALIGN', (0,0), (-1,-1), 'CENTER'),
        ('BOX', (0,0), (-1,-1), 1, colors.HexColor("#0D9488")),
        ('TOPPADDING', (0,0), (-1,0), 4),
        ('BOTTOMPADDING', (0,0), (-1,0), 4),
        ('BACKGROUND', (0,0), (-1,0), primary_color),
        ('ALIGN', (0,0), (-1,0), 'CENTER'),
        ('BOTTOMPADDING', (0,-1), (-1,-1), 6),
    ]))
    story.append(title_table)
    story.append(Spacer(1, 6))

    intro_p = Paragraph(
        "<b>Portée et utilité du document :</b> Ce guide est le manuel de référence des chauffeurs municipaux CleanFlow. Il régit chaque phase opérationnelle : prise de service, vérifications du camion, vidage des bacs, signalement des anomalies et clôture de tournée. <b>Il alimente directement le module RAG (Retrieval-Augmented Generation)</b>, permettant à l'Assistant IA d'apporter des réponses fiables et conformes en <b>Français</b> et en <b>Derja Tunisienne</b>.",
        body_style
    )
    intro_table = Table([[intro_p]], colWidths=[505])
    intro_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor("#F0FDFA")),
        ('BOX', (0,0), (-1,-1), 0.8, colors.HexColor("#99F6E4")),
        ('PADDING', (0,0), (-1,-1), 6),
    ]))
    story.append(intro_table)
    story.append(Spacer(1, 6))

    # SECTION 1
    story.append(Paragraph("1. Prise de Service, Contrôles Véhicule & Authentification", h1_style))
    story.append(HRFlowable(width="100%", thickness=0.8, color=primary_color, spaceAfter=4))
    
    story.append(Paragraph("<b>1.1 Connexion à l'application mobile CleanFlow :</b>", h2_style))
    story.append(Paragraph("&bull; <b>Identifiant de connexion :</b> Le chauffeur peut se connecter soit avec son <b>numéro de CIN</b> (8 chiffres, ex: 09845123), soit avec son <b>matricule d'agent</b> (ex: 222LMT105), soit avec son <b>e-mail professionnel</b>.", bullet_style))
    story.append(Paragraph("&bull; <b>Mot de passe :</b> Par défaut à l'embauche, le mot de passe initial correspond au <b>numéro de CIN</b> du chauffeur.", bullet_style))
    story.append(Paragraph("&bull; <b>Configuration réseau :</b> Si le téléphone est en Wi-Fi municipal, utiliser le bouton <i>« Configurer le serveur »</i> pour saisir l'adresse IP de l'API (ex: http://192.168.1.50:8080/api).", bullet_style))

    story.append(Paragraph("<b>1.2 Contrôles obligatoires avant départ (Ronde de sécurité véhicule) :</b>", h2_style))
    story.append(Paragraph("&bull; <b>Niveaux et pneumatiques :</b> Contrôle visuel de la pression des pneus (aucun caillou coincé dans le jumelage), niveau d'huile moteur, liquide de refroidissement et réservoir de gasoil (&ge; 50%).", bullet_style))
    story.append(Paragraph("&bull; <b>Signaux de sécurité :</b> Vérification du gyrophare orange, des feux de détresse, de l'avertisseur sonore de recul et de la caméra de recul.", bullet_style))
    story.append(Paragraph("&bull; <b>Système de benne :</b> Test à vide du lève-conteneur hydraulique et du cycle de compression de la trémie.", bullet_style))
    story.append(Paragraph("&bull; <b>Équipements de Protection Individuelle (EPI) obligatoires :</b> Gilet haute visibilité classe 3, gants de manutention renforcés et chaussures de sécurité coquées.", bullet_style))

    # SECTION 2
    story.append(Paragraph("2. Démarrage de Tournée & Navigation GPS Active", h1_style))
    story.append(HRFlowable(width="100%", thickness=0.8, color=primary_color, spaceAfter=4))
    story.append(Paragraph("<b>2.1 Consultation et départ :</b>", h2_style))
    story.append(Paragraph("&bull; L'écran d'accueil affiche la tournée assignée par le superviseur : secteur, camion affecté, nombre de bacs et tonnage prévisionnel.", bullet_style))
    story.append(Paragraph("&bull; Le chauffeur appuie sur <b>« Démarrer la tournée »</b> lors du départ effectif du dépôt municipal pour avertir le centre de supervision.", bullet_style))

    story.append(Paragraph("<b>2.2 Suivi GPS et recalcul automatique :</b>", h2_style))
    story.append(Paragraph("&bull; L'application transmet la position GPS du camion toutes les <b>10 secondes</b> vers le backend municipal.", bullet_style))
    story.append(Paragraph("&bull; L'itinéraire est optimisé par l'IA CleanFlow pour réduire la consommation et éviter les manœuvres dangereuses.", bullet_style))
    story.append(Paragraph("&bull; <b>Déviation :</b> En cas d'obstacle (rue bloquée, travaux), le chauffeur peut dévier ; l'application réorganise les points restants.", bullet_style))

    # SECTION 3
    story.append(Paragraph("3. Opérations sur les Points de Collecte (Bacs & Conteneurs)", h1_style))
    story.append(HRFlowable(width="100%", thickness=0.8, color=primary_color, spaceAfter=4))
    story.append(Paragraph("<b>3.1 Protocole de vidage :</b> Arrêt complet du camion avec frein de parc et feux de détresse. Enclencher les ergots du bac sur le peigne de levage. Reposer le conteneur dans son emplacement sans bloquer le trottoir.", bullet_style))
    story.append(Paragraph("<b>3.2 Règle des photos :</b>", h2_style))
    story.append(Paragraph("&bull; <b>Photo AVANT (Facultative) :</b> Recommandée si le bac présente une anomalie dès votre arrivée (sacs éventrés, dégradation préexistante).", bullet_style))
    story.append(Paragraph("&bull; <b>Photo APRÈS (Obligatoire) :</b> Exigée par CleanFlow pour valider le vidage et certifier la propreté finale du point de collecte.", bullet_style))

    story.append(PageBreak())

    # ================= PAGE 2 =================
    # SECTION 4
    story.append(Paragraph("4. Signalement des Incidents & Réclamations Terrain", h1_style))
    story.append(HRFlowable(width="100%", thickness=0.8, color=primary_color, spaceAfter=4))
    story.append(Paragraph("En cas d'anomalie sur le terrain, le chauffeur utilise sans délai le module <b>« Réclamations »</b> selon la grille officielle :", body_style))

    incident_data = [
        [Paragraph("Incident / Motif", table_header_style), Paragraph("Code & Urgence", table_header_style), Paragraph("Procédure & Conduite à Tenir", table_header_style)],
        [
            Paragraph("<b>Bac Inaccessible</b>", table_cell_bold),
            Paragraph("Code <b>ACCES</b><br/>Urgence : Moyenne", table_cell_style),
            Paragraph("Véhicule stationné devant le bac, grille fermée, route bloquée. Prendre une photo claire de l'obstacle. Ne pas forcer. Le superviseur est alerté pour joindre la police municipale.", table_cell_style)
        ],
        [
            Paragraph("<b>Bac Endommagé / Vandalisé</b>", table_cell_bold),
            Paragraph("Code <b>BAC_CASSE</b><br/>Urgence : Moyenne", table_cell_style),
            Paragraph("Roue arrachée, cuve fendue, couvercle manquant ou bac brûlé. Prendre une photo explicite. Laisser le bac sur place sans le vider s'il risque de se déchirer dans la benne. Remplacement ordonné.", table_cell_style)
        ],
        [
            Paragraph("<b>Débordement / Décharge Sauvage</b>", table_cell_bold),
            Paragraph("Code <b>DEBORDEMENT</b><br/>Urgence : Haute", table_cell_style),
            Paragraph("Sacs au sol ou volume dépassant largement le conteneur. Photographier les abords. Ramasser le surplus si la capacité le permet, sinon demander un passage d'appui.", table_cell_style)
        ],
        [
            Paragraph("<b>Déchets Interdits / Dangereux</b>", table_cell_bold),
            Paragraph("Code <b>NON_CONFORME</b><br/>Refus immédiat", table_cell_style),
            Paragraph("Gravats de chantier, ferraille lourde, solvants chimiques, braises ou batteries. <b>INTERDICTION FORMELLE de charger</b> dans la benne tasseuse (risque de destruction mécanique ou incendie).", table_cell_style)
        ]
    ]

    incident_table = Table(incident_data, colWidths=[120, 95, 290])
    incident_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), primary_color),
        ('ALIGN', (0,0), (-1,-1), 'LEFT'),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('GRID', (0,0), (-1,-1), 0.5, border_color),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, light_bg]),
        ('TOPPADDING', (0,0), (-1,-1), 4.5),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4.5),
    ]))
    story.append(incident_table)
    story.append(Spacer(1, 8))

    # SECTION 5
    story.append(Paragraph("5. Déchargement, Clôture de Tournée & Retour au Dépôt", h1_style))
    story.append(HRFlowable(width="100%", thickness=0.8, color=primary_color, spaceAfter=4))
    story.append(Paragraph("&bull; <b>Centre de transfert / Décharge municipale :</b> Respecter la vitesse limite (20 km/h). Effectuer le passage sur le pont-bascule à l'entrée (poids brut) et à la sortie (tare) pour enregistrer le ticket de tonnage.", bullet_style))
    story.append(Paragraph("&bull; <b>Vidage benne :</b> S'assurer que le périmètre arrière est dégagé de toute personne lors du basculement et du fonctionnement du bouclier d'éjection.", bullet_style))
    story.append(Paragraph("&bull; <b>Nettoyage :</b> Rincer la trémie et la bavette de chargement à la lance haute pression sur l'aire de lavage municipale.", bullet_style))
    story.append(Paragraph("&bull; <b>Clôture informatique :</b> À l'arrivée au dépôt, appuyer sur <b>« Terminer la tournée »</b> dans l'application mobile. Le bilan complet est synchronisé avec le tableau de bord du superviseur.", bullet_style))

    story.append(Spacer(1, 8))

    # SECTION 6
    story.append(Paragraph("6. Consignes de Sécurité, Pannes Mécaniques & Urgences", h1_style))
    story.append(HRFlowable(width="100%", thickness=0.8, color=primary_color, spaceAfter=4))
    story.append(Paragraph("&bull; <b>Panne mécanique :</b> Immobiliser le camion en zone sécurisée, couper le moteur, placer le triangle à 30 mètres et revêtir le gilet haute visibilité. Signaler l'incident au superviseur via l'application et par téléphone.", bullet_style))
    story.append(Paragraph("&bull; <b>Accident de circulation :</b> Assurer d'abord la sécurité des personnes. Si blessé, appeler la <b>Protection Civile (198)</b> ou la <b>Police/Garde Nationale (197)</b>. Remplir le constat amiable avec le superviseur.", bullet_style))
    story.append(Paragraph("&bull; <b>Coupure de batterie du smartphone :</b> Aucune donnée n'est perdue. Dès redémarrage, ouvrir CleanFlow : la tournée reprend exactement au dernier point validé.", bullet_style))

    story.append(PageBreak())

    # ================= PAGE 3 =================
    # SECTION 7 - QUESTIONS FREQUENTES PRISES EN CHARGE PAR L'ASSISTANT IA
    story.append(Paragraph("7. Référentiel des Questions Prises en Charge par l'Assistant IA", h1_style))
    story.append(HRFlowable(width="100%", thickness=0.8, color=primary_color, spaceAfter=4))
    story.append(Paragraph("L'Assistant IA CleanFlow est connecté en temps réel à ce référentiel grâce à son moteur RAG. Le chauffeur peut poser ses questions indifféremment en <b>Français</b>, en <b>Derja Tunisienne (Arabizi)</b> ou en <b>Arabe</b> :", body_style))

    faq_data = [
        [Paragraph("Thématique", table_header_style), Paragraph("Exemple en Derja Tunisienne (Arabizi)", table_header_style), Paragraph("Exemple en Français", table_header_style)],
        [
            Paragraph("<b>Tournée & Ordre</b>", table_cell_bold),
            Paragraph("Kifech najm nchouf el tournee mte3i lyoum w les points eli mazalou ?", table_cell_style),
            Paragraph("Comment consulter ma tournée et la liste des points de collecte restants ?", table_cell_style)
        ],
        [
            Paragraph("<b>Bac cassé / brûlé</b>", table_cell_bold),
            Paragraph("Chnowa naamel ken nal9a bac mkasser walla ma3andouch 3jali ?", table_cell_style),
            Paragraph("Quelle est la procédure exacte face à un bac cassé ou sans roues ?", table_cell_style)
        ],
        [
            Paragraph("<b>Obstacle & Accès</b>", table_cell_bold),
            Paragraph("Fama karhba garia msakra el thneya 9odem el bac, kifech ntsarref ?", table_cell_style),
            Paragraph("Comment signaler un bac dont l'accès est bloqué par un véhicule mal garé ?", table_cell_style)
        ],
        [
            Paragraph("<b>Obligation Photo</b>", table_cell_bold),
            Paragraph("Lazemni dima nekhou taswira mba3d ma nfaragh el bac ?", table_cell_style),
            Paragraph("La prise de photo après vidage est-elle obligatoire pour valider ?", table_cell_style)
        ],
        [
            Paragraph("<b>Gravats & Chantier</b>", table_cell_bold),
            Paragraph("L9it rdem w chanti fel bac, najjem nhezzou fel benne tasseuse ?", table_cell_style),
            Paragraph("Peut-on charger des gravats de démolition dans la benne tasseuse ?", table_cell_style)
        ],
        [
            Paragraph("<b>Panne en tournée</b>", table_cell_bold),
            Paragraph("El benne tbloquét w ma 3adech theb tcompacti, chkoun nkalem ?", table_cell_style),
            Paragraph("Comment déclarer une panne hydraulique survenue en cours de tournée ?", table_cell_style)
        ],
        [
            Paragraph("<b>Extinction Mobile</b>", table_cell_bold),
            Paragraph("El portable tfa w t3awed t7al, bech n3awed el tournee men loul ?", table_cell_style),
            Paragraph("Mes points validés sont-ils conservés si mon téléphone s'éteint brutalement ?", table_cell_style)
        ],
        [
            Paragraph("<b>Pesée & Décharge</b>", table_cell_bold),
            Paragraph("Win lezem n3adi el camion fel bascule 9bal ma nrawa7 lel dépôt ?", table_cell_style),
            Paragraph("Où doit-on effectuer la pesée du chargement avant le retour au dépôt ?", table_cell_style)
        ],
        [
            Paragraph("<b>EPI & Sécurité</b>", table_cell_bold),
            Paragraph("Chnowa houma les EPI eli lezem nelbesshom 9bal ma nokhrej ?", table_cell_style),
            Paragraph("Quels sont les équipements de protection individuelle obligatoires ?", table_cell_style)
        ],
        [
            Paragraph("<b>Accident route</b>", table_cell_bold),
            Paragraph("Sar accident sghir m3a karhba fel thneya, chnowa lezem naamel ?", table_cell_style),
            Paragraph("Quelle est la conduite à tenir en cas d'accrochage ou accident en tournée ?", table_cell_style)
        ]
    ]

    faq_table = Table(faq_data, colWidths=[105, 200, 200])
    faq_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), secondary_color),
        ('ALIGN', (0,0), (-1,-1), 'LEFT'),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('GRID', (0,0), (-1,-1), 0.5, border_color),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, light_bg]),
        ('TOPPADDING', (0,0), (-1,-1), 4.5),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4.5),
    ]))
    story.append(faq_table)
    story.append(Spacer(1, 10))

    # Summary note
    note_p = Paragraph(
        "<b>Note PFE & RAG :</b> Toutes ces situations et questions sont indexées dans la base vectorielle de CleanFlow. Lorsqu'un chauffeur interroge l'assistant depuis l'application mobile, le moteur de recherche documentaire retrouve la procédure correspondante et génère une réponse claire et synthétique dans la langue choisie par l'utilisateur.",
        body_style
    )
    note_table = Table([[note_p]], colWidths=[505])
    note_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor("#FEF3C7")),
        ('BOX', (0,0), (-1,-1), 0.8, colors.HexColor("#FCD34D")),
        ('PADDING', (0,0), (-1,-1), 6),
    ]))
    story.append(note_table)

    doc.build(story, canvasmaker=NumberedCanvas)
    print("SUCCESS: 3-page PDF generated at", filename)

if __name__ == "__main__":
    build_pdf()
