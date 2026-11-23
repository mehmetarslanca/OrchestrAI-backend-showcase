package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;

public interface StarterSelectionResolver {

    StarterSelection resolveSelectedStarter(StarterSelection originalSelection, String requestedStarterKey);
}
