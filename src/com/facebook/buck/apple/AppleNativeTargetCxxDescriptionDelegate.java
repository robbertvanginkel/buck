/*
 * Copyright 2018-present Facebook, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License. You may obtain
 *  a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */

package com.facebook.buck.apple;

import com.facebook.buck.apple.AppleLibraryDescription.Type;
import com.facebook.buck.apple.toolchain.AppleCxxPlatform;
import com.facebook.buck.apple.toolchain.AppleCxxPlatformsProvider;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.FlavorConvertible;
import com.facebook.buck.core.model.FlavorDomain;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolver;
import com.facebook.buck.core.sourcepath.resolver.impl.DefaultSourcePathResolver;
import com.facebook.buck.cxx.CxxDescriptionDelegate;
import com.facebook.buck.cxx.CxxHeaders;
import com.facebook.buck.cxx.CxxHeadersDir;
import com.facebook.buck.cxx.CxxPreprocessables;
import com.facebook.buck.cxx.CxxPreprocessorInput;
import com.facebook.buck.cxx.CxxSymlinkTreeHeaders;
import com.facebook.buck.cxx.HeaderSymlinkTreeWithHeaderMap;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.CxxPlatformsProvider;
import com.facebook.buck.cxx.toolchain.HeaderSymlinkTree;
import com.facebook.buck.cxx.toolchain.HeaderSymlinkTreeWithModuleMap;
import com.facebook.buck.cxx.toolchain.HeaderVisibility;
import com.facebook.buck.cxx.toolchain.linker.Linker;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkable;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.swift.SwiftCompile;
import com.facebook.buck.swift.SwiftRuntimeNativeLinkable;
import com.facebook.buck.swift.toolchain.SwiftPlatform;
import com.facebook.buck.swift.toolchain.SwiftPlatformsProvider;
import com.facebook.buck.toolchain.ToolchainProvider;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public interface AppleNativeTargetCxxDescriptionDelegate extends CxxDescriptionDelegate {

  enum MetadataType implements FlavorConvertible {
    APPLE_SWIFT_METADATA(InternalFlavor.of("swift-metadata")),
    APPLE_SWIFT_EXPORTED_OBJC_CXX_HEADERS(InternalFlavor.of("swift-objc-cxx-headers")),
    APPLE_SWIFT_OBJC_CXX_HEADERS(InternalFlavor.of("swift-private-objc-cxx-headers")),
    APPLE_SWIFT_MODULE_CXX_HEADERS(InternalFlavor.of("swift-module-cxx-headers")),
    APPLE_SWIFT_PREPROCESSOR_INPUT(InternalFlavor.of("swift-preprocessor-input")),
    APPLE_SWIFT_PRIVATE_PREPROCESSOR_INPUT(InternalFlavor.of("swift-private-preprocessor-input")),
    APPLE_SWIFT_UNDERLYING_MODULE_INPUT(InternalFlavor.of("swift-underlying-module-input")),
    ;

    private final Flavor flavor;

    MetadataType(Flavor flavor) {
      this.flavor = flavor;
    }

    @Override
    public Flavor getFlavor() {
      return flavor;
    }
  }

  FlavorDomain<MetadataType> METADATA_TYPE =
      FlavorDomain.from(
          "Apple Swift Metadata Type", AppleNativeTargetCxxDescriptionDelegate.MetadataType.class);

  ToolchainProvider toolchainProvider();

  default FlavorDomain<AppleCxxPlatform> getAppleCxxPlatformDomain() {
    AppleCxxPlatformsProvider appleCxxPlatformsProvider =
        toolchainProvider()
            .getByName(AppleCxxPlatformsProvider.DEFAULT_NAME, AppleCxxPlatformsProvider.class);

    return appleCxxPlatformsProvider.getAppleCxxPlatforms();
  }

  default CxxPlatformsProvider getCxxPlatformsProvider() {
    return toolchainProvider()
        .getByName(CxxPlatformsProvider.DEFAULT_NAME, CxxPlatformsProvider.class);
  }

  static boolean targetContainsSwift(BuildTarget target, ActionGraphBuilder graphBuilder) {
    BuildTarget metadataTarget = target.withFlavors(MetadataType.APPLE_SWIFT_METADATA.getFlavor());
    Optional<AppleLibrarySwiftMetadata> metadata =
        graphBuilder.requireMetadata(metadataTarget, AppleLibrarySwiftMetadata.class);
    return metadata.map(m -> !m.getSwiftSources().isEmpty()).orElse(false);
  }

  static Optional<CxxPreprocessorInput> queryMetadataCxxSwiftPreprocessorInput(
      ActionGraphBuilder graphBuilder,
      BuildTarget baseTarget,
      CxxPlatform platform,
      HeaderVisibility headerVisibility) {
    if (!targetContainsSwift(baseTarget, graphBuilder)) {
      return Optional.empty();
    }

    MetadataType metadataType = null;
    switch (headerVisibility) {
      case PUBLIC:
        metadataType = MetadataType.APPLE_SWIFT_PREPROCESSOR_INPUT;
        break;
      case PRIVATE:
        metadataType = MetadataType.APPLE_SWIFT_PRIVATE_PREPROCESSOR_INPUT;
        break;
    }

    Preconditions.checkNotNull(metadataType);

    return graphBuilder.requireMetadata(
        baseTarget.withAppendedFlavors(metadataType.getFlavor(), platform.getFlavor()),
        CxxPreprocessorInput.class);
  }

  @Override
  default Optional<CxxPreprocessorInput> getPreprocessorInput(
      BuildTarget target, ActionGraphBuilder graphBuilder, CxxPlatform platform) {
    if (!targetContainsSwift(target, graphBuilder)) {
      return Optional.empty();
    }

    return queryMetadataCxxSwiftPreprocessorInput(
        graphBuilder, target, platform, HeaderVisibility.PUBLIC);
  }

  @Override
  default Optional<CxxPreprocessorInput> getPrivatePreprocessorInput(
      BuildTarget target, ActionGraphBuilder graphBuilder, CxxPlatform platform) {
    if (!targetContainsSwift(target, graphBuilder)) {
      return Optional.empty();
    }

    return queryMetadataCxxSwiftPreprocessorInput(
        graphBuilder, target, platform, HeaderVisibility.PRIVATE);
  }

  @Override
  default Optional<HeaderSymlinkTree> getPrivateHeaderSymlinkTree(
      BuildTarget buildTarget, ActionGraphBuilder graphBuilder, CxxPlatform cxxPlatform) {
    if (!targetContainsSwift(buildTarget, graphBuilder)) {
      return Optional.empty();
    }

    BuildTarget ruleTarget =
        AppleLibraryDescriptionSwiftEnhancer.createBuildTargetForObjCGeneratedHeaderBuildRule(
            buildTarget, HeaderVisibility.PRIVATE, cxxPlatform);
    BuildRule headerRule = graphBuilder.requireRule(ruleTarget);
    if (headerRule instanceof HeaderSymlinkTree) {
      return Optional.of((HeaderSymlinkTree) headerRule);
    }

    return Optional.empty();
  }

  @Override
  default Optional<ImmutableList<SourcePath>> getObjectFilePaths(
      BuildTarget target, ActionGraphBuilder graphBuilder, CxxPlatform cxxPlatform) {
    if (!targetContainsSwift(target, graphBuilder)) {
      return Optional.empty();
    }

    BuildTarget swiftTarget =
        AppleLibraryDescriptionSwiftEnhancer.createBuildTargetForSwiftCompile(target, cxxPlatform);
    SwiftCompile compile = (SwiftCompile) graphBuilder.requireRule(swiftTarget);
    return Optional.of(compile.getObjectPaths());
  }

  @Override
  default Optional<ImmutableList<NativeLinkable>> getNativeLinkableExportedDeps(
      BuildTarget target, ActionGraphBuilder graphBuilder, CxxPlatform platform) {
    if (!targetContainsSwift(target, graphBuilder)) {
      return Optional.empty();
    }

    SwiftPlatformsProvider swiftPlatformsProvider =
        toolchainProvider()
            .getByName(SwiftPlatformsProvider.DEFAULT_NAME, SwiftPlatformsProvider.class);
    FlavorDomain<SwiftPlatform> swiftPlatformFlavorDomain =
        swiftPlatformsProvider.getSwiftCxxPlatforms();

    BuildTarget targetWithPlatform = target.withAppendedFlavors(platform.getFlavor());
    Optional<SwiftPlatform> swiftPlatform = swiftPlatformFlavorDomain.getValue(targetWithPlatform);
    if (swiftPlatform.isPresent()) {
      return Optional.of(ImmutableList.of(new SwiftRuntimeNativeLinkable(swiftPlatform.get())));
    }

    return Optional.empty();
  }

  @Override
  default ImmutableList<Arg> getAdditionalExportedLinkerFlags(
      BuildTarget target, ActionGraphBuilder graphBuilder, CxxPlatform cxxPlatform) {
    if (!targetContainsSwift(target, graphBuilder)) {
      return ImmutableList.of();
    }

    BuildTarget swiftTarget =
        AppleLibraryDescriptionSwiftEnhancer.createBuildTargetForSwiftCompile(target, cxxPlatform);
    SwiftCompile compile = (SwiftCompile) graphBuilder.requireRule(swiftTarget);

    return compile.getAstLinkArgs();
  }

  @Override
  default boolean getShouldProduceLibraryArtifact(
      BuildTarget target,
      ActionGraphBuilder graphBuilder,
      CxxPlatform cxxPlatform,
      Linker.LinkableDepType type,
      boolean forceLinkWhole) {
    return targetContainsSwift(target, graphBuilder);
  }

  default Optional<ImmutableMap<Path, SourcePath>> getSwiftGeneratedObjCHeader(
      BuildTarget target, ActionGraphBuilder graphBuilder, CxxPlatform cxxPlatform) {
    if (!targetContainsSwift(target, graphBuilder)) {
      return Optional.empty();
    }

    return Optional.of(
        AppleLibraryDescriptionSwiftEnhancer.getObjCGeneratedHeader(
            target, graphBuilder, cxxPlatform, HeaderVisibility.PUBLIC));
  }

  default <U> Optional<U> createSwiftMetadata(
      BuildTarget buildTarget,
      ActionGraphBuilder graphBuilder,
      AppleNativeTargetDescriptionArg args,
      Class<U> metadataClass) {
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(graphBuilder));

    Optional<Map.Entry<Flavor, MetadataType>> metaType =
        METADATA_TYPE.getFlavorAndValue(buildTarget);
    if (metaType.isPresent()) {
      BuildTarget baseTarget = buildTarget.withoutFlavors(metaType.get().getKey());
      switch (metaType.get().getValue()) {
        case APPLE_SWIFT_METADATA:
          {
            AppleLibrarySwiftMetadata metadata =
                AppleLibrarySwiftMetadata.from(args.getSrcs(), pathResolver);
            return Optional.of(metadata).map(metadataClass::cast);
          }

        case APPLE_SWIFT_EXPORTED_OBJC_CXX_HEADERS:
          {
            BuildTarget swiftHeadersTarget =
                baseTarget.withAppendedFlavors(
                    Type.SWIFT_EXPORTED_OBJC_GENERATED_HEADER.getFlavor());
            HeaderSymlinkTreeWithHeaderMap headersRule =
                (HeaderSymlinkTreeWithHeaderMap) graphBuilder.requireRule(swiftHeadersTarget);

            CxxHeaders headers =
                CxxSymlinkTreeHeaders.from(headersRule, CxxPreprocessables.IncludeType.LOCAL);
            return Optional.of(headers).map(metadataClass::cast);
          }

        case APPLE_SWIFT_OBJC_CXX_HEADERS:
          {
            BuildTarget swiftHeadersTarget =
                baseTarget.withAppendedFlavors(Type.SWIFT_OBJC_GENERATED_HEADER.getFlavor());
            HeaderSymlinkTreeWithHeaderMap headersRule =
                (HeaderSymlinkTreeWithHeaderMap) graphBuilder.requireRule(swiftHeadersTarget);

            CxxHeaders headers =
                CxxSymlinkTreeHeaders.from(headersRule, CxxPreprocessables.IncludeType.LOCAL);
            return Optional.of(headers).map(metadataClass::cast);
          }

        case APPLE_SWIFT_MODULE_CXX_HEADERS:
          {
            BuildTarget swiftCompileTarget =
                baseTarget.withAppendedFlavors(Type.SWIFT_COMPILE.getFlavor());
            SwiftCompile compile = (SwiftCompile) graphBuilder.requireRule(swiftCompileTarget);

            CxxHeaders headers =
                CxxHeadersDir.of(CxxPreprocessables.IncludeType.LOCAL, compile.getOutputPath());
            return Optional.of(headers).map(metadataClass::cast);
          }

        case APPLE_SWIFT_PREPROCESSOR_INPUT:
          {
            BuildTarget moduleHeadersTarget =
                baseTarget.withAppendedFlavors(
                    MetadataType.APPLE_SWIFT_MODULE_CXX_HEADERS.getFlavor());
            Optional<CxxHeaders> moduleHeaders =
                graphBuilder.requireMetadata(moduleHeadersTarget, CxxHeaders.class);

            BuildTarget objcHeadersTarget =
                baseTarget.withAppendedFlavors(
                    MetadataType.APPLE_SWIFT_EXPORTED_OBJC_CXX_HEADERS.getFlavor());
            Optional<CxxHeaders> objcHeaders =
                graphBuilder.requireMetadata(objcHeadersTarget, CxxHeaders.class);

            CxxPreprocessorInput.Builder builder = CxxPreprocessorInput.builder();
            moduleHeaders.ifPresent(s -> builder.addIncludes(s));
            objcHeaders.ifPresent(s -> builder.addIncludes(s));

            CxxPreprocessorInput input = builder.build();
            return Optional.of(input).map(metadataClass::cast);
          }

        case APPLE_SWIFT_PRIVATE_PREPROCESSOR_INPUT:
          {
            BuildTarget objcHeadersTarget =
                baseTarget.withAppendedFlavors(
                    MetadataType.APPLE_SWIFT_OBJC_CXX_HEADERS.getFlavor());
            Optional<CxxHeaders> objcHeaders =
                graphBuilder.requireMetadata(objcHeadersTarget, CxxHeaders.class);

            CxxPreprocessorInput.Builder builder = CxxPreprocessorInput.builder();
            objcHeaders.ifPresent(s -> builder.addIncludes(s));

            CxxPreprocessorInput input = builder.build();
            return Optional.of(input).map(metadataClass::cast);
          }
        case APPLE_SWIFT_UNDERLYING_MODULE_INPUT:
          {
            if (!args.isModular()) {
              return Optional.empty();
            }
            BuildTarget swiftCompileTarget =
                baseTarget.withAppendedFlavors(Type.SWIFT_UNDERLYING_MODULE.getFlavor());
            HeaderSymlinkTreeWithModuleMap modulemap =
                (HeaderSymlinkTreeWithModuleMap) graphBuilder.requireRule(swiftCompileTarget);
            if (modulemap.getLinks().size() > 0) {
              CxxPreprocessorInput.Builder builder = CxxPreprocessorInput.builder();
              builder.addIncludes(
                  CxxSymlinkTreeHeaders.from(modulemap, CxxPreprocessables.IncludeType.LOCAL));
              return Optional.of(builder.build()).map(metadataClass::cast);
            }
            return Optional.empty();
          }
      }
    }
    return Optional.empty();
  }
}
