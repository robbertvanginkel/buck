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

package com.facebook.buck.apple;

import com.facebook.buck.apple.toolchain.AppleCxxPlatform;
import com.facebook.buck.core.cell.resolver.CellPathResolver;
import com.facebook.buck.core.description.BuildRuleParams;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.impl.DefaultSourcePathResolver;
import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxLibrary;
import com.facebook.buck.cxx.CxxPreprocessorInput;
import com.facebook.buck.cxx.DepsBuilder;
import com.facebook.buck.cxx.HeaderSymlinkTreeWithHeaderMap;
import com.facebook.buck.cxx.PreprocessorFlags;
import com.facebook.buck.cxx.TransitiveCxxPreprocessorInputCache;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.HeaderVisibility;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.swift.SwiftBuckConfig;
import com.facebook.buck.swift.SwiftCompile;
import com.facebook.buck.swift.SwiftDescriptions;
import com.facebook.buck.util.RichStream;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class AppleLibraryDescriptionSwiftEnhancer {

  public static BuildRule createSwiftCompileRule(
      BuildTarget target,
      CellPathResolver cellRoots,
      ActionGraphBuilder graphBuilder,
      SourcePathRuleFinder ruleFinder,
      BuildRuleParams params,
      AppleNativeTargetDescriptionArg args,
      ProjectFilesystem filesystem,
      CxxPlatform cxxPlatform,
      AppleCxxPlatform applePlatform,
      SwiftBuckConfig swiftBuckConfig,
      ImmutableSet<CxxPreprocessorInput> inputs) {

    SourcePathRuleFinder rulePathFinder = new SourcePathRuleFinder(graphBuilder);
    ImmutableSortedSet<SourcePath> swiftSources =
        SwiftDescriptions.filterSwiftSources(
            DefaultSourcePathResolver.from(rulePathFinder), args.getSrcs());

    ImmutableSet<BuildRule> inputDeps =
        RichStream.from(inputs)
            .flatMap(input -> RichStream.from(input.getDeps(graphBuilder, rulePathFinder)))
            .toImmutableSet();

    ImmutableSortedSet.Builder<BuildRule> sortedDeps = ImmutableSortedSet.naturalOrder();
    sortedDeps.addAll(inputDeps);

    PreprocessorFlags.Builder flagsBuilder = PreprocessorFlags.builder();
    inputs.forEach(input -> flagsBuilder.addAllIncludes(input.getIncludes()));
    inputs.forEach(input -> flagsBuilder.addAllFrameworkPaths(input.getFrameworks()));

    DepsBuilder srcsDepsBuilder = new DepsBuilder(ruleFinder);
    swiftSources.forEach(src -> srcsDepsBuilder.add(src));

    return new SwiftCompile(
        cxxPlatform,
        swiftBuckConfig,
        target,
        filesystem,
        params
            .copyAppendingExtraDeps(srcsDepsBuilder.build())
            .copyAppendingExtraDeps(sortedDeps.build()),
        applePlatform.getSwiftPlatform().get().getSwiftc(),
        args.getFrameworks(),
        args.getModuleName().orElse(target.getShortName()),
        BuildTargets.getGenPath(filesystem, target, "%s"),
        swiftSources,
        args.getSwiftVersion(),
        RichStream.from(args.getCompilerFlags())
            .map(
                f ->
                    CxxDescriptionEnhancer.toStringWithMacrosArgs(
                        target, cellRoots, graphBuilder, cxxPlatform, f))
            .toImmutableList(),
        Optional.empty(),
        args.getBridgingHeader(),
        cxxPlatform.getCpp().resolve(graphBuilder),
        flagsBuilder.build(),
        AppleLibraryDescription.underlyingModuleCxxPreprocessorInput(
                target, graphBuilder, cxxPlatform)
            .isPresent());
  }

  /**
   * Returns transitive preprocessor inputs excluding those from the swift delegate of the given
   * CxxLibrary.
   */
  public static ImmutableSet<CxxPreprocessorInput> getPreprocessorInputsForAppleLibrary(
      BuildTarget target,
      ActionGraphBuilder graphBuilder,
      CxxPlatform platform,
      AppleNativeTargetDescriptionArg arg) {
    CxxLibrary lib = (CxxLibrary) graphBuilder.requireRule(target.withFlavors());
    ImmutableMap<BuildTarget, CxxPreprocessorInput> transitiveMap =
        TransitiveCxxPreprocessorInputCache.computeTransitiveCxxToPreprocessorInputMap(
            platform, lib, false, graphBuilder);

    ImmutableSet.Builder<CxxPreprocessorInput> builder = ImmutableSet.builder();
    builder.addAll(transitiveMap.values());
    if (arg.isModular()) {
      Optional<CxxPreprocessorInput> underlyingModule =
          AppleLibraryDescription.underlyingModuleCxxPreprocessorInput(
              target, graphBuilder, platform);
      underlyingModule.ifPresent(builder::add);
    } else {
      builder.add(lib.getPublicCxxPreprocessorInputExcludingDelegate(platform, graphBuilder));
    }

    return builder.build();
  }

  public static BuildRule createObjCGeneratedHeaderBuildRule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      SourcePathRuleFinder ruleFinder,
      ActionGraphBuilder graphBuilder,
      CxxPlatform cxxPlatform,
      HeaderVisibility headerVisibility) {
    ImmutableMap<Path, SourcePath> headers =
        getObjCGeneratedHeader(buildTarget, graphBuilder, cxxPlatform, headerVisibility);

    Path outputPath = BuildTargets.getGenPath(projectFilesystem, buildTarget, "%s");
    HeaderSymlinkTreeWithHeaderMap headerMapRule =
        HeaderSymlinkTreeWithHeaderMap.create(
            buildTarget, projectFilesystem, outputPath, headers, ruleFinder);

    return headerMapRule;
  }

  public static ImmutableMap<Path, SourcePath> getObjCGeneratedHeader(
      BuildTarget buildTarget,
      ActionGraphBuilder graphBuilder,
      CxxPlatform cxxPlatform,
      HeaderVisibility headerVisibility) {
    BuildTarget swiftCompileTarget = createBuildTargetForSwiftCompile(buildTarget, cxxPlatform);
    SwiftCompile compile = (SwiftCompile) graphBuilder.requireRule(swiftCompileTarget);

    Path objCImportPath = getObjCGeneratedHeaderSourceIncludePath(headerVisibility, compile);
    SourcePath objCGeneratedPath = compile.getObjCGeneratedHeaderPath();

    ImmutableMap.Builder<Path, SourcePath> headerLinks = ImmutableMap.builder();
    headerLinks.put(objCImportPath, objCGeneratedPath);
    return headerLinks.build();
  }

  private static Path getObjCGeneratedHeaderSourceIncludePath(
      HeaderVisibility headerVisibility, SwiftCompile compile) {
    Path publicPath = Paths.get(compile.getModuleName(), compile.getObjCGeneratedHeaderFileName());
    switch (headerVisibility) {
      case PUBLIC:
        return publicPath;
      case PRIVATE:
        return Paths.get(compile.getObjCGeneratedHeaderFileName());
    }

    return publicPath;
  }

  public static BuildTarget createBuildTargetForObjCGeneratedHeaderBuildRule(
      BuildTarget buildTarget, HeaderVisibility headerVisibility, CxxPlatform cxxPlatform) {
    AppleLibraryDescription.Type appleLibType = null;
    switch (headerVisibility) {
      case PUBLIC:
        appleLibType = AppleLibraryDescription.Type.SWIFT_EXPORTED_OBJC_GENERATED_HEADER;
        break;
      case PRIVATE:
        appleLibType = AppleLibraryDescription.Type.SWIFT_OBJC_GENERATED_HEADER;
        break;
    }

    Preconditions.checkNotNull(appleLibType);

    return buildTarget.withFlavors(appleLibType.getFlavor(), cxxPlatform.getFlavor());
  }

  public static BuildTarget createBuildTargetForSwiftCompile(
      BuildTarget target, CxxPlatform cxxPlatform) {
    // `target` is not necessarily flavored with just `apple_library` flavors, that's because
    // Swift compile rules can be required by other rules (e.g., `apple_test`, `apple_binary` etc).
    return target.withFlavors(
        AppleLibraryDescription.Type.SWIFT_COMPILE.getFlavor(), cxxPlatform.getFlavor());
  }
}
