/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("unused")

package kotlin.script.experimental.api

typealias ScriptCompileConfiguration = HeterogeneousMap

typealias ProcessedScriptData = HeterogeneousMap

interface ScriptConfigurator {

    // with null scriptSource should return a generic configuration for the script type
    suspend fun baseConfiguration(scriptSource: ScriptSource?) : ResultWithDiagnostics<ScriptCompileConfiguration>

    suspend fun refineConfiguration(
        configuration: ScriptCompileConfiguration,
        processedScriptData: ProcessedScriptData = ProcessedScriptData()
    ): ResultWithDiagnostics<ScriptCompileConfiguration>
}
