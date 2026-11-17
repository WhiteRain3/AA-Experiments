package io.github.highwindbr.aaxp.discovery

import io.github.highwindbr.aaxp.runtime.ThemeColors
import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.StringReference
import java.io.File
import java.util.zip.ZipFile

/**
 * Finds Android Auto's palette-role enum in an APK without relying on its (obfuscated) name:
 * it is the enum whose static initialiser mentions the Material role names in
 * [ThemeColors.MARKERS]. Returns the binary class name, or null when no single enum matches.
 */
object ThemeRoleLocator {
    fun locate(apk: File): String? {
        val matches = mutableListOf<String>()
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.removePrefix("./").matches(Regex("classes\\d*\\.dex")) }
                .forEach { entry ->
                    val dex = DexBackedDexFile(Opcodes.getDefault(), zip.getInputStream(entry).use { it.readBytes() })
                    for (definition in dex.classes) {
                        if (definition.accessFlags and AccessFlags.ENUM.value == 0) continue
                        val initialiser = definition.directMethods.firstOrNull { it.name == "<clinit>" } ?: continue
                        val strings = HashSet<String>()
                        initialiser.implementation?.instructions?.forEach { instruction ->
                            val reference = (instruction as? ReferenceInstruction)?.reference
                            if (reference is StringReference) strings += reference.string
                        }
                        if (ThemeColors.looksLikeRoleEnum(strings)) {
                            // "Lfoo/Bar;" -> "foo.Bar"
                            matches += definition.type.removePrefix("L").removeSuffix(";").replace('/', '.')
                        }
                    }
                }
        }
        // Ambiguity is treated as "not found": never hook a guess.
        return matches.singleOrNull()
    }
}
