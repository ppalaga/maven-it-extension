package org.apache.maven.jupiter.extension;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.maven.jupiter.extension.maven.MavenCache;
import org.apache.maven.jupiter.extension.maven.MavenCacheResult;
import org.apache.maven.jupiter.extension.maven.MavenExecutionResult;
import org.apache.maven.jupiter.extension.maven.MavenExecutionResult.ExecutionResult;
import org.apache.maven.jupiter.extension.maven.MavenLog;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.util.AnnotationUtils;

public class MavenITExtension implements BeforeEachCallback, BeforeAllCallback, TestInstancePostProcessor,
    ParameterResolver, BeforeTestExecutionCallback, AfterTestExecutionCallback, AfterAllCallback {

  private static final String BASE_DIRECTORY = "BASE_DIRECTORY";

  private static final String EXECUTION_RESULT = "EXECUTION_RESULT";
  private static final String LOG_RESULT = "LOG_RESULT";
  private static final String CACHE_RESULT = "CACHE_RESULT";

  private static final Namespace NAMESPACE_MAVEN_IT = Namespace.create(MavenITExtension.class);

  private Optional<MavenIT> findMavenIt(ExtensionContext context) {
    Optional<ExtensionContext> current = Optional.of(context);
    while (current.isPresent()) {
      Optional<MavenIT> endToEndTest = AnnotationUtils.findAnnotation(current.get().getRequiredTestClass(),
          MavenIT.class);
      if (endToEndTest.isPresent()) {
        return endToEndTest;
      }
      current = current.get().getParent();
    }
    return Optional.empty();
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    System.out.println("MavenITExtension.beforeAll root:" + context.getUniqueId());
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    System.out.println("MavenITExtension.beforeEach " + context.getUniqueId());
    Class<?> testClass = context.getTestClass()
        .orElseThrow(() -> new ExtensionConfigurationException("MavenITExtension is only supported for classes."));

    MavenIT mavenITAnnotation = findMavenIt(context).orElseThrow(
        () -> new IllegalStateException("Annotation is not present."));

    File baseDirectory = new File(DirectoryHelper.getTargetDir(), "maven-it");
    String toFullyQualifiedPath = DirectoryHelper.toFullyQualifiedPath(testClass.getPackage(),
        testClass.getSimpleName());
    System.out.println("toFullyQualifiedPath = " + toFullyQualifiedPath);

    //    File mavenItBaseDirectory = new File(baseDirectory, DirectoryHelper.path(context.getUniqueId()).toString());
    File mavenItBaseDirectory = new File(baseDirectory, toFullyQualifiedPath);
    mavenItBaseDirectory.mkdirs();

    Store store = context.getStore(NAMESPACE_MAVEN_IT);
    store.put(BASE_DIRECTORY, mavenItBaseDirectory);
  }

  @Override
  public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
    System.out.println("MavenITExtension.postProcessTestInstance " + context.getUniqueId());
  }

  @Override
  public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    System.out.println("MavenITExtension.supportsParameter");
    List<Class<?>> availableTypes = Arrays.asList(MavenExecutionResult.class, MavenLog.class, MavenCacheResult.class);
    System.out.println(" --> Checking for " + availableTypes);
    System.out.println(
        "     parameterContext.getParameter().getName() = " + parameterContext.getParameter().getParameterizedType());
    return availableTypes.contains(parameterContext.getParameter().getParameterizedType());
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    System.out.println(" --> MavenITExtension.resolveParameter");

    Store nameSpace = extensionContext.getStore(NAMESPACE_MAVEN_IT);
    if (MavenExecutionResult.class.equals(parameterContext.getParameter().getType())) {
      return nameSpace.get(EXECUTION_RESULT + extensionContext.getUniqueId(), MavenExecutionResult.class);
    }
    if (MavenLog.class.equals(parameterContext.getParameter().getType())) {
      return nameSpace.get(LOG_RESULT + extensionContext.getUniqueId(), MavenLog.class);
    }
    if (MavenCacheResult.class.equals(parameterContext.getParameter().getType())) {
      return nameSpace.get(CACHE_RESULT + extensionContext.getUniqueId(), MavenCacheResult.class);
    }
    //TODO: Think about this.
    return Void.TYPE;
  }

  private boolean isDebug(Method method) {
    if (!method.isAnnotationPresent(MavenTest.class)) {
      throw new IllegalStateException("MavenTest Annotation nicht an der Method");
    }
    MavenTest mavenTestAnnotation = method.getAnnotation(MavenTest.class);

    return mavenTestAnnotation.debug();
  }

  private boolean hasProfiles(Method method) {
    return getProfiles(method).length > 0;
  }

  private boolean hasGoals(Method method) {
    return getGoals(method).length > 0;
  }

  private String[] getProfiles(Method method) {
    if (!method.isAnnotationPresent(MavenTest.class)) {
      throw new IllegalStateException("MavenTest Annotation nicht an der Method");
    }
    MavenTest mavenTestAnnotation = method.getAnnotation(MavenTest.class);

    return mavenTestAnnotation.activeProfiles();
  }

  private String[] getGoals(Method method) {
    if (!method.isAnnotationPresent(MavenTest.class)) {
      throw new IllegalStateException("MavenTest Annotation nicht an der Method");
    }
    MavenTest mavenTestAnnotation = method.getAnnotation(MavenTest.class);

    return mavenTestAnnotation.goals();
  }

  @Override
  public void beforeTestExecution(ExtensionContext context) throws IOException, InterruptedException {
    System.out.println("MavenITExtension.beforeTestExecution");
    AnnotatedElement annotatedElement = context.getElement()
        .orElseThrow(() -> new IllegalStateException("MavenIT Annotation not found."));

    Store nameSpace = context.getStore(NAMESPACE_MAVEN_IT);
    File mavenItBaseDirectory = nameSpace.get(BASE_DIRECTORY, File.class);

    Method methodName = context.getTestMethod().orElseThrow(() -> new IllegalStateException("No method given"));

    Class<?> testClass = context.getTestClass().orElseThrow(() -> new IllegalStateException("Test class not found."));
    MavenIT mavenIT = testClass.getAnnotation(MavenIT.class);
    System.out.println("mavenIT = " + mavenIT);

    File integrationTestCaseDirectory = new File(mavenItBaseDirectory, methodName.getName());
    integrationTestCaseDirectory.mkdirs();

    File cacheDirectory = new File(integrationTestCaseDirectory, ".m2/repository");
    if (MavenCache.Global.equals(mavenIT.mavenCache())) {
      cacheDirectory = new File(mavenItBaseDirectory, ".m2/repository");
    }
    cacheDirectory.mkdirs();

    File projectDirectory = new File(integrationTestCaseDirectory, "project");
    projectDirectory.mkdirs();

    String toFullyQualifiedPath = DirectoryHelper.toFullyQualifiedPath(testClass.getPackage(),
        testClass.getSimpleName());

    //FIXME: Removed hard coded parts.
    File mavenItsBaseDirectory = new File(DirectoryHelper.getTargetDir(), "test-classes/maven-its");
    File copyMavenPluginProject = new File(mavenItsBaseDirectory, toFullyQualifiedPath + "/" + methodName.getName());
    System.out.println("copyMavenPluginProject = " + copyMavenPluginProject);
    FileUtils.copyDirectory(copyMavenPluginProject, projectDirectory);

    //FIXME: Removed hard coded parts.
    ApplicationExecutor mavenExecutor = new ApplicationExecutor(projectDirectory, integrationTestCaseDirectory,
        new File("/Users/khmarbaise/tools/maven/bin/mvn"), Collections.emptyList(), "mvn");

    //Process start = mavenExecutor.start(Arrays.asList("--no-transfer-progress", "-V", "clean", "verify"));
    //FIXME: Need to think about the default options given for a IT.

    List<String> executionArguments = new ArrayList<>();
    List<String> defaultArguments = Arrays.asList("-Dmaven.repo.local=" + cacheDirectory.toString(), "--batch-mode",
        "-V");
    executionArguments.addAll(defaultArguments);

    if (hasProfiles(methodName)) {
      String collect = Arrays.asList(getProfiles(methodName)).stream().collect(joining(",", "-P", ""));
      executionArguments.add(collect);
    }

    if (isDebug(methodName)) {
      executionArguments.add("-X");
    }

    if (hasGoals(methodName)) {
      List<String> goals = Arrays.asList(getGoals(methodName)).stream().collect(toList());
      executionArguments.addAll(goals);
    }

    Process start = mavenExecutor.start(executionArguments);

    int processCompletableFuture = start.waitFor();

    ExecutionResult executionResult = ExecutionResult.Successful;
    if (processCompletableFuture != 0) {
      executionResult = ExecutionResult.Failure;
    }
    MavenExecutionResult result = new MavenExecutionResult(executionResult, processCompletableFuture);

    MavenLog log = new MavenLog(mavenExecutor.getStdout(), mavenExecutor.getStdErr());
    MavenCacheResult mavenCacheResult = new MavenCacheResult(cacheDirectory.toPath());

    nameSpace.put(EXECUTION_RESULT + context.getUniqueId(), result);
    nameSpace.put(LOG_RESULT + context.getUniqueId(), log);
    nameSpace.put(CACHE_RESULT + context.getUniqueId(), mavenCacheResult);
  }

  @Override
  public void afterTestExecution(ExtensionContext context) {
    System.out.println("MavenITExtension.afterTestExecution");
  }

  @Override
  public void afterAll(ExtensionContext context) {
    System.out.println("MavenITExtension.afterAll root:" + context.getUniqueId());
  }
}