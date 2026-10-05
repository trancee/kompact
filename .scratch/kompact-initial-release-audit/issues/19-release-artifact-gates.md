---
Type: grilling
Status: resolved
---

## Question

Which publication artifacts must be assembled and verified before release?
Decide whether pull-request CI must dry-run the primary `:kompact` Central
Portal bundle as it already does for sibling modules, and define the required
ABI/coverage/validation gates for the Gradle plugin versus explicit
CI-named exclusions. Keep the policy aligned with Constitution Q5/T3/E7 and
the actual published module set.

## Answer

Every PR must dry-run the primary `:kompact` Central Portal bundle, including
checksums/bundle assembly, without uploading or publishing. The published
Gradle plugin receives a Linux JVM ABI baseline/check and the same 100% line
and branch coverage gate as maintained runtime/KSP production code. Do not
exclude plugin production logic from coverage; test reflection failure paths
with an appropriate seam. Test fixtures and generated output are not
production source and need not be counted.
