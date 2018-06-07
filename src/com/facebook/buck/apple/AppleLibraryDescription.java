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

import static com.facebook.buck.cxx.toolchain.nativelink.NativeLinkable.Linkage;

import com.facebook.buck.apple.toolchain.AppleCxxPlatform;
import com.facebook.buck.apple.toolchain.CodeSignIdentityStore;
import com.facebook.buck.apple.toolchain.ProvisioningProfileStore;
import com.facebook.buck.core.cell.resolver.CellPathResolver;
import com.facebook.buck.core.description.BuildRuleParams;
import com.facebook.buck.core.description.DescriptionCache;
import com.facebook.buck.core.description.MetadataProvidingDescription;
import com.facebook.buck.core.description.attr.ImplicitDepsInferringDescription;
import com.facebook.buck.core.description.attr.ImplicitFlavorsInferringDescription;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.FlavorConvertible;
import com.facebook.buck.core.model.FlavorDomain;
import com.facebook.buck.core.model.Flavored;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.model.targetgraph.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.model.targetgraph.DescriptionWithTargetGraph;
import com.facebook.buck.core.model.targetgraph.TargetGraph;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolver;
import com.facebook.buck.core.sourcepath.resolver.impl.DefaultSourcePathResolver;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.facebook.buck.cxx.CxxCompilationDatabase;
import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.cxx.CxxLibraryDescriptionArg;
import com.facebook.buck.cxx.CxxLibraryFactory;
import com.facebook.buck.cxx.CxxLibraryFlavored;
import com.facebook.buck.cxx.CxxLibraryImplicitFlavors;
import com.facebook.buck.cxx.CxxLibraryMetadataFactory;
import com.facebook.buck.cxx.CxxPreprocessables;
import com.facebook.buck.cxx.CxxPreprocessorInput;
import com.facebook.buck.cxx.CxxStrip;
import com.facebook.buck.cxx.CxxSymlinkTreeHeaders;
import com.facebook.buck.cxx.FrameworkDependencies;
import com.facebook.buck.cxx.HasAppleDebugSymbolDeps;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.CxxPlatformsProvider;
import com.facebook.buck.cxx.toolchain.HeaderMode;
import com.facebook.buck.cxx.toolchain.HeaderSymlinkTree;
import com.facebook.buck.cxx.toolchain.HeaderVisibility;
import com.facebook.buck.cxx.toolchain.LinkerMapMode;
import com.facebook.buck.cxx.toolchain.StripStyle;
import com.facebook.buck.cxx.toolchain.linker.Linker;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.swift.SwiftBuckConfig;
import com.facebook.buck.toolchain.ToolchainProvider;
import com.facebook.buck.util.types.Either;
import com.facebook.buck.versions.Version;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import org.immutables.value.Value;

