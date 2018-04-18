package net.gegy1000.blocksystems.server.core;

import com.google.common.io.Closeables;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class MappingHandler {
    private static final Map<String, Map<String, String>> FIELD_MAPPINGS = new HashMap<>();
    private static final Map<String, Map<String, String>> METHOD_MAPPINGS = new HashMap<>();

    public static void loadMappings() {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(MappingHandler.class.getResourceAsStream("/blocksystems.mappings")));
            String line;
            while ((line = in.readLine()) != null) {
                int splitIndex = (line.contains("(") ? line.split("\\(")[0] : line).lastIndexOf('/');
                String cls = line.substring(0, splitIndex);
                String afterClass = line.substring(splitIndex + 1);
                String[] split = afterClass.split("=");
                String name = split[0];
                String mapping = split[1];
                if (line.contains("(") && line.contains(")")) {
                    Map<String, String> classMappings = METHOD_MAPPINGS.computeIfAbsent(cls, k -> new HashMap<>());
                    classMappings.put(name, mapping);
                } else {
                    Map<String, String> classMappings = FIELD_MAPPINGS.computeIfAbsent(cls, k -> new HashMap<>());
                    classMappings.put(name, mapping);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load BlockSystems ASM Mappings!");
            e.printStackTrace();
        } finally {
            Closeables.closeQuietly(in);
        }
    }

    public static String getMappedMethod(String cls, String method, String desc) {
        if (!BlockSystemPlugin.development) {
            cls = cls.replace(".", "/");
            Map<String, String> classMappings = METHOD_MAPPINGS.get(cls);
            if (classMappings != null) {
                String mapping = classMappings.get(method + desc);
                if (mapping != null) {
                    return mapping;
                }
            }
        }
        return method;
    }

    public static String getMappedField(String cls, String field) {
        if (!BlockSystemPlugin.development) {
            cls = cls.replace(".", "/");
            Map<String, String> classMappings = FIELD_MAPPINGS.get(cls);
            if (classMappings != null) {
                String mapping = classMappings.get(field);
                if (mapping != null) {
                    return mapping;
                }
            }
        }
        return field;
    }
}
