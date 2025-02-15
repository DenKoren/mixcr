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
package com.milaboratory.mixcr.cli.qc

import com.milaboratory.miplots.ExportType
import com.milaboratory.miplots.writeFile
import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.cli.MiXCRCommandWithOutputs
import com.milaboratory.mixcr.qc.Coverage.coveragePlot
import com.milaboratory.primitivio.flatten
import com.milaboratory.primitivio.mapInParallelOrdered
import com.milaboratory.primitivio.port
import com.milaboratory.primitivio.toList
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.nio.file.Paths

@Command(description = ["Reads coverage plots."])
class CommandExportQcCoverage : MiXCRCommandWithOutputs() {
    companion object {
        private const val inputsLabel = "sample.vdjca..."

        private const val outputLabel = "coverage.(pdf|eps|png|jpeg)"

        fun mkCommandSpec(): CommandSpec = CommandSpec.forAnnotatedObject(CommandExportQcCoverage::class.java)
            .addPositional(
                CommandLine.Model.PositionalParamSpec.builder()
                    .index("0")
                    .required(false)
                    .arity("0..*")
                    .type(Path::class.java)
                    .paramLabel(inputsLabel)
                    .hideParamSyntax(true)
                    .description("Paths to input files")
                    .build()
            )
            .addPositional(
                CommandLine.Model.PositionalParamSpec.builder()
                    .index("1")
                    .required(false)
                    .arity("0..*")
                    .type(Path::class.java)
                    .paramLabel(outputLabel)
                    .hideParamSyntax(true)
                    .description("Path where to write output plots")
                    .build()
            )
    }


    @Parameters(
        index = "0",
        arity = "2..*",
        paramLabel = "$inputsLabel $outputLabel",
        hideParamSyntax = true,
        //help is covered by mkCommandSpec
        hidden = true
    )
    var inOut: List<Path> = mutableListOf()

    @Option(names = ["--show-boundaries"], description = ["Show V alignment begin and J alignment end"])
    var showAlignmentBoundaries = false

    override val inputFiles
        get() = inOut.subList(0, inOut.size - 1)

    override val outputFiles
        get() = listOf(inOut.last())

    override fun run0() {
        val inputFiles = inputFiles.map { it }
        val plots = inputFiles.port
            .mapInParallelOrdered(Runtime.getRuntime().availableProcessors()) {
                coveragePlot(it, showAlignmentBoundaries)
            }
            .flatten()
            .toList()
        val out = outputFiles.first()
        if (ExportType.determine(out) === ExportType.PDF) {
            writePDF(out, plots)
        } else {
            plots.forEachIndexed { i, plt ->
                var outStr = outputFiles.first().toString()
                val l = outStr.lastIndexOf(".")
                val suffix = if (i < 3) "R$i" else "Overlap"
                outStr = "${outStr.substring(0, l)}_$suffix${outStr.substring(l)}"
                writeFile(Paths.get(outStr), plt)
            }
        }
    }
}
