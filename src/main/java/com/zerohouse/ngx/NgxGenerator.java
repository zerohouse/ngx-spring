package com.zerohouse.ngx;


import org.springframework.web.bind.annotation.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NgxGenerator {

    String prefix = "";

    String paramMethod = "  %s(%s): Observable<%s> {\n" +
            "    return this.http.%s<%s>(%s, %s);\n" +
            "  }\n";


    Map<String, String> defaultTypes;

    public NgxGenerator(String prefix) {
        this.prefix = prefix;
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

    public void generate(Class<?> aClass, String path) {
        String name = aClass.getSimpleName();
        TsGenerator tsGenerator = new TsGenerator(name,
                "import {Injectable} from '@angular/core';\n" +
                        "import {HttpClient} from '@angular/common/http';\n" +
                        "import {Observable} from 'rxjs';\n", "private http: HttpClient");
        Set<Method> methods = getMethodsAnnotatedWith(aClass, GetMapping.class);
        methods.addAll(getMethodsAnnotatedWith(aClass, PostMapping.class));
        methods.addAll(getMethodsAnnotatedWith(aClass, PutMapping.class));
        methods.addAll(getMethodsAnnotatedWith(aClass, DeleteMapping.class));
        methods.addAll(getMethodsAnnotatedWith(aClass, RequestMapping.class));
        Set<String> returnTypeSimpleNames = new HashSet<>();
        tsGenerator.addMethods(
                methods.stream().map(method -> {
                    String url = getUrl(method);
                    String params = "param?";
                    if (url.contains("{") && url.contains("}")) {
                        url = "`" + url.replaceAll("\\{(.+?)}", "\\${$1}") + "`";
                        Set<String> paramsInUrl = getParamsInUrl(url);
                        params = paramsInUrl.stream().collect(Collectors.joining(", ")) + ", " + params;
                    } else
                        url = "'" + url + "'";

                    String methodName = method.getName();
                    String httpMethod = getMethod(method);
                    String returnType = getReturnType(method, returnTypeSimpleNames);
                    String paramsString = "param";
                    if (httpMethod.equals("get") || methodName.equals("delete")) {
                        paramsString = "{params: param}";
                    }
                    return String.format(paramMethod, methodName, params, returnType, httpMethod, returnType, url, paramsString);
                }).collect(Collectors.joining("\n")));
        tsGenerator.addImports(returnTypeSimpleNames.stream()
                .map(s -> String.format("import {%s} from './app';", s)).collect(Collectors.joining("\n")));
        tsGenerator.saveResult(path);
    }

    private String getReturnType(Method method, Set<String> returnTypeSimpleNames) {
        return makeName(method.getGenericReturnType().getTypeName(), returnTypeSimpleNames);
    }

    private String makeName(String typeName, Set<String> returnTypeSimpleNames, String... args) {
        if (!typeName.contains("<")) {
            String name = parsedName(typeName, returnTypeSimpleNames);
            if (defaultTypes.containsKey(name)) {
                if (args.length != 0)
                    return String.format(defaultTypes.get(name), args[0]);
                return defaultTypes.get(name);
            }
            if (!name.contains(","))
                returnTypeSimpleNames.add(name);
            if (args.length != 0)
                return String.format("%s<%s>", name, args[0]);
            System.out.println(name);
            return name;
        }
        return makeName(typeName.substring(0, typeName.indexOf("<")), returnTypeSimpleNames,
                makeName(typeName.substring(typeName.indexOf("<") + 1, typeName.lastIndexOf(">")), returnTypeSimpleNames));
    }

    private String parsedName(String typeName, Set<String> returnTypeSimpleNames) {
        if (typeName.contains(",")) {
            return Arrays.stream(typeName.split(",")).map(s -> this.makeName(s, returnTypeSimpleNames)).collect(Collectors.joining(", "));
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
            result.add(matcher.group(1));
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