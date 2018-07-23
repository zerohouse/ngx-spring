package com.zerohouse.ngx;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TsGenerator {
    String name;

    String imports;

    String head = "@Injectable()\n" +
            "export class %s {\n";

    String end = "}\n";

    String methods = "";
    Set<String> dependencies = new LinkedHashSet<>();

    public TsGenerator(String name, String imports, String... dependencies) {
        this.name = name;
        this.imports = "/* tslint:disable */\n" + imports;
        this.dependencies.addAll(Arrays.asList(dependencies));
    }

    public String getResult() {
        return imports + "\n\n" + String.format(head, this.name) + constructor() + methods + end;
    }

    private String constructor() {
        if (dependencies == null || dependencies.size() == 0)
            return "";
        String origin = "";
        if (dependencies.stream().anyMatch(s -> s.contains("APP_BASE_HREF")))
            origin = "       if (!origin)\n" +
                    "         this.origin = '';";
        return String.format("  constructor(%s) {\n%s" +
                "  }\n\n", dependencies.stream().collect(Collectors.joining(", ")), origin);
    }

    public void addImports(String collect) {
        this.imports += "\n" + collect;
    }

    public void addMethods(String collect) {
        this.methods += collect;
    }

    public void addDependency(String format) {
        this.dependencies.add(format);
    }

    public void saveResult(String path) {
        String file = getFileName(name) + ".ts";
        try {
            FileUtils.write(new File(path + "/" + file), getResult(), "utf8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getFileName(String name) {
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_DOT, String.format("%s", name));
    }
}
