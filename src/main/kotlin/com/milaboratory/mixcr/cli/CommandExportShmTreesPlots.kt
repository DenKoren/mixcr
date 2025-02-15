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

import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.postanalysis.plots.AlignmentOption
import com.milaboratory.mixcr.postanalysis.plots.DefaultMeta
import com.milaboratory.mixcr.postanalysis.plots.SeqPattern
import com.milaboratory.mixcr.postanalysis.plots.ShmTreePlotter
import com.milaboratory.mixcr.postanalysis.plots.TreeFilter
import io.repseq.core.GeneFeature
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Command(
    description = ["Visualize SHM tree and save in PDF format"]
)
class CommandExportShmTreesPlots : CommandExportShmTreesAbstract() {
    @Parameters(
        index = "1",
        description = ["Path where to write PDF file with plots."],
        paramLabel = "plots.pdf"
    )
    lateinit var out: Path

    @set:Option(
        names = ["--metadata", "-m"],
        description = [
            "Path to metadata file",
            "Metadata should be a .tsv or .csv file with a column named 'sample' with filenames of .clns files used in findShmTrees"
        ],
        paramLabel = "<path>"
    )
    var metadata: Path? = null
        set(value) {
            ValidationException.requireXSV(value)
            //TODO validate content
            field = value
        }

    @set:Option(
        names = ["--filter-min-nodes"],
        description = ["Minimal number of nodes in tree"],
        paramLabel = "<n>"
    )
    var minNodes: Int? = null
        set(value) {
            ValidationException.require(value == null || value > 0) { "value must be greater then 0" }
            field = value
        }

    @set:Option(
        names = ["--filter-min-height"],
        description = ["Minimal height of the tree "],
        paramLabel = "<n>"
    )
    var minHeight: Int? = null
        set(value) {
            ValidationException.require(value == null || value > 0) { "value must be greater then 0" }
            field = value
        }

    @Option(
        names = ["--ids"],
        description = ["Filter specific trees by id"],
        split = ",",
        paramLabel = "<id>"
    )
    var treeIds: Set<Int>? = null

    class PatternOptions {
        class PatternChoice {
            @Option(
                names = ["--filter-aa-pattern"],
                description = ["Filter specific trees by aa pattern."],
                paramLabel = "<pattern>"
            )
            var seqAa: String? = null

            @Option(
                names = ["--filter-nt-pattern"],
                description = ["Filter specific trees by nt pattern."],
                paramLabel = "<pattern>"
            )
            var seqNt: String? = null
        }

        @ArgGroup(multiplicity = "1", exclusive = true)
        lateinit var pattern: PatternChoice

        @Option(
            names = ["--filter-in-feature"],
            description = ["Match pattern inside specified gene feature."],
            paramLabel = Labels.GENE_FEATURE,
            defaultValue = "CDR3",
            showDefaultValue = ALWAYS
        )
        lateinit var inFeature: GeneFeature

        @Option(
            names = ["--pattern-max-errors"],
            description = ["Max allowed subs & indels."],
            paramLabel = "<n>",
            showDefaultValue = ALWAYS,
            defaultValue = "0"
        )
        var maxErrors: Int = 0
    }

    @ArgGroup(
        heading = "Filter by pattern\n",
        exclusive = false
    )
    var patternOptions: PatternOptions? = null

    @set:Option(
        names = ["--limit"],
        description = ["Take first N trees (for debug purposes)"],
        hidden = true,
        paramLabel = "<n>"
    )
    var limit: Int? = null
        set(value) {
            ValidationException.require(value == null || value > 0) { "value must be greater then 0" }
            field = value
        }

    @Option(
        names = ["--node-color"],
        description = ["Color nodes with given metadata column"],
        paramLabel = "<meta>"
    )
    var nodeColor: String? = null

    @Option(
        names = ["--line-color"],
        description = ["Color lines with given metadata column"],
        paramLabel = "<meta>"
    )
    var lineColor: String? = null

    @Option(
        names = ["--node-size"],
        description = ["Size nodes with given metadata column. Predefined columns: \"${DefaultMeta.Abundance}\"."],
        paramLabel = "<meta>",
        showDefaultValue = ALWAYS
    )
    var nodeSize: String = DefaultMeta.Abundance

    @Option(
        names = ["--node-label"],
        description = ["Label nodes with given metadata column. Predefined columns: \"${DefaultMeta.Isotype}\""],
        paramLabel = "<meta>"
    )
    var nodeLabel: String? = null


    @Option(
        names = ["--alignment-nt"],
        description = ["Show tree nucleotide alignments using specified gene feature"],
        paramLabel = Labels.GENE_FEATURE
    )
    var alignmentGeneFeatureNt: GeneFeature? = null

    @Option(
        names = ["--alignment-aa"],
        description = ["Show tree amino acid alignments using specified gene feature"],
        paramLabel = Labels.GENE_FEATURE
    )
    var alignmentGeneFeatureAa: GeneFeature? = null

    @Option(
        names = ["--alignment-no-fill"],
        description = ["Do not highlight alignments with color"]
    )
    var noAlignmentFill: Boolean = false

    override val inputFiles: List<Path>
        get() = listOfNotNull(input, metadata)

    override val outputFiles
        get() = listOf(out)

    override fun validate() {
        super.validate()
        ValidationException.requireExtension("Output file should have", out, "pdf")
    }

    val alignment by lazy {
        if (alignmentGeneFeatureAa == null && alignmentGeneFeatureNt == null)
            null
        else if (alignmentGeneFeatureAa != null)
            AlignmentOption(alignmentGeneFeatureAa!!, true, !noAlignmentFill)
        else
            AlignmentOption(alignmentGeneFeatureNt!!, false, !noAlignmentFill)
    }

    private val pattern by lazy {
        patternOptions?.let { options ->
            if (options.pattern.seqNt != null)
                SeqPattern(options.pattern.seqNt!!, false, options.inFeature, options.maxErrors)
            else
                SeqPattern(options.pattern.seqAa!!, true, options.inFeature, options.maxErrors)
        }
    }

    private val filter by lazy {
        if (minNodes == null && minHeight == null && treeIds == null && pattern == null)
            null
        else
            TreeFilter(
                minNodes = minNodes,
                minHeight = minHeight,
                treeIds = treeIds,
                seqPattern = pattern
            )
    }

    override fun run0() {
        val plots = ShmTreePlotter(
            input.toAbsolutePath(),
            metadata?.toAbsolutePath(),
            filter = filter,
            limit = limit,
            nodeColor = nodeColor,
            lineColor = lineColor,
            nodeSize = nodeSize,
            nodeLabel = nodeLabel,
            alignment = alignment
        ).plots

        out.toAbsolutePath().parent.createDirectories()
        writePDF(out.toAbsolutePath(), plots)
    }
}
