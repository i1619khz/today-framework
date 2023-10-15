/*
 * Copyright 2017 - 2023 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see [http://www.gnu.org/licenses/]
 */

package cn.taketoday.gradle.plugin;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator;
import org.gradle.jvm.application.tasks.CreateStartScripts;
import org.gradle.util.GradleVersion;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import cn.taketoday.core.io.ClassPathResource;
import cn.taketoday.gradle.tasks.run.InfraRun;
import cn.taketoday.util.StreamUtils;

/**
 * Action that is executed in response to the {@link ApplicationPlugin} being applied.
 *
 * @author Andy Wilkinson
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0
 */
final class ApplicationPluginAction implements PluginApplicationAction {

  @Override
  public void execute(Project project) {
    JavaApplication javaApplication = project.getExtensions().getByType(JavaApplication.class);
    DistributionContainer distributions = project.getExtensions().getByType(DistributionContainer.class);
    Distribution distribution = distributions.create("infra");
    distribution.getDistributionBaseName()
            .convention(project.provider(() -> javaApplication.getApplicationName() + "-infra-app"));
    TaskProvider<CreateStartScripts> infraStartScripts = project.getTasks()
            .register("infraStartScripts", CreateStartScripts.class,
                    (CreateStartScripts task) -> configureCreateStartScripts(project, javaApplication, distribution, task));
    CopySpec binCopySpec = project.copySpec().into("bin").from(infraStartScripts);
    configureFilePermissions(binCopySpec, 0755);
    distribution.getContents().with(binCopySpec);
    applyApplicationDefaultJvmArgsToRunTasks(project.getTasks(), javaApplication);
  }

  private void applyApplicationDefaultJvmArgsToRunTasks(TaskContainer tasks, JavaApplication javaApplication) {
    applyApplicationDefaultJvmArgsToRunTask(tasks, javaApplication, InfraApplicationPlugin.INFRA_RUN_TASK_NAME);
    applyApplicationDefaultJvmArgsToRunTask(tasks, javaApplication, InfraApplicationPlugin.INFRA_TEST_RUN_TASK_NAME);
  }

  private void applyApplicationDefaultJvmArgsToRunTask(TaskContainer tasks, JavaApplication javaApplication, String taskName) {
    tasks.named(taskName, InfraRun.class)
            .configure(infraRun -> infraRun.getConventionMapping()
                    .map("jvmArgs", javaApplication::getApplicationDefaultJvmArgs));
  }

  private void configureCreateStartScripts(Project project, JavaApplication javaApplication,
          Distribution distribution, CreateStartScripts createStartScripts) {
    createStartScripts.setDescription("Generates OS-specific start scripts to run the project as a Infra application.");

    ((TemplateBasedScriptGenerator) createStartScripts.getUnixStartScriptGenerator())
            .setTemplate(project.getResources().getText().fromString(loadResource("unixStartScript.txt")));

    ((TemplateBasedScriptGenerator) createStartScripts.getWindowsStartScriptGenerator())
            .setTemplate(project.getResources().getText().fromString(loadResource("windowsStartScript.txt")));

    project.getConfigurations().all(configuration -> {
      if (InfraApplicationPlugin.INFRA_ARCHIVES_CONFIGURATION_NAME.equals(configuration.getName())) {
        distribution.getContents().with(artifactFilesToLibCopySpec(project, configuration));
        createStartScripts.setClasspath(configuration.getArtifacts().getFiles());
      }
    });
    createStartScripts.getConventionMapping()
            .map("outputDir", () -> project.getLayout().getBuildDirectory().dir("infraScripts").get().getAsFile());
    createStartScripts.getConventionMapping().map("applicationName", javaApplication::getApplicationName);
    createStartScripts.getConventionMapping().map("defaultJvmOpts", javaApplication::getApplicationDefaultJvmArgs);
  }

  private CopySpec artifactFilesToLibCopySpec(Project project, Configuration configuration) {
    CopySpec copySpec = project.copySpec().into("lib").from(artifactFiles(configuration));
    configureFilePermissions(copySpec, 0644);
    return copySpec;
  }

  private Callable<FileCollection> artifactFiles(Configuration configuration) {
    return () -> configuration.getArtifacts().getFiles();
  }

  @Override
  public Class<? extends Plugin<Project>> getPluginClass() {
    return ApplicationPlugin.class;
  }

  private String loadResource(String name) {
    ClassPathResource resource = new ClassPathResource(name);
    try (InputStream inputStream = resource.getInputStream()) {
      return StreamUtils.copyToString(inputStream);
    }
    catch (IOException ex) {
      throw new GradleException("Failed to read '" + name + "'", ex);
    }
  }

  private void configureFilePermissions(CopySpec copySpec, int mode) {
    if (GradleVersion.current().compareTo(GradleVersion.version("8.3")) >= 0) {
      try {
        Method filePermissions = copySpec.getClass().getMethod("filePermissions", Action.class);
        filePermissions.invoke(copySpec, new Action<Object>() {

          @Override
          public void execute(Object filePermissions) {
            String unixPermissions = Integer.toString(mode, 8);
            try {
              Method unix = filePermissions.getClass().getMethod("unix", String.class);
              unix.invoke(filePermissions, unixPermissions);
            }
            catch (Exception ex) {
              throw new GradleException("Failed to set file permissions to '" + unixPermissions + "'",
                      ex);
            }
          }

        });
      }
      catch (Exception ex) {
        throw new GradleException("Failed to set file permissions", ex);
      }
    }
    else {
      copySpec.setFileMode(mode);
    }
  }

}
