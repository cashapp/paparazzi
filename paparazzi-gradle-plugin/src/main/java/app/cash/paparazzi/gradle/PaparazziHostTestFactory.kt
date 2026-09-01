package app.cash.paparazzi.gradle

import app.cash.paparazzi.gradle.instrumentation.ResourcesCompatTransformAction
import app.cash.paparazzi.gradle.utils.capitalize
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Builds a [PaparazziHostTest] for a first-party source set such as `src/screenshotTest`.
 *
 * AGP will not compile a source set it does not know about, so everything a compilation needs is
 * assembled here: the user-facing dependency configurations, resolvable compile/runtime classpaths
 * carrying the variant's resolution attributes, and a Kotlin compile task.
 */
internal class PaparazziHostTestFactory(
  private val project: Project,
  private val extension: AndroidComponentsExtension<*, *, *>,
  private val sourceSetName: String
) {
  /**
   * User-facing dependency configurations, e.g. `screenshotTestImplementation`.
   *
   * These are declarable-only, mirroring how AGP and the Java plugin expose source-set
   * dependencies, and are created once for the project rather than per variant.
   */
  private val declaredConfigurations: List<Configuration> =
    listOf("Implementation", "CompileOnly", "RuntimeOnly").map { suffix ->
      project.configurations.maybeCreate("$sourceSetName$suffix").apply {
        isCanBeConsumed = false
        isCanBeResolved = false
        description = "$suffix dependencies for the '$sourceSetName' Paparazzi source set."
      }
    }

  init {
    // Created eagerly: build scripts declare dependencies against these during evaluation, which
    // happens before any variant callback runs.
    project.kaptConfiguration(sourceSetName)
    project.kspConfiguration(sourceSetName)
  }

  private val implementationConfiguration: Configuration
    get() = declaredConfigurations.first()

  /** Dependencies that apply at compile time: `implementation` + `compileOnly`. */
  private val compileDeclarations: List<Configuration>
    get() = listOf(declaredConfigurations[0], declaredConfigurations[1])

  /** Dependencies that apply at runtime: `implementation` + `runtimeOnly`. */
  private val runtimeDeclarations: List<Configuration>
    get() = listOf(declaredConfigurations[0], declaredConfigurations[2])

  fun implementationConfigurationName(): String = implementationConfiguration.name

  /**
   * Registers the transform that applies Paparazzi's instrumentation to dependency classes.
   *
   * Registered once for the project; the runtime classpath opts in by requesting
   * [PAPARAZZI_INSTRUMENTED_CLASSES].
   */
  private fun registerInstrumentationTransform() {
    if (transformRegistered) return
    transformRegistered = true
    project.dependencies.registerTransform(ResourcesCompatTransformAction::class.java) { spec ->
      spec.from.attribute(ARTIFACT_TYPE, ANDROID_CLASSES_JAR)
      spec.to.attribute(ARTIFACT_TYPE, PAPARAZZI_INSTRUMENTED_CLASSES)
    }
  }

  private var transformRegistered = false

  fun create(variant: Variant): PaparazziHostTest {
    registerInstrumentationTransform()

    val variantSlug = variant.name.capitalize()
    val componentName = "${variant.name}${sourceSetName.capitalize()}"
    val componentSlug = componentName.capitalize()

    val sourceDir = project.layout.projectDirectory.dir("src/$sourceSetName")
    // Java sources are handed to the Kotlin compiler too so it can resolve references into them.
    val kotlinSources = project.fileTree(sourceDir) { tree ->
      tree.include("**/*.kt", "**/*.java")
    }
    val javaSources = project.fileTree(sourceDir) { tree -> tree.include("**/*.java") }

    // The variant's own compiled classes, so tests can see the code they are snapshotting.
    val variantClasses = project.tasks
      .register("paparazzi${variantSlug}VariantClasses", ScopedClassesTask::class.java)
    // PROJECT scope covers this module's own classes, including generated ones such as the R class
    // that Paparazzi tests resolve resources through.
    variant.artifacts.forScope(com.android.build.api.variant.ScopedArtifacts.Scope.PROJECT)
      .use(variantClasses)
      .toGet(com.android.build.api.artifact.ScopedArtifact.CLASSES, { it.jars }, { it.dirs })

    val variantClassFiles = project.files(
      variantClasses.flatMap { it.jars },
      variantClasses.flatMap { it.dirs }
    )

    // Extend the variant's own classpaths so the source set sees everything the code under test
    // depends on, exactly as AGP's unit-test compilation does, plus its own declared dependencies.
    val compileClasspathConfiguration = project.createResolvableClasspath(
      name = "${componentName}CompileClasspath",
      template = variant.compileConfiguration,
      extendsFrom = compileDeclarations + listOf(variant.compileConfiguration)
    )
    val runtimeClasspathConfiguration = project.createResolvableClasspath(
      name = "${componentName}RuntimeClasspath",
      template = variant.runtimeConfiguration,
      extendsFrom = runtimeDeclarations + listOf(variant.runtimeConfiguration)
    )

    // R classes for dependencies are only produced for a real AGP host-test component. Rather than
    // reimplementing that generation, reuse the unit-test component's stub R jar, which is
    // per-variant and independent of anything in `src/test`.
    val stubRClassJar = project.tasks
      .matching { it.name == "generate${variantSlug}UnitTestStubRFile" }
    // Passing the task collection (rather than a plain provider over its outputs) keeps the
    // producing task wired as a dependency, so the jar is generated before anything consumes it.
    val stubRClassFiles = project.files(stubRClassJar)

    // The raw android.jar is a stub: `Build.VERSION.SDK_INT` reads 0, which makes androidx pick
    // pre-API-29 code paths and breaks things like font loading. AGP solves this by running the
    // jar through its mockable-jar transform, which bakes in the real values; reuse that transform
    // rather than putting the stub jar on the classpath.
    val bootClasspath = mockableAndroidJar()

    val compileClasspath = project.files(
      variantClassFiles,
      compileClasspathConfiguration.asClassesClasspath(),
      stubRClassFiles,
      bootClasspath
    )

    val classesDir: Provider<Directory> =
      project.layout.buildDirectory.dir("intermediates/paparazzi/$componentName/classes")
    val javaClassesDir: Provider<Directory> =
      project.layout.buildDirectory.dir("intermediates/paparazzi/$componentName/javaClasses")

    val compileTask = registerKotlinCompile(
      taskName = "compile${componentSlug}Kotlin",
      moduleName = componentName,
      variantSlug = variantSlug,
      sources = kotlinSources,
      classpath = compileClasspath,
      destination = classesDir
    )

    // Recreates the ResourcesCompat font fix that AGP's instrumentation applies to the host-test
    // components it owns.
    val dependencyClasses = project.files(
      runtimeClasspathConfiguration.asClassesClasspath(instrumented = true),
      stubRClassFiles
    )

    val jvmTarget = project.provider {
      compileTask.get().compilerOptions.jvmTarget.orNull?.target
    }
    val ksp = project.registerKspIfNeeded(
      componentName = componentName,
      sourceSetName = sourceSetName,
      kspVersion = KSP_VERSION,
      sources = kotlinSources,
      javaSources = javaSources,
      compileClasspath = compileClasspath,
      jvmTarget = jvmTarget,
      // KSP requires an explicit value; fall back to the Kotlin plugin's own major.minor.
      languageVersion = project.provider {
        compileTask.get().compilerOptions.languageVersion.orNull?.version
          ?: kotlinApiPlugin.pluginVersion.split('.').take(2).joinToString(".")
      }
    )
    if (ksp != null) {
      compileTask.configure { task ->
        task.source(ksp.generatedKotlinSources, ksp.generatedJavaSources)
        task.dependsOn(ksp.task)
      }
    }

    val kapt = project.registerKaptIfNeeded(
      kotlinApiPlugin = kotlinApiPlugin,
      componentName = componentName,
      sourceSetName = sourceSetName,
      kotlinCompileTask = compileTask,
      sources = kotlinSources,
      compileClasspath = compileClasspath
    )
    if (kapt != null) {
      // Generated sources feed back into the Kotlin compilation that triggered processing.
      compileTask.configure { task ->
        task.source(kapt.generatedKotlinSources, kapt.generatedJavaSources)
        task.dependsOn(kapt.tasks)
      }
    }

    // Java is compiled after Kotlin, against Kotlin's output, matching how AGP orders the two.
    val javaCompileTask = project.tasks.register(
      "compile${componentSlug}Java",
      JavaCompile::class.java
    ) { task ->
      task.source(javaSources)
      if (kapt != null) task.source(kapt.generatedJavaSources)
      if (ksp != null) task.source(ksp.generatedJavaSources)
      task.classpath = project.files(compileClasspath, classesDir)
      task.destinationDirectory.set(javaClassesDir)
      task.dependsOn(compileTask)
      val javaExtension = project.extensions.findByType(JavaPluginExtension::class.java)
      if (javaExtension != null) {
        task.sourceCompatibility = javaExtension.sourceCompatibility.toString()
        task.targetCompatibility = javaExtension.targetCompatibility.toString()
      }
    }

    val runtimeClasspath = project.files(
      classesDir,
      javaClassesDir,
      variantClassFiles,
      dependencyClasses,
      bootClasspath
    )

    return PaparazziHostTest(
      name = componentName,
      snapshotDir = project.provider { sourceDir.dir("snapshots") },
      testClassesDirs = project.files(classesDir, javaClassesDir),
      runtimeClasspath = runtimeClasspath,
      compileTasks = listOf(compileTask, javaCompileTask, variantClasses) +
        (kapt?.tasks ?: emptyList()) + listOfNotNull(ksp?.task)
    )
  }

  /**
   * Registers a Kotlin compilation for the source set.
   *
   * [KotlinBaseApiPlugin] is the same entry point AGP uses for its own built-in Kotlin support, and
   * is what makes it possible to compile a source set KGP has no compilation for. Compiler plugins
   * and options are inherited from the variant's main compile task so that things like the Compose
   * compiler apply here too.
   */
  private val kotlinApiPlugin: KotlinBaseApiPlugin by lazy {
    project.plugins.apply(KotlinBaseApiPlugin::class.java)
  }

  private fun registerKotlinCompile(
    taskName: String,
    moduleName: String,
    variantSlug: String,
    sources: org.gradle.api.file.FileTree,
    classpath: org.gradle.api.file.FileCollection,
    destination: Provider<Directory>
  ): KotlinCompileProvider {
    // Resolved lazily: the variant's Kotlin task may not be registered yet when this runs, and it
    // does not exist at all for Java-only projects.
    val mainCompileTasks = project.tasks
      .withType(KotlinJvmCompile::class.java)
      .matching { it.name == "compile${variantSlug}Kotlin" }

    val compileTask = kotlinApiPlugin.registerKotlinJvmCompileTask(taskName, moduleName)
    compileTask.configure { task ->
      task.source(sources)
      task.libraries.from(classpath)
      task.destinationDirectory.set(destination)
      task.multiPlatformEnabled.set(false)
      task.sourceSetName.set(sourceSetName)

      // Inherit compiler plugins and options from the variant so that, for example, the Compose
      // compiler applies to this source set exactly as it does to `src/main`.
      task.pluginClasspath.from(
        project.provider { mainCompileTasks.flatMap { it.pluginClasspath.files } }
      )
      task.pluginOptions.addAll(
        project.provider { mainCompileTasks.flatMap { it.pluginOptions.get() } }
      )
      task.compilerOptions.jvmTarget.set(
        project.provider {
          mainCompileTasks.firstOrNull()?.compilerOptions?.jvmTarget?.orNull
        }
      )
      task.compilerOptions.freeCompilerArgs.addAll(
        project.provider {
          mainCompileTasks.firstOrNull()?.compilerOptions?.freeCompilerArgs?.orNull ?: emptyList()
        }
      )
    }
    return compileTask
  }

  /**
   * Resolves the android.jar through AGP's globally-registered mockable-jar transform.
   */
  private fun mockableAndroidJar(): org.gradle.api.file.FileCollection {
    val configuration = project.configurations.maybeCreate(MOCKABLE_ANDROID_JAR_CONFIGURATION)
    configuration.isCanBeConsumed = false
    configuration.isCanBeResolved = true
    if (configuration.dependencies.isEmpty()) {
      project.dependencies.add(
        configuration.name,
        project.files(extension.sdkComponents.bootClasspath)
      )
    }
    val returnDefaultValues = project.extensions
      .findByType(com.android.build.api.dsl.CommonExtension::class.java)
      ?.testOptions?.unitTests?.isReturnDefaultValues == true
    return configuration.incoming.artifactView { view ->
      view.attributes.attribute(ARTIFACT_TYPE, ANDROID_MOCKABLE_JAR)
      view.attributes.attribute(MOCKABLE_JAR_RETURN_DEFAULT_VALUES, returnDefaultValues)
    }.files
  }

  internal companion object {
    const val SCREENSHOT_TEST_SOURCE_SET: String = "screenshotTest"
    private const val MOCKABLE_ANDROID_JAR_CONFIGURATION = "paparazziMockableAndroidJar"

    /** Guard against Paparazzi being declared on a source set it cannot run on. */
    val TEST_SOURCE_SET_NAME: String = SourceSet.TEST_SOURCE_SET_NAME
  }
}
