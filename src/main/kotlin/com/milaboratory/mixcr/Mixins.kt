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
package com.milaboratory.mixcr

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.milaboratory.cli.*
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.cli.CommandAlign
import com.milaboratory.mixcr.cli.CommandAssemble
import com.milaboratory.mixcr.vdjaligners.KGeneAlignmentParameters
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
sealed interface MiXCRMixin : Mixin<MiXCRParamsBundle>, Comparable<MiXCRMixin> {
    /** Returns command line arguments for this mixin */
    val cmdArgs: List<String>

    /** Mixins with higher priority goes first */
    override fun compareTo(other: MiXCRMixin) = other.priority.compareTo(priority)
}

@POverridesBuilderDsl
interface MixinBuilderOps : POverridesBuilderOps<MiXCRParamsBundle> {
    fun dropFlag(flagName: String) = MiXCRParamsBundle::flags.updateBy { it - flagName }
}

//
// Generic helper methods
//

private fun MixinBuilderOps.modifyAlignmentParams(action: VDJCAlignerParameters.() -> Unit) =
    MiXCRParamsBundle::align.update {
        CommandAlign.Params::parameters.applyAfterClone(VDJCAlignerParameters::clone) {
            action()
        }
    }

private fun MixinBuilderOps.modifyGeneAlignmentParams(gt: GeneType, action: KGeneAlignmentParameters.() -> Unit) =
    modifyAlignmentParams {
        val p = getVJCGeneAlignerParameters(gt)
        p.action()
    }

sealed class MiXCRMixinBase(
    @JsonIgnore override val priority: Int,
    @JsonIgnore private val flags: List<String>
) : MiXCRMixin {
    constructor(priority: Int, vararg flags: String) : this(priority, flags.asList())

    abstract fun MixinBuilderOps.action()

    override fun apply(target: MiXCRParamsBundle): MiXCRParamsBundle {
        val overrides = mutableListOf<POverride<MiXCRParamsBundle>>()
        val builderTarget = object : POverridesBuilderOpsAbstract<MiXCRParamsBundle>(), MixinBuilderOps {
            override fun addOverride(override: POverride<MiXCRParamsBundle>) {
                overrides += override
            }
        }
        builderTarget.apply {
            flags.forEach { dropFlag(it) }
            action()
        }
        return overrides.fold(target) { acc, o -> o.apply(acc) }
    }
}

// ==========================
//           Mixins
// ==========================

//
// Generic
//

@JsonTypeName("SetSpecies")
data class SetSpecies(val species: String) : MiXCRMixinBase(50) {
    override fun MixinBuilderOps.action() {
        MiXCRParamsBundle::align.update {
            CommandAlign.Params::species setTo species
        }
    }

    override val cmdArgs get() = listOf(CMD_OPTION, species)

    companion object {
        const val CMD_OPTION = "+species"
    }
}

@JsonTypeName("SetClonotypeAssemblingFeatures")
data class SetClonotypeAssemblingFeatures(val features: GeneFeatures) : MiXCRMixinBase(50) {
    override fun MixinBuilderOps.action() {
        MiXCRParamsBundle::assemble.update {
            CommandAssemble.Params::cloneAssemblerParameters.applyAfterClone(CloneAssemblerParameters::clone) {
                assemblingFeatures = features.features
            }
        }
    }

    override val cmdArgs get() = listOf(CMD_OPTION, features.encode())

    companion object {
        const val CMD_OPTION = "+assembleClonotypesBy"
    }
}

//
// Material Type
//

@JsonTypeName("MaterialTypeDNA")
object MaterialTypeDNA : MiXCRMixinBase(20, Flags.MaterialType) {
    override fun MixinBuilderOps.action() =
        modifyAlignmentParams {
            // Aligning V gene with intron sequence
            vAlignerParameters.geneFeatureToAlign = GeneFeature.VGeneWithP

            // TODO consider adding genomic sequences downstream J ( or add another mix-in for that ? )

            // C gene is too far from CDR3 in DNA data,
            // there is no need in spending time searching for it
            // NOTE: If such behaviour is still needed, other parameters should also be considered and changed,
            // to achieve expected results
            setGeneAlignerParameters(GeneType.Constant, null)
        }

    override val cmdArgs get() = listOf(CMD_OPTION)

    const val CMD_OPTION = "+dna"
}

@JsonTypeName("MaterialTypeRNA")
object MaterialTypeRNA : MiXCRMixinBase(20, Flags.MaterialType) {
    override fun MixinBuilderOps.action() =
        modifyGeneAlignmentParams(GeneType.Variable) {
            // V gene without intron sequence, including 5'UTR sequence
            geneFeatureToAlign = GeneFeature.VTranscriptWithP
        }

