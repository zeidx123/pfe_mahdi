package com.municipalite.gestiontournees.pointcollecte;

import com.municipalite.gestiontournees.pointcollecte.dto.PointCollecteRequest;
import com.municipalite.gestiontournees.pointcollecte.model.PointCollecte;
import com.municipalite.gestiontournees.pointcollecte.model.TypeDechet;
import com.municipalite.gestiontournees.pointcollecte.repository.PointCollecteRepository;
import com.municipalite.gestiontournees.pointcollecte.service.PointCollecteService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PointValidationTest {
    private final PointCollecteRepository points = mock(PointCollecteRepository.class);
    private final PointCollecteService service = new PointCollecteService(points);

    @Test void omittedOptionalValuesArePreservedDuringUpdate() {
        var existing = PointCollecte.builder().id("p1").reference("P1").niveauRemplissage(100)
                .typeDechet(TypeDechet.VERRE).zone("Centre").actif(false).build();
        when(points.findById("p1")).thenReturn(Optional.of(existing));
        when(points.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = validRequest();
        var result = service.update("p1", request);
        assertEquals(100, result.getNiveauRemplissage());
        assertEquals(TypeDechet.VERRE, result.getTypeDechet());
        assertEquals("Centre", result.getZone());
        assertFalse(result.isActif());
        request.setNiveauRemplissage(0);
        assertEquals(0, service.update("p1", request).getNiveauRemplissage());
    }

    @Test void aFillOnlyUpdatePreservesAddressAndCoordinates() {
        var existing = PointCollecte.builder().id("p1").reference("P1").adresse("Centre")
                .latitude(36.8).longitude(10.1).niveauRemplissage(100).actif(true).build();
        when(points.findById("p1")).thenReturn(Optional.of(existing));
        when(points.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new PointCollecteRequest(); request.setNiveauRemplissage(25);
        var result = service.update("p1", request);
        assertEquals("Centre", result.getAdresse());
        assertEquals(36.8, result.getLatitude());
        assertEquals(10.1, result.getLongitude());
        assertEquals(25, result.getNiveauRemplissage());
    }

    @Test void invalidCoordinatesOrFillNeverReachStorage() {
        var request = validRequest(); request.setLatitude(999.0);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        request.setLatitude(Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        PointCollecteRequest invalidFill = validRequest();
        invalidFill.setNiveauRemplissage(101);
        assertThrows(IllegalArgumentException.class, () -> service.create(invalidFill));
        verifyNoInteractions(points);
    }

    private PointCollecteRequest validRequest() {
        var request = new PointCollecteRequest();
        request.setReference("P1"); request.setAdresse("Rue de test");
        request.setLatitude(36.8); request.setLongitude(10.1);
        return request;
    }
}
