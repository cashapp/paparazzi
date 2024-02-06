///*
// * Copyright (C) 2021 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.android.tools.idea.res;
//
//import static com.android.AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE;
//
//import com.android.tools.idea.projectsystem.AndroidModuleSystem;
//import com.android.tools.idea.projectsystem.ProjectSystemUtil;
//import com.intellij.ProjectTopics;
//import com.intellij.facet.Facet;
//import com.intellij.facet.FacetManager;
//import com.intellij.facet.FacetManagerAdapter;
//import com.intellij.openapi.Disposable;
//import com.intellij.openapi.diagnostic.Logger;
//import com.intellij.openapi.module.Module;
//import com.intellij.openapi.progress.ProgressManager;
//import com.intellij.openapi.project.ModuleListener;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.roots.DependencyScope;
//import com.intellij.openapi.roots.ModuleOrderEntry;
//import com.intellij.openapi.roots.ModuleRootManager;
//import com.intellij.openapi.roots.OrderEntry;
//import com.intellij.openapi.util.Disposer;
//import com.intellij.openapi.util.Key;
//import com.intellij.util.containers.ContainerUtil;
//import com.intellij.util.messages.MessageBusConnection;
//import java.lang.ref.WeakReference;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Objects;
//import java.util.Set;
//import java.util.concurrent.atomic.AtomicReference;
//import java.util.stream.Collectors;
//import org.jetbrains.android.facet.AndroidFacet;
//import org.jetbrains.annotations.NotNull;
//
//public class AndroidDependenciesCache implements Disposable {
//  private static final Key<AndroidDependenciesCache> KEY = Key.create(AndroidDependenciesCache.class.getName());
//
//  private final Module myModule;
//  private final AtomicReference<List<WeakReference<AndroidFacet>>> myAllDependencies = new AtomicReference<>();
//  private final AtomicReference<List<WeakReference<AndroidFacet>>> myAllLibraryDependencies = new AtomicReference<>();
//
//  private AndroidDependenciesCache(@NotNull Module module) {
//    myModule = module;
//
//    AndroidProjectRootListener.ensureSubscribed(module.getProject());
//
//    // ProjectTopics.MODULES publishes on the project bus, FACETS_TOPIC is per module but broadcasts to parent buses. For this
//    // AndroidDependenciesCache we are interested in any changes in the project.
//    MessageBusConnection busConnection = module.getProject().getMessageBus().connect(this);
//
//    busConnection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
//      @Override
//      public void facetAdded(@NotNull Facet facet) {
//        dropCache();
//      }
//
//      @Override
//      public void facetRemoved(@NotNull Facet facet) {
//        dropCache();
//      }
//
//      @Override
//      public void facetConfigurationChanged(@NotNull Facet facet) {
//        dropCache();
//      }
//    });
//
//    busConnection.subscribe(ProjectTopics.MODULES, new ModuleListener() {
//      @Override
//      public void modulesAdded(@NotNull Project project, @NotNull List<? extends Module> modules) {
//        dropCache();
//      }
//
//      @Override
//      public void moduleRemoved(@NotNull Project project, @NotNull Module module) {
//        dropCache();
//      }
//    });
//  }
//
//  @Override
//  public void dispose() {}
//
//  /**
//   * This method is called by {@link AndroidProjectRootListener} when module dependencies change.
//   */
//  public synchronized void dropCache() {
//    myAllDependencies.set(null);
//    myAllLibraryDependencies.set(null);
//  }
//
//  @NotNull
//  public static synchronized AndroidDependenciesCache getInstance(@NotNull Module module) {
//    AndroidDependenciesCache cache = module.getUserData(KEY);
//
//    if (cache == null) {
//      cache = new AndroidDependenciesCache(module);
//      Disposer.register(module, cache);
//      module.putUserData(KEY, cache);
//    }
//    return cache;
//  }
//
//  @NotNull
//  public synchronized List<AndroidFacet> getAllAndroidDependencies(boolean androidLibrariesOnly) {
//    return getAllAndroidDependencies(myModule, androidLibrariesOnly, getListRef(androidLibrariesOnly));
//  }
//
//  /**
//   * Returns the AndroidFacets corresponding to all the Android modules that the given module
//   * transitively depends on.
//   * <p/>
//   * Callers who need to know a module's dependencies <b>in the context of resolving resources</b>
//   * should consider using {@link AndroidDependenciesCache#getAndroidResourceDependencies(Module)} instead,
//   * as that method may exclude irrelevant modules depending on the build system.
//   */
//  @NotNull
//  public static List<AndroidFacet> getAllAndroidDependencies(@NotNull Module module, boolean androidLibrariesOnly) {
//    return AndroidDependenciesCache.getInstance(module).getAllAndroidDependencies(androidLibrariesOnly);
//  }
//
//  /**
//   * Returns the AndroidFacets corresponding to the Android modules that the given module transitively
//   * depends on for resources.
//   * <p/>
//   * Note that this method should only be used to find dependencies in the context of resolving Android
//   * resources. This is a special case where, depending on the build system, we may be able to ignore
//   * certain modules to improve performance. If you want to find <b>all</b> the modules that a given module
//   * depends on according to its order entries, use {@link AndroidDependenciesCache#getAllAndroidDependencies}.
//   * <p/>
//   * TODO(b/118317486): Remove this API once resource module dependencies can accurately
//   * be determined from order entries for all supported build systems.
//   *
//   * @see AndroidModuleSystem#getResourceModuleDependencies()
//   */
//  public static List<AndroidFacet> getAndroidResourceDependencies(@NotNull Module module) {
//    return ProjectSystemUtil.getModuleSystem(module)
//      .getResourceModuleDependencies()
//      .stream()
//      .map(AndroidFacet::getInstance)
//      .filter(Objects::nonNull)
//      .collect(Collectors.toList());
//  }
//
//  @NotNull
//  private AtomicReference<List<WeakReference<AndroidFacet>>> getListRef(boolean androidLibrariesOnly) {
//    return androidLibrariesOnly ? myAllLibraryDependencies : myAllDependencies;
//  }
//
//  @NotNull
//  private static List<AndroidFacet> getAllAndroidDependencies(@NotNull Module module,
//    boolean androidLibrariesOnly,
//    @NotNull AtomicReference<List<WeakReference<AndroidFacet>>> listRef) {
//    if (module.isDisposed()) {
//      return Collections.emptyList();
//    }
//
//    List<WeakReference<AndroidFacet>> refs = listRef.get();
//
//    if (refs == null) {
//      List<AndroidFacet> facets = new ArrayList<>();
//      collectAllAndroidDependencies(module, androidLibrariesOnly, facets, new HashSet<>());
//
//      refs = ContainerUtil.map(ContainerUtil.reverse(facets), facet -> new WeakReference<>(facet));
//      listRef.set(refs);
//    }
//    return dereference(refs);
//  }
//
//  @NotNull
//  private static List<AndroidFacet> dereference(@NotNull List<WeakReference<AndroidFacet>> refs) {
//    return ContainerUtil.mapNotNull(refs, ref -> {
//      AndroidFacet facet = ref.get();
//      if (facet == null) {
//        logNullReference();
//        return null;
//      }
//
//      if (facet.isDisposed()) {
//        logDisposedFacet();
//        return null;
//      }
//
//      AndroidFacet facetFromModule = AndroidFacet.getInstance(facet.getModule());
//      if (facetFromModule == null) {
//        logNonAndroidModule();
//        return null;
//      }
//
//      if (facetFromModule != facet) {
//        // This means the facet thinks it belongs to a module which uses a different instance of AndroidFacet already.
//        logObsoleteFacet();
//        return null;
//      }
//
//      return facet;
//    });
//  }
//
//  private static void logObsoleteFacet() {
//    Logger.getInstance(AndroidDependenciesCache.class).warn("obsolete facet");
//  }
//
//  private static void logNonAndroidModule() {
//    Logger.getInstance(AndroidDependenciesCache.class).warn("non-Android module");
//  }
//
//  private static void logDisposedFacet() {
//    Logger.getInstance(AndroidDependenciesCache.class).warn("disposed facet");
//  }
//
//  private static void logNullReference() {
//    Logger.getInstance(AndroidDependenciesCache.class).warn("null in dereference");
//  }
//
//  private static void collectAllAndroidDependencies(@NotNull Module module,
//    boolean androidLibrariesOnly,
//    @NotNull List<AndroidFacet> result,
//    @NotNull Set<AndroidFacet> visited) {
//    AndroidFacet facet = AndroidFacet.getInstance(module);
//    boolean isDynamicFeature = facet != null && facet.getConfiguration().getProjectType() == PROJECT_TYPE_DYNAMIC_FEATURE;
//    OrderEntry[] entries = ModuleRootManager.getInstance(module).getOrderEntries();
//    // Loop in the reverse order to resolve dependencies on the libraries, so that if a library
//    // is required by two higher level libraries it can be inserted in the correct place.
//
//    for (int i = entries.length; --i >= 0;) {
//      ProgressManager.checkCanceled();
//      OrderEntry orderEntry = entries[i];
//      if (orderEntry instanceof ModuleOrderEntry) {
//        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
//
//        if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
//          Module depModule = moduleOrderEntry.getModule();
//
//          if (depModule != null) {
//            AndroidFacet depFacet = AndroidFacet.getInstance(depModule);
//
//            if (depFacet != null &&
//              !(androidLibrariesOnly && depFacet.getConfiguration().isAppProject() && !isDynamicFeature) &&
//              visited.add(depFacet)) {
//              List<WeakReference<AndroidFacet>> cachedDepDeps = getInstance(depModule).getListRef(androidLibrariesOnly).get();
//
//              if (cachedDepDeps != null) {
//                List<AndroidFacet> depDeps = dereference(cachedDepDeps);
//
//                for (AndroidFacet depDepFacet : depDeps) {
//                  if (visited.add(depDepFacet)) {
//                    result.add(depDepFacet);
//                  }
//                }
//              }
//              else {
//                collectAllAndroidDependencies(depModule, androidLibrariesOnly, result, visited);
//              }
//              result.add(depFacet);
//            }
//          }
//        }
//      }
//    }
//  }
//}
