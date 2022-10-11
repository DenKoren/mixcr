/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.cli

import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.cli.ParamsResolver
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.MiXCRParamsSpec
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.nio.file.Paths

object CommandExportPreset {
    const val COMMAND_NAME = "exportPreset"

    @Command(
        name = COMMAND_NAME,
        sortOptions = false,
        description = ["Export a preset file given the preset name and a set of mix-ins"]
    )
    class Cmd : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<Unit> {
        @Parameters(
            arity = "1..2",
            hideParamSyntax = true,
            description = ["preset_name preset_file.(yaml|yml)"]
        )
        private val inOut: List<String> = mutableListOf()

        private val presetName get() = inOut[0]
        private val outputFile get() = if (inOut.size == 1) null else Paths.get(inOut[1])

        override val inputFiles get() = mutableListOf<Path>()

        override val outputFiles get() = outputFile?.let { mutableListOf(it) } ?: mutableListOf()

        @ArgGroup(validate = false, heading = "Analysis mix-ins")
        var mixins: AllMiXCRMixins? = null

        override fun run0() {
            val (bundle, _) = paramsResolver.resolve(
                MiXCRParamsSpec(presetName, mixins = mixins?.mixins ?: emptyList()),
                printParameters = false
            )
            val of = outputFile
            if (of != null)
                K_YAML_OM.writeValue(of.toFile(), bundle)
            else
                K_YAML_OM.writeValue(System.out, bundle)
        }

        override val paramsResolver: ParamsResolver<MiXCRParamsBundle, Unit>
            get() = object : MiXCRParamsResolver<Unit>(MiXCRParamsBundle::exportPreset) {
                override fun POverridesBuilderOps<Unit>.paramsOverrides() {
                }
            }
    }
}
