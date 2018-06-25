package com.zerohouse.ngx;


import cz.habarta.typescript.generator.*;
import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.util.StringUtils;
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
        this.exclude(HttpServletRequest.class);
        this.exclude(HttpServletResponse.class);
        this.exclude(PathVariable.class);
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

    public void exclude(Class<?> aClass) {
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
            classes.forEach(aClass -> this.generateControllers(aClass, outputPath));
            TsGenerator apiService = new TsGenerator("ApiService",
                    "import {Injectable} from '@angular/core';");
            classes.forEach(aClass -> {
                String name = aClass.getSimpleName().replace("Controller", "");
                apiService.addImports(String.format("import {%s} from './controllers/%s';", aClass.getSimpleName(), TsGenerator.getFileName(aClass.getSimpleName())));
                apiService.addDependency(String.format("public %s: %s", StringUtils.uncapitalise(name), aClass.getSimpleName()));
            });
            apiService.saveResult(outputPath);
            TsGenerator ngxModule = new TsGenerator("NgxSpringModule",
                    "import {NgModule} from '@angular/core';\n" +
                            "import {HttpClientModule} from '@angular/common/http';\n" +
                            "import {ApiService} from './api.service';\n" +
                            "import {ApiHttp} from './api.http';");
            ngxModule.head = "@NgModule({\n" +
                    "  imports: [HttpClientModule],\n" +
                    "  providers: [ApiService, ApiHttp, " +
                    classes.stream().map(Class::getSimpleName).collect(Collectors.joining(", ")) +
                    "]\n" +
                    "})\nexport class %s {\n";
            classes.forEach(aClass -> {
                ngxModule.addImports(String.format("import {%s} from './controllers/%s';", aClass.getSimpleName(), TsGenerator.getFileName(aClass.getSimpleName())));
            });
            FileUtils.write(new File(outputPath + "/api.http.ts"), "import {Inject, Injectable, Optional} from '@angular/core';\n" +
                    "import {HttpClient} from '@angular/common/http';\n" +
                    "import {APP_BASE_HREF} from '@angular/common';\n" +
                    "import {Observable} from 'rxjs';\n" +
                    "\n" +
                    "@Injectable()\n" +
                    "export class ApiHttp {\n" +
                    "  constructor(private http: HttpClient, @Inject(APP_BASE_HREF) @Optional() private readonly origin: string) {\n" +
                    "    if (!origin)\n" +
                    "      this.origin = '';\n" +
                    "  }\n" +
                    "\n" +
                    "  put<T>(url, body?, queryParams?): Observable<T> {\n" +
                    "    return this.http.put<T>(this.origin + url, body, {params: this.valid(queryParams)});\n" +
                    "  }\n" +
                    "\n" +
                    "  delete<T>(url, queryParams?): Observable<T> {\n" +
                    "    return this.http.delete<T>(this.origin + url, {params: this.valid(queryParams)});\n" +
                    "  }\n" +
                    "\n" +
                    "  post<T>(url, body?, queryParams?): Observable<T> {\n" +
                    "    return this.http.post<T>(this.origin + url, body, {params: this.valid(queryParams)});\n" +
                    "  }\n" +
                    "\n" +
                    "  get<T>(url, queryParams?): Observable<T> {\n" +
                    "    return this.http.get<T>(this.origin + url, {params: this.valid(queryParams)});\n" +
                    "  }\n" +
                    "\n" +
                    "  private valid(queryParams) {\n" +
                    "    if (queryParams === null || queryParams === undefined)\n" +
                    "      return null;\n" +
                    "    Object.keys(queryParams).forEach(value => {\n" +
                    "      if (queryParams[value] === null || queryParams[value] === undefined)\n" +
                    "        delete  queryParams[value];\n" +
                    "    });\n" +
                    "    return queryParams;\n" +
                    "  }\n" +
                    "}\n", "utf8");
            ngxModule.saveResult(outputPath);
            new TypeScriptGenerator(settings).generateTypeScript(
                    Input.from(this.types.toArray(new Type[]{})),
                    Output.to(new File(outputPath + "/api.model.d.ts")));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private void generateControllers(Class<?> aClass, String path) {
        String name = aClass.getSimpleName();
        TsGenerator tsGenerator = new TsGenerator(name,
                "import {Injectable} from '@angular/core';\n" +
                        "import {Observable} from 'rxjs';\n" +
                        "import {ApiHttp} from '../api.http';\n",

                "private http: ApiHttp");
        Set<Method> methods = getMethodsAnnotatedWith(aClass, GetMapping.class);
        methods.addAll(getMethodsAnnotatedWith(aClass, PostMapping.class));
        methods.addAll(getMethodsAnnotatedWith(aClass, PutMapping.class));
        methods.addAll(getMethodsAnnotatedWith(aClass, DeleteMapping.class));
        methods.addAll(getMethodsAnnotatedWith(aClass, RequestMapping.class));
        Set<String> returnTypeSimpleNames = new HashSet<>();
        tsGenerator.addMethods(
                methods.stream().map(method -> {
                    String url = getUrl(method);

                    List<Param> params = new ArrayList<>();

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
                            this.typescriptModelAdd(parameter.getType());
                            params.add(new Param(names[i], parameter.getType().getSimpleName(), parameter.getAnnotation(RequestBody.class).required()));
                            continue;
                        }
                        this.typescriptModelAdd(parameter.getType());
                        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                        params.add(new Param(names[i], parameter.getType().getSimpleName(), requestParam != null && requestParam.required()));
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
                        ngxClientParams += ", " + String.format("{%s}",
                                queryParams.stream().map(s -> String.format("%s: %s", s, s))
                                        .collect(Collectors.joining(", ")));
                    }
                    return String.format(
                            "  %s(%s): Observable<%s> {\n" +
                                    "    return this.http.%s<%s>(%s);\n" +
                                    "  }\n",
                            methodName,
                            params.size() == 0 ? "" :
                                    params.stream().sorted((o1, o2) -> Boolean.compare(o2.required, o1.required)).map(param ->
                                            String.format("%s%s: %s", param.name, param.required ? "" : "?", makeFromTypeName(param.type, returnTypeSimpleNames))
                                    ).collect(Collectors.joining(", ")),
                            returnType,
                            httpMethod,
                            returnType,
                            ngxClientParams);
                }).collect(Collectors.joining("\n")));
        tsGenerator.addImports(returnTypeSimpleNames.stream()
                .map(s -> String.format("import {%s} from '../api.model';", s)).collect(Collectors.joining("\n")));
        tsGenerator.saveResult(path + "/controllers");
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
                return String.format(defaultTypes.get(name), "any");

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

    private Set<Param> getParamsInUrl(String url) {
        Set<Param> result = new HashSet<>();
        Pattern pattern = Pattern.compile("\\{(.+?)}");
        Matcher matcher = pattern.matcher(url);
        while (matcher.find()) {
            result.add(new Param(matcher.group(1), "string", true));
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