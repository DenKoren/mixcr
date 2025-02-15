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

package com.milaboratory.mixcr.cli.postanalysis

import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.cli.MiXCRCommand
import com.milaboratory.mixcr.postanalysis.preproc.ChainsFilter
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

/**
 *
 */
abstract class CommandPaExport : MiXCRCommand {
    @Parameters(
        description = ["Input file with postanalysis results."],
        paramLabel = "pa.json[.gz]",
        index = "0",
    )
    lateinit var input: Path

    @Option(
        description = ["Export only for specified immunological chains."],
        names = ["--chains"],
        paramLabel = Labels.CHAIN
    )
    var chains: Set<String>? = null

    private val parsedPaResultFromInput: PaResult by lazy {
        PaResult.readJson(input.toAbsolutePath())
    }

    private val paResultFromConstructor: PaResult?

    constructor() {
        paResultFromConstructor = null
    }

    /** Constructor used to export tables from code  */
    internal constructor(paResult: PaResult) {
        this.paResultFromConstructor = paResult
    }

    val inputFiles
        get() = listOf(input)

    /**
     * Get full PA result
     */
    protected fun getPaResult(): PaResult = paResultFromConstructor ?: parsedPaResultFromInput

    override fun run0() {
        val chainsToProcess = chains?.run { ChainsFilter.parseChainsList(chains) }
        for (r in getPaResult().results) {
            if (chainsToProcess == null || chainsToProcess.contains(r.group.chains)) {
                run(r)
            }
        }
    }

    abstract fun run(result: PaResultByGroup)
}