    override val cmdArgs get() = listOf(CMD_OPTION)

    const val CMD_OPTION = "+rna"
}

//
// Left Boundary
//

object AlignmentBoundaryMixinConstants {
    const val LEFT_FLOATING_CMD_OPTION = "+floatingLeftAlignmentBoundary"
    const val LEFT_RIGID_CMD_OPTION = "+rigidLeftAlignmentBoundary"
    const val RIGHT_FLOATING_CMD_OPTION = "+floatingRightAlignmentBoundary"
    const val RIGHT_RIGID_CMD_OPTION = "+rigidRightAlignmentBoundary"
}

@JsonTypeName("LeftAlignmentBoundaryNoPoint")
data class LeftAlignmentBoundaryNoPoint(val floating: Boolean) :
    MiXCRMixinBase(10, Flags.LeftAlignmentMode) {
    override fun MixinBuilderOps.action() =
        modifyGeneAlignmentParams(GeneType.Variable) {
            parameters.isFloatingLeftBound = floating
        }

    override val cmdArgs
        get() = listOf(
            if (floating)
                AlignmentBoundaryMixinConstants.LEFT_FLOATING_CMD_OPTION
            else
                AlignmentBoundaryMixinConstants.LEFT_RIGID_CMD_OPTION
        )
}

@JsonTypeName("LeftAlignmentBoundaryWithPoint")
data class LeftAlignmentBoundaryWithPoint(val floating: Boolean, val refPoint: ReferencePoint) :
    MiXCRMixinBase(10, Flags.LeftAlignmentMode) {
    init {
        // Checking parameters
        if (refPoint.geneType != GeneType.Variable)
            throw RuntimeException("$refPoint is not inside the V gene")
    }

    override fun MixinBuilderOps.action() =
        modifyGeneAlignmentParams(GeneType.Variable) {
            if (!geneFeatureToAlign.contains(refPoint))
                throw RuntimeException("Can't apply mixin because $geneFeatureToAlign does not contain $refPoint")
            geneFeatureToAlign = geneFeatureToAlign.splitBy(refPoint).right
            parameters.isFloatingLeftBound = floating
        }

    override val cmdArgs
        get() = listOf(
            if (floating)
                AlignmentBoundaryMixinConstants.LEFT_FLOATING_CMD_OPTION
            else
                AlignmentBoundaryMixinConstants.LEFT_RIGID_CMD_OPTION,
            refPoint.toString()
        )
}

//
// Right Boundary
//

@JsonTypeName("RightAlignmentBoundaryNoPoint")
data class RightAlignmentBoundaryNoPoint(val floating: Boolean, val geneType: GeneType?) :
    MiXCRMixinBase(10, Flags.RightAlignmentMode) {
    override fun MixinBuilderOps.action() =
        when (geneType) {
            null -> {
                if (floating)
                    throw IllegalArgumentException() // must be checked in the options
                modifyAlignmentParams {
                    jAlignerParameters.parameters.isFloatingRightBound = false
                    cAlignerParameters.parameters.isFloatingRightBound = false
                }
            }

            GeneType.Joining ->
                modifyAlignmentParams {
                    // Setting alignment mode
                    jAlignerParameters.parameters.isFloatingRightBound = floating
                    // And turn off C gene alignment as alignment should terminate somewhere in J gene
                    setGeneAlignerParameters(GeneType.Constant, null)
                }

            GeneType.Constant ->
                modifyAlignmentParams {
                    // Checking mixin assumptions
                    if (jAlignerParameters.geneFeatureToAlign.lastPoint != ReferencePoint.FR4End)
                        throw RuntimeException(
                            "Incompatible J gene right alignment feature boundary for the mix-in: " +
                                    "${jAlignerParameters.geneFeatureToAlign.lastPoint}"
                        )

                    // And setting strict alignment mode for the J gene
                    jAlignerParameters.parameters.isFloatingRightBound = false
                    // Setting alignment mode
                    cAlignerParameters.parameters.isFloatingRightBound = floating
                }

            else -> throw IllegalArgumentException()
        }

    override val cmdArgs
        get() = listOf(
            if (floating)
                AlignmentBoundaryMixinConstants.RIGHT_FLOATING_CMD_OPTION
            else
                AlignmentBoundaryMixinConstants.RIGHT_RIGID_CMD_OPTION,
        ) + if (geneType != null)
            listOf(geneType.letter.toString())
        else
            emptyList()
}

