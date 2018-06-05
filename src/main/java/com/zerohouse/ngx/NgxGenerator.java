package com.zerohouse.ngx;


import cz.habarta.typescript.generator.*;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NgxGenerator {

    List<Class<?>> excludes = new ArrayList<>();
    ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    String prefix = "";

    Map<String, String> defaultTypes;

    Set<Type> types = new HashSet<>();

    public NgxGenerator(String urlPrefix) {
        this.excludeAdd(HttpServletRequest.class);
        this.excludeAdd(HttpServletResponse.class);
        this.excludeAdd(PathVariable.class);
        this.prefix = urlPrefix;
        defaultTypes = new HashMap<>();
        defaultTypes.put("boolean", "boolean");
        defaultTypes.put("Boolean", "boolean");
        defaultTypes.put("Long", "number");
        defaultTypes.put("Integer", "number");
        defaultTypes.put("int", "number");
        defaultTypes.put("long", "number");
        defaultTypes.put("Double", "number");
        defaultTypes.put("double", "number");
        defaultTypes.put("BigDecimal", "number");
        defaultTypes.put("BigInteger", "number");
        defaultTypes.put("String", "string");
        defaultTypes.put("List", "%s[]");
        defaultTypes.put("Set", "%s[]");
        defaultTypes.put("Map", "Map<%s>");
    }

    public void excludeAdd(Class<?> aClass) {
        this.excludes.add(aClass);
    }

    public void generate(String packagePath, String outputPath) {
        try {
            Settings settings = new Settings();
            settings.outputKind = TypeScriptOutputKind.module;
            settings.jsonLibrary = JsonLibrary.jackson2;
            Reflections reflections = new Reflections(packagePath, new TypeAnnotationsScanner(), new SubTypesScanner());
            Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Controller.class);
            classes.addAll(reflections.getTypesAnnotatedWith(RestController.class));
            classes.removeAll(this.excludes);
            classes.forEach(aClass -> this.generate(aClass, outputPath));
            TsGenerator tsGenerator = new TsGenerator("ApiService",
                    "import {Injectable} from '@angular/core';");
            classes.forEach(aClass -> {
                String name = aClass.getSimpleName().replace("Controller", "");
                tsGenerator.addImports(String.format("import {%s} from './%s';", aClass.getSimpleName(), aClass.getSimpleName()));
                tsGenerator.addDependency(String.format("public %s: %s", name.toLowerCase(), aClass.getSimpleName()));
            });
            tsGenerator.saveResult(outputPath);
            new TypeScriptGenerator(settings).generateTypeScript(
                    Input.from(this.types.toArray(new Type[]{})),
                    Output.to(new File(outputPath + "/model.d.ts")));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public void generate(Class<?> aClass, String path) {
        String name = aClass.getSimpleName();
        TsGenerator tsGenerator = new TsGenerator(name,
                "import {Inject, Injectable, Optional} from '@angular/core';\n" +
                        "import {HttpClient} from '@angular/common/http';\n" +
                        "import {Observable} from 'rxjs';\n" +
                        "import {APP_BASE_HREF} from '@angular/common';\n", "private http: HttpClient", "@Optional() @Inject(APP_BASE_HREF) private origin: string");
        Set<Method> methods = getMethodsAnnotatedWith(aClass, GetMapping.class);
        methods.addAll(getMethodsAnnotatedWith(aClass, PostMapping.class));
        methods.addAll(getMethodsAnnotatedWith(aClass, PutMapping.class));
        methods.addAll(getMethodsAnnotatedWith(aClass, DeleteMapping.class));
        methods.addAll(getMethodsAnnotatedWith(aClass, RequestMapping.class));
        Set<String> returnTypeSimpleNames = new HashSet<>();
        tsGenerator.addMethods(
                methods.stream().map(method -> {
                    String url = getUrl(method);

                    List<String> params = new ArrayList<>();

                    if (url.contains("{") && url.contains("}")) {
                        url = "`" + url.replaceAll("\\{(.+?)}", "\\${$1}") + "`";
                        params.addAll(getParamsInUrl(url));
                    } else
                        url = "'" + url + "'";


                    Parameter[] parameters = method.getParameters();
                    String body = null;
                    String[] names = parameterNameDiscoverer.getParameterNames(method);
                    Set<String> queryParams = new HashSet<>();
                    for (int i = 0; i < parameters.length; i++) {
                        Parameter parameter = parameters[i];
                        if (excludes.contains(parameter.getType()) || Arrays.stream(parameter.getAnnotations()).anyMatch(annotation -> excludes.contains(annotation.annotationType())))
                            continue;
                        if (parameter.isAnnotationPresent(RequestBody.class)) {
                            body = names[i];
                            params.add(0, getTypedParameterName(names[i], parameter.getType(), returnTypeSimpleNames, false));
                            continue;
                        }
                        params.add(getTypedParameterName(names[i], parameter.getType(), returnTypeSimpleNames, true));
                        queryParams.add(names[i]);
                    }

                    String methodName = method.getName();
                    String httpMethod = getMethod(method);
                    this.typescriptModelAdd(method.getGenericReturnType());
                    String returnType = makeFromTypeName(method.getGenericReturnType().getTypeName(), returnTypeSimpleNames);
                    String ngxClientParams = url;

                    if (httpMethod.equals("post") || methodName.equals("put")) {
                        if (body == null)
                            ngxClientParams += ", null";
                        else
                            ngxClientParams += ", " + body;
                    }
                    if (!queryParams.isEmpty()) {
                        ngxClientParams += ", " + String.format("{params: new HttpParams().%s}",
                                queryParams.stream().map(s -> String.format("set('%s', String(%s))", s, s))
                                        .collect(Collectors.joining(".")));
                        tsGenerator.imports = tsGenerator.imports.replace("import {HttpClient} from '@angular/common/http';", "import {HttpClient, HttpParams} from '@angular/common/http';");
                    }
                    return String.format(
                            "  %s(%s): Observable<%s> {\n" +
                                    "    return this.http.%s<%s>(this.origin + %s);\n" +
                                    "  }\n",
                            methodName,
                            params.size() == 0 ? "" : params.stream().collect(Collectors.joining(", ")),
                            returnType,
                            httpMethod,
                            returnType,
                            ngxClientParams);
                }).collect(Collectors.joining("\n")));
        tsGenerator.addImports(returnTypeSimpleNames.stream()
                .map(s -> String.format("import {%s} from './model';", s)).collect(Collectors.joining("\n")));
        tsGenerator.saveResult(path);
    }

    private String getTypedParameterName(String name, Class<?> type, Set<String> returnTypeSimpleNames, boolean q) {
        this.typescriptModelAdd(type);
        return String.format("%s%s: %s", name, q ? "?" : "", makeFromTypeName(type.getTypeName(), returnTypeSimpleNames));
    }

    public void typescriptModelAdd(Type type) {
        if (excludes.contains(type))
            return;
        if (type.getClass().equals(Class.class) && Arrays.stream(((Class) type).getAnnotations()).anyMatch(annotation -> excludes.contains(annotation.annotationType())))
            return;
        this.types.add(type);
    }


    private String makeFromTypeName(String typeName, Set<String> returnTypeSimpleNames, String... args) {
        if (!typeName.contains("<")) {
            String name = parsedName(typeName, returnTypeSimpleNames);
            if (defaultTypes.containsKey(name)) {
                if (args.length != 0)
                    return String.format(defaultTypes.get(name), args[0]);
                return defaultTypes.get(name);
            }
            if (!name.contains(",")) {
                returnTypeSimpleNames.add(name);
            }
            if (args.length != 0)
                return String.format("%s<%s>", name, args[0]);
            return name;
        }
        return makeFromTypeName(typeName.substring(0, typeName.indexOf("<")), returnTypeSimpleNames,
                makeFromTypeName(typeName.substring(typeName.indexOf("<") + 1, typeName.lastIndexOf(">")), returnTypeSimpleNames));
    }

    private String parsedName(String typeName, Set<String> returnTypeSimpleNames) {
        if (typeName.contains(",")) {
            return Arrays.stream(typeName.split(",")).map(s -> this.makeFromTypeName(s, returnTypeSimpleNames)).collect(Collectors.joining(", "));
        }
        if (typeName.contains("$"))
            return typeName.substring(typeName.lastIndexOf("$") + 1);
        return typeName.substring(typeName.lastIndexOf(".") + 1);
    }

    private Set<String> getParamsInUrl(String url) {
        Set<String> result = new HashSet<>();
        Pattern pattern = Pattern.compile("\\{(.+?)}");
        Matcher matcher = pattern.matcher(url);
        while (matcher.find()) {
            result.add(matcher.group(1) + ": string");
        }
        return result;
    }

    private String getMethod(Method method) {
        List<RequestMethod> result = new ArrayList<>();
        RequestMapping methodAnnotation = method.getAnnotation(RequestMapping.class);
        if (methodAnnotation != null)
            Collections.addAll(result, methodAnnotation.method());
        if (method.isAnnotationPresent(GetMapping.class) && !result.contains(RequestMethod.GET))
            result.add(RequestMethod.GET);
        if (method.isAnnotationPresent(PostMapping.class) && !result.contains(RequestMethod.POST))
            result.add(RequestMethod.POST);
        if (method.isAnnotationPresent(DeleteMapping.class) && !result.contains(RequestMethod.DELETE))
            result.add(RequestMethod.DELETE);
        if (method.isAnnotationPresent(PutMapping.class) && !result.contains(RequestMethod.PUT))
            result.add(RequestMethod.PUT);
        result.toArray(new RequestMethod[0]);
        return result.get(0).toString().toLowerCase();
    }

    private String getUrl(Method method) {
        String url = prefix == null ? "" : prefix;
        url += method.getDeclaringClass().getAnnotation(RequestMapping.class) != null ?
                method.getDeclaringClass().getAnnotation(RequestMapping.class).value().length != 0 ?
                        method.getDeclaringClass().getAnnotation(RequestMapping.class).value()[0] : "" : "";
        if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
            if (requestMapping.value().length != 0)
                url += requestMapping.value()[0];
        } else if (method.isAnnotationPresent(GetMapping.class)) {
            GetMapping requestMapping = method.getAnnotation(GetMapping.class);
            if (requestMapping.value().length != 0)
                url += requestMapping.value()[0];
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            PostMapping requestMapping = method.getAnnotation(PostMapping.class);
            if (requestMapping.value().length != 0)
                url += requestMapping.value()[0];
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            PutMapping requestMapping = method.getAnnotation(PutMapping.class);
            if (requestMapping.value().length != 0)
                url += requestMapping.value()[0];
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            DeleteMapping requestMapping = method.getAnnotation(DeleteMapping.class);
            if (requestMapping.value().length != 0)
                url += requestMapping.value()[0];
        }
        return url;
    }

    public static Set<Method> getMethodsAnnotatedWith(final Class<?> type, final Class<? extends Annotation> annotation) {
        final Set<Method> methods = new HashSet<>();
        Class<?> klass = type;
        while (klass != Object.class) { // need to iterated thought hierarchy in order to retrieve methods from above the current instance
            final List<Method> allMethods = new ArrayList<Method>(Arrays.asList(klass.getDeclaredMethods()));
            for (final Method method : allMethods) {
                if (method.isAnnotationPresent(annotation)) {
                    methods.add(method);
                }
            }
            klass = klass.getSuperclass();
        }
        return methods;
    }

}