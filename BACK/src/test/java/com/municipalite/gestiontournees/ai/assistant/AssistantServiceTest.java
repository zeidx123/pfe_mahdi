package com.municipalite.gestiontournees.ai.assistant;

import com.municipalite.gestiontournees.ai.assistant.repository.ChatConversationRepository;
import com.municipalite.gestiontournees.ai.assistant.service.AssistantService;
import com.municipalite.gestiontournees.ai.assistant.service.tools.AssistantTools;
import com.municipalite.gestiontournees.ai.common.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantServiceTest {
    @Test void providerFailureDoesNotInventEmergencyProcedure() {
        var gemini = mock(GeminiClient.class); var tools = mock(AssistantTools.class); var histories = mock(ChatConversationRepository.class);
        when(gemini.generateText(anyString())).thenThrow(new AiServiceException("Unavailable"));
        when(tools.buildSystemContext()).thenReturn("Aucune donnée dans cet exercice.");
        when(histories.save(any())).thenAnswer(call -> call.getArgument(0));
        var dialect = new com.municipalite.gestiontournees.ai.assistant.service.TunisianDialectService();
        var conversation = new AssistantService(gemini, tools, histories, dialect).chat(null, "supervisor", "Quelle procédure en cas de panne ?");
        assertThat(conversation.getMessages().get(1).getStatus()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(conversation.getMessages().get(1).getContent()).doesNotContain("triangle", "dépanneuse", "30m");
    }
}
