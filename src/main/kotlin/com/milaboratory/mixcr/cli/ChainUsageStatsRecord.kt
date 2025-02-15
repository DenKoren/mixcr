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

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class ChainUsageStatsRecord(
    @JsonProperty("total") val total: Long,
    @JsonProperty("nonFunctional") val nonFunctional: Long,
    @JsonProperty("isOOF") val isOOF: Long,
    @JsonProperty("hasStops") val hasStops: Long
) {
    init {
        assert(isOOF + hasStops == nonFunctional)
    }

    fun productive(): Long {
        return total - nonFunctional
    }

    companion object {
        val EMPTY = ChainUsageStatsRecord(0, 0, 0, 0)
    }
}