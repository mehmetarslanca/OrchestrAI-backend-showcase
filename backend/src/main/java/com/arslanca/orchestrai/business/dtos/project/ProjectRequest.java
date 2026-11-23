package com.arslanca.orchestrai.business.dtos.project;

import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
                @NotBlank(message = "Proje adı boş olamaz") @Size(max = 200, message = "Proje adı en fazla 200 karakter olabilir") String projectName,

                @NotBlank(message = "IDE hedefi belirtilmelidir") String ideTarget,

                @NotBlank(message = "Prompt boş olamaz") @Size(max = 5000, message = "Prompt en fazla 5.000 karakter olabilir") String prompt,

                @Size(max = 120, message = "Heavy model anahtari en fazla 120 karakter olabilir") String heavyModelKey,

                CredentialSource heavyModelAuthSource) {

        public ProjectRequest(
                        final String projectName,
                        final String ideTarget,
                        final String prompt,
                        final String heavyModelKey) {
                this(projectName, ideTarget, prompt, heavyModelKey, null);
        }
}
