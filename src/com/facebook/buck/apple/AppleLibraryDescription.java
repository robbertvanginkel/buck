/*
 * Copyright 2013-present Facebook, Inc.
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

import static com.facebook.buck.cxx.CxxLibraryDescription.HEADER_MODE;
import static com.facebook.buck.cxx.toolchain.nativelink.NativeLinkable.Linkage;

import com.facebook.buck.cxx.Archive;
import com.facebook.buck.cxx.CxxCompilationDatabase;
import com.facebook.buck.cxx.CxxDeps;
import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.cxx.CxxLibraryDescriptionArg;
import com.facebook.buck.cxx.CxxPreprocessAndCompile;
import com.facebook.buck.cxx.CxxPreprocessables;
import com.facebook.buck.cxx.CxxPreprocessorInput;
import com.facebook.buck.cxx.CxxSourceRuleFactory;
import com.facebook.buck.cxx.CxxStrip;
import com.facebook.buck.cxx.CxxSymlinkTreeHeaders;
import com.facebook.buck.cxx.FrameworkDependencies;
import com.facebook.buck.cxx.ProvidesLinkedBinaryDeps;
import com.facebook.buck.cxx.toolchain.CxxBuckConfig;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.HeaderMode;
import com.facebook.buck.cxx.toolchain.HeaderSymlinkTree;
import com.facebook.buck.cxx.toolchain.HeaderVisibility;
import com.facebook.buck.cxx.toolchain.LinkerMapMode;
import com.facebook.buck.cxx.toolchain.StripStyle;
import com.facebook.buck.cxx.toolchain.linker.Linker;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.Either;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorConvertible;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.model.InternalFlavor;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.DefaultSourcePathResolver;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.ImplicitFlavorsInferringDescription;
import com.facebook.buck.rules.MetadataProvidingDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.macros.Macro;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.swift.SwiftBuckConfig;
import com.facebook.buck.swift.SwiftCompile;
import com.facebook.buck.swift.SwiftDescriptions;
import com.facebook.buck.swift.SwiftLibraryDescription;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.MoreCollectors;
import com.facebook.buck.util.RichStream;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.facebook.buck.versions.Version;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.immutables.value.Value;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class AppleLibraryDescription
    implements Description<AppleLibraryDescriptionArg>,
        Flavored,
        ImplicitDepsInferringDescription<
            AppleLibraryDescription.AbstractAppleLibraryDescriptionArg>,
        ImplicitFlavorsInferringDescription,
        MetadataProvidingDescription<AppleLibraryDescriptionArg> {

  public static final Flavor OBJC_HEADER_SYMLINK_TREE_FLAVOR = InternalFlavor.of("objc-headers");

  @SuppressWarnings("PMD") // PMD doesn't understand method references
  private static final Set<Flavor> SUPPORTED_FLAVORS =
      ImmutableSet.of(
          OBJC_HEADER_SYMLINK_TREE_FLAVOR,
          CxxCompilationDatabase.COMPILATION_DATABASE,
          CxxCompilationDatabase.UBER_COMPILATION_DATABASE,
          CxxDescriptionEnhancer.HEADER_SYMLINK_TREE_FLAVOR,
          CxxDescriptionEnhancer.EXPORTED_HEADER_SYMLINK_TREE_FLAVOR,
          CxxDescriptionEnhancer.STATIC_FLAVOR,
          CxxDescriptionEnhancer.SHARED_FLAVOR,
          AppleDescriptions.FRAMEWORK_FLAVOR,
          AppleDebugFormat.DWARF_AND_DSYM.getFlavor(),
          AppleDebugFormat.DWARF.getFlavor(),
          AppleDebugFormat.NONE.getFlavor(),
          StripStyle.NON_GLOBAL_SYMBOLS.getFlavor(),
          StripStyle.ALL_SYMBOLS.getFlavor(),
          StripStyle.DEBUGGING_SYMBOLS.getFlavor(),
          LinkerMapMode.NO_LINKER_MAP.getFlavor(),
          InternalFlavor.of("default"));

  private enum Type implements FlavorConvertible {
    OBJC_MODULE(OBJC_HEADER_SYMLINK_TREE_FLAVOR),
    HEADERS(CxxDescriptionEnhancer.HEADER_SYMLINK_TREE_FLAVOR),
    EXPORTED_HEADERS(CxxDescriptionEnhancer.EXPORTED_HEADER_SYMLINK_TREE_FLAVOR),
    SANDBOX(CxxDescriptionEnhancer.SANDBOX_TREE_FLAVOR),
    SHARED(CxxDescriptionEnhancer.SHARED_FLAVOR),
    STATIC_PIC(CxxDescriptionEnhancer.STATIC_PIC_FLAVOR),
    STATIC(CxxDescriptionEnhancer.STATIC_FLAVOR),
    MACH_O_BUNDLE(CxxDescriptionEnhancer.MACH_O_BUNDLE_FLAVOR),
    FRAMEWORK(AppleDescriptions.FRAMEWORK_FLAVOR),
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

  public static final FlavorDomain<Type> LIBRARY_TYPE =
      FlavorDomain.from("C/C++ Library Type", Type.class);

  private final CxxLibraryDescription delegate;
  private final FlavorDomain<AppleCxxPlatform> appleCxxPlatformFlavorDomain;
  private final Flavor defaultCxxFlavor;
  private final CodeSignIdentityStore codeSignIdentityStore;
  private final ProvisioningProfileStore provisioningProfileStore;
  private final AppleConfig appleConfig;

  public AppleLibraryDescription(
      CxxLibraryDescription delegate,
      FlavorDomain<AppleCxxPlatform> appleCxxPlatformFlavorDomain,
      Flavor defaultCxxFlavor,
      CodeSignIdentityStore codeSignIdentityStore,
      ProvisioningProfileStore provisioningProfileStore,
      AppleConfig appleConfig) {
    this.delegate = delegate;
    this.appleCxxPlatformFlavorDomain = appleCxxPlatformFlavorDomain;
    this.defaultCxxFlavor = defaultCxxFlavor;
    this.codeSignIdentityStore = codeSignIdentityStore;
    this.provisioningProfileStore = provisioningProfileStore;
    this.appleConfig = appleConfig;
  }

  @Override
  public Class<AppleLibraryDescriptionArg> getConstructorArgType() {
    return AppleLibraryDescriptionArg.class;
  }

  @Override
  public Optional<ImmutableSet<FlavorDomain<?>>> flavorDomains() {
    ImmutableSet.Builder<FlavorDomain<?>> builder = ImmutableSet.builder();

    ImmutableSet<FlavorDomain<?>> localDomains = ImmutableSet.of(AppleDebugFormat.FLAVOR_DOMAIN);

    builder.addAll(localDomains);
    delegate.flavorDomains().ifPresent(domains -> builder.addAll(domains));

    ImmutableSet<FlavorDomain<?>> result = builder.build();

    // Drop StripStyle because it's overridden by AppleDebugFormat
    result =
        result
            .stream()
            .filter(domain -> !domain.equals(StripStyle.FLAVOR_DOMAIN))
            .collect(MoreCollectors.toImmutableSet());

    return Optional.of(result);
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    return FluentIterable.from(flavors).allMatch(SUPPORTED_FLAVORS::contains)
        || delegate.hasFlavors(flavors);
  }

  @Override
  public BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      AppleLibraryDescriptionArg args) {
    Optional<Map.Entry<Flavor, Type>> type = LIBRARY_TYPE.getFlavorAndValue(buildTarget);
    if (type.isPresent() && type.get().getValue().equals(Type.FRAMEWORK)) {
      return createFrameworkBundleBuildRule(
          targetGraph, buildTarget, projectFilesystem, params, resolver, args);
    } else {
      return createLibraryBuildRule(
          buildTarget,
          projectFilesystem,
          params,
          resolver,
          cellRoots,
          args,
          args.getLinkStyle(),
          Optional.empty(),
          ImmutableSet.of(),
          ImmutableSortedSet.of(),
          CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction.fromLibraryRule());
    }
  }

  private <A extends AbstractAppleLibraryDescriptionArg> BuildRule createFrameworkBundleBuildRule(
      TargetGraph targetGraph,
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      AppleLibraryDescriptionArg args) {
    if (!args.getInfoPlist().isPresent()) {
      throw new HumanReadableException(
          "Cannot create framework for apple_library '%s':\n"
              + "No value specified for 'info_plist' attribute.",
          buildTarget.getUnflavoredBuildTarget());
    }
    if (!AppleDescriptions.INCLUDE_FRAMEWORKS.getValue(buildTarget).isPresent()) {
      return resolver.requireRule(
          buildTarget.withAppendedFlavors(AppleDescriptions.INCLUDE_FRAMEWORKS_FLAVOR));
    }
    AppleDebugFormat debugFormat =
        AppleDebugFormat.FLAVOR_DOMAIN
            .getValue(buildTarget)
            .orElse(appleConfig.getDefaultDebugInfoFormatForLibraries());
    if (!buildTarget.getFlavors().contains(debugFormat.getFlavor())) {
      return resolver.requireRule(buildTarget.withAppendedFlavors(debugFormat.getFlavor()));
    }

    return AppleDescriptions.createAppleBundle(
        delegate.getCxxPlatforms(),
        defaultCxxFlavor,
        appleCxxPlatformFlavorDomain,
        targetGraph,
        buildTarget,
        projectFilesystem,
        params,
        resolver,
        codeSignIdentityStore,
        provisioningProfileStore,
        buildTarget,
        Either.ofLeft(AppleBundleExtension.FRAMEWORK),
        Optional.empty(),
        args.getInfoPlist().get(),
        args.getInfoPlistSubstitutions(),
        args.getDeps(),
        args.getTests(),
        debugFormat,
        appleConfig.useDryRunCodeSigning(),
        appleConfig.cacheBundlesAndPackages());
  }

  /**
   * @param projectFilesystem
   * @param cellRoots The roots of known cells.
   * @param bundleLoader The binary in which the current library will be (dynamically) loaded into.
   */
  public <A extends AppleNativeTargetDescriptionArg> BuildRule createLibraryBuildRule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      A args,
      Optional<Linker.LinkableDepType> linkableDepType,
      Optional<SourcePath> bundleLoader,
      ImmutableSet<BuildTarget> blacklist,
      ImmutableSortedSet<BuildTarget> extraCxxDeps,
      CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction transitiveCxxPreprocessorInput) {
    // We explicitly remove flavors from params to make sure rule
    // has the same output regardless if we will strip or not.
    Optional<StripStyle> flavoredStripStyle = StripStyle.FLAVOR_DOMAIN.getValue(buildTarget);
    buildTarget = CxxStrip.removeStripStyleFlavorInTarget(buildTarget, flavoredStripStyle);

    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(resolver));

    BuildRule unstrippedBinaryRule =
        requireUnstrippedBuildRule(
            buildTarget,
            projectFilesystem,
            params,
            resolver,
            cellRoots,
            args,
            linkableDepType,
            bundleLoader,
            blacklist,
            pathResolver,
            extraCxxDeps,
            transitiveCxxPreprocessorInput);

    if (!shouldWrapIntoDebuggableBinary(buildTarget, unstrippedBinaryRule)) {
      return unstrippedBinaryRule;
    }

    // If we built a multiarch binary, we can just use the strip tool from any platform.
    // We pick the platform in this odd way due to FlavorDomain's restriction of allowing only one
    // matching flavor in the build target.
    CxxPlatform representativePlatform =
        delegate
            .getCxxPlatforms()
            .getValue(
                Iterables.getFirst(
                    Sets.intersection(
                        delegate.getCxxPlatforms().getFlavors(), buildTarget.getFlavors()),
                    defaultCxxFlavor));

    buildTarget = CxxStrip.restoreStripStyleFlavorInTarget(buildTarget, flavoredStripStyle);

    BuildRule strippedBinaryRule =
        CxxDescriptionEnhancer.createCxxStripRule(
            buildTarget,
            projectFilesystem,
            resolver,
            flavoredStripStyle.orElse(StripStyle.NON_GLOBAL_SYMBOLS),
            unstrippedBinaryRule,
            representativePlatform);

    return AppleDescriptions.createAppleDebuggableBinary(
        buildTarget,
        projectFilesystem,
        params,
        resolver,
        strippedBinaryRule,
        (ProvidesLinkedBinaryDeps) unstrippedBinaryRule,
        AppleDebugFormat.FLAVOR_DOMAIN
            .getValue(buildTarget)
            .orElse(appleConfig.getDefaultDebugInfoFormatForLibraries()),
        delegate.getCxxPlatforms(),
        delegate.getDefaultCxxFlavor(),
        appleCxxPlatformFlavorDomain);
  }

  private <A extends AppleNativeTargetDescriptionArg> BuildRule requireUnstrippedBuildRule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      A args,
      Optional<Linker.LinkableDepType> linkableDepType,
      Optional<SourcePath> bundleLoader,
      ImmutableSet<BuildTarget> blacklist,
      SourcePathResolver pathResolver,
      ImmutableSortedSet<BuildTarget> extraCxxDeps,
      CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction transitiveCxxPreprocessorInput) {
    Optional<MultiarchFileInfo> multiarchFileInfo =
        MultiarchFileInfos.create(appleCxxPlatformFlavorDomain, buildTarget);
    if (multiarchFileInfo.isPresent()) {
      ImmutableSortedSet.Builder<BuildRule> thinRules = ImmutableSortedSet.naturalOrder();
      for (BuildTarget thinTarget : multiarchFileInfo.get().getThinTargets()) {
        thinRules.add(
            requireSingleArchUnstrippedBuildRule(
                thinTarget,
                projectFilesystem,
                params,
                resolver,
                cellRoots,
                args,
                linkableDepType,
                bundleLoader,
                blacklist,
                pathResolver,
                extraCxxDeps,
                transitiveCxxPreprocessorInput));
      }
      BuildTarget multiarchBuildTarget =
          buildTarget.withoutFlavors(AppleDebugFormat.FLAVOR_DOMAIN.getFlavors());
      return MultiarchFileInfos.requireMultiarchRule(
          multiarchBuildTarget,
          projectFilesystem,
          // In the same manner that debug flavors are omitted from single-arch constituents, they
          // are omitted here as well.
          params,
          resolver,
          multiarchFileInfo.get(),
          thinRules.build());
    } else {
      return requireSingleArchUnstrippedBuildRule(
          buildTarget,
          projectFilesystem,
          params,
          resolver,
          cellRoots,
          args,
          linkableDepType,
          bundleLoader,
          blacklist,
          pathResolver,
          extraCxxDeps,
          transitiveCxxPreprocessorInput);
    }
  }

  private <A extends AppleNativeTargetDescriptionArg>
      BuildRule requireSingleArchUnstrippedBuildRule(
          BuildTarget buildTarget,
          ProjectFilesystem projectFilesystem,
          BuildRuleParams params,
          BuildRuleResolver resolver,
          CellPathResolver cellRoots,
          A args,
          Optional<Linker.LinkableDepType> linkableDepType,
          Optional<SourcePath> bundleLoader,
          ImmutableSet<BuildTarget> blacklist,
          SourcePathResolver pathResolver,
          ImmutableSortedSet<BuildTarget> extraCxxDeps,
          CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction transitiveCxxDeps) {

    // remove some flavors from cxx rule that don't affect the rule output
    BuildTarget unstrippedTarget =
        buildTarget.withoutFlavors(AppleDebugFormat.FLAVOR_DOMAIN.getFlavors());
    if (AppleDescriptions.flavorsDoNotAllowLinkerMapMode(buildTarget)) {
      unstrippedTarget = unstrippedTarget.withoutFlavors(LinkerMapMode.NO_LINKER_MAP.getFlavor());
    }

    ImmutableSortedSet<SourcePath> swiftSources = SwiftDescriptions.filterSwiftSources(
        pathResolver,
        args.getSrcs());
    if (!swiftSources.isEmpty()) {
      Optional<BuildRule> buildRule = hijackForSwift(
          buildTarget,
          projectFilesystem,
          params,
          resolver,
          cellRoots,
          args,
          linkableDepType,
          blacklist,
          transitiveCxxDeps);
      if (buildRule.isPresent()) {
        return buildRule.get();
      }
    }

    Optional<BuildRule> existingRule = resolver.getRuleOptional(unstrippedTarget);
    if (existingRule.isPresent()) {
      return existingRule.get();
    } else {
      CxxLibraryDescriptionArg.Builder delegateArg = CxxLibraryDescriptionArg.builder().from(args);
      AppleDescriptions.populateCxxLibraryDescriptionArg(
          pathResolver, delegateArg, args, buildTarget);

      BuildRule rule =
          delegate.createBuildRule(
              unstrippedTarget,
              projectFilesystem,
              params,
              resolver,
              cellRoots,
              delegateArg.build(),
              linkableDepType,
              bundleLoader,
              blacklist,
              extraCxxDeps,
              transitiveCxxDeps);
      return resolver.addToIndex(rule);
    }
  }

  private <A extends AppleNativeTargetDescriptionArg> Optional<BuildRule> hijackForSwift(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      A args,
      Optional<Linker.LinkableDepType> linkableDepType,
      ImmutableSet<BuildTarget> blacklist,
      CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction transitiveCxxPreprocessorInputFunction) {
    Optional<Map.Entry<Flavor, Type>> type = LIBRARY_TYPE.getFlavorAndValue(buildTarget);
    Optional<CxxPlatform> platform = delegate.getCxxPlatforms().getValue(buildTarget);
    CxxDeps cxxDeps = CxxDeps.builder().addDeps(args.getCxxDeps()).build();

    if (type.isPresent() && platform.isPresent()) {
      BuildTarget untypedBuildTarget = buildTarget.withoutFlavors(type.get().getKey());
      SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
      SourcePathResolver sourcePathResolver = DefaultSourcePathResolver.from(ruleFinder);
      if (type.get().getValue() == Type.OBJC_MODULE) {
        String moduleName = args.getHeaderPathPrefix().orElse(args.getName());
        Path headerPathPrefix = Paths.get(moduleName);
        ImmutableSortedMap<String, SourcePath> headerMap =
            AppleDescriptions.convertAppleHeadersToPublicCxxHeaders(
                buildTarget, sourcePathResolver::getRelativePath, headerPathPrefix, args);
        ImmutableMap<Path, SourcePath> cxxHeaders = CxxPreprocessables.resolveHeaderMap(
            args.getHeaderNamespace().map(Paths::get).orElse(buildTarget.getBasePath()),
            headerMap);
        return Optional.of(
            CxxPreprocessables.createHeaderSymlinkTreeBuildRule(
                buildTarget,
                projectFilesystem,
                BuildTargets.getGenPath(projectFilesystem, buildTarget, "%s"),
                cxxHeaders,
                HeaderMode.SYMLINK_TREE_WITH_MODULEMAP));
      }
      CxxPlatform cxxPlatform = platform.get();
      final BuildTarget compileBuildTarget = untypedBuildTarget.withFlavors(SwiftLibraryDescription.SWIFT_COMPILE_FLAVOR, cxxPlatform.getFlavor());
      String moduleName = args.getHeaderPathPrefix().orElse(args.getName());

      BuildRule underlyingModuleRule = resolver.requireRule(
          untypedBuildTarget.withAppendedFlavors(OBJC_HEADER_SYMLINK_TREE_FLAVOR));
      CxxPreprocessorInput.Builder builder = CxxPreprocessorInput.builder();
      builder.addIncludes(CxxSymlinkTreeHeaders.from((HeaderSymlinkTree) underlyingModuleRule, CxxPreprocessables.IncludeType.LOCAL));
      CxxPreprocessorInput underlyingModule = builder.build();

      ImmutableList<Either<String, Macro>> importArg = ImmutableList.of(Either.ofLeft(
          "-import-underlying-module"));
      SwiftCompile swiftCompile = SwiftLibraryDescription.getSwiftCompile(
          projectFilesystem,
          params,
          resolver,
          cellRoots,
          appleCxxPlatformFlavorDomain.getValue(cxxPlatform.getFlavor()).getSwiftPlatform().get(),
          new SwiftBuckConfig(delegate.getCxxBuckConfig().delegate),
          cxxPlatform,
          compileBuildTarget,
          moduleName,
          args.getFrameworks(),
          SwiftDescriptions.filterSwiftSources(
              sourcePathResolver,
              args.getSrcs()),
          Optional.of(true),
          Optional.empty(),
          ImmutableList.of(StringWithMacros.of(importArg)),
          Optional.of(underlyingModule));

      switch (type.get().getValue()) {
        case HEADERS:
          return Optional.of(createHeaderSymlinkTreeBuildRule(
              untypedBuildTarget, projectFilesystem, resolver, platform.get(), args));
        case EXPORTED_HEADERS:
          return Optional.of(createExportedPlatformHeaderSymlinkTreeBuildRule(
              untypedBuildTarget, projectFilesystem, resolver, args, swiftCompile, moduleName));
        case SHARED:
          return Optional.of(createSharedLibraryBuildRule(
              untypedBuildTarget,
              projectFilesystem,
              resolver,
              cellRoots,
              delegate.getCxxBuckConfig(),
              platform.get(),
              args,
              cxxDeps.get(resolver, platform.get()),
              Linker.LinkType.SHARED,
              linkableDepType.orElse(Linker.LinkableDepType.SHARED),
              Optional.empty(),
              blacklist,
              transitiveCxxPreprocessorInputFunction));
        case STATIC:
        case STATIC_PIC:
          return Optional.of(createStaticLibraryBuildRule(
              untypedBuildTarget,
              projectFilesystem,
              resolver,
              cellRoots,
              delegate.getCxxBuckConfig(),
              platform.get(),
              args,
              cxxDeps.get(resolver, platform.get()),
              transitiveCxxPreprocessorInputFunction,
              swiftCompile));
        // $CASES-OMITTED$
        default:
      }
      throw new RuntimeException("unhandled library build type: " + type.get());
    }
    return Optional.empty();
  }

  private <A extends AppleNativeTargetDescriptionArg> BuildRule createStaticLibraryBuildRule(
      BuildTarget untypedBuildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      CxxBuckConfig cxxBuckConfig,
      CxxPlatform cxxPlatform,
      A args,
      ImmutableSet<BuildRule> deps,
      CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction transitiveCxxPreprocessorInputFunction,
      SwiftCompile swiftCompile) {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver sourcePathResolver = DefaultSourcePathResolver.from(ruleFinder);
    CxxSourceRuleFactory.PicType pic = CxxSourceRuleFactory.PicType.PIC;

    // Create rules for compiling the object files.
    ImmutableMap<CxxPreprocessAndCompile, SourcePath> objects =
        CxxLibraryDescription.requireObjects(
            untypedBuildTarget,
            projectFilesystem,
            resolver,
            sourcePathResolver,
            ruleFinder,
            cellRoots,
            cxxBuckConfig,
            cxxPlatform,
            pic,
            args,
            deps,
            transitiveCxxPreprocessorInputFunction);

    // Write a build rule to create the archive for this C/C++ library.
    BuildTarget staticTarget =
        CxxDescriptionEnhancer.createStaticLibraryBuildTarget(
            untypedBuildTarget, cxxPlatform.getFlavor(), pic);
    Path staticLibraryPath =
        CxxDescriptionEnhancer.getStaticLibraryPath(
            projectFilesystem,
            untypedBuildTarget,
            cxxPlatform.getFlavor(),
            pic,
            cxxPlatform.getStaticLibraryExtension());
    return Archive.from(
        staticTarget,
        projectFilesystem,
        ruleFinder,
        cxxPlatform,
        cxxBuckConfig.getArchiveContents(),
        staticLibraryPath,
        RichStream.from(objects.values())
            .concat(RichStream.of(swiftCompile.getSourcePathToObjectOutput()))
            .toImmutableList(),
        /* cacheable */ true);
  }

  @SuppressWarnings("unused")
  private <A extends AppleNativeTargetDescriptionArg> BuildRule createSharedLibraryBuildRule(
      BuildTarget untypedBuildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      CxxBuckConfig cxxBuckConfig,
      CxxPlatform cxxPlatform,
      A args,
      ImmutableSet<BuildRule> buildRules,
      Linker.LinkType shared,
      Linker.LinkableDepType linkableDepType,
      Optional<Object> empty,
      ImmutableSet<BuildTarget> blacklist,
      CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction transitiveCxxPreprocessorInputFunction) {
    throw new NotImplementedException();
  }

  @SuppressWarnings("unused")
  private <A extends AppleNativeTargetDescriptionArg> BuildRule createHeaderSymlinkTreeBuildRule(
      BuildTarget untypedBuildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleResolver resolver,
      CxxPlatform cxxPlatform,
      A args) {
    throw new NotImplementedException();
  }

  private <A extends AppleNativeTargetDescriptionArg> BuildRule createExportedPlatformHeaderSymlinkTreeBuildRule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleResolver resolver,
      A args,
      SwiftCompile swiftCompile,
      String moduleName) {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver sourcePathResolver = DefaultSourcePathResolver.from(ruleFinder);
    Path headerPathPrefix = Paths.get(moduleName);
    ImmutableSortedMap<String, SourcePath> headerMap =
        AppleDescriptions.convertAppleHeadersToPublicCxxHeaders(
            buildTarget, sourcePathResolver::getRelativePath, headerPathPrefix, args);
    ImmutableMap<Path, SourcePath> cxxHeaders = CxxPreprocessables.resolveHeaderMap(
        args.getHeaderNamespace().map(Paths::get).orElse(buildTarget.getBasePath()),
        headerMap);

    ImmutableMap<Path, SourcePath> swiftHeaders = ImmutableMap.of(
        Paths.get(moduleName, moduleName + "-Swift.h"), swiftCompile.getSourcePathToObjCHeaderOutput(),
        Paths.get(moduleName + ".swiftmodule"), swiftCompile.getSourcePathToSwiftModuleOutput()
    );
    ImmutableMap<Path, SourcePath> headers = ImmutableMap.<Path, SourcePath>builder()
        .putAll(swiftHeaders)
        .putAll(cxxHeaders)
        .build();
    return CxxDescriptionEnhancer.createHeaderSymlinkTree(
        buildTarget,
        projectFilesystem,
        HeaderMode.SYMLINK_TREE_WITH_MODULEMAP,
        headers,
        HeaderVisibility.PUBLIC);

  }

  private boolean shouldWrapIntoDebuggableBinary(BuildTarget buildTarget, BuildRule buildRule) {
    if (!AppleDebugFormat.FLAVOR_DOMAIN.getValue(buildTarget).isPresent()) {
      return false;
    }
    if (!buildTarget.getFlavors().contains(CxxDescriptionEnhancer.SHARED_FLAVOR)
        && !buildTarget.getFlavors().contains(CxxDescriptionEnhancer.MACH_O_BUNDLE_FLAVOR)) {
      return false;
    }

    return AppleDebuggableBinary.isBuildRuleDebuggable(buildRule);
  }

  <U> Optional<U> createMetadataForLibrary(
      BuildTarget buildTarget,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      Optional<ImmutableMap<BuildTarget, Version>> selectedVersions,
      AppleNativeTargetDescriptionArg args,
      Class<U> metadataClass) {

    // If this is a modular apple_library, always do symlinktree with modulemap
    if (args.isModular() &&
        buildTarget.getFlavors().contains(CxxLibraryDescription.MetadataType.CXX_HEADERS.getFlavor())) {
      Map.Entry<Flavor, HeaderMode> headerMode =
          HEADER_MODE.getFlavorAndValue(buildTarget).get();
      if (headerMode.getValue() != HeaderMode.SYMLINK_TREE_WITH_MODULEMAP) {
        buildTarget = buildTarget.withoutFlavors(headerMode.getKey())
            .withAppendedFlavors(HeaderMode.SYMLINK_TREE_WITH_MODULEMAP.getFlavor());
      }
    }
    // Forward to C/C++ library description.
    if (CxxLibraryDescription.METADATA_TYPE.containsAnyOf(buildTarget.getFlavors())) {
      CxxLibraryDescriptionArg.Builder delegateArg = CxxLibraryDescriptionArg.builder().from(args);
      AppleDescriptions.populateCxxLibraryDescriptionArg(
          DefaultSourcePathResolver.from(new SourcePathRuleFinder(resolver)),
          delegateArg,
          args,
          buildTarget);
      return delegate.createMetadata(
          buildTarget, resolver, cellRoots, delegateArg.build(), selectedVersions, metadataClass);
    }

    if (metadataClass.isAssignableFrom(FrameworkDependencies.class)
        && buildTarget.getFlavors().contains(AppleDescriptions.FRAMEWORK_FLAVOR)) {
      Optional<Flavor> cxxPlatformFlavor = delegate.getCxxPlatforms().getFlavor(buildTarget);
      Preconditions.checkState(
          cxxPlatformFlavor.isPresent(),
          "Could not find cxx platform in:\n%s",
          Joiner.on(", ").join(buildTarget.getFlavors()));
      ImmutableSet.Builder<SourcePath> sourcePaths = ImmutableSet.builder();
      for (BuildTarget dep : args.getDeps()) {
        Optional<FrameworkDependencies> frameworks =
            resolver.requireMetadata(
                dep.withAppendedFlavors(
                    AppleDescriptions.FRAMEWORK_FLAVOR,
                    AppleDescriptions.NO_INCLUDE_FRAMEWORKS_FLAVOR,
                    cxxPlatformFlavor.get()),
                FrameworkDependencies.class);
        if (frameworks.isPresent()) {
          sourcePaths.addAll(frameworks.get().getSourcePaths());
        }
      }
      // Not all parts of Buck use require yet, so require the rule here so it's available in the
      // resolver for the parts that don't.
      BuildRule buildRule = resolver.requireRule(buildTarget);
      sourcePaths.add(buildRule.getSourcePathToOutput());
      return Optional.of(metadataClass.cast(FrameworkDependencies.of(sourcePaths.build())));
    }

    return Optional.empty();
  }

  @Override
  public <U> Optional<U> createMetadata(
      BuildTarget buildTarget,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      AppleLibraryDescriptionArg args,
      Optional<ImmutableMap<BuildTarget, Version>> selectedVersions,
      Class<U> metadataClass) {
    return createMetadataForLibrary(
        buildTarget, resolver, cellRoots, selectedVersions, args, metadataClass);
  }

  @Override
  public ImmutableSortedSet<Flavor> addImplicitFlavors(
      ImmutableSortedSet<Flavor> argDefaultFlavors) {
    // Use defaults.apple_library if present, but fall back to defaults.cxx_library otherwise.
    return delegate.addImplicitFlavorsForRuleTypes(
        argDefaultFlavors,
        Description.getBuildRuleType(this),
        Description.getBuildRuleType(CxxLibraryDescription.class));
  }

  @Override
  public void findDepsForTargetFromConstructorArgs(
      final BuildTarget buildTarget,
      final CellPathResolver cellRoots,
      final AbstractAppleLibraryDescriptionArg constructorArg,
      ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder) {
    findDepsForTargetFromConstructorArgs(
        buildTarget,
        cellRoots,
        (AppleNativeTargetDescriptionArg) constructorArg,
        extraDepsBuilder,
        targetGraphOnlyDepsBuilder);
  }

  public void findDepsForTargetFromConstructorArgs(
      final BuildTarget buildTarget,
      final CellPathResolver cellRoots,
      final AppleNativeTargetDescriptionArg constructorArg,
      ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder) {
    delegate.findDepsForTargetFromConstructorArgs(
        buildTarget, cellRoots, constructorArg, extraDepsBuilder, targetGraphOnlyDepsBuilder);
  }

  public static boolean isNotStaticallyLinkedLibraryNode(
      TargetNode<CxxLibraryDescription.CommonArg, ?> node) {
    SortedSet<Flavor> flavors = node.getBuildTarget().getFlavors();
    if (LIBRARY_TYPE.getFlavor(flavors).isPresent()) {
      return flavors.contains(CxxDescriptionEnhancer.SHARED_FLAVOR)
          || flavors.contains(CxxDescriptionEnhancer.EXPORTED_HEADER_SYMLINK_TREE_FLAVOR);
    } else {
      return node.getConstructorArg().getPreferredLinkage().equals(Optional.of(Linkage.SHARED));
    }
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractAppleLibraryDescriptionArg extends AppleNativeTargetDescriptionArg {
    Optional<SourcePath> getInfoPlist();

    ImmutableMap<String, String> getInfoPlistSubstitutions();
  }
}
