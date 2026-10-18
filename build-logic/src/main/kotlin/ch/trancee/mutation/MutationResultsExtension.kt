package ch.trancee.mutation

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/** Module-level settings for the `mutation-results` plugin, exposed as `mutationTesting`. */
abstract class MutationResultsExtension {
    /** Kotlin test sources whose `@Test` methods the generated MutFlow adapter wraps. */
    abstract val adapterSources: ConfigurableFileCollection

    /** Package of the generated adapter class; it must see the wrapped test classes. */
    abstract val adapterPackage: Property<String>

    /** Simple name of the generated adapter class. */
    abstract val adapterClassName: Property<String>

    /** Gradle test-class patterns; defaults to the generated adapter. `-PmutationTest.includes` overrides it. */
    abstract val includes: ListProperty<String>
}
