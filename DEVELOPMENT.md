# Development Guide

This repository holds the patch bundles of [NexFusion](https://github.com/a092devs/NexFusion). How bundles are
loaded, the bundle format and the rules for bundles are described in its
[development guide](https://github.com/a092devs/NexFusion/blob/main/DEVELOPMENT.md).

```text
📦 Nexfusion-Patches
 ├ build-logic/    Removed; nexfusion.bundle is consumed from NexFusion's Maven repository
 ├ bundles/
 │  ├ nexfusion/    NexFusion Patches: Morphe, ReVanced and hoodles patches
 │  ├ nexfusion/    NexFusion Patches: community ports (Discord, Facebook, Gboard, Instagram, LinkedIn, MiXplorer, Proton VPN, Reddit, Spotify, X)
 │  └ testing/     Fingerprint test harness shared by the bundles
 └ stub/           Stubs of YouTube classes used by the Morphe extensions
```

The bundles compile against the patcher API `io.github.a092devs.nexfusion:patcher` (version `patcher` in
[libs.versions.toml](gradle/libs.versions.toml)), published to `https://raw.githubusercontent.com/a092devs/NexFusion/maven`.
To build against a local NexFusion checkout, run `./gradlew :patcher:publishAllPublicationsToReleaseRepository`
there and pass `-PnexfusionMaven=file:///path/to/NexFusion/build/maven` here.

```sh
./gradlew assembleBundle   # bundles/*/build/outputs/bundle/*.nxb
```

The `nexfusion.bundle` convention plugin is also published by NexFusion. Its
version is pinned as `nexfusionBuildLogic` in
[libs.versions.toml](gradle/libs.versions.toml). Update that version only after
the corresponding plugin has been published.

### Upstream submodules

`morphe-patches` and `morphe-patches-library` are NexFusion-maintained forks
of the upstream extension projects. Changes to files inside either submodule
must be committed and pushed in that submodule first, then the resulting
submodule commit must be updated in this repository:

```sh
git -C morphe-patches checkout nexfusion
git -C morphe-patches pull --ff-only
git add morphe-patches
git commit -m "chore: update morphe-patches"
```

Do not edit generated `build/` output or local `binaries/` APKs for a
release. Both are intentionally excluded from version control.

### Adding an app

Add the app to the bundle's entry point, e.g. [NexFusionPatches.kt](bundles/nexfusion/src/main/java/io/github/a092devs/nexfusion/patches/NexFusionPatches.kt):

```kotlin
app("Contoso", "com.contoso.app", ContosoPatches),
```

### Patches

Patches follow this structure:

```text
📦your.patches.app.category
 ├ Fingerprints.kt
 └ SomePatch.kt
```

#### `Fingerprints.kt`
```kotlin
package com.example.patches.contoso.misc.unlock.plus

import io.github.a092devs.nexfusion.morphe.Fingerprint

object IsPlusUnlockedFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf("genius"),
)
```

#### `UnlockPlusPatch.kt`
```kotlin
package com.example.patches.contoso.misc.unlock.plus

import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import io.github.a092devs.nexfusion.patch

val UnlockPlus = patch(name = "Unlock Plus") {
    IsPlusUnlockedFingerprint.hookMethod(returnConstant(true))
}
```

Patches named `""` or starting with `<` are internal: they always run and are hidden in the manager.

### Porting Upstream Patches

The [FingerprintCompat.kt](patcher/src/main/java/io/github/a092devs/nexfusion/morphe/FingerprintCompat.kt) utility assists in translating ReVanced Patcher API calls to DexKit Matchers. While simple fingerprints can often be directly copied, consider the following translation patterns for complex cases:

1.  **Literal Mapping:**
    If an upstream patch defines a literal within the patch file (e.g., `aLiteral = resourceMappings["id", "aLiteral"]`) for use in a fingerprint, convert it to a getter property in the corresponding `Fingerprints.kt` file.

    *From (Upstream):*
    ```kotlin
    // In ***Patch.kt beside the Fingerprints.kt
    // val aLiteral = resourceMappings["id", "aLiteral"]
    fingerprint { literal { aLiteral } }
    ```

    *To (This Project's Fingerprints.kt):*
    ```kotlin
    val aLiteral get() = resourceMappings["id", "aLiteral"] // Defined in Fingerprints.kt
    fingerprint { literal { aLiteral } }
    ```

2.  **Custom Class and Method Matchers:**

    *From (Upstream):*
    ```kotlin
    fingerprint {
        custom { method, classDef ->
            method.name == "onCreate" && classDef.endsWith("/MusicActivity;")
        }
    }
    ```

    *To (This Project):*
    ```kotlin
    fingerprint {
        methodMatcher { name = "onCreate" }
        classMatcher { className(".MusicActivity", StringMatchType.EndsWith) }
    }
    ```

3.  **Instruction-based Method Reference:**

    *From (Upstream):*
    ```kotlin
    fun indexOfTranslationInstruction(method: Method) =
        method.indexOfFirstInstructionReversed {
            getReference<MethodReference>()?.name == "setTranslationY"
        }

    val motionEventFingerprint = fingerprint {
        custom { method, _ ->
            indexOfTranslationInstruction(method) >= 0
        }
    }
    ```

    *To (This Project):*
    ```kotlin
    val motionEventFingerprint = fingerprint {
        methodMatcher { addInvoke { name = "setTranslationY" } }
    }
    ```

4.  **Matching Specific Class Types or Defining Classes:**
    When an upstream `custom` block primarily checks `classDef.type` or `method.definingClass`, this translates to a `classMatcher`:

    ```kotlin
    fingerprint {
        classMatcher { descriptor = "Lcom/example/SomeClass;" }
        methodMatcher { name = "targetMethod" }
    }
    ```

5.  **Porting Complex `custom` Logic or Chained Lookups with Direct Finders:**
    For intricate upstream fingerprints, use the direct finder functions (e.g., `findMethodDirect`, `findClassDirect`) from `FingerprintCompat.kt`, combining DexKit's filtering with Kotlin's collection processing:

    ```kotlin
    val complexCustomFingerprint = findMethodDirect {
        findMethod {
            matcher {
                returns("Lcom/example/ReturnType;")
                declaredClass { descriptor = "Lcom/example/HostClass;" }
            }
        }
        .filter { methodData -> methodData.name.startsWith("get") }
        .single { methodData ->
            methodData.paramTypes.size == 1 &&
            methodData.paramTypes.firstOrNull()?.descriptor == "Lcom/example/ParameterType;"
        }
    }
    ```

### Extension code

As per ReVanced Patcher documentation:
> Instead of involving many abstract changes in one patch or writing entire methods or classes in a patch, you can write code in extensions.

The NexFusion Patches bundle compiles the extensions of the `morphe-patches` and `morphe-patches-library` submodules
(NexFusion forks). The nexfusion Patches bundle carries the Spotify extension code of the
[2eno-patches](https://github.com/2eno/2eno-patches) project vendored in-tree under
`bundles/nexfusion/src/main/java/app/twoeno/extension` (GPL-3.0, see the bundle's NOTICE).

## Unit Testing

Bundles test their fingerprints against real APKs with
[FingerprintsKtTest.kt](bundles/testing/java/io/github/a092devs/nexfusion/FingerprintsKtTest.kt). Place APKs into the bundle's
`binaries/` directory (e.g. `bundles/nexfusion/binaries/`), prefixed with their package name
(e.g. `com.google.android.youtube-20.10.38.apk`), and list the app in the bundle's `FingerprintTestTargets.kt`.

```sh
./gradlew testDebugUnitTest
```

## Releasing

Bump the `versionCode`/`versionName` of the changed bundles, then push a tag (`X.Y.Z`, or `X.Y.Z-suffix` for a
pre-release) or run the Release workflow with a tag name. It attaches every bundle `.nxb` to a GitHub release, which
NexFusion installs from this patch source.
