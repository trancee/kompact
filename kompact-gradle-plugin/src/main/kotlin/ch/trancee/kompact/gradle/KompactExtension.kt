package ch.trancee.kompact.gradle

import javax.inject.Inject
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

public abstract class KompactExtension @Inject constructor(objects: ObjectFactory) {
    public val namespace: Property<String> = objects.property(String::class.java)
    public val maxPacketBytes: Property<Int> = objects.property(Int::class.java)
    public val registryFile: RegularFileProperty = objects.fileProperty()
    public val compatibilityBaseline: RegularFileProperty = objects.fileProperty()
    public val requireCompatibilityBaseline: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)
    public val publishCHeaders: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)
    public val cHeadersClassifier: Property<String> =
        objects.property(String::class.java).convention("c-headers")
}
