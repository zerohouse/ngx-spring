package com.zerohouse.ngx;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TsGenerator {
    String name;

    String imports;

    String head = "@Injectable({\n" +
            "  providedIn: 'root'\n" +
            "})\n" +
            "export class %s {\n";

    String end = "\n}\n";

    String methods = "";
    Set<String> dependencies = new HashSet<>();

    public TsGenerator(String name, String imports, String... dependencies) {
        this.name = name;
        this.imports = imports;
        this.dependencies.addAll(Arrays.asList(dependencies));
    }

    public String getResult() {
        return imports + "\n\n" + String.format(head, this.name) + constructor() + methods + end;
    }

    private String constructor() {
        if (dependencies == null || dependencies.size() == 0)
            return "";
        return String.format("  constructor(%s) {\n" +
                "  }\n\n", dependencies.stream().collect(Collectors.joining(", ")));
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
        String file = String.format("%s.ts", name);
        try {
            FileUtils.write(new File(path + "/" + file), getResult(), "utf8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
