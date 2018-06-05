
package com.zerohouse.ngx;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Generates TypeScript declaration file from specified java classes.
 * For more information see README and Wiki on GitHub.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateMojo extends AbstractMojo {

    @Parameter
    private String path;

    @Parameter
    private String packagePath;


    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            NgxGenerator ngxGenerator = new NgxGenerator();
            Reflections reflections = new Reflections(new TypeAnnotationsScanner(), new MethodAnnotationsScanner(), new SubTypesScanner(), ClasspathHelper.forPackage(packagePath));
            Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Controller.class);
            classes.addAll(reflections.getTypesAnnotatedWith(RestController.class));
            classes.forEach(aClass -> ngxGenerator.generate(aClass, path));
            TsGenerator tsGenerator = new TsGenerator("ApiService",
                    "import {Injectable} from '@angular/core';");
            classes.forEach(aClass -> {
                String name = aClass.getSimpleName().replace("Controller", "").toLowerCase();
                tsGenerator.addImports(String.format("import {%s} from './%s';", aClass.getSimpleName(), aClass.getSimpleName()));
                tsGenerator.addDependency(String.format("public %s: %s", name, aClass.getSimpleName()));
            });
            tsGenerator.saveResult(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