@JsonTypeName("RightAlignmentBoundaryWithPoint")
data class RightAlignmentBoundaryWithPoint(val floating: Boolean, val refPoint: ReferencePoint) :
    MiXCRMixinBase(10, Flags.RightAlignmentMode) {
    override fun MixinBuilderOps.action() =
        when (refPoint.geneType) {
            GeneType.Joining ->
                modifyAlignmentParams {
                    // Checking mixin assumptions
                    if (jAlignerParameters.geneFeatureToAlign.lastPoint != ReferencePoint.FR4End &&
                        !jAlignerParameters.geneFeatureToAlign.contains(refPoint)
                    )
                        throw RuntimeException(
                            "Incompatible J gene alignment feature for the mix-in: " +
                                    "${jAlignerParameters.geneFeatureToAlign}"
                        )

                    // Adjusting feature to align
                    jAlignerParameters.geneFeatureToAlign = jAlignerParameters.geneFeatureToAlign.setLastPoint(refPoint)
                    // Setting alignment mode
                    jAlignerParameters.parameters.isFloatingRightBound = floating
                    // And turn off C gene alignment as alignment should terminate somewhere in J gene
                    setGeneAlignerParameters(GeneType.Constant, null)
                }

            GeneType.Constant ->
                modifyAlignmentParams {
                    // Checking mixin assumptions
                    if (cAlignerParameters.geneFeatureToAlign.lastPoint != ReferencePoint.CExon1End &&
                        !cAlignerParameters.geneFeatureToAlign.contains(refPoint)
                    )
                        throw RuntimeException(
                            "Incompatible C gene alignment feature for the mix-in: " +
                                    "${cAlignerParameters.geneFeatureToAlign}"
                        )
                    if (jAlignerParameters.geneFeatureToAlign.lastPoint != ReferencePoint.FR4End)
                        throw RuntimeException(
                            "Incompatible J gene right alignment feature boundary for the mix-in: " +
                                    "${jAlignerParameters.geneFeatureToAlign.lastPoint}"
                        )

                    // And setting strict alignment mode for the J gene
                    jAlignerParameters.parameters.isFloatingRightBound = false

                    // Adjusting feature to align
                    cAlignerParameters.geneFeatureToAlign = cAlignerParameters.geneFeatureToAlign.setLastPoint(refPoint)
                    // Setting alignment mode
                    cAlignerParameters.parameters.isFloatingRightBound = floating
                }

            else -> throw RuntimeException("$refPoint is not inside the J or C gene")
        }

    override val cmdArgs
        get() = listOf(
            if (floating)
                AlignmentBoundaryMixinConstants.RIGHT_FLOATING_CMD_OPTION
            else
                AlignmentBoundaryMixinConstants.RIGHT_RIGID_CMD_OPTION,
            refPoint.toString()
        )
}

//
// Tags
//

@JsonTypeName("SetTagPattern")
data class SetTagPattern(val tagPattern: String) : MiXCRMixinBase(50, Flags.TagPattern) {
    override fun MixinBuilderOps.action() {
        MiXCRParamsBundle::align.update {
            CommandAlign.Params::tagPattern setTo tagPattern
        }
    }

    override val cmdArgs get() = listOf(CMD_OPTION, tagPattern)

    companion object {
        const val CMD_OPTION = "+tagPattern"
    }
}

//
// Tags
//

@JsonTypeName("AddPipelineStep")
data class AddPipelineStep(val step: String) : MiXCRMixinBase(10) {
    private val command = MiXCRCommand.fromString(step)
    override fun MixinBuilderOps.action() {
        MiXCRParamsBundle::pipeline.updateBy {
            val newValue = ArrayList(it)
            newValue += command
            newValue.sort()
            newValue
        }

        if (command == MiXCRCommand.assembleContigs)
            MiXCRParamsBundle::assemble.update {
                CommandAssemble.Params::clnaOutput setTo true
            }
    }

    override val cmdArgs get() = listOf(CMD_OPTION, step)

    companion object {
        const val CMD_OPTION = "+addStep"
    }
}

@JsonTypeName("RemovePipelineStep")
data class RemovePipelineStep(val step: String) : MiXCRMixinBase(10) {
    private val command = MiXCRCommand.fromString(step)
    override fun MixinBuilderOps.action() {
        MiXCRParamsBundle::pipeline.updateBy {
            val idx = it.indexOf(command)
            if (idx == -1)
                it
            else {
                val newValue = ArrayList(it)
                newValue.removeAt(idx)
                newValue
            }
        }

        if (command == MiXCRCommand.assembleContigs)
            MiXCRParamsBundle::assemble.update {
                CommandAssemble.Params::clnaOutput setTo false
            }
    }

    override val cmdArgs get() = listOf(CMD_OPTION, step)

    companion object {
        const val CMD_OPTION = "+removeStep"
    }
}