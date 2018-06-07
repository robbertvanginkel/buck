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

import com.facebook.buck.apple.AppleBinaryDescription.AbstractAppleBinaryDescriptionArg;
import com.facebook.buck.apple.AppleLibraryDescription.Type;
import com.facebook.buck.apple.toolchain.AppleCxxPlatform;
import com.facebook.buck.apple.toolchain.AppleCxxPlatformsProvider;
import com.facebook.buck.apple.toolchain.ApplePlatform;
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
import com.facebook.buck.core.model.FlavorDomain;
import com.facebook.buck.core.model.Flavored;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.model.targetgraph.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.model.targetgraph.DescriptionWithTargetGraph;
import com.facebook.buck.core.model.targetgraph.TargetGraph;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolver;
import com.facebook.buck.core.sourcepath.resolver.impl.DefaultSourcePathResolver;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.facebook.buck.cxx.CxxBinaryDescription;
import com.facebook.buck.cxx.CxxBinaryDescriptionArg;
import com.facebook.buck.cxx.CxxBinaryFactory;
import com.facebook.buck.cxx.CxxBinaryFlavored;
import com.facebook.buck.cxx.CxxBinaryImplicitFlavors;
import com.facebook.buck.cxx.CxxBinaryMetadataFactory;
import com.facebook.buck.cxx.CxxCompilationDatabase;
import com.facebook.buck.cxx.CxxLibraryDescription.CommonArg;
import com.facebook.buck.cxx.CxxPreprocessorInput;
import com.facebook.buck.cxx.FrameworkDependencies;
import com.facebook.buck.cxx.HasAppleDebugSymbolDeps;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.CxxPlatforms;
import com.facebook.buck.cxx.toolchain.CxxPlatformsProvider;
import com.facebook.buck.cxx.toolchain.HeaderVisibility;
import com.facebook.buck.cxx.toolchain.LinkerMapMode;
import com.facebook.buck.cxx.toolchain.StripStyle;
import com.facebook.buck.file.WriteFile;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.swift.SwiftBuckConfig;
import com.facebook.buck.toolchain.ToolchainProvider;
import com.facebook.buck.util.types.Either;
import com.facebook.buck.versions.Version;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.immutables.value.Value;

public class AppleBinaryDescription
    implements DescriptionWithTargetGraph<AppleBinaryDescriptionArg>,
        Flavored,
        ImplicitDepsInferringDescription<AbstractAppleBinaryDescriptionArg>,
        ImplicitFlavorsInferringDescription,
        MetadataProvidingDescription<AppleBinaryDescriptionArg>,
        AppleNativeTargetCxxDescriptionDelegate,
        AppleLibrarySwiftDelegate {

  public static final Flavor APP_FLAVOR = InternalFlavor.of("app");
  public static final Sets.SetView<Flavor> NON_DELEGATE_FLAVORS =
      Sets.union(AppleDebugFormat.FLAVOR_DOMAIN.getFlavors(), ImmutableSet.of(APP_FLAVOR));
  public static final Flavor LEGACY_WATCH_FLAVOR = InternalFlavor.of("legacy_watch");

  @SuppressWarnings("PMD") // PMD doesn't understand method references
  private static final Set<Flavor> SUPPORTED_FLAVORS =
      ImmutableSet.of(
          APP_FLAVOR,
          CxxCompilationDatabase.COMPILATION_DATABASE,
          CxxCompilationDatabase.UBER_COMPILATION_DATABASE,
          AppleDebugFormat.DWARF_AND_DSYM.getFlavor(),
          AppleDebugFormat.DWARF.getFlavor(),
          AppleDebugFormat.NONE.getFlavor(),
          LinkerMapMode.NO_LINKER_MAP.getFlavor());

  private final ToolchainProvider toolchainProvider;
  private final AppleConfig appleConfig;
  private final SwiftBuckConfig swiftBuckConfig;
  private final CxxBinaryImplicitFlavors cxxBinaryImplicitFlavors;
  private final CxxBinaryFactory cxxBinaryFactory;
  private final CxxBinaryMetadataFactory cxxBinaryMetadataFactory;
  private final CxxBinaryFlavored cxxBinaryFlavored;

  public AppleBinaryDescription(
      ToolchainProvider toolchainProvider,
      AppleConfig appleConfig,
      SwiftBuckConfig swiftBuckConfig,
      CxxBinaryImplicitFlavors cxxBinaryImplicitFlavors,
      CxxBinaryFactory cxxBinaryFactory,
      CxxBinaryMetadataFactory cxxBinaryMetadataFactory,
      CxxBinaryFlavored cxxBinaryFlavored) {
    this.toolchainProvider = toolchainProvider;
    this.appleConfig = appleConfig;
    this.swiftBuckConfig = swiftBuckConfig;
    this.cxxBinaryImplicitFlavors = cxxBinaryImplicitFlavors;
    this.cxxBinaryFactory = cxxBinaryFactory;
    this.cxxBinaryMetadataFactory = cxxBinaryMetadataFactory;
    this.cxxBinaryFlavored = cxxBinaryFlavored;
  }

  @Override
  public Class<AppleBinaryDescriptionArg> getConstructorArgType() {
    return AppleBinaryDescriptionArg.class;
  }

  @Override
  public Optional<ImmutableSet<FlavorDomain<?>>> flavorDomains() {
    ImmutableSet.Builder<FlavorDomain<?>> builder = ImmutableSet.builder();

    ImmutableSet<FlavorDomain<?>> localDomains = ImmutableSet.of(AppleDebugFormat.FLAVOR_DOMAIN);

    builder.addAll(localDomains);
    cxxBinaryFlavored.flavorDomains().ifPresent(domains -> builder.addAll(domains));

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
    if (FluentIterable.from(flavors).allMatch(SUPPORTED_FLAVORS::contains)) {
      return true;
    }
    ImmutableSet<Flavor> delegateFlavors =
        ImmutableSet.copyOf(Sets.difference(flavors, NON_DELEGATE_FLAVORS));
    ImmutableList<ImmutableSortedSet<Flavor>> thinFlavorSets =
        generateThinDelegateFlavors(delegateFlavors);
    if (thinFlavorSets.size() > 0) {
      return Iterables.all(thinFlavorSets, cxxBinaryFlavored::hasFlavors);
    } else {
      return cxxBinaryFlavored.hasFlavors(delegateFlavors);
    }
  }

  private ImmutableList<ImmutableSortedSet<Flavor>> generateThinDelegateFlavors(
      ImmutableSet<Flavor> delegateFlavors) {
    return MultiarchFileInfos.generateThinFlavors(
        getAppleCxxPlatformsFlavorDomain().getFlavors(),
        ImmutableSortedSet.copyOf(delegateFlavors));
  }

  @Override
  public BuildRule createBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      AppleBinaryDescriptionArg args) {
    Optional<BuildRule> swiftRule = createSwiftBuildRule(buildTarget, context, params, args, this);
    if (swiftRule.isPresent()) {
      return swiftRule.get();
    }

    FlavorDomain<AppleCxxPlatform> appleCxxPlatformsFlavorDomain =
        getAppleCxxPlatformsFlavorDomain();
    if (buildTarget.getFlavors().contains(APP_FLAVOR)) {
      return createBundleBuildRule(
          context.getTargetGraph(),
          buildTarget,
          context.getProjectFilesystem(),
          params,
          context.getActionGraphBuilder(),
          appleCxxPlatformsFlavorDomain,
          args);
    } else {
      return createBinaryBuildRule(
          buildTarget,
          context.getProjectFilesystem(),
          params,
          context.getActionGraphBuilder(),
          context.getCellPathResolver(),
          appleCxxPlatformsFlavorDomain,
          args);
    }
  }

  private FlavorDomain<AppleCxxPlatform> getAppleCxxPlatformsFlavorDomain() {
    AppleCxxPlatformsProvider appleCxxPlatformsProvider =
        toolchainProvider.getByName(
            AppleCxxPlatformsProvider.DEFAULT_NAME, AppleCxxPlatformsProvider.class);
    return appleCxxPlatformsProvider.getAppleCxxPlatforms();
  }

  // We want to wrap only if we have explicit debug flavor. This is because we don't want to
  // force dSYM generation in case if its enabled by default in config. We just want the binary,
  // so unless flavor is explicitly set, lets just produce binary!
  private boolean shouldWrapIntoAppleDebuggableBinary(
      BuildTarget buildTarget, BuildRule binaryBuildRule) {
    Optional<AppleDebugFormat> explicitDebugInfoFormat =
        AppleDebugFormat.FLAVOR_DOMAIN.getValue(buildTarget);
    boolean binaryIsWrappable = AppleDebuggableBinary.canWrapBinaryBuildRule(binaryBuildRule);
    return explicitDebugInfoFormat.isPresent() && binaryIsWrappable;
  }

  private BuildRule createBinaryBuildRule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      FlavorDomain<AppleCxxPlatform> appleCxxPlatformsFlavorDomain,
      AppleBinaryDescriptionArg args) {
    // remove some flavors so binary will have the same output regardless their values
    BuildTarget unstrippedBinaryBuildTarget =
        buildTarget
            .withoutFlavors(AppleDebugFormat.FLAVOR_DOMAIN.getFlavors())
            .withoutFlavors(StripStyle.FLAVOR_DOMAIN.getFlavors());

    BuildRule unstrippedBinaryRule =
        createBinary(
            unstrippedBinaryBuildTarget,
            projectFilesystem,
            params,
            graphBuilder,
            cellRoots,
            appleCxxPlatformsFlavorDomain,
            args);

    if (shouldWrapIntoAppleDebuggableBinary(buildTarget, unstrippedBinaryRule)) {
      return createAppleDebuggableBinary(
          buildTarget,
          projectFilesystem,
          params,
          graphBuilder,
          cellRoots,
          appleCxxPlatformsFlavorDomain,
          args,
          unstrippedBinaryBuildTarget,
          (HasAppleDebugSymbolDeps) unstrippedBinaryRule);
    } else {
      return unstrippedBinaryRule;
    }
  }

  private BuildRule createAppleDebuggableBinary(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      FlavorDomain<AppleCxxPlatform> appleCxxPlatformsFlavorDomain,
      AppleBinaryDescriptionArg args,
      BuildTarget unstrippedBinaryBuildTarget,
      HasAppleDebugSymbolDeps unstrippedBinaryRule) {
    CxxPlatformsProvider cxxPlatformsProvider = getCxxPlatformsProvider();
    BuildTarget strippedBinaryBuildTarget =
        unstrippedBinaryBuildTarget.withAppendedFlavors(
            StripStyle.FLAVOR_DOMAIN
                .getFlavor(buildTarget.getFlavors())
                .orElse(StripStyle.NON_GLOBAL_SYMBOLS.getFlavor()));
    BuildRule strippedBinaryRule =
        createBinary(
            strippedBinaryBuildTarget,
            projectFilesystem,
            params,
            graphBuilder,
            cellRoots,
            appleCxxPlatformsFlavorDomain,
            args);
    return AppleDescriptions.createAppleDebuggableBinary(
        unstrippedBinaryBuildTarget,
        projectFilesystem,
        graphBuilder,
        strippedBinaryRule,
        unstrippedBinaryRule,
        AppleDebugFormat.FLAVOR_DOMAIN.getRequiredValue(buildTarget),
        cxxPlatformsProvider.getCxxPlatforms(),
        cxxPlatformsProvider.getDefaultCxxPlatform().getFlavor(),
        appleCxxPlatformsFlavorDomain);
  }

  private BuildRule createBundleBuildRule(
      TargetGraph targetGraph,
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      ActionGraphBuilder graphBuilder,
      FlavorDomain<AppleCxxPlatform> appleCxxPlatformsFlavorDomain,
      AppleBinaryDescriptionArg args) {
    if (!args.getInfoPlist().isPresent()) {
      throw new HumanReadableException(
          "Cannot create application for apple_binary '%s':\n",
          "No value specified for 'info_plist' attribute.", buildTarget.getUnflavoredBuildTarget());
    }
    AppleDebugFormat flavoredDebugFormat =
        AppleDebugFormat.FLAVOR_DOMAIN
            .getValue(buildTarget)
            .orElse(appleConfig.getDefaultDebugInfoFormatForBinaries());
    if (!buildTarget.getFlavors().contains(flavoredDebugFormat.getFlavor())) {
      return graphBuilder.requireRule(
          buildTarget.withAppendedFlavors(flavoredDebugFormat.getFlavor()));
    }
    CxxPlatformsProvider cxxPlatformsProvider = getCxxPlatformsProvider();
    FlavorDomain<CxxPlatform> cxxPlatforms = cxxPlatformsProvider.getCxxPlatforms();
    Flavor defaultCxxFlavor = cxxPlatformsProvider.getDefaultCxxPlatform().getFlavor();
    if (!AppleDescriptions.INCLUDE_FRAMEWORKS.getValue(buildTarget).isPresent()) {
      CxxPlatform cxxPlatform =
          cxxPlatforms.getValue(buildTarget).orElse(cxxPlatforms.getValue(defaultCxxFlavor));
      ApplePlatform applePlatform =
          appleCxxPlatformsFlavorDomain
              .getValue(cxxPlatform.getFlavor())
              .getAppleSdk()
              .getApplePlatform();
      if (applePlatform.getAppIncludesFrameworks()) {
        return graphBuilder.requireRule(
            buildTarget.withAppendedFlavors(AppleDescriptions.INCLUDE_FRAMEWORKS_FLAVOR));
      }
      return graphBuilder.requireRule(
          buildTarget.withAppendedFlavors(AppleDescriptions.NO_INCLUDE_FRAMEWORKS_FLAVOR));
    }
    BuildTarget binaryTarget = buildTarget.withoutFlavors(APP_FLAVOR);
    return AppleDescriptions.createAppleBundle(
        cxxPlatforms,
        defaultCxxFlavor,
        appleCxxPlatformsFlavorDomain,
        targetGraph,
        buildTarget,
        projectFilesystem,
        params,
        graphBuilder,
        toolchainProvider.getByName(
            CodeSignIdentityStore.DEFAULT_NAME, CodeSignIdentityStore.class),
        toolchainProvider.getByName(
            ProvisioningProfileStore.DEFAULT_NAME, ProvisioningProfileStore.class),
        Optional.of(binaryTarget),
        Optional.empty(),
        Either.ofLeft(AppleBundleExtension.APP),
        Optional.empty(),
        args.getInfoPlist().get(),
        args.getInfoPlistSubstitutions(),
        args.getDeps(),
        args.getTests(),
        flavoredDebugFormat,
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

  private BuildRule createBinary(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      FlavorDomain<AppleCxxPlatform> appleCxxPlatformsFlavorDomain,
      AppleBinaryDescriptionArg args) {

    if (AppleDescriptions.flavorsDoNotAllowLinkerMapMode(buildTarget)) {
      buildTarget = buildTarget.withoutFlavors(LinkerMapMode.NO_LINKER_MAP.getFlavor());
    }

    Optional<MultiarchFileInfo> fatBinaryInfo =
        MultiarchFileInfos.create(appleCxxPlatformsFlavorDomain, buildTarget);
    if (fatBinaryInfo.isPresent()) {
      if (shouldUseStubBinary(buildTarget)) {
        BuildTarget thinTarget = Iterables.getFirst(fatBinaryInfo.get().getThinTargets(), null);
        return requireThinBinary(
            thinTarget,
            projectFilesystem,
            graphBuilder,
            cellRoots,
            appleCxxPlatformsFlavorDomain,
            args);
      }

      ImmutableSortedSet.Builder<BuildRule> thinRules = ImmutableSortedSet.naturalOrder();
      for (BuildTarget thinTarget : fatBinaryInfo.get().getThinTargets()) {
        thinRules.add(
            requireThinBinary(
                thinTarget,
                projectFilesystem,
                graphBuilder,
                cellRoots,
                appleCxxPlatformsFlavorDomain,
                args));
      }
      return MultiarchFileInfos.requireMultiarchRule(
          buildTarget,
          projectFilesystem,
          params,
          graphBuilder,
          fatBinaryInfo.get(),
          thinRules.build());
    } else {
      return requireThinBinary(
          buildTarget,
          projectFilesystem,
          graphBuilder,
          cellRoots,
          appleCxxPlatformsFlavorDomain,
          args);
    }
  }

  private BuildRule requireThinBinary(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      FlavorDomain<AppleCxxPlatform> appleCxxPlatformsFlavorDomain,
      AppleBinaryDescriptionArg args) {

    return graphBuilder.computeIfAbsent(
        buildTarget,
        ignored -> {
          SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(graphBuilder);
          SourcePathResolver pathResolver = DefaultSourcePathResolver.from(ruleFinder);

          Optional<Path> stubBinaryPath =
              getStubBinaryPath(buildTarget, appleCxxPlatformsFlavorDomain, args);
          if (shouldUseStubBinary(buildTarget) && stubBinaryPath.isPresent()) {
            try {
              return new WriteFile(
                  buildTarget,
                  projectFilesystem,
                  Files.readAllBytes(stubBinaryPath.get()),
                  BuildTargets.getGenPath(projectFilesystem, buildTarget, "%s"),
                  true);
            } catch (IOException e) {
              throw new HumanReadableException(
                  "Could not read stub binary " + stubBinaryPath.get());
            }
          } else {
            CxxBinaryDescriptionArg.Builder delegateArg =
                CxxBinaryDescriptionArg.builder().from(args);
            AppleDescriptions.populateCxxBinaryDescriptionArg(
                pathResolver, delegateArg, args, buildTarget);

            Optional<ApplePlatform> applePlatform =
                getApplePlatformForTarget(buildTarget, appleCxxPlatformsFlavorDomain);
            if (applePlatform.isPresent()
                && ApplePlatform.needsEntitlementsInBinary(applePlatform.get().getName())) {
              Optional<SourcePath> entitlements = args.getEntitlementsFile();
              if (entitlements.isPresent()) {
                ImmutableList<String> flags =
                    ImmutableList.of(
                        "-Xlinker",
                        "-sectcreate",
                        "-Xlinker",
                        "__TEXT",
                        "-Xlinker",
                        "__entitlements",
                        "-Xlinker",
                        pathResolver.getAbsolutePath(entitlements.get()).toString());
                delegateArg.addAllLinkerFlags(
                    Iterables.transform(
                        flags, flag -> StringWithMacros.of(ImmutableList.of(Either.ofLeft(flag)))));
              }
            }

            return cxxBinaryFactory.createBuildRule(
                buildTarget,
                projectFilesystem,
                graphBuilder,
                cellRoots,
                delegateArg.build(),
                Optional.of(this));
          }
        });
  }

  private boolean shouldUseStubBinary(BuildTarget buildTarget) {
    ImmutableSortedSet<Flavor> flavors = buildTarget.getFlavors();
    return (flavors.contains(AppleBundleDescription.WATCH_OS_FLAVOR)
        || flavors.contains(AppleBundleDescription.WATCH_SIMULATOR_FLAVOR)
        || flavors.contains(LEGACY_WATCH_FLAVOR));
  }

  private Optional<Path> getStubBinaryPath(
      BuildTarget buildTarget,
      FlavorDomain<AppleCxxPlatform> appleCxxPlatformsFlavorDomain,
      AppleBinaryDescriptionArg args) {
    Optional<Path> stubBinaryPath = Optional.empty();
    Optional<AppleCxxPlatform> appleCxxPlatform =
        getAppleCxxPlatformFromParams(appleCxxPlatformsFlavorDomain, buildTarget);
    if (appleCxxPlatform.isPresent() && args.getSrcs().isEmpty()) {
      stubBinaryPath = appleCxxPlatform.get().getStubBinary();
    }
    return stubBinaryPath;
  }

  private Optional<ApplePlatform> getApplePlatformForTarget(
      BuildTarget buildTarget, FlavorDomain<AppleCxxPlatform> appleCxxPlatformsFlavorDomain) {
    CxxPlatformsProvider cxxPlatformsProvider = getCxxPlatformsProvider();
    FlavorDomain<CxxPlatform> cxxPlatforms = cxxPlatformsProvider.getCxxPlatforms();
    Flavor defaultCxxFlavor = cxxPlatformsProvider.getDefaultCxxPlatform().getFlavor();
    CxxPlatform cxxPlatform =
        cxxPlatforms.getValue(buildTarget).orElse(cxxPlatforms.getValue(defaultCxxFlavor));

    if (!appleCxxPlatformsFlavorDomain.contains(cxxPlatform.getFlavor())) {
      return Optional.empty();
    }
    return Optional.of(
        appleCxxPlatformsFlavorDomain
            .getValue(cxxPlatform.getFlavor())
            .getAppleSdk()
            .getApplePlatform());
  }

  private Optional<AppleCxxPlatform> getAppleCxxPlatformFromParams(
      FlavorDomain<AppleCxxPlatform> appleCxxPlatformsFlavorDomain, BuildTarget buildTarget) {
    return appleCxxPlatformsFlavorDomain.getValue(buildTarget);
  }

  @Override
  public <U> Optional<U> createMetadata(
      BuildTarget buildTarget,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      AppleBinaryDescriptionArg args,
      Optional<ImmutableMap<BuildTarget, Version>> selectedVersions,
      Class<U> metadataClass) {
    Optional<Map.Entry<Flavor, MetadataType>> metaType =
        AppleNativeTargetCxxDescriptionDelegate.METADATA_TYPE.getFlavorAndValue(buildTarget);
    if (metaType.isPresent()) {
      return createSwiftMetadata(buildTarget, graphBuilder, args, metadataClass);
    }

    if (!metadataClass.isAssignableFrom(FrameworkDependencies.class)) {
      CxxBinaryDescriptionArg.Builder delegateArg = CxxBinaryDescriptionArg.builder().from(args);
      AppleDescriptions.populateCxxBinaryDescriptionArg(
          DefaultSourcePathResolver.from(new SourcePathRuleFinder(graphBuilder)),
          delegateArg,
          args,
          buildTarget);
      return cxxBinaryMetadataFactory.createMetadata(
          buildTarget, graphBuilder, delegateArg.build().getDeps(), metadataClass);
    }

    if (metadataClass.isAssignableFrom(HasEntitlementsFile.class)) {
      return Optional.of(metadataClass.cast(args));
    }

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
                  AppleDescriptions.NO_INCLUDE_FRAMEWORKS_FLAVOR, cxxPlatformFlavor.get()),
              FrameworkDependencies.class);
      if (frameworks.isPresent()) {
        sourcePaths.addAll(frameworks.get().getSourcePaths());
      }
    }

    return Optional.of(metadataClass.cast(FrameworkDependencies.of(sourcePaths.build())));
  }

  @Override
  public ImmutableSortedSet<Flavor> addImplicitFlavors(
      ImmutableSortedSet<Flavor> argDefaultFlavors) {
    // Use defaults.apple_binary if present, but fall back to defaults.cxx_binary otherwise.
    return cxxBinaryImplicitFlavors.addImplicitFlavorsForRuleTypes(
        argDefaultFlavors,
        DescriptionCache.getBuildRuleType(this),
        DescriptionCache.getBuildRuleType(CxxBinaryDescription.class));
  }

  @Override
  public void findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      AbstractAppleBinaryDescriptionArg constructorArg,
      ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder) {
    ImmutableList<ImmutableSortedSet<Flavor>> thinFlavorSets =
        generateThinDelegateFlavors(buildTarget.getFlavors());
    CxxPlatformsProvider cxxPlatformsProvider = getCxxPlatformsProvider();
    if (thinFlavorSets.size() > 0) {
      for (ImmutableSortedSet<Flavor> flavors : thinFlavorSets) {
        extraDepsBuilder.addAll(
            CxxPlatforms.findDepsForTargetFromConstructorArgs(
                cxxPlatformsProvider, buildTarget.withFlavors(flavors), Optional.empty()));
      }
    } else {
      extraDepsBuilder.addAll(
          CxxPlatforms.findDepsForTargetFromConstructorArgs(
              cxxPlatformsProvider, buildTarget, Optional.empty()));
    }
  }

  @Override
  public ToolchainProvider toolchainProvider() {
    return toolchainProvider;
  }

  public Optional<BuildRule> createSwiftBuildRule(
      BuildTarget buildTarget,
      BuildRuleCreationContextWithTargetGraph context,
      BuildRuleParams params,
      AppleNativeTargetDescriptionArg args,
      AppleLibrarySwiftDelegate swiftDelegate) {
    SourcePathRuleFinder sourcePathRuleFinder =
        new SourcePathRuleFinder(context.getActionGraphBuilder());
    Optional<Map.Entry<Flavor, Type>> maybeType =
        AppleLibraryDescription.LIBRARY_TYPE.getFlavorAndValue(buildTarget);
    return maybeType.flatMap(
        type -> {
          FlavorDomain<CxxPlatform> cxxPlatforms = getCxxPlatformsProvider().getCxxPlatforms();
          if (type.getValue().equals(Type.SWIFT_EXPORTED_OBJC_GENERATED_HEADER)) {
            CxxPlatform cxxPlatform =
                cxxPlatforms.getValue(buildTarget).orElseThrow(IllegalArgumentException::new);

            return Optional.of(
                AppleLibraryDescriptionSwiftEnhancer.createObjCGeneratedHeaderBuildRule(
                    buildTarget,
                    context.getProjectFilesystem(),
                    sourcePathRuleFinder,
                    context.getActionGraphBuilder(),
                    cxxPlatform,
                    HeaderVisibility.PUBLIC));
          } else if (type.getValue().equals(Type.SWIFT_OBJC_GENERATED_HEADER)) {
            CxxPlatform cxxPlatform =
                cxxPlatforms.getValue(buildTarget).orElseThrow(IllegalArgumentException::new);

            return Optional.of(
                AppleLibraryDescriptionSwiftEnhancer.createObjCGeneratedHeaderBuildRule(
                    buildTarget,
                    context.getProjectFilesystem(),
                    sourcePathRuleFinder,
                    context.getActionGraphBuilder(),
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
                swiftDelegate.getPreprocessorInputForSwift(
                    buildTarget, context.getActionGraphBuilder(), cxxPlatform, args);

            return Optional.of(
                AppleLibraryDescriptionSwiftEnhancer.createSwiftCompileRule(
                    buildTarget,
                    context.getCellPathResolver(),
                    context.getActionGraphBuilder(),
                    sourcePathRuleFinder,
                    params,
                    args,
                    context.getProjectFilesystem(),
                    cxxPlatform,
                    applePlatform,
                    swiftBuckConfig,
                    preprocessorInputs));
          }

          return Optional.empty();
        });
  }

  @Override
  public ImmutableSet<CxxPreprocessorInput> getPreprocessorInputForSwift(
      BuildTarget buildTarget,
      ActionGraphBuilder graphBuilder,
      CxxPlatform cxxPlatform,
      CommonArg args) {
    // TODO: query does not exist yet. Can't use objc headers in swift right now.
    return ImmutableSet.of();
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractAppleBinaryDescriptionArg
      extends AppleNativeTargetDescriptionArg, HasEntitlementsFile {
    Optional<SourcePath> getInfoPlist();

    ImmutableMap<String, String> getInfoPlistSubstitutions();
  }
}
