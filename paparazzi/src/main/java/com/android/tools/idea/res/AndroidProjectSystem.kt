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
// @file:JvmName("ProjectSystemUtil")
//
// package com.android.tools.idea.projectsystem
//
// import com.android.tools.idea.model.AndroidModel
// import com.android.tools.idea.model.ClassJarProvider
// import com.android.tools.idea.run.ApkProvider
// import com.android.tools.idea.run.ApkProvisionException
// import com.android.tools.idea.run.ApplicationIdProvider
// import com.android.tools.idea.run.ValidationError
// import com.intellij.execution.configurations.RunConfiguration
// import com.intellij.facet.ProjectFacetManager
// import com.intellij.openapi.application.runReadAction
// import com.intellij.openapi.extensions.ExtensionPointName
// import com.intellij.openapi.module.Module
// import com.intellij.openapi.module.ModuleUtilCore
// import com.intellij.openapi.project.Project
// import com.intellij.openapi.roots.ProjectFileIndex
// import com.intellij.openapi.vfs.VirtualFile
// import com.intellij.psi.PsiElement
// import com.intellij.psi.PsiElementFinder
// import com.intellij.util.containers.ContainerUtil
// import org.jetbrains.android.facet.AndroidFacet
// import java.nio.file.Path
//
// /**
//  * Provides a build-system-agnostic interface to the build system. Instances of this interface
//  * only apply to a specific [Project].
//  */
// interface AndroidProjectSystem: ModuleHierarchyProvider {
//
//   /**
//    * Returns path to android.jar
//    */
//   fun getBootClasspath(module: Module): Collection<String>
//
//   /**
//    * Uses build-system-specific heuristics to locate the APK file produced by the given project, or null if none. The heuristics try
//    * to determine the most likely APK file corresponding to the application the user is working on in the project's current configuration.
//    */
//   fun getDefaultApkFile(): VirtualFile?
//
//   /**
//    * Returns the absolute filesystem path to the aapt executable being used for the given project.
//    */
//   fun getPathToAapt(): Path
//
//   /**
//    * Returns true if the project allows adding new modules.
//    */
//   fun allowsFileCreation(): Boolean
//
//   /**
//    * Returns an interface for interacting with the given module.
//    */
//   fun getModuleSystem(module: Module): AndroidModuleSystem
//
//   /**
//    * Returns the best effort [ApplicationIdProvider] for the given project and [runConfiguration].
//    *
//    * NOTE: The returned application id provider represents the current build configuration and may become invalid if it changes,
//    *       hence this reference should not be cached.
//    *
//    * Some project systems may be unable to retrieve the package name if no [runConfiguration] is provided or before
//    * the project has been successfully built. The returned [ApplicationIdProvider] will throw [ApkProvisionException]'s
//    * or return a name derived from incomplete configuration in this case.
//    */
//   fun getApplicationIdProvider(runConfiguration: RunConfiguration): ApplicationIdProvider? = null
//
//   /**
//    * Returns the [ApkProvider] for the given [runConfiguration].
//    *
//    * NOTE: The returned apk provider represents the current build configuration and may become invalid if it changes,
//    *       hence this reference should not be cached.
//    *
//    * Returns `null`, if the project system does not recognize the [runConfiguration] as a supported one.
//    */
//   fun getApkProvider(runConfiguration: RunConfiguration): ApkProvider? = null
//
//   fun validateRunConfiguration(runConfiguration: RunConfiguration): List<ValidationError> {
//     return listOf(ValidationError.fatal("Run configuration ${runConfiguration.name} is not supported in this project"));
//   }
//
//   /**
//    * Returns an instance of [ProjectSystemSyncManager] that applies to the project.
//    */
//   fun getSyncManager(): ProjectSystemSyncManager
//
//   fun getBuildManager(): ProjectSystemBuildManager
//
//   /**
//    * [PsiElementFinder]s used with the given build system, e.g. for the R classes.
//    *
//    * These finders should not be registered as extensions
//    */
//   fun getPsiElementFinders(): Collection<PsiElementFinder>
//
//   /**
//    * [LightResourceClassService] instance used by this project system (if used at all).
//    */
//   fun getLightResourceClassService(): LightResourceClassService
//
//   /**
//    * [SourceProvidersFactory] instance used by the project system internally to re-instantiate the cached instance
//    * when the structure of the project changes.
//    */
//   fun getSourceProvidersFactory(): SourceProvidersFactory
//
//   /**
//    * Returns a source provider describing build configuration files.
//    */
//   fun getBuildConfigurationSourceProvider(): BuildConfigurationSourceProvider? = null
//
//   /**
//    * @return A provider for finding .class output files and external .jars.
//    */
//   fun getClassJarProvider(): ClassJarProvider
//   /**
//    * Returns a list of [AndroidFacet]s by given package name.
//    */
//   fun getAndroidFacetsWithPackageName(project: Project, packageName: String): Collection<AndroidFacet>
//
//   /**
//    * Returns true if the given [packageName] is either one of the namespaces, or a parent.
//    *
//    * For example, if the project contains `com.example.myapp` and `com.example.mylib`, this
//    * would return true for exactly
//    *    `com`, `com.example`, `com.example.myapp` and `com.example.mylib`.
//    *
//    * This method may return false for packages that do exist if it is called when project
//    * sync has failed, or for non-gradle build systems if it is called before indexes are ready,
//    * but it should never throw IndexNotReadyException.
//    */
//   fun isNamespaceOrParentPackage(packageName: String): Boolean
//
//   /**
//    * @return all the application IDs of artifacts this project module is known to produce.
//    */
//   fun getKnownApplicationIds(project: Project): Set<String> = emptySet()
//
//   /**
//    * @return true if the project's build system supports building the app with a profiling mode flag (profileable, debuggable, etc.).
//    */
//   fun supportsProfilingMode() = false
//
// }
//
// val EP_NAME = ExtensionPointName<AndroidProjectSystemProvider>("com.android.project.projectsystem")
//
// /**
//  * Returns the instance of {@link AndroidProjectSystem} that applies to the given {@link Project}.
//  */
// fun Project.getProjectSystem(): AndroidProjectSystem {
//   return ProjectSystemService.getInstance(this).projectSystem
// }
//
// /**
//  * Returns the instance of [ProjectSystemSyncManager] that applies to the given [Project].
//  */
// fun Project.getSyncManager(): ProjectSystemSyncManager {
//   return getProjectSystem().getSyncManager()
// }
//
// /**
//  * Returns the instance of [AndroidModuleSystem] that applies to the given [Module].
//  */
// fun Module.getModuleSystem(): AndroidModuleSystem {
//   return project.getProjectSystem().getModuleSystem(this)
// }
//
// /**
//  * Returns the instance of [AndroidModuleSystem] that applies to the given [AndroidFacet].
//  */
// fun AndroidFacet.getModuleSystem(): AndroidModuleSystem {
//   return module.getModuleSystem()
// }
//
// /**
//  * Returns the instance of [AndroidModuleSystem] that applies to the given [PsiElement], if it can be determined.
//  */
// fun PsiElement.getModuleSystem(): AndroidModuleSystem? = ModuleUtilCore.findModuleForPsiElement(this)?.getModuleSystem()
//
//
// /**
//  * Returns a list of all Android holder modules. These are the intellij [Module] objects that correspond to an emptyish (no roots/deps)
//  * module that contains the other source set modules as children. If you need to obtain the actual module for the currently active source
//  * set then please you [getMainModule] on the return [Module] objects.
//  *
//  * If [additionalFilter] is supplied then the modules list returns will also only contain modules passing that filter.
//  */
// fun Project.getAndroidModulesForDisplay(additionalFilter: ((Module) -> Boolean)? = null) : List<Module> {
//   return ProjectFacetManager.getInstance(this).getModulesWithFacet(AndroidFacet.ID).filter { module ->
//     module.isHolderModule() && (additionalFilter?.invoke(module) ?: true)
//   }
// }
//
// /**
//  * Returns a list of AndroidFacets attached to holder modules.
//  *
//  * Note: A copy of AndroidFacet is attached to all source set modules so we need to filter only the ones belong to holder modules here.
//  */
// fun Project?.getAndroidFacets(): List<AndroidFacet> {
//   return this?.let {
//     ProjectFacetManager.getInstance(this).getFacets(AndroidFacet.ID).filter { facet ->
//       facet.module.isHolderModule()
//     }
//   } ?: listOf()
// }
//
// /**
//  * Indicates whether the given project has at least one module backed by build models.
//  */
// fun Project.requiresAndroidModel(): Boolean {
//   val androidFacets: List<AndroidFacet> = getAndroidFacets()
//   return ContainerUtil.exists(androidFacets) { facet: AndroidFacet -> AndroidModel.isRequired(facet) }
// }
//
// fun isAndroidTestFile(project: Project, file: VirtualFile?) = runReadAction {
//   val module = file?.let { ProjectFileIndex.getInstance(project).getModuleForFile(file) }
//   module?.let { TestArtifactSearchScopes.getInstance(module)?.isAndroidTestSource(file) } ?: false
// }
//
// fun isUnitTestFile(project: Project, file: VirtualFile?) = runReadAction {
//   val module = file?.let { ProjectFileIndex.getInstance(project).getModuleForFile(file) }
//   module?.let { TestArtifactSearchScopes.getInstance(module)?.isUnitTestSource(file) } ?: false
// }
//
// fun isTestFile(project: Project, file: VirtualFile?) = isUnitTestFile(project, file) || isAndroidTestFile(project, file)