public class AppleLibraryDescription
    implements DescriptionWithTargetGraph<AppleLibraryDescriptionArg>,
        Flavored,
        ImplicitDepsInferringDescription<
            AppleLibraryDescription.AbstractAppleLibraryDescriptionArg>,
        ImplicitFlavorsInferringDescription,
        MetadataProvidingDescription<AppleLibraryDescriptionArg>,
        AppleNativeTargetCxxDescriptionDelegate {

  @SuppressWarnings("PMD") // PMD doesn't understand method references
  private static final Set<Flavor> SUPPORTED_FLAVORS =
      ImmutableSet.of(
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

  public enum Type implements FlavorConvertible {
    HEADERS(CxxDescriptionEnhancer.HEADER_SYMLINK_TREE_FLAVOR),
    EXPORTED_HEADERS(CxxDescriptionEnhancer.EXPORTED_HEADER_SYMLINK_TREE_FLAVOR),
    SANDBOX(CxxDescriptionEnhancer.SANDBOX_TREE_FLAVOR),
    SHARED(CxxDescriptionEnhancer.SHARED_FLAVOR),
    STATIC_PIC(CxxDescriptionEnhancer.STATIC_PIC_FLAVOR),
    STATIC(CxxDescriptionEnhancer.STATIC_FLAVOR),
    MACH_O_BUNDLE(CxxDescriptionEnhancer.MACH_O_BUNDLE_FLAVOR),
    FRAMEWORK(AppleDescriptions.FRAMEWORK_FLAVOR),
    SWIFT_COMPILE(AppleDescriptions.SWIFT_COMPILE_FLAVOR),
    SWIFT_OBJC_GENERATED_HEADER(AppleDescriptions.SWIFT_OBJC_GENERATED_HEADER_SYMLINK_TREE_FLAVOR),
    SWIFT_EXPORTED_OBJC_GENERATED_HEADER(
        AppleDescriptions.SWIFT_EXPORTED_OBJC_GENERATED_HEADER_SYMLINK_TREE_FLAVOR),
    SWIFT_UNDERLYING_MODULE(AppleDescriptions.SWIFT_UNDERLYING_MODULE_FLAVOR),
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

  private final ToolchainProvider toolchainProvider;
  private final AppleConfig appleConfig;
  private final SwiftBuckConfig swiftBuckConfig;
  private final CxxLibraryImplicitFlavors cxxLibraryImplicitFlavors;
  private final CxxLibraryFlavored cxxLibraryFlavored;
  private final CxxLibraryFactory cxxLibraryFactory;
  private final CxxLibraryMetadataFactory cxxLibraryMetadataFactory;

  public AppleLibraryDescription(
      ToolchainProvider toolchainProvider,
      AppleConfig appleConfig,
      SwiftBuckConfig swiftBuckConfig,
      CxxLibraryImplicitFlavors cxxLibraryImplicitFlavors,
      CxxLibraryFlavored cxxLibraryFlavored,
      CxxLibraryFactory cxxLibraryFactory,
      CxxLibraryMetadataFactory cxxLibraryMetadataFactory) {
    this.toolchainProvider = toolchainProvider;
    this.cxxLibraryImplicitFlavors = cxxLibraryImplicitFlavors;
    this.cxxLibraryFlavored = cxxLibraryFlavored;
    this.cxxLibraryFactory = cxxLibraryFactory;
    this.cxxLibraryMetadataFactory = cxxLibraryMetadataFactory;
    this.appleConfig = appleConfig;
    this.swiftBuckConfig = swiftBuckConfig;
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
    cxxLibraryFlavored.flavorDomains().ifPresent(domains -> builder.addAll(domains));

    ImmutableSet<FlavorDomain<?>> result = builder.build();

    // Drop StripStyle because it's overridden by AppleDebugFormat
    result =
        result
            .stream()
            .filter(domain -> !domain.equals(StripStyle.FLAVOR_DOMAIN))
            .collect(ImmutableSet.toImmutableSet());

    return Optional.of(result);
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    return SUPPORTED_FLAVORS.containsAll(flavors) || cxxLibraryFlavored.hasFlavors(flavors);
  }

  public Optional<BuildRule> createSwiftBuildRule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      ActionGraphBuilder graphBuilder,
      SourcePathRuleFinder ruleFinder,
      CellPathResolver cellRoots,
      AppleNativeTargetDescriptionArg args,
      Optional<AppleLibrarySwiftDelegate> swiftDelegate) {
    Optional<Map.Entry<Flavor, Type>> maybeType = LIBRARY_TYPE.getFlavorAndValue(buildTarget);
    return maybeType.flatMap(
        type -> {
          FlavorDomain<CxxPlatform> cxxPlatforms = getCxxPlatformsProvider().getCxxPlatforms();
          if (type.getValue().equals(Type.SWIFT_UNDERLYING_MODULE)) {
            return Optional.of(
                createUnderlyingModuleSymlinkTreeBuildRule(
                    buildTarget, projectFilesystem, graphBuilder, args));
          } else if (type.getValue().equals(Type.SWIFT_EXPORTED_OBJC_GENERATED_HEADER)) {
            CxxPlatform cxxPlatform =
                cxxPlatforms.getValue(buildTarget).orElseThrow(IllegalArgumentException::new);

            return Optional.of(
                AppleLibraryDescriptionSwiftEnhancer.createObjCGeneratedHeaderBuildRule(
                    buildTarget,
                    projectFilesystem,
                    ruleFinder,
                    graphBuilder,
                    cxxPlatform,
                    HeaderVisibility.PUBLIC));
          } else if (type.getValue().equals(Type.SWIFT_OBJC_GENERATED_HEADER)) {
            CxxPlatform cxxPlatform =
                cxxPlatforms.getValue(buildTarget).orElseThrow(IllegalArgumentException::new);

            return Optional.of(
                AppleLibraryDescriptionSwiftEnhancer.createObjCGeneratedHeaderBuildRule(
                    buildTarget,
                    projectFilesystem,
                    ruleFinder,
                    graphBuilder,
                    cxxPlatform,
                    HeaderVisibility.PRIVATE));
          } else if (type.getValue().equals(Type.SWIFT_COMPILE)) {
            CxxPlatform cxxPlatform =
                cxxPlatforms.getValue(buildTarget).orElseThrow(IllegalArgumentException::new);

            // TODO(mgd): Must handle 'default' platform
            AppleCxxPlatform applePlatform =
                getAppleCxxPlatformDomain()
                    .getValue(buildTarget)
                    .orElseThrow(IllegalArgumentException::new);

            ImmutableSet<CxxPreprocessorInput> preprocessorInputs =
                swiftDelegate
                    .map(
                        d ->
                            d.getPreprocessorInputForSwift(
                                buildTarget, graphBuilder, cxxPlatform, args))
                    .orElseGet(
                        () ->
                            AppleLibraryDescriptionSwiftEnhancer
                                .getPreprocessorInputsForAppleLibrary(
                                    buildTarget, graphBuilder, cxxPlatform, args));

            return Optional.of(
                AppleLibraryDescriptionSwiftEnhancer.createSwiftCompileRule(
                    buildTarget,
                    cellRoots,
                    graphBuilder,
                    ruleFinder,
                    params,
                    args,
                    projectFilesystem,
                    cxxPlatform,
                    applePlatform,
                    swiftBuckConfig,
                    preprocessorInputs));
          }

          return Optional.empty();
        });
  }

  @Override
  public BuildRule createBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      AppleLibraryDescriptionArg args) {
    TargetGraph targetGraph = context.getTargetGraph();
    ActionGraphBuilder graphBuilder = context.getActionGraphBuilder();
    Optional<Map.Entry<Flavor, Type>> type = LIBRARY_TYPE.getFlavorAndValue(buildTarget);
    if (type.isPresent() && type.get().getValue().equals(Type.FRAMEWORK)) {
      return createFrameworkBundleBuildRule(
          targetGraph, buildTarget, context.getProjectFilesystem(), params, graphBuilder, args);
    }

    Optional<BuildRule> swiftRule =
        createSwiftBuildRule(
            buildTarget,
            context.getProjectFilesystem(),
            params,
            graphBuilder,
            new SourcePathRuleFinder(graphBuilder),
            context.getCellPathResolver(),
            args,
            Optional.empty());
    if (swiftRule.isPresent()) {
      return swiftRule.get();
    }

    return createLibraryBuildRule(
        context,
        buildTarget,
        params,
        graphBuilder,
        args,
        args.getLinkStyle(),
        Optional.empty(),
        ImmutableSet.of(),
        ImmutableSortedSet.of(),
        CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction.fromLibraryRule());
  }

  private <A extends AbstractAppleLibraryDescriptionArg> BuildRule createFrameworkBundleBuildRule(
      TargetGraph targetGraph,
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      ActionGraphBuilder graphBuilder,
      AppleLibraryDescriptionArg args) {
    if (!args.getInfoPlist().isPresent()) {
      throw new HumanReadableException(
          "Cannot create framework for apple_library '%s':\n"
              + "No value specified for 'info_plist' attribute.",
          buildTarget.getUnflavoredBuildTarget());
    }
    if (!AppleDescriptions.INCLUDE_FRAMEWORKS.getValue(buildTarget).isPresent()) {
      return graphBuilder.requireRule(
          buildTarget.withAppendedFlavors(AppleDescriptions.INCLUDE_FRAMEWORKS_FLAVOR));
    }
    AppleDebugFormat debugFormat =
        AppleDebugFormat.FLAVOR_DOMAIN
            .getValue(buildTarget)
            .orElse(appleConfig.getDefaultDebugInfoFormatForLibraries());
    if (!buildTarget.getFlavors().contains(debugFormat.getFlavor())) {
      return graphBuilder.requireRule(buildTarget.withAppendedFlavors(debugFormat.getFlavor()));
    }

    CxxPlatformsProvider cxxPlatformsProvider = getCxxPlatformsProvider();

    return AppleDescriptions.createAppleBundle(
        cxxPlatformsProvider.getCxxPlatforms(),
        cxxPlatformsProvider.getDefaultCxxPlatform().getFlavor(),
        getAppleCxxPlatformDomain(),
        targetGraph,
        buildTarget,
        projectFilesystem,
        params,
        graphBuilder,
        toolchainProvider.getByName(
            CodeSignIdentityStore.DEFAULT_NAME, CodeSignIdentityStore.class),
        toolchainProvider.getByName(
            ProvisioningProfileStore.DEFAULT_NAME, ProvisioningProfileStore.class),
        Optional.of(buildTarget),
        Optional.empty(),
        Either.ofLeft(AppleBundleExtension.FRAMEWORK),
        Optional.empty(),
        args.getInfoPlist().get(),
        args.getInfoPlistSubstitutions(),
        args.getDeps(),
        args.getTests(),
        debugFormat,
        appleConfig.useDryRunCodeSigning(),
        appleConfig.cacheBundlesAndPackages(),
        appleConfig.shouldVerifyBundleResources(),
        appleConfig.assetCatalogValidation(),
        AppleAssetCatalogsCompilationOptions.builder().build(),
        ImmutableList.of(),
        Optional.empty(),
        Optional.empty(),
        appleConfig.getCodesignTimeout());
  }

  /**
   * @param bundleLoader The binary in which the current library will be (dynamically) loaded into.
   */
  public <A extends AppleNativeTargetDescriptionArg> BuildRule createLibraryBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      ActionGraphBuilder graphBuilder,
      A args,
      Optional<Linker.LinkableDepType> linkableDepType,
      Optional<SourcePath> bundleLoader,
      ImmutableSet<BuildTarget> blacklist,
      ImmutableSortedSet<BuildTarget> extraCxxDeps,
      CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction transitiveCxxPreprocessorInput) {
    // We explicitly remove flavors from params to make sure rule
    // has the same output regardless if we will strip or not.
    Optional<StripStyle> flavoredStripStyle = StripStyle.FLAVOR_DOMAIN.getValue(buildTarget);
    BuildTarget unstrippedBuildTarget =
        CxxStrip.removeStripStyleFlavorInTarget(buildTarget, flavoredStripStyle);

    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(graphBuilder));

    BuildRule unstrippedBinaryRule =
        requireUnstrippedBuildRule(
            context,
            unstrippedBuildTarget,
            params,
            graphBuilder,
            args,
            linkableDepType,
            bundleLoader,
            blacklist,
            pathResolver,
            extraCxxDeps,
            transitiveCxxPreprocessorInput);

    if (!shouldWrapIntoDebuggableBinary(unstrippedBuildTarget, unstrippedBinaryRule)) {
      return unstrippedBinaryRule;
    }

    CxxPlatformsProvider cxxPlatformsProvider = getCxxPlatformsProvider();
    FlavorDomain<CxxPlatform> cxxPlatforms = cxxPlatformsProvider.getCxxPlatforms();
    Flavor defaultCxxFlavor = cxxPlatformsProvider.getDefaultCxxPlatform().getFlavor();

    // If we built a multiarch binary, we can just use the strip tool from any platform.
    // We pick the platform in this odd way due to FlavorDomain's restriction of allowing only one
    // matching flavor in the build target.
    CxxPlatform representativePlatform =
        cxxPlatforms.getValue(
            Iterables.getFirst(
                Sets.intersection(cxxPlatforms.getFlavors(), unstrippedBuildTarget.getFlavors()),
                defaultCxxFlavor));

    BuildTarget strippedBuildTarget =
        CxxStrip.restoreStripStyleFlavorInTarget(unstrippedBuildTarget, flavoredStripStyle);

    BuildRule strippedBinaryRule =
        CxxDescriptionEnhancer.createCxxStripRule(
            strippedBuildTarget,
            context.getProjectFilesystem(),
            graphBuilder,
            flavoredStripStyle.orElse(StripStyle.NON_GLOBAL_SYMBOLS),
            unstrippedBinaryRule,
            representativePlatform);

    return AppleDescriptions.createAppleDebuggableBinary(
        unstrippedBuildTarget,
        context.getProjectFilesystem(),
        graphBuilder,
        strippedBinaryRule,
        (HasAppleDebugSymbolDeps) unstrippedBinaryRule,
        AppleDebugFormat.FLAVOR_DOMAIN
            .getValue(buildTarget)
            .orElse(appleConfig.getDefaultDebugInfoFormatForLibraries()),
        cxxPlatforms,
        defaultCxxFlavor,
        getAppleCxxPlatformDomain());
  }

  private <A extends AppleNativeTargetDescriptionArg> BuildRule requireUnstrippedBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      ActionGraphBuilder graphBuilder,
      A args,
      Optional<Linker.LinkableDepType> linkableDepType,
      Optional<SourcePath> bundleLoader,
      ImmutableSet<BuildTarget> blacklist,
      SourcePathResolver pathResolver,
      ImmutableSortedSet<BuildTarget> extraCxxDeps,
      CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction transitiveCxxPreprocessorInput) {
    Optional<MultiarchFileInfo> multiarchFileInfo =
        MultiarchFileInfos.create(getAppleCxxPlatformDomain(), buildTarget);
    if (multiarchFileInfo.isPresent()) {
      ImmutableSortedSet.Builder<BuildRule> thinRules = ImmutableSortedSet.naturalOrder();
      for (BuildTarget thinTarget : multiarchFileInfo.get().getThinTargets()) {
        thinRules.add(
            requireSingleArchUnstrippedBuildRule(
                context,
                thinTarget,
                params,
                graphBuilder,
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
          context.getProjectFilesystem(),
          // In the same manner that debug flavors are omitted from single-arch constituents, they
          // are omitted here as well.
          params,
          graphBuilder,
          multiarchFileInfo.get(),
          thinRules.build());
    } else {
      return requireSingleArchUnstrippedBuildRule(
          context,
          buildTarget,
          params,
          graphBuilder,
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
          BuildRuleCreationContextWithTargetGraph context,
          BuildTarget buildTarget,
          BuildRuleParams params,
          ActionGraphBuilder graphBuilder,
          A args,
          Optional<Linker.LinkableDepType> linkableDepType,
          Optional<SourcePath> bundleLoader,
          ImmutableSet<BuildTarget> blacklist,
          SourcePathResolver pathResolver,
          ImmutableSortedSet<BuildTarget> extraCxxDeps,
          CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction transitiveCxxDeps) {

    CxxLibraryDescriptionArg.Builder delegateArg = CxxLibraryDescriptionArg.builder().from(args);
    AppleDescriptions.populateCxxLibraryDescriptionArg(
        pathResolver, delegateArg, args, buildTarget);

    // remove some flavors from cxx rule that don't affect the rule output
    BuildTarget unstrippedTarget =
        buildTarget.withoutFlavors(AppleDebugFormat.FLAVOR_DOMAIN.getFlavors());
    if (AppleDescriptions.flavorsDoNotAllowLinkerMapMode(buildTarget)) {
      unstrippedTarget = unstrippedTarget.withoutFlavors(LinkerMapMode.NO_LINKER_MAP.getFlavor());
    }

    Optional<CxxPlatform> platform =
        getCxxPlatformsProvider().getCxxPlatforms().getValue(buildTarget);
    Optional<Type> libType = LIBRARY_TYPE.getValue(buildTarget);
    Optional<HeaderMode> headerMode = CxxLibraryDescription.HEADER_MODE.getValue(buildTarget);
    if (platform.isPresent()
        && libType.isPresent()
        && libType.get().equals(Type.EXPORTED_HEADERS)
        && headerMode.isPresent()
        && headerMode.get().equals(HeaderMode.SYMLINK_TREE_WITH_MODULEMAP)) {
      return createExportedModuleSymlinkTreeBuildRule(
          buildTarget, context.getProjectFilesystem(), graphBuilder, platform.get(), args);
    } else if (platform.isPresent()
        && libType.isPresent()
        && libType.get().equals(Type.SWIFT_UNDERLYING_MODULE)) {
      return createUnderlyingModuleSymlinkTreeBuildRule(
          buildTarget, context.getProjectFilesystem(), graphBuilder, args);
    }

    return graphBuilder.computeIfAbsent(
        unstrippedTarget,
        unstrippedTarget1 -> {
          return cxxLibraryFactory.createBuildRule(
              unstrippedTarget1,
              context.getProjectFilesystem(),
              params,
              graphBuilder,
              context.getCellPathResolver(),
              delegateArg.build(),
              linkableDepType,
              bundleLoader,
              blacklist,
              extraCxxDeps,
              transitiveCxxDeps,
              Optional.of(this));
        });
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

  /** @return a {@link HeaderSymlinkTree} for the exported headers of this C/C++ library. */
  private HeaderSymlinkTree createExportedModuleSymlinkTreeBuildRule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      ActionGraphBuilder graphBuilder,
      CxxPlatform cxxPlatform,
      AppleNativeTargetDescriptionArg args) {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(graphBuilder);
    SourcePathResolver pathResolver = DefaultSourcePathResolver.from(ruleFinder);

    Path headerPathPrefix = AppleDescriptions.getHeaderPathPrefix(args, buildTarget);
    ImmutableSortedMap.Builder<Path, SourcePath> headers = ImmutableSortedMap.naturalOrder();
    headers.putAll(
        CxxPreprocessables.resolveHeaderMap(
            Paths.get(""),
            AppleDescriptions.parseAppleHeadersForUseFromOtherTargets(
                buildTarget,
                pathResolver::getRelativePath,
                headerPathPrefix,
                args.getExportedHeaders())));
    getSwiftGeneratedObjCHeader(buildTarget, graphBuilder, cxxPlatform).ifPresent(headers::putAll);

    return CxxDescriptionEnhancer.createHeaderSymlinkTree(
        buildTarget,
        projectFilesystem,
        ruleFinder,
        HeaderMode.SYMLINK_TREE_WITH_MODULEMAP,
        headers.build(),
        HeaderVisibility.PUBLIC);
  }

  private HeaderSymlinkTree createUnderlyingModuleSymlinkTreeBuildRule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      ActionGraphBuilder graphBuilder,
      AppleNativeTargetDescriptionArg args) {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(graphBuilder);
    SourcePathResolver pathResolver = DefaultSourcePathResolver.from(ruleFinder);

    Path headerPathPrefix = AppleDescriptions.getHeaderPathPrefix(args, buildTarget);
    ImmutableMap<Path, SourcePath> headers =
        CxxPreprocessables.resolveHeaderMap(
            Paths.get(""),
            AppleDescriptions.parseAppleHeadersForUseFromOtherTargets(
                buildTarget,
                pathResolver::getRelativePath,
                headerPathPrefix,
                args.getExportedHeaders()));

    Path root = BuildTargets.getGenPath(projectFilesystem, buildTarget, "%s");
    return CxxPreprocessables.createHeaderSymlinkTreeBuildRule(
        buildTarget,
        projectFilesystem,
        ruleFinder,
        root,
        headers,
        HeaderMode.SYMLINK_TREE_WITH_MODULEMAP);
  }

  <U> Optional<U> createMetadataForLibrary(
      BuildTarget buildTarget,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      AppleNativeTargetDescriptionArg args,
      Class<U> metadataClass) {

    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(graphBuilder));

    if (CxxLibraryDescription.METADATA_TYPE.containsAnyOf(buildTarget.getFlavors())) {
      // Modules are always platform specific so we need to only have one platform specific
      // headersymlinktree with a modulemap. We cannot forward the metadata to a cxxlibrary
      // description as it makes an optimization of having multiple header symlinktrees (platform
      // specific and general). This also gives us more control over exposing the correct swift
      // header modularly for mixed targets
      if (args.isModular()) {
        Map.Entry<Flavor, CxxLibraryDescription.MetadataType> cxxMetaDataType =
            CxxLibraryDescription.METADATA_TYPE.getFlavorAndValue(buildTarget).get();
        switch (cxxMetaDataType.getValue()) {
          case CXX_PREPROCESSOR_INPUT:
            return createCxxPreprocessorInputMetadata(
                buildTarget, graphBuilder, cellRoots, args, metadataClass, cxxMetaDataType);
          case CXX_HEADERS:
            throw new IllegalStateException(
                "Modular apple_library should provide a unified modular CXX_PREPROCESSOR_INPUT and not pass individual CXX_HEADERS");
        }
      } else {
        return forwardMetadataToCxxLibraryDescription(
            buildTarget, graphBuilder, cellRoots, args, metadataClass, pathResolver);
      }
    }

    if (metadataClass.isAssignableFrom(FrameworkDependencies.class)
        && buildTarget.getFlavors().contains(AppleDescriptions.FRAMEWORK_FLAVOR)) {
      Optional<Flavor> cxxPlatformFlavor =
          getCxxPlatformsProvider().getCxxPlatforms().getFlavor(buildTarget);
      Preconditions.checkState(
          cxxPlatformFlavor.isPresent(),
          "Could not find cxx platform in:\n%s",
          Joiner.on(", ").join(buildTarget.getFlavors()));
      ImmutableSet.Builder<SourcePath> sourcePaths = ImmutableSet.builder();
      for (BuildTarget dep : args.getDeps()) {
        Optional<FrameworkDependencies> frameworks =
            graphBuilder.requireMetadata(
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
      // graphBuilder for the parts that don't.
      BuildRule buildRule = graphBuilder.requireRule(buildTarget);
      sourcePaths.add(buildRule.getSourcePathToOutput());
      return Optional.of(metadataClass.cast(FrameworkDependencies.of(sourcePaths.build())));
    }

    Optional<Map.Entry<Flavor, MetadataType>> metaType =
        AppleNativeTargetCxxDescriptionDelegate.METADATA_TYPE.getFlavorAndValue(buildTarget);
    if (metaType.isPresent()) {
      return createSwiftMetadata(buildTarget, graphBuilder, args, metadataClass);
    }

    return Optional.empty();
  }

  private <U> Optional<U> createCxxPreprocessorInputMetadata(
      BuildTarget buildTarget,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      AppleNativeTargetDescriptionArg args,
      Class<U> metadataClass,
      Entry<Flavor, CxxLibraryDescription.MetadataType> cxxMetaDataType) {
    Entry<Flavor, CxxPlatform> platform =
        getCxxPlatformsProvider()
            .getCxxPlatforms()
            .getFlavorAndValue(buildTarget)
            .orElseThrow(IllegalArgumentException::new);
    Entry<Flavor, HeaderVisibility> visibility =
        CxxLibraryDescription.HEADER_VISIBILITY
            .getFlavorAndValue(buildTarget)
            .orElseThrow(IllegalArgumentException::new);
    BuildTarget baseTarget =
        buildTarget.withoutFlavors(
            cxxMetaDataType.getKey(), platform.getKey(), visibility.getKey());

    CxxPreprocessorInput.Builder cxxPreprocessorInputBuilder = CxxPreprocessorInput.builder();
    CxxLibraryMetadataFactory.addCxxPreprocessorInputFromArgs(
        cxxPreprocessorInputBuilder,
        args,
        platform,
        f ->
            CxxDescriptionEnhancer.toStringWithMacrosArgs(
                buildTarget, cellRoots, graphBuilder, platform.getValue(), f));

    HeaderSymlinkTree symlinkTree =
        (HeaderSymlinkTree)
            graphBuilder.requireRule(
                baseTarget
                    .withoutFlavors(LIBRARY_TYPE.getFlavors())
                    .withAppendedFlavors(
                        CxxLibraryDescription.Type.EXPORTED_HEADERS.getFlavor(),
                        platform.getKey(),
                        HeaderMode.SYMLINK_TREE_WITH_MODULEMAP.getFlavor()));
    cxxPreprocessorInputBuilder.addIncludes(
        CxxSymlinkTreeHeaders.from(symlinkTree, CxxPreprocessables.IncludeType.LOCAL));
    CxxPreprocessorInput cxxPreprocessorInput = cxxPreprocessorInputBuilder.build();
    return Optional.of(cxxPreprocessorInput).map(metadataClass::cast);
  }

  private <U> Optional<U> forwardMetadataToCxxLibraryDescription(
      BuildTarget buildTarget,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      AppleNativeTargetDescriptionArg args,
      Class<U> metadataClass,
      SourcePathResolver pathResolver) {
    CxxLibraryDescriptionArg.Builder delegateArg = CxxLibraryDescriptionArg.builder().from(args);
    AppleDescriptions.populateCxxLibraryDescriptionArg(
        pathResolver, delegateArg, args, buildTarget);
    return cxxLibraryMetadataFactory.createMetadata(
        buildTarget, graphBuilder, cellRoots, delegateArg.build(), metadataClass);
  }

  @Override
  public <U> Optional<U> createMetadata(
      BuildTarget buildTarget,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      AppleLibraryDescriptionArg args,
      Optional<ImmutableMap<BuildTarget, Version>> selectedVersions,
      Class<U> metadataClass) {
    return createMetadataForLibrary(buildTarget, graphBuilder, cellRoots, args, metadataClass);
  }

  @Override
  public ImmutableSortedSet<Flavor> addImplicitFlavors(
      ImmutableSortedSet<Flavor> argDefaultFlavors) {
    // Use defaults.apple_library if present, but fall back to defaults.cxx_library otherwise.
    return cxxLibraryImplicitFlavors.addImplicitFlavorsForRuleTypes(
        argDefaultFlavors,
        DescriptionCache.getBuildRuleType(this),
        DescriptionCache.getBuildRuleType(CxxLibraryDescription.class));
  }

  @Override
  public void findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      AbstractAppleLibraryDescriptionArg constructorArg,
      ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder) {
    MultiarchFileInfos.checkTargetSupportsMultiarch(getAppleCxxPlatformDomain(), buildTarget);
    extraDepsBuilder.addAll(
        cxxLibraryFactory.getPlatformParseTimeDeps(buildTarget, constructorArg));
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

  // CxxDescriptionDelegate

  @Override
  public ToolchainProvider toolchainProvider() {
    return toolchainProvider;
  }

  public static Optional<CxxPreprocessorInput> underlyingModuleCxxPreprocessorInput(
      BuildTarget target, ActionGraphBuilder graphBuilder, CxxPlatform platform) {
    return graphBuilder.requireMetadata(
        target.withFlavors(
            platform.getFlavor(),
            AppleLibraryDescription.MetadataType.APPLE_SWIFT_UNDERLYING_MODULE_INPUT.getFlavor()),
        CxxPreprocessorInput.class);
  }
}
