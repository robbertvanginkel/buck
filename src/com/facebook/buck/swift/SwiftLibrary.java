/*
 * Copyright 2016-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.swift;

import com.facebook.buck.cxx.Archive;
import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.cxx.CxxPreprocessables;
import com.facebook.buck.cxx.CxxPreprocessorDep;
import com.facebook.buck.cxx.CxxPreprocessorInput;
import com.facebook.buck.cxx.toolchain.HeaderVisibility;
import com.facebook.buck.cxx.NativeLinkableCacheKey;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.linker.Linker;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkable;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkableInput;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.HasRuntimeDeps;
import com.facebook.buck.rules.NoopBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.FileListableLinkerInputArg;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.util.MoreCollectors;
import com.facebook.buck.util.RichStream;
import com.google.common.base.Preconditions;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An action graph representation of a Swift library from the target graph, providing the various
 * interfaces to make it consumable by C/C native linkable rules.
 */
class SwiftLibrary extends NoopBuildRuleWithDeclaredAndExtraDeps
    implements HasRuntimeDeps, NativeLinkable, CxxPreprocessorDep {

  private final Map<NativeLinkableCacheKey, NativeLinkableInput> nativeLinkableCache =
      new HashMap<>();

  private final LoadingCache<CxxPlatform, ImmutableMap<BuildTarget, CxxPreprocessorInput>>
      transitiveCxxPreprocessorInputCache =
          CxxPreprocessables.getTransitiveCxxPreprocessorInputCache(this);

  private final BuildRuleResolver ruleResolver;

  private final Collection<? extends BuildRule> exportedDeps;
  private final ImmutableSet<FrameworkPath> frameworks;
  private final ImmutableSet<FrameworkPath> libraries;
  private final FlavorDomain<SwiftPlatform> swiftPlatformFlavorDomain;
  private final Optional<Pattern> supportedPlatformsRegex;
  private final Linkage linkage;

  SwiftLibrary(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver ruleResolver,
      Collection<? extends BuildRule> exportedDeps,
      FlavorDomain<SwiftPlatform> swiftPlatformFlavorDomain,
      ImmutableSet<FrameworkPath> frameworks,
      ImmutableSet<FrameworkPath> libraries,
      Optional<Pattern> supportedPlatformsRegex,
      Linkage linkage) {
    super(buildTarget, projectFilesystem, params);
    this.ruleResolver = ruleResolver;
    this.exportedDeps = exportedDeps;
    this.frameworks = frameworks;
    this.libraries = libraries;
    this.swiftPlatformFlavorDomain = swiftPlatformFlavorDomain;
    this.supportedPlatformsRegex = supportedPlatformsRegex;
    this.linkage = linkage;
  }

  private boolean isPlatformSupported(CxxPlatform cxxPlatform) {
    return !supportedPlatformsRegex.isPresent()
        || supportedPlatformsRegex.get().matcher(cxxPlatform.getFlavor().toString()).find();
  }

  @Override
  public Iterable<NativeLinkable> getNativeLinkableDeps() {
    // TODO(beng, markwang): Use pseudo targets to represent the Swift
    // runtime library's linker args here so NativeLinkables can
    // deduplicate the linker flags on the build target (which would be the same for
    // all libraries).
    return RichStream.from(getDeclaredDeps())
        .filter(NativeLinkable.class)
        .collect(MoreCollectors.toImmutableSet());
  }

  @Override
  public Iterable<? extends NativeLinkable> getNativeLinkableExportedDeps() {
    throw new RuntimeException(
        "SwiftLibrary does not support getting linkable exported deps "
            + "without a specific platform.");
  }

  @Override
  public Iterable<? extends NativeLinkable> getNativeLinkableExportedDepsForPlatform(
      CxxPlatform cxxPlatform) {
    if (!isPlatformSupported(cxxPlatform)) {
      return ImmutableList.of();
    }
    SwiftRuntimeNativeLinkable swiftRuntimeNativeLinkable =
        new SwiftRuntimeNativeLinkable(swiftPlatformFlavorDomain.getValue(cxxPlatform.getFlavor()));
    return RichStream.from(exportedDeps)
        .filter(NativeLinkable.class)
        .concat(RichStream.of(swiftRuntimeNativeLinkable))
        .collect(MoreCollectors.toImmutableSet());
  }

  private NativeLinkableInput getNativeLinkableInputUncached(
      CxxPlatform cxxPlatform, Linker.LinkableDepType type, boolean forceLinkWhole) {

    if (!isPlatformSupported(cxxPlatform)) {
      return NativeLinkableInput.of();
    }

    // Build up the arguments used to link this library.  If we're linking the
    // whole archive, wrap the library argument in the necessary "ld" flags.

    boolean isStatic;
    switch (linkage) {
      case STATIC:
        isStatic = true;
        break;
      case SHARED:
        isStatic = false;
        break;
      case ANY:
        isStatic = type != Linker.LinkableDepType.SHARED;
        break;
      default:
        throw new IllegalStateException("unhandled linkage type: " + linkage);
    }
    ImmutableList.Builder<Arg> linkerArgsBuilder = ImmutableList.builder();
    if (isStatic) {
      Archive archive =
          (Archive)
              requireBuildRule(
                  cxxPlatform.getFlavor(),
                  type == Linker.LinkableDepType.STATIC
                      ? CxxDescriptionEnhancer.STATIC_FLAVOR
                      : CxxDescriptionEnhancer.STATIC_PIC_FLAVOR);
      if (forceLinkWhole) {
        Linker linker = cxxPlatform.getLd().resolve(ruleResolver);
        linkerArgsBuilder.addAll(linker.linkWhole(archive.toArg()));
      } else {
        Arg libraryArg = archive.toArg();
        if (libraryArg instanceof SourcePathArg) {
          linkerArgsBuilder.add(
              FileListableLinkerInputArg.withSourcePathArg((SourcePathArg) libraryArg));
        } else {
          linkerArgsBuilder.add(libraryArg);
        }
      }
    } else {
      BuildRule rule =
          requireBuildRule(
              cxxPlatform.getFlavor(),
              cxxPlatform.getSharedLibraryInterfaceParams().isPresent()
                  ? CxxLibraryDescription.Type.SHARED_INTERFACE.getFlavor()
                  : CxxLibraryDescription.Type.SHARED.getFlavor());
      linkerArgsBuilder.add(
          SourcePathArg.of(Preconditions.checkNotNull(rule.getSourcePathToOutput())));
    }
    final ImmutableList<Arg> linkerArgs = linkerArgsBuilder.build();

    return NativeLinkableInput.of(
        linkerArgs, Preconditions.checkNotNull(frameworks), Preconditions.checkNotNull(libraries));
  }

  @Override
  public NativeLinkableInput getNativeLinkableInput(
      CxxPlatform cxxPlatform,
      Linker.LinkableDepType type,
      boolean forceLinkWhole,
      ImmutableSet<NativeLinkable.LanguageExtensions> languageExtensions) {
    NativeLinkableCacheKey key =
        NativeLinkableCacheKey.of(cxxPlatform.getFlavor(), type, forceLinkWhole);
    NativeLinkableInput input = nativeLinkableCache.get(key);
    if (input == null) {
      input = getNativeLinkableInputUncached(cxxPlatform, type, forceLinkWhole);
      nativeLinkableCache.put(key, input);
    }
    return input;
  }

  public BuildRule requireBuildRule(Flavor... flavors) {
    return ruleResolver.requireRule(getBuildTarget().withAppendedFlavors(flavors));
  }

  @Override
  public ImmutableMap<String, SourcePath> getSharedLibraries(CxxPlatform cxxPlatform) {
    if (!isPlatformSupported(cxxPlatform)) {
      return ImmutableMap.of();
    }
    ImmutableMap.Builder<String, SourcePath> libs = ImmutableMap.builder();
    String sharedLibrarySoname =
        CxxDescriptionEnhancer.getSharedLibrarySoname(Optional.empty(), getBuildTarget(), cxxPlatform);
    BuildRule sharedLibraryBuildRule =
        requireBuildRule(cxxPlatform.getFlavor(), CxxDescriptionEnhancer.SHARED_FLAVOR);

    libs.put(sharedLibrarySoname, sharedLibraryBuildRule.getSourcePathToOutput());
    return libs.build();
  }

  @Override
  public NativeLinkable.Linkage getPreferredLinkage(CxxPlatform cxxPlatform) {
    return linkage;
  }

  @Override
  public Stream<BuildTarget> getRuntimeDeps(SourcePathRuleFinder ruleFinder) {
    // We export all declared deps as runtime deps, to setup a transitive runtime dep chain which
    // will pull in runtime deps (e.g. other binaries) or transitive C/C++ libraries.  Since the
    // `CxxLibrary` rules themselves are noop meta rules, they shouldn't add any unnecessary
    // overhead.
    return Stream.concat(
            getDeclaredDeps().stream(), StreamSupport.stream(exportedDeps.spliterator(), false))
        .map(BuildRule::getBuildTarget);
  }

  @Override
  public Iterable<CxxPreprocessorDep> getCxxPreprocessorDeps(CxxPlatform cxxPlatform) {
    return getBuildDeps()
        .stream()
        .filter(CxxPreprocessorDep.class::isInstance)
        .map(CxxPreprocessorDep.class::cast)
        .collect(MoreCollectors.toImmutableSet());
  }

  @Override
  public CxxPreprocessorInput getCxxPreprocessorInput(CxxPlatform cxxPlatform) {
    return getCxxPreprocessorInput(cxxPlatform, HeaderVisibility.PUBLIC);
  }

  private CxxPreprocessorInput getCxxPreprocessorInput(
      CxxPlatform cxxPlatform, HeaderVisibility headerVisibility) {
    // Handle via metadata query.
    return CxxLibraryDescription.queryMetadataCxxPreprocessorInput(
        ruleResolver, getBuildTarget(), cxxPlatform, headerVisibility)
        .orElseThrow(IllegalStateException::new);
  }

  @Override
  public ImmutableMap<BuildTarget, CxxPreprocessorInput> getTransitiveCxxPreprocessorInput(
      CxxPlatform cxxPlatform) {
    return transitiveCxxPreprocessorInputCache.getUnchecked(cxxPlatform);
  }
}
