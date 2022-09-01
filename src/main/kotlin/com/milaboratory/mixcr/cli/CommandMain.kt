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

import com.milaboratory.cli.ABaseCommand
import com.milaboratory.cli.AppVersionInfo
import com.milaboratory.mixcr.util.MiXCRVersionInfo
import io.repseq.core.VDJCLibraryRegistry
import io.repseq.core.VDJCLibraryRegistry.ClasspathLibraryResolver
import io.repseq.core.VDJCLibraryRegistry.FolderLibraryResolver
import picocli.CommandLine

@CommandLine.Command(name = "mixcr", versionProvider = CommandMain.VersionProvider::class, separator = " ")
class CommandMain internal constructor() : ABaseCommand("mixcr") {
    @CommandLine.Option(
        names = ["-v", "--version"],
        versionHelp = true,
        description = ["print version information and exit"]
    )
    var versionRequested = false

    internal class VersionProvider : CommandLine.IVersionProvider {
        override fun getVersion(): Array<String> {
            val lines = mutableListOf<String>()
            lines += MiXCRVersionInfo.get()
                .getVersionString(AppVersionInfo.OutputType.ToConsole, true)
                .split("\n".toRegex()).dropLastWhile { it.isEmpty() }
            lines += ""
            lines += "Library search path:"
            for (resolver in VDJCLibraryRegistry.getDefault().libraryResolvers) {
                lines += when (resolver) {
                    is ClasspathLibraryResolver -> "- built-in libraries"
                    is FolderLibraryResolver -> "- ${resolver.path}"
                    else -> throw UnsupportedOperationException()
                }
            }
            return lines.toTypedArray()
        }
    }
}
