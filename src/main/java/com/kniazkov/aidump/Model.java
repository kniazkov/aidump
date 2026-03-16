package com.kniazkov.aidump;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

public final class Model {

    private Model() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class LibraryDump {
        public String schemaVersion;
        public String generatedAt;
        public ProjectInfo project;
        public List<String> sourceRoots = new ArrayList<>();
        public List<EntryPoint> entryPoints = new ArrayList<>();
        public List<ApiIndexItem> apiIndex = new ArrayList<>();
        public List<PackageDoc> packages = new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ProjectPom {
        public String groupId;
        public String artifactId;
        public String version;
        public String name;
        public String description;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ProjectInfo {
        public String rootDirectory;
        public String folderName;
        public String groupId;
        public String artifactId;
        public String version;
        public String name;
        public String description;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class PackageDoc {
        public String name;
        public List<TypeDoc> types = new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class TypeDoc {
        public String kind;
        public String name;
        public String qualifiedName;
        public String sourceFile;
        public boolean topLevel;
        public List<String> modifiers = new ArrayList<>();
        public List<String> annotations = new ArrayList<>();
        public List<String> typeParameters = new ArrayList<>();
        public String extendsType;
        public List<String> implementsTypes = new ArrayList<>();
        public JavadocDoc javadoc;
        public TypeHints hints;
        public List<FieldDoc> fields = new ArrayList<>();
        public List<ConstructorDoc> constructors = new ArrayList<>();
        public List<MethodDoc> methods = new ArrayList<>();
        public List<TypeDoc> nestedTypes = new ArrayList<>();
        public List<String> enumConstants = new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class TypeHints {
        public boolean hasPublicConstructor;
        public boolean hasStaticFactoryMethod;
        public boolean hasChainableMethods;
        public List<String> staticFactoryMethods = new ArrayList<>();
        public List<String> chainableMethods = new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class FieldDoc {
        public String name;
        public String type;
        public String declaringType;
        public List<String> modifiers = new ArrayList<>();
        public List<String> annotations = new ArrayList<>();
        public String initializer;
        public JavadocDoc javadoc;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ConstructorDoc {
        public String signature;
        public List<String> modifiers = new ArrayList<>();
        public List<String> annotations = new ArrayList<>();
        public List<ParameterDoc> parameters = new ArrayList<>();
        public List<String> throwsTypes = new ArrayList<>();
        public JavadocDoc javadoc;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class MethodDoc {
        public String name;
        public String signature;
        public String shortSignature;
        public String returnType;
        public String declaringType;
        public List<String> modifiers = new ArrayList<>();
        public List<String> annotations = new ArrayList<>();
        public List<String> typeParameters = new ArrayList<>();
        public List<ParameterDoc> parameters = new ArrayList<>();
        public List<String> throwsTypes = new ArrayList<>();
        public boolean isStatic;
        public boolean isChainable;
        public boolean returnsSelfType;
        public JavadocDoc javadoc;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ParameterDoc {
        public String name;
        public String type;
        public boolean varArgs;
        public List<String> annotations = new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class JavadocDoc {
        public String summary;
        public String details;
        public String raw;
        public List<ParamTagDoc> params = new ArrayList<>();
        public String returns;
        public List<ThrowsTagDoc> throwsDocs = new ArrayList<>();
        public String deprecated;
        public String since;
        public List<String> seeAlso = new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ParamTagDoc {
        public String name;
        public String description;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ThrowsTagDoc {
        public String name;
        public String description;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class EntryPoint {
        public String qualifiedName;
        public String kind;
        public String summary;
        public List<String> reasons = new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ApiIndexItem {
        public String qualifiedName;
        public String kind;
        public String summary;
        public List<String> constructors = new ArrayList<>();
        public List<String> methods = new ArrayList<>();
    }
}
