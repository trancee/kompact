package ch.trancee.kompact.ksp.testing

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.NonExistLocation
import java.io.ByteArrayOutputStream
import java.io.OutputStream

// ------------------------------------------------------------------
// KSName
// ------------------------------------------------------------------

class FakeKSName(
    private val name: String,
) : KSName {
    override fun asString(): String = name

    override fun getQualifier(): String = name.substringBeforeLast('.', "")

    override fun getShortName(): String = name.substringAfterLast('.')

    override fun toString(): String = name
}

// ------------------------------------------------------------------
// Location constant
// ------------------------------------------------------------------

val FakeLocation: Location = NonExistLocation

// ------------------------------------------------------------------
// KSPLogger
// ------------------------------------------------------------------

class FakeKSPLogger : KSPLogger {
    val infos: MutableList<String> = mutableListOf()
    val warnings: MutableList<String> = mutableListOf()
    val errors: MutableList<String> = mutableListOf()

    override fun logging(
        message: String,
        symbol: KSNode?,
    ) {
        infos.add(message)
    }

    override fun info(
        message: String,
        symbol: KSNode?,
    ) {
        infos.add(message)
    }

    override fun warn(
        message: String,
        symbol: KSNode?,
    ) {
        warnings.add(message)
    }

    override fun error(
        message: String,
        symbol: KSNode?,
    ) {
        errors.add(message)
    }

    override fun exception(e: Throwable) {
        errors.add(e.message ?: "")
    }
}

// ------------------------------------------------------------------
// CodeGenerator
// ------------------------------------------------------------------

class FakeCodeGenerator(
    private val throwOnWrite: Boolean = false,
) : CodeGenerator {
    val generatedFiles: MutableMap<String, String> = mutableMapOf()

    override fun createNewFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        extensionName: String,
    ): OutputStream {
        val key = "$packageName.$fileName.$extensionName"
        val baos = ByteArrayOutputStream()
        return object : OutputStream() {
            override fun write(b: Int) {
                if (throwOnWrite) throw RuntimeException("Write failed")
                baos.write(b)
            }

            override fun write(
                b: ByteArray,
                off: Int,
                len: Int,
            ) {
                if (throwOnWrite) throw RuntimeException("Write failed")
                baos.write(b, off, len)
            }

            override fun close() {
                baos.close()
                generatedFiles[key] = baos.toString(Charsets.UTF_8.name())
            }
        }
    }

    override fun createNewFileByPath(
        dependencies: Dependencies,
        path: String,
        extensionName: String,
    ): OutputStream = createNewFile(dependencies, path, "generated", extensionName)

    override fun associate(
        sources: List<KSFile>,
        packageName: String,
        fileName: String,
        extensionName: String,
    ) {
    }

    override fun associateByPath(
        sources: List<KSFile>,
        path: String,
        extensionName: String,
    ) {
    }

    override fun associateWithClasses(
        classes: List<KSClassDeclaration>,
        packageName: String,
        fileName: String,
        extensionName: String,
    ) {
    }

    override val generatedFile: Collection<java.io.File> = emptyList()
}
