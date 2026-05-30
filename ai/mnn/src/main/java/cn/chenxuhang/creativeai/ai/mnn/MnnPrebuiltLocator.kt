package cn.chenxuhang.creativeai.ai.mnn

import java.io.File

data class MnnPrebuiltLayoutReport(
    val rootDirectory: String,
    val includeDirectoryExists: Boolean,
    val availableAbis: List<String>,
    val missingAbis: List<String>,
    val hasAnyLibrary: Boolean,
)

object MnnPrebuiltLocator {
    private val expectedAbis = listOf("arm64-v8a")

    fun expectedAbis(): List<String> = expectedAbis

    fun inspect(rootDirectory: String): MnnPrebuiltLayoutReport {
        val root = File(rootDirectory)
        val includeDirectoryExists = File(root, "include/MNN/Interpreter.hpp").exists()
        val availableAbis = expectedAbis.filter { abi ->
            File(root, "lib/$abi/libMNN.so").exists()
        }
        val missingAbis = expectedAbis - availableAbis.toSet()
        return MnnPrebuiltLayoutReport(
            rootDirectory = root.absolutePath,
            includeDirectoryExists = includeDirectoryExists,
            availableAbis = availableAbis,
            missingAbis = missingAbis,
            hasAnyLibrary = availableAbis.isNotEmpty(),
        )
    }

    fun requiredPaths(rootDirectory: String): List<String> {
        val root = File(rootDirectory)
        return buildList {
            add(File(root, "include/MNN/Interpreter.hpp").absolutePath)
            expectedAbis.forEach { abi ->
                add(File(root, "lib/$abi/libMNN.so").absolutePath)
            }
        }
    }
}
