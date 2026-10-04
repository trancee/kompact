---
Type: grilling
Status: resolved
---

## Question

Which Kotlin Multiplatform targets must be supported and satisfy the hard
allocation requirement?

## Answer

Keep the current target matrix: JVM, Android JVM, iOS Arm64, iOS Simulator
Arm64, and Android Native Arm64. The no-allocation guarantee must have
target-specific proof for every target on which it is claimed; do not infer
Native or Android results from JVM measurements.
