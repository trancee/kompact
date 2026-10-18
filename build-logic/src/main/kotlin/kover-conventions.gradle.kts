import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    id("org.jetbrains.kotlinx.kover")
}

// 100 % line + branch coverage on production code; XML only (the HTML report needs freemarker).
kover {
    reports {
        total {
            xml { onCheck.set(true) }
            html { onCheck.set(false) }
        }
        verify {
            rule { minBound(100, CoverageUnit.LINE) }
            rule { minBound(100, CoverageUnit.BRANCH) }
        }
    }
}
