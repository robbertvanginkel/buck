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

import static com.facebook.buck.cxx.CxxLibraryDescription.METADATA_TYPE;

import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxHeaders;
import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.cxx.CxxPreprocessables;
import com.facebook.buck.cxx.CxxPreprocessorInput;
import com.facebook.buck.cxx.CxxSymlinkTreeHeaders;
import com.facebook.buck.cxx.HeaderSymlinkTreeWithModuleMap;
import com.facebook.buck.cxx.toolchain.CxxBuckConfig;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.HeaderMode;
import com.facebook.buck.cxx.toolchain.HeaderVisibility;
import com.facebook.buck.cxx.toolchain.LinkerMapMode;
import com.facebook.buck.cxx.toolchain.StripStyle;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkable;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorConvertible;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.model.InternalFlavor;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.CommonDescriptionArg;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.HasDeclaredDeps;
import com.facebook.buck.rules.HasSrcs;
import com.facebook.buck.rules.MetadataProvidingDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.facebook.buck.versions.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import org.immutables.value.Value;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class SwiftLibraryDescription implements Description<SwiftLibraryDescriptionArg>, Flavored, MetadataProvidingDescription<SwiftLibraryDescriptionArg> {

  static final Flavor SWIFT_COMPILE_FLAVOR = InternalFlavor.of("swift-compile");

  public enum Type implements FlavorConvertible {
    EXPORTED_HEADERS(CxxDescriptionEnhancer.EXPORTED_HEADER_SYMLINK_TREE_FLAVOR),
    SHARED(CxxDescriptionEnhancer.SHARED_FLAVOR),
    STATIC(CxxDescriptionEnhancer.STATIC_FLAVOR),
    MACH_O_BUNDLE(CxxDescriptionEnhancer.MACH_O_BUNDLE_FLAVOR),
    ;

    private final Flavor flavor;

    Type(Flavor flavor) {
      this.flavor = flavor;
    }

    @Override
    public Flavor getFlavor() {
      return flavor;
    }
  }

  private static final FlavorDomain<Type> LIBRARY_TYPE =
      FlavorDomain.from("Swift Library Type", Type.class);

  @SuppressWarnings("unused")
  private final CxxBuckConfig cxxBuckConfig;
  @SuppressWarnings("unused")
  private final SwiftBuckConfig swiftBuckConfig;
  private final FlavorDomain<CxxPlatform> cxxPlatformFlavorDomain;
  private final FlavorDomain<SwiftPlatform> swiftPlatformFlavorDomain;

  public SwiftLibraryDescription(
      CxxBuckConfig cxxBuckConfig,
      SwiftBuckConfig swiftBuckConfig,
      FlavorDomain<CxxPlatform> cxxPlatformFlavorDomain,
      FlavorDomain<SwiftPlatform> swiftPlatformFlavorDomain) {
    this.cxxBuckConfig = cxxBuckConfig;
    this.swiftBuckConfig = swiftBuckConfig;
    this.cxxPlatformFlavorDomain = cxxPlatformFlavorDomain;
    this.swiftPlatformFlavorDomain = swiftPlatformFlavorDomain;
  }

  @Override
  public Class<SwiftLibraryDescriptionArg> getConstructorArgType() {
    return SwiftLibraryDescriptionArg.class;
  }

  @Override
  public Optional<ImmutableSet<FlavorDomain<?>>> flavorDomains() {
    return Optional.of(
        ImmutableSet.of(
            // Missing: swift-companion
            // Missing: swift-compile
            cxxPlatformFlavorDomain, LinkerMapMode.FLAVOR_DOMAIN, StripStyle.FLAVOR_DOMAIN));
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    return cxxPlatformFlavorDomain.containsAnyOf(flavors)
        || flavors.contains(CxxDescriptionEnhancer.EXPORTED_HEADER_SYMLINK_TREE_FLAVOR)
        || LinkerMapMode.FLAVOR_DOMAIN.containsAnyOf(flavors);
  }

  @Override
  public BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      final BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      SwiftLibraryDescriptionArg args) {

    Optional<LinkerMapMode> flavoredLinkerMapMode =
        LinkerMapMode.FLAVOR_DOMAIN.getValue(buildTarget);
    buildTarget =
        LinkerMapMode.removeLinkerMapModeFlavorInTarget(buildTarget, flavoredLinkerMapMode);

    // See if we're building a particular "type" and "platform" of this library, and if so, extract
    // them from the flavors attached to the build target.
    Optional<Map.Entry<Flavor, CxxPlatform>> platform =
        cxxPlatformFlavorDomain.getFlavorAndValue(buildTarget);
    Optional<Map.Entry<Flavor, SwiftLibraryDescription.Type>> type = getLibType(buildTarget);

    if (type.isPresent() && platform.isPresent()) {
      switch (type.get().getValue()) {
        case EXPORTED_HEADERS:
        case SHARED:
        case MACH_O_BUNDLE:
        case STATIC:
          throw new RuntimeException("unhandled library build type: " + type.get());
      }
    }

    buildTarget =
        LinkerMapMode.restoreLinkerMapModeFlavorInTarget(buildTarget, flavoredLinkerMapMode);
    return new SwiftLibrary(
        buildTarget,
        projectFilesystem,
        params,
        resolver,
        ImmutableSet.of(),
        swiftPlatformFlavorDomain,
        args.getFrameworks(),
        args.getLibraries(),
        args.getSupportedPlatformsRegex(),
        args.getPreferredLinkage().orElse(NativeLinkable.Linkage.ANY));
  }

  public static Optional<Map.Entry<Flavor, SwiftLibraryDescription.Type>> getLibType(BuildTarget buildTarget) {
    return LIBRARY_TYPE.getFlavorAndValue(buildTarget);
  }

  @Override
  public <U> Optional<U> createMetadata(
      BuildTarget buildTarget,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      SwiftLibraryDescriptionArg args,
      Optional<ImmutableMap<BuildTarget, Version>> selectedVersions,
      Class<U> metadataClass) {

    Map.Entry<Flavor, CxxLibraryDescription.MetadataType> type =
        METADATA_TYPE.getFlavorAndValue(buildTarget).orElseThrow(IllegalArgumentException::new);
    BuildTarget baseTarget = buildTarget.withoutFlavors(type.getKey());

    switch (type.getValue()) {
      case CXX_HEADERS:
      {
        HeaderMode mode = CxxLibraryDescription.HEADER_MODE.getRequiredValue(buildTarget);
        baseTarget = baseTarget.withoutFlavors(mode.getFlavor());
        Optional<CxxHeaders> symlinkTree =
              Optional.of(
                  CxxSymlinkTreeHeaders.from(
                      (HeaderSymlinkTreeWithModuleMap)
                          resolver.requireRule(
                              baseTarget
                                  .withoutFlavors(SwiftLibraryDescription.LIBRARY_TYPE.getFlavors())
                                  .withAppendedFlavors(
                                      SwiftLibraryDescription.Type.EXPORTED_HEADERS.getFlavor(), mode.getFlavor())),
                      CxxPreprocessables.IncludeType.LOCAL));
        return symlinkTree.map(metadataClass::cast);
      }

      case CXX_PREPROCESSOR_INPUT:
      {
        Map.Entry<Flavor, CxxPlatform> platform =
            cxxPlatformFlavorDomain
                .getFlavorAndValue(buildTarget)
                .orElseThrow(IllegalArgumentException::new);
        Map.Entry<Flavor, HeaderVisibility> visibility =
            CxxLibraryDescription.HEADER_VISIBILITY
                .getFlavorAndValue(buildTarget)
                .orElseThrow(IllegalArgumentException::new);
        baseTarget = baseTarget.withoutFlavors(platform.getKey(), visibility.getKey());

        CxxPreprocessorInput.Builder cxxPreprocessorInputBuilder = CxxPreprocessorInput.builder();
        cxxPreprocessorInputBuilder.addAllFrameworks(args.getFrameworks());

        if (visibility.getValue() == HeaderVisibility.PUBLIC) {
          resolver.requireMetadata(
              baseTarget.withAppendedFlavors(
                  CxxLibraryDescription.MetadataType.CXX_HEADERS.getFlavor(),
                  HeaderMode.SYMLINK_TREE_WITH_MODULEMAP.getFlavor(),
                  platform.getKey()),
              CxxHeaders.class)
              .ifPresent(cxxPreprocessorInputBuilder::addIncludes);
        }

        CxxPreprocessorInput cxxPreprocessorInput = cxxPreprocessorInputBuilder.build();
        return Optional.of(cxxPreprocessorInput).map(metadataClass::cast);
      }
    }

    throw new IllegalStateException(String.format("unhandled metadata type: %s", type.getValue()));
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractSwiftLibraryDescriptionArg
      extends CommonDescriptionArg, HasDeclaredDeps, HasSrcs {
    Optional<String> getModuleName();

    ImmutableList<StringWithMacros> getCompilerFlags();

    Optional<String> getVersion();

    @Value.NaturalOrder
    ImmutableSortedSet<FrameworkPath> getFrameworks();

    @Value.NaturalOrder
    ImmutableSortedSet<FrameworkPath> getLibraries();

    Optional<Boolean> getEnableObjcInterop();

    Optional<Pattern> getSupportedPlatformsRegex();

    Optional<String> getSoname();

    Optional<SourcePath> getBridgingHeader();

    Optional<NativeLinkable.Linkage> getPreferredLinkage();
  }
}
