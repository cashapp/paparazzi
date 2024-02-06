// /*
//  * Copyright (C) 2017 The Android Open Source Project
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *      http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */
// package com.android.tools.idea.projectsystem.gradle
//
// import com.android.ide.common.gradle.Component
// import com.android.ide.common.gradle.Dependency
// import com.android.ide.common.gradle.RichVersion
// import com.android.ide.common.gradle.Module as ExternalModule
// import com.android.ide.common.repository.AgpVersion
// import com.android.ide.common.repository.GradleCoordinate
// import com.android.manifmerger.ManifestSystemProperty
// import com.android.projectmodel.ExternalAndroidLibrary
// import com.android.tools.idea.flags.StudioFlags
// import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
// import com.android.tools.idea.gradle.model.IdeAndroidGradlePluginProjectFlags
// import com.android.tools.idea.gradle.model.IdeAndroidLibrary
// import com.android.tools.idea.gradle.model.IdeAndroidLibraryDependency
// import com.android.tools.idea.gradle.model.IdeAndroidProjectType
// import com.android.tools.idea.gradle.model.IdeDependencies
// import com.android.tools.idea.gradle.model.IdeModuleLibrary
// import com.android.tools.idea.gradle.project.model.GradleAndroidModel
// import com.android.tools.idea.gradle.project.sync.idea.getGradleProjectPath
// import com.android.tools.idea.gradle.util.DynamicAppUtils
// import com.android.tools.idea.projectsystem.AndroidModuleSystem
// import com.android.tools.idea.projectsystem.AndroidProjectRootUtil
// import com.android.tools.idea.projectsystem.CapabilityStatus
// import com.android.tools.idea.projectsystem.CapabilitySupported
// import com.android.tools.idea.projectsystem.ClassFileFinder
// import com.android.tools.idea.projectsystem.CodeShrinker
// import com.android.tools.idea.projectsystem.DependencyScopeType
// import com.android.tools.idea.projectsystem.DependencyType
// import com.android.tools.idea.projectsystem.ManifestOverrides
// import com.android.tools.idea.projectsystem.MergedManifestContributors
// import com.android.tools.idea.projectsystem.ModuleHierarchyProvider
// import com.android.tools.idea.projectsystem.NamedModuleTemplate
// import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
// import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider
// import com.android.tools.idea.projectsystem.ScopeType
// import com.android.tools.idea.projectsystem.TestArtifactSearchScopes
// import com.android.tools.idea.projectsystem.androidFacetsForNonHolderModules
// import com.android.tools.idea.projectsystem.buildNamedModuleTemplatesFor
// import com.android.tools.idea.projectsystem.getAndroidTestModule
// import com.android.tools.idea.projectsystem.getFlavorAndBuildTypeManifests
// import com.android.tools.idea.projectsystem.getFlavorAndBuildTypeManifestsOfLibs
// import com.android.tools.idea.projectsystem.getForFile
// import com.android.tools.idea.projectsystem.getMainModule
// import com.android.tools.idea.projectsystem.getTestFixturesModule
// import com.android.tools.idea.projectsystem.getTransitiveNavigationFiles
// import com.android.tools.idea.projectsystem.getUnitTestModule
// import com.android.tools.idea.projectsystem.isAndroidTestFile
// import com.android.tools.idea.projectsystem.isAndroidTestModule
// import com.android.tools.idea.projectsystem.isUnitTestModule
// import com.android.tools.idea.projectsystem.sourceProviders
// import com.android.tools.idea.res.AndroidDependenciesCache
// import com.android.tools.idea.res.MainContentRootSampleDataDirectoryProvider
// import com.android.tools.idea.run.ApplicationIdProvider
// import com.android.tools.idea.run.GradleApplicationIdProvider
// import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild
// import com.android.tools.idea.stats.recordTestLibraries
// import com.android.tools.idea.testartifacts.scopes.GradleTestArtifactSearchScopes
// import com.android.tools.idea.util.androidFacet
// import com.google.wireless.android.sdk.stats.TestLibraries
// import com.intellij.openapi.module.Module
// import com.intellij.openapi.module.ModuleManager
// import com.intellij.openapi.vfs.VfsUtil
// import com.intellij.openapi.vfs.VirtualFile
// import com.intellij.psi.search.GlobalSearchScope
// import com.intellij.psi.util.CachedValueProvider
// import com.intellij.psi.util.CachedValuesManager
// import org.jetbrains.android.dom.manifest.getPrimaryManifestXml
// import org.jetbrains.android.facet.AndroidFacet
// import org.jetbrains.kotlin.idea.versions.LOG
// import java.io.File
// import java.nio.file.Path
// import java.util.Collections
// import java.util.concurrent.TimeUnit
//
// /**
//  * Make [.getRegisteredDependency] return the direct module dependencies.
//  *
//  * The method [.getRegisteredDependency] should return direct module dependencies,
//  * but we do not have those available with the current model see b/128449813.
//  *
//  * The artifacts in
//  *   [com.android.tools.idea.gradle.dsl.api.GradleBuildModel.dependencies().artifacts]
//  * is a list of the direct dependencies parsed from the build.gradle files but the
//  * information will not be available for complex build files.
//  *
//  * For now always look at the transitive closure of dependencies.
//  */
// const val CHECK_DIRECT_GRADLE_DEPENDENCIES = false
//
// /** Creates a map for the given pairs, filtering out null values. */
// private fun <K, V> notNullMapOf(vararg pairs: Pair<K, V?>): Map<K, V> {
//   @Suppress("UNCHECKED_CAST")
//   return pairs.asSequence()
//     .filter { it.second != null }
//     .toMap() as Map<K, V>
// }
//
// class GradleModuleSystem(
//   override val module: Module,
//   private val projectBuildModelHandler: ProjectBuildModelHandler,
//   private val moduleHierarchyProvider: ModuleHierarchyProvider,
// ) : AndroidModuleSystem,
//   SampleDataDirectoryProvider by MainContentRootSampleDataDirectoryProvider(module) {
//
//   override val type: AndroidModuleSystem.Type
//     get() = when (GradleAndroidModel.get(module)?.androidProject?.projectType) {
//       IdeAndroidProjectType.PROJECT_TYPE_APP -> AndroidModuleSystem.Type.TYPE_APP
//       IdeAndroidProjectType.PROJECT_TYPE_ATOM -> AndroidModuleSystem.Type.TYPE_ATOM
//       IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE -> AndroidModuleSystem.Type.TYPE_DYNAMIC_FEATURE
//       IdeAndroidProjectType.PROJECT_TYPE_FEATURE -> AndroidModuleSystem.Type.TYPE_FEATURE
//       IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP -> AndroidModuleSystem.Type.TYPE_INSTANTAPP
//       IdeAndroidProjectType.PROJECT_TYPE_LIBRARY -> AndroidModuleSystem.Type.TYPE_LIBRARY
//       IdeAndroidProjectType.PROJECT_TYPE_KOTLIN_MULTIPLATFORM -> AndroidModuleSystem.Type.TYPE_LIBRARY
//       IdeAndroidProjectType.PROJECT_TYPE_TEST -> AndroidModuleSystem.Type.TYPE_TEST
//       null -> AndroidModuleSystem.Type.TYPE_NON_ANDROID
//     }
//
//   override val moduleClassFileFinder: ClassFileFinder = GradleClassFileFinder.create(module, false)
//   private val androidTestsClassFileFinder: ClassFileFinder = GradleClassFileFinder.create(module, true)
//
//   private val dependencyCompatibility = GradleDependencyCompatibilityAnalyzer(this, projectBuildModelHandler)
//
//   /**
//    * Return the corresponding [ClassFileFinder], depending on whether the [sourceFile] is an android
//    * test file or not. In case the [sourceFile] is not specified (is null), the [androidTestsClassFileFinder]
//    * will be returned, as it has a wider search scope than [moduleClassFileFinder].
//    */
//   override fun getClassFileFinderForSourceFile(sourceFile: VirtualFile?) =
//     if (sourceFile == null || isAndroidTestFile(module.project, sourceFile)) androidTestsClassFileFinder else moduleClassFileFinder
//
//   override fun getResolvedDependency(coordinate: GradleCoordinate, scope: DependencyScopeType): GradleCoordinate? {
//     return getCompileDependenciesFor(module, scope)
//       ?.let { it.androidLibraries.asSequence() + it.javaLibraries.asSequence() }
//       ?.mapNotNull { it.target.component }
//       ?.find { it.matches(coordinate) }
//       ?.let { GradleCoordinate(it.group, it.name, it.version.toString()) }
//   }
//
//   private fun Component.matches(coordinate: GradleCoordinate): Boolean =
//     this.group == coordinate.groupId &&
//       this.name == coordinate.artifactId &&
//       RichVersion.parse(coordinate.revision).contains(this.version)
//
//   private fun GradleCoordinate.toDependency(): Dependency = Dependency.parse(toString());
//
//   override fun getDependencyPath(coordinate: GradleCoordinate): Path? {
//     return getCompileDependenciesFor(module, DependencyScopeType.MAIN)
//       ?.let { dependencies ->
//         dependencies.androidLibraries.asSequence().mapNotNull { it.target.component?.let { c -> c to it.target.artifact } } +
//           dependencies.javaLibraries.asSequence().mapNotNull { it.target.component?.let { c -> c to it.target.artifact } }
//       }
//       ?.find { it.first.matches(coordinate) }
//       ?.second?.toPath()
//   }
//
//   // TODO: b/129297171
//   override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? =
//   // TODO(xof): I'm reasonably convinced that this interface (in terms of GradleCoordinate) and its implementation
//   //  verifying that any version field specified in the GradleCoordinate matches (in some sense, where "+" is treated
//   //  specially in the coordinate but not in any of the matches) is not reasonably supportable.  Almost all uses of this in production
//   //  use a bare version of "+", which will match any version.  There is an exception, which attempts to insert specific versions
//   //  taken from fragments of other Gradle build files (or Maven XML).
//   //  I think the ideal final state will involve removing this function from the AndroidModuleSystem interface; converting users of
//   //  this to the getRegisteredDependency(ExternalModule) method below; and require clients who want to query or add specific
//   //  dependency versions to Gradle build files to accept that they are using GradleModuleSystem facilities, which we will allow from
//     //  a limited set of modules.  In the meantime, preserve existing behavior by emulating GradleCoordinate.matches(...)
//     getRegisteredDependency(ExternalModule(coordinate.groupId, coordinate.artifactId))
//       ?.takeIf { it.matches(coordinate) }
//       ?.toIdentifier()
//       ?.let { GradleCoordinate.parseCoordinateString(it) }
//
//   // This only exists to support the contract of getRegisteredDependency(), which is that an existing declared dependency should be
//   // returned if it matches the coordinate given, with a possibly-wild or possibly rich version.  If the declared dependency is to
//   // an explicit singleton, we check whether the pattern contains that version; if the pattern version is wild, we accept any
//   // declared dependency; otherwise, we accept only exact rich version matches.
//   private fun Dependency.matches(coordinate: GradleCoordinate): Boolean =
//     coordinate.groupId == this.group &&
//       coordinate.artifactId == this.name &&
//       when (val version = this.version?.explicitSingletonVersion) {
//         null -> coordinate.revision == "+" || RichVersion.parse(coordinate.revision) == this.version
//         else -> RichVersion.parse(coordinate.revision).contains(version)
//       }
//
//   fun getRegisteredDependency(externalModule: ExternalModule): Dependency? =
//     getDirectDependencies(module).find { it.name == externalModule.name && it.group == externalModule.group }
//
//   private fun Component.dependency() = Dependency(group, name, RichVersion.parse(version.toString()))
//
//   fun getDirectDependencies(module: Module): Sequence<Dependency> {
//     // TODO: b/129297171
//     @Suppress("ConstantConditionIf")
//     return if (CHECK_DIRECT_GRADLE_DEPENDENCIES) {
//       projectBuildModelHandler.read {
//         // TODO: Replace the below artifacts with the direct dependencies from the GradleAndroidModel see b/128449813
//         val artifacts = getModuleBuildModel(module)?.dependencies()?.artifacts() ?: return@read emptySequence<Dependency>()
//         artifacts
//           .asSequence()
//           .mapNotNull { Dependency.parse("${it.group()}:${it.name().forceString()}:${it.version()}") }
//       }
//     } else {
//       getCompileDependenciesFor(module, DependencyScopeType.MAIN)
//         ?.let { it.androidLibraries.asSequence() + it.javaLibraries.asSequence() }
//         ?.mapNotNull { it.target.component?.dependency() } ?: emptySequence()
//     }
//   }
//
//   override fun getResourceModuleDependencies() =
//     AndroidDependenciesCache.getAllAndroidDependencies(module.getMainModule(), true).map(AndroidFacet::getModule)
//
//   override fun getAndroidTestDirectResourceModuleDependencies(): List<Module> {
//     val dependencies = GradleAndroidModel.get(this.module)?.selectedAndroidTestCompileDependencies
//     return dependencies?.libraries?.filterIsInstance<IdeModuleLibrary>()
//       ?.mapNotNull { it.getGradleProjectPath().resolveIn(this.module.project) }
//       ?.toList()
//       ?: emptyList()
//   }
//
//   override fun getDirectResourceModuleDependents(): List<Module> = ModuleManager.getInstance(module.project).getModuleDependentModules(
//     module
//   )
//
//   override fun getAndroidLibraryDependencies(scope: DependencyScopeType): Collection<ExternalAndroidLibrary> {
//     // TODO: b/129297171 When this bug is resolved we may not need getResolvedLibraryDependencies(Module)
//     return getRuntimeDependenciesFor(module, scope)
//       .flatMap { it.androidLibraries }
//       .distinct()
//       .map(IdeAndroidLibraryDependency::target)
//       .map(::convertLibraryToExternalLibrary)
//       .toList()
//   }
//
//   private fun getCompileDependenciesFor(module: Module, scope: DependencyScopeType): IdeDependencies? {
//     val gradleModel = GradleAndroidModel.get(module) ?: return null
//
//     return when (scope) {
//       DependencyScopeType.MAIN -> gradleModel.selectedVariant.mainArtifact.compileClasspath
//       DependencyScopeType.ANDROID_TEST -> gradleModel.selectedVariant.androidTestArtifact?.compileClasspath
//       DependencyScopeType.UNIT_TEST -> gradleModel.selectedVariant.unitTestArtifact?.compileClasspath
//       DependencyScopeType.TEST_FIXTURES -> gradleModel.selectedVariant.testFixturesArtifact?.compileClasspath
//     }
//   }
//
//   private fun getRuntimeDependenciesFor(module: Module, scope: DependencyScopeType): Sequence<IdeDependencies> {
//     fun impl(module: Module, scope: DependencyScopeType): Sequence<IdeDependencies> = sequence {
//       val gradleModel = GradleAndroidModel.get(module)
//       if (gradleModel == null) {
//         // TODO(b/253476264): Returning an incomplete set of dependencies is highly problematic and should be avoided.
//         ClearResourceCacheAfterFirstBuild.getInstance(module.project).setIncompleteRuntimeDependencies()
//         return@sequence
//       }
//
//       val selectedVariant = gradleModel.selectedVariant
//       val artifact = when (scope) {
//         DependencyScopeType.MAIN -> selectedVariant.mainArtifact
//         DependencyScopeType.ANDROID_TEST -> selectedVariant.androidTestArtifact
//         DependencyScopeType.UNIT_TEST -> selectedVariant.unitTestArtifact
//         DependencyScopeType.TEST_FIXTURES -> selectedVariant.testFixturesArtifact
//       }
//       if (artifact != null) yield(artifact.runtimeClasspath)
//
//       yieldAll(
//         when {
//           scope != DependencyScopeType.MAIN -> impl(module, DependencyScopeType.MAIN)
//           gradleModel.androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE -> {
//             val baseFeature = DynamicAppUtils.getBaseFeature(module)
//             if (baseFeature != null) {
//               impl(baseFeature, DependencyScopeType.MAIN)
//             } else {
//               LOG.error("Cannot find base feature module for: $module")
//               emptySequence()
//             }
//           }
//           else -> emptySequence()
//         }
//       )
//     }
//
//     return impl(module, scope)
//   }
//
//   override fun canRegisterDependency(type: DependencyType): CapabilityStatus {
//     return CapabilitySupported()
//   }
//
//   override fun registerDependency(coordinate: GradleCoordinate) {
//     registerDependency(coordinate, DependencyType.IMPLEMENTATION)
//   }
//
//   override fun registerDependency(coordinate: GradleCoordinate, type: DependencyType) {
//     val manager = GradleDependencyManager.getInstance(module.project)
//     val dependencies = Collections.singletonList(coordinate.toDependency())
//
//     when (type) {
//       DependencyType.ANNOTATION_PROCESSOR -> {
//         // addDependenciesWithoutSync doesn't support this: more direct implementation
//         manager.addDependenciesWithoutSync(module, dependencies) { _, name, _ ->
//           when {
//             name.startsWith("androidTest") -> "androidTestAnnotationProcessor"
//             name.startsWith("test") -> "testAnnotationProcessor"
//             else -> "annotationProcessor"
//           }
//         }
//       }
//       DependencyType.DEBUG_IMPLEMENTATION -> {
//         manager.addDependenciesWithoutSync(module, dependencies) { _, _, _ ->
//           "debugImplementation"
//         }
//       }
//       else -> {
//         manager.addDependenciesWithoutSync(module, dependencies)
//       }
//     }
//   }
//
//   override fun updateLibrariesToVersion(toVersions: List<GradleCoordinate>) {
//     val manager = GradleDependencyManager.getInstance(module.project)
//     manager.updateLibrariesToVersion(module, toVersions.map { it.toDependency() })
//   }
//
//   override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
//     val moduleRootDir = AndroidProjectRootUtil.getModuleDirPath(module)?.let { File(it) }
//     val sourceProviders = module.androidFacet?.sourceProviders ?: return listOf()
//     val selectedSourceProviders = targetDirectory?.let { sourceProviders.getForFile(targetDirectory) }
//       ?: (sourceProviders.currentAndSomeFrequentlyUsedInactiveSourceProviders + sourceProviders.currentAndroidTestSourceProviders)
//     return sourceProviders.buildNamedModuleTemplatesFor(moduleRootDir, selectedSourceProviders)
//   }
//
//   override fun canGeneratePngFromVectorGraphics(): CapabilityStatus {
//     return supportsPngGeneration(module)
//   }
//
//   /**
//    * See the documentation on [AndroidModuleSystem.analyzeDependencyCompatibility]
//    */
//   override fun analyzeDependencyCompatibility(dependenciesToAdd: List<GradleCoordinate>)
//     : Triple<List<GradleCoordinate>, List<GradleCoordinate>, String> =
//     //TODO: Change the API to return a ListenableFuture instead of calling get with a timeout here...
//     dependencyCompatibility.analyzeDependencyCompatibility(dependenciesToAdd).get(30, TimeUnit.SECONDS)
//
//   override fun getManifestOverrides(): ManifestOverrides {
//     val facet = AndroidFacet.getInstance(module)
//     val androidModel = facet?.let(GradleAndroidModel::get) ?: return ManifestOverrides()
//     val directOverrides = notNullMapOf(
//       ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION to androidModel.minSdkVersion?.apiString,
//       ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION to androidModel.targetSdkVersion?.apiString,
//       ManifestSystemProperty.Manifest.VERSION_CODE to androidModel.versionCode?.takeIf { it > 0 }?.toString(),
//       ManifestSystemProperty.Document.PACKAGE to
//         (
//           when (androidModel.androidProject.projectType) {
//             IdeAndroidProjectType.PROJECT_TYPE_APP -> androidModel.applicationId
//             IdeAndroidProjectType.PROJECT_TYPE_ATOM -> androidModel.applicationId
//             IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP -> androidModel.applicationId
//             IdeAndroidProjectType.PROJECT_TYPE_FEATURE -> androidModel.applicationId
//             IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE -> androidModel.applicationId
//             IdeAndroidProjectType.PROJECT_TYPE_TEST -> androidModel.applicationId
//             IdeAndroidProjectType.PROJECT_TYPE_LIBRARY -> getPackageName()
//             IdeAndroidProjectType.PROJECT_TYPE_KOTLIN_MULTIPLATFORM -> getPackageName()
//           }
//           )
//     )
//     val variant = androidModel.selectedVariant
//     val placeholders = getManifestPlaceholders()
//     val directOverridesFromGradle = notNullMapOf(
//       ManifestSystemProperty.UsesSdk.MAX_SDK_VERSION to variant.maxSdkVersion?.toString(),
//       ManifestSystemProperty.Manifest.VERSION_NAME to getVersionNameOverride(facet, androidModel)
//     )
//     return ManifestOverrides(directOverrides + directOverridesFromGradle, placeholders)
//   }
//
//   override fun getManifestPlaceholders(): Map<String, String> {
//     val facet = AndroidFacet.getInstance(module)
//     val androidModel = facet?.let(GradleAndroidModel::get) ?: return emptyMap()
//     return androidModel.selectedVariant.manifestPlaceholders
//   }
//
//   override fun getMergedManifestContributors(): MergedManifestContributors {
//     val facet = module.androidFacet!!
//     val dependencies = getResourceModuleDependencies().mapNotNull { it.androidFacet }
//     return MergedManifestContributors(
//       primaryManifest = facet.sourceProviders.mainManifestFile,
//       flavorAndBuildTypeManifests = facet.getFlavorAndBuildTypeManifests(),
//       libraryManifests = if (facet.configuration.isAppOrFeature) facet.getLibraryManifests(dependencies) else emptyList(),
//       navigationFiles = facet.getTransitiveNavigationFiles(dependencies),
//       flavorAndBuildTypeManifestsOfLibs = facet.getFlavorAndBuildTypeManifestsOfLibs(dependencies)
//     )
//   }
//
//   private fun getVersionNameOverride(facet: AndroidFacet, gradleModel: GradleAndroidModel): String? {
//     val variant = gradleModel.selectedVariant
//     val versionNameWithSuffix = variant.versionNameWithSuffix
//     val versionNameSuffix = variant.versionNameSuffix
//     return when {
//       !versionNameWithSuffix.isNullOrEmpty() -> versionNameWithSuffix
//       versionNameSuffix.isNullOrEmpty() -> null
//       else -> facet.getPrimaryManifestXml()?.versionName.orEmpty() + versionNameSuffix
//     }
//   }
//
//   override fun getPackageName(): String? {
//     val facet = AndroidFacet.getInstance(module) ?: return null
//     return GradleAndroidModel.get(facet)?.androidProject?.namespace
//   }
//
//   override fun getTestPackageName(): String? {
//     val facet = AndroidFacet.getInstance(module) ?: return null
//     val gradleAndroidModel = GradleAndroidModel.get(facet)
//     val variant = gradleAndroidModel?.selectedVariant ?: return null
//     // Only report a test package if the selected variant actually has corresponding androidTest components
//     if (variant.androidTestArtifact == null) return null
//     return gradleAndroidModel.androidProject.testNamespace ?: variant.deprecatedPreMergedTestApplicationId ?: run {
//       // That's how older versions of AGP that do not include testNamespace directly in the model work:
//       // in apps the applicationId from the model is used with the ".test" suffix (ignoring the manifest), in libs
//       // there is no applicationId and the package name from the manifest is used with the suffix.
//       val applicationId = if (facet.configuration.isLibraryProject) getPackageName() else variant.deprecatedPreMergedApplicationId
//       if (applicationId.isNullOrEmpty()) null else "$applicationId.test"
//     }
//   }
//
//   override fun getApplicationIdProvider(): ApplicationIdProvider {
//     val androidFacet = AndroidFacet.getInstance(module) ?: error("Cannot find AndroidFacet. Module: ${module.name}")
//     val androidModel = GradleAndroidModel.get(androidFacet) ?: error("Cannot find GradleAndroidModel. Module: ${module.name}")
//     val forTests =  androidFacet.module.isUnitTestModule() || androidFacet.module.isAndroidTestModule()
//     return GradleApplicationIdProvider.create(
//       androidFacet, forTests, androidModel, androidModel.selectedBasicVariant, androidModel.selectedVariant
//     )
//   }
//
//   override fun getResolveScope(scopeType: ScopeType): GlobalSearchScope {
//     val type = type
//     val mainModule = if (type == AndroidModuleSystem.Type.TYPE_TEST) null else module.getMainModule()
//     val androidTestModule = if (type == AndroidModuleSystem.Type.TYPE_TEST) module.getMainModule() else module.getAndroidTestModule()
//     val unitTestModule = module.getUnitTestModule()
//     val fixturesModule = module.getTestFixturesModule()
//     return when (scopeType) {
//       ScopeType.MAIN -> mainModule?.getModuleWithDependenciesAndLibrariesScope(false)
//       ScopeType.UNIT_TEST -> unitTestModule?.getModuleWithDependenciesAndLibrariesScope(true)
//       ScopeType.ANDROID_TEST -> androidTestModule?.getModuleWithDependenciesAndLibrariesScope(true)
//       ScopeType.TEST_FIXTURES -> fixturesModule?.getModuleWithDependenciesAndLibrariesScope(false)
//       ScopeType.SHARED_TEST -> GlobalSearchScope.EMPTY_SCOPE
//     } ?: GlobalSearchScope.EMPTY_SCOPE
//   }
//
//   override fun getTestArtifactSearchScopes(): TestArtifactSearchScopes = GradleTestArtifactSearchScopes(module)
//
//   private inline fun <T> readFromAgpFlags(read: (IdeAndroidGradlePluginProjectFlags) -> T): T? {
//     return GradleAndroidModel.get(module)?.androidProject?.agpFlags?.let(read)
//   }
//
//   private data class AgpBuildGlobalFlags(
//     val useAndroidX: Boolean,
//     val enableVcsInfo: Boolean
//   )
//
//   /**
//    * Returns the module that is the root for this build.
//    *
//    * This does not traverse across builds if there is a composite build,
//    * or if multiple gradle projects were imported in idea, the value is per
//    * gradle build.
//    */
//   private fun Module.getGradleBuildRootModule(): Module? {
//     val currentPath = module.getGradleProjectPath() ?: return null
//     return project.findModule(currentPath.resolve(":"))
//   }
//
//   /**
//    * For some flags, we know they are global to a build, but are only reported by android projects
//    *
//    * The value is read from any android model in this Gradle build (not traversing included builds)
//    * and cached in the module corresponding to the root of that Gradle build.
//    *
//    * Returns default values if there are no Android models in the same Gradle build as this module
//    */
//   private val agpBuildGlobalFlags: AgpBuildGlobalFlags
//     get() = module.getGradleBuildRootModule()?.let { gradleBuildRoot ->
//       CachedValuesManager.getManager(module.project).getCachedValue(gradleBuildRoot, AgpBuildGlobalFlagsProvider(gradleBuildRoot))
//     } ?: AGP_GLOBAL_FLAGS_DEFAULTS
//
//   private class AgpBuildGlobalFlagsProvider(private val gradleBuildRoot: Module) : CachedValueProvider<AgpBuildGlobalFlags> {
//     override fun compute(): CachedValueProvider.Result<AgpBuildGlobalFlags> {
//       val tracker = ProjectSyncModificationTracker.getInstance(gradleBuildRoot.project)
//       val buildRoot = gradleBuildRoot.getGradleProjectPath()?.buildRoot ?: return CachedValueProvider.Result(null, tracker)
//       val gradleAndroidModel =
//         gradleBuildRoot.project.androidFacetsForNonHolderModules()
//           .filter { it.module.getGradleProjectPath()?.buildRoot == buildRoot }
//           .mapNotNull { GradleAndroidModel.get(it) }
//           .firstOrNull()
//           ?: return CachedValueProvider.Result(null, tracker)
//       val agpBuildGlobalFlags = AgpBuildGlobalFlags(
//         useAndroidX = gradleAndroidModel.androidProject.agpFlags.useAndroidX,
//         enableVcsInfo = gradleAndroidModel.androidProject.agpFlags.enableVcsInfo
//       )
//       return CachedValueProvider.Result(agpBuildGlobalFlags, tracker)
//     }
//   }
//
//   override val usesCompose: Boolean
//     get() = StudioFlags.COMPOSE_PROJECT_USES_COMPOSE_OVERRIDE.get() ||
//       readFromAgpFlags { it.usesCompose } ?: false
//
//   override val codeShrinker: CodeShrinker?
//     get() = when (GradleAndroidModel.get(module)?.selectedVariant?.mainArtifact?.codeShrinker) {
//       com.android.tools.idea.gradle.model.CodeShrinker.PROGUARD -> CodeShrinker.PROGUARD
//       com.android.tools.idea.gradle.model.CodeShrinker.R8 -> CodeShrinker.R8
//       null -> null
//     }
//
//   override val isRClassTransitive: Boolean get() = readFromAgpFlags { it.transitiveRClasses } ?: true
//
//   override fun getTestLibrariesInUse(): TestLibraries? {
//     val androidTestArtifact = GradleAndroidModel.get(module)?.selectedVariant?.androidTestArtifact ?: return null
//     return TestLibraries.newBuilder().also { recordTestLibraries(it, androidTestArtifact) }.build()
//   }
//
//   override fun getDynamicFeatureModules(): List<Module> {
//     val project = GradleAndroidModel.get(module)?.androidProject ?: return emptyList()
//     val ourGradleProjectPath = gradleProjectPath.toHolder()
//     return project.dynamicFeatures.map { dynamicFeature ->
//       val dynamicFeatureGradleProjectPath = ourGradleProjectPath.copy(path = dynamicFeature)
//       dynamicFeatureGradleProjectPath.resolveIn(module.project) ?: error("Missing dynamic feature module: $dynamicFeatureGradleProjectPath")
//     }
//   }
//
//   override fun getBaseFeatureModule(): Module? {
//     val ideAndroidProject = GradleAndroidModel.get(module)?.androidProject ?: return null
//     return ideAndroidProject
//       .baseFeature
//       ?.let { baseFeature -> gradleProjectPath.toHolder().copy(path = baseFeature) }
//       ?.resolveIn(module.project)
//   }
//
//   private val gradleProjectPath: GradleProjectPath get() = module.getGradleProjectPath() ?: error("getGradleProjectPath($module) == null")
//
//   override val isMlModelBindingEnabled: Boolean get() = readFromAgpFlags { it.mlModelBindingEnabled } ?: false
//
//   override val isViewBindingEnabled: Boolean get() = GradleAndroidModel.get(module)?.androidProject?.viewBindingOptions?.enabled ?: false
//
//   override val isKaptEnabled: Boolean get() = GradleAndroidModel.get(module)?.androidProject?.isKaptEnabled ?: false
//
//   override val applicationRClassConstantIds: Boolean get() = readFromAgpFlags { it.applicationRClassConstantIds } ?: true
//
//   override val testRClassConstantIds: Boolean get() = readFromAgpFlags { it.testRClassConstantIds } ?: true
//
//   /**
//    * Whether AndroidX libraries should be used instead of legacy support libraries.
//    *
//    * This property is global to the Gradle build, but only reported in Android models,
//    * so the value is read from the first found android model in the same Gradle build,
//    * and cached on the idea module corresponding to the root of that gradle build.
//    */
//   override val useAndroidX: Boolean get() = agpBuildGlobalFlags.useAndroidX
//
//   override val enableVcsInfo: Boolean get() = agpBuildGlobalFlags.enableVcsInfo
//
//   override val submodules: Collection<Module>
//     get() = moduleHierarchyProvider.submodules
//
//   override val desugarLibraryConfigFilesKnown: Boolean
//     get() = GradleAndroidModel.get(module)?.agpVersion?.let {it >= (DESUGAR_LIBRARY_CONFIG_MINIMUM_AGP_VERSION) } ?: false
//   override val desugarLibraryConfigFilesNotKnownUserMessage: String?
//     get() = when {
//       GradleAndroidModel.get(module) == null -> "Not supported for non-Android modules."
//       !desugarLibraryConfigFilesKnown -> "Only supported for projects using Android Gradle plugin '$DESUGAR_LIBRARY_CONFIG_MINIMUM_AGP_VERSION' and above."
//       else -> null
//     }
//   override val desugarLibraryConfigFiles: List<Path>
//     get() = GradleAndroidModel.get(module)?.androidProject?.desugarLibraryConfigFiles?.map { it.toPath() } ?: emptyList()
//
//   companion object {
//     private val AGP_GLOBAL_FLAGS_DEFAULTS = AgpBuildGlobalFlags(
//       useAndroidX = true,
//       enableVcsInfo = false
//     )
//     private val DESUGAR_LIBRARY_CONFIG_MINIMUM_AGP_VERSION = AgpVersion.parse("8.1.0-alpha05")
//   }
// }
//
//
// private fun AndroidFacet.getLibraryManifests(dependencies: List<AndroidFacet>): List<VirtualFile> {
//   if (isDisposed) return emptyList()
//   val localLibManifests = dependencies.mapNotNull { it.sourceProviders.mainManifestFile }
//   fun IdeAndroidLibrary.manifestFile(): File? = this.folder?.resolve(this.manifest)
//
//   val aarManifests =
//     (listOf(this) + dependencies)
//       .flatMap {
//         GradleAndroidModel.get(it)
//           ?.selectedMainCompileDependencies
//           ?.androidLibraries
//           ?.mapNotNull { it.target.manifestFile() }
//           .orEmpty()
//       }
//       .toSet()
//
//   // Local library manifests come first because they have higher priority.
//   return localLibManifests +
//     // If any of these are null, then the file is specified in the model,
//     // but not actually available yet, such as exploded AAR manifests.
//     aarManifests.mapNotNull { VfsUtil.findFileByIoFile(it, false) }
// }
