package com.hiveapp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithStructureTests {

    ApplicationModules modules = ApplicationModules.of(HiveAppApplication.class);

    @Test
    void verifyModularStructure() {
        modules.verify();
    }

    @Test
    void generateModuleDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
