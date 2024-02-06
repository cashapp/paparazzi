///*
// * Copyright (C) 2016 The Android Open Source Project
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
//import com.android.tools.res.LocalResourceRepository;
//import com.android.tools.res.MultiResourceRepository;
//import com.google.common.collect.ImmutableList;
//import com.intellij.openapi.util.Disposer;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import org.jetbrains.android.facet.AndroidFacet;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.TestOnly;
//
///**
// * @see StudioResourceRepositoryManager#getProjectResources()
// */
//final class ProjectResourceRepository extends MultiResourceRepository {
//  private final AndroidFacet myFacet;
//
//  private ProjectResourceRepository(@NotNull AndroidFacet facet, @NotNull List<LocalResourceRepository> localResources) {
//    super(facet.getModule().getName() + " with modules");
//    myFacet = facet;
//    setChildren(localResources, ImmutableList.of(), ImmutableList.of());
//  }
//
//  @NotNull
//  public static ProjectResourceRepository create(@NotNull AndroidFacet facet) {
//    List<LocalResourceRepository> resources = computeRepositories(facet);
//    return new ProjectResourceRepository(facet, resources);
//  }
//
//  @NotNull
//  private static List<LocalResourceRepository> computeRepositories(@NotNull AndroidFacet facet) {
//    LocalResourceRepository main = StudioResourceRepositoryManager.getModuleResources(facet);
//
//    // List of module facets the given module depends on.
//    List<AndroidFacet> dependencies = AndroidDependenciesCache.getAndroidResourceDependencies(facet.getModule());
//    if (dependencies.isEmpty()) {
//      return Collections.singletonList(main);
//    }
//
//    List<LocalResourceRepository> resources = new ArrayList<>(dependencies.size() + 1);
//    resources.add(main);
//    for (AndroidFacet dependency : dependencies) {
//      resources.add(StudioResourceRepositoryManager.getModuleResources(dependency));
//    }
//
//    return resources;
//  }
//
//  void updateRoots() {
//    List<LocalResourceRepository> repositories = computeRepositories(myFacet);
//    invalidateResourceDirs();
//    setChildren(repositories, ImmutableList.of(), ImmutableList.of());
//  }
//
//  @TestOnly
//  @NotNull
//  static ProjectResourceRepository createForTest(@NotNull AndroidFacet facet, @NotNull List<LocalResourceRepository> modules) {
//    ProjectResourceRepository repository = new ProjectResourceRepository(facet, modules);
//    Disposer.register(facet, repository);
//    return repository;
//  }
//}
