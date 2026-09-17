package com.municipalite.gestiontournees.dashboard.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStats {
    private long agentsActifs;
    private long camionsTotal;
    private long camionsEnTournee;
    private long camionsDisponibles;
    private long camionsEnMaintenance;
    private long tourneesEnCours;
    private long alertesCritiques;
}
