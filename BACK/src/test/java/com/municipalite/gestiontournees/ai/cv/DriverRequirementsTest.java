package com.municipalite.gestiontournees.ai.cv;

import com.municipalite.gestiontournees.ai.cv.model.DriverRequirements;
import com.municipalite.gestiontournees.ai.cv.repository.DriverRequirementsRepository;
import com.municipalite.gestiontournees.ai.cv.service.DriverRequirementsService;
import com.municipalite.gestiontournees.security.AccessService;
import com.municipalite.gestiontournees.user.model.User;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriverRequirementsTest {
    @Test void editingCreatesNewVersionAndRejectsInconsistentWeights() {
        var repository = mock(DriverRequirementsRepository.class);
        var access = mock(AccessService.class);
        when(access.currentUser()).thenReturn(User.builder().id("supervisor").build());
        when(repository.findTopByOrderByVersionDesc()).thenReturn(Optional.of(DriverRequirements.initial()));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new DriverRequirementsService(repository, access);
        var input = DriverRequirements.initial(); input.setExperienceTargetYears(3);
        var saved = service.update(input);
        assertEquals(2, saved.getVersion()); assertNotEquals("driver-profile-v1", saved.getId());
        assertEquals("supervisor", saved.getEditedBy());
        input.setLicenceWeight(90);
        assertThrows(IllegalArgumentException.class, () -> service.update(input));
        verify(repository, times(1)).save(any());
    }
}
