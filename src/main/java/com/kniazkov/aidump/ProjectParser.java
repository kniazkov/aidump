package com.kniazkov.aidump;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ProjectParser {

    private ProjectParser() {
    }

    public static Model.LibraryDump parse(Path projectRoot, Model.ProjectPom projectPom) throws IOException {
        Path sourceRoot = projectRoot.resolve("src/main/java");
        List<Path> javaFiles;

        if (Files.isDirectory(sourceRoot)) {
            javaFiles = scanJavaFiles(sourceRoot);
        } else {
            javaFiles = scanJavaFiles(projectRoot).stream()
                .filter(path -> !path.toString().contains("/target/"))
                .filter(path -> !path.toString().contains("\\target\\"))
                .collect(Collectors.toList());
            sourceRoot = projectRoot;
        }

        JavaParser parser = new JavaParser();
        Map<String, Model.PackageDoc> packageMap = new TreeMap<>();

        for (Path javaFile : javaFiles) {
            ParseResult<CompilationUnit> result = parser.parse(javaFile);
            if (result.getResult().isEmpty()) {
                System.err.println("Cannot parse file: " + javaFile);
                continue;
            }

            CompilationUnit cu = result.getResult().get();
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            Model.PackageDoc packageDoc = packageMap.computeIfAbsent(packageName, name -> {
                Model.PackageDoc p = new Model.PackageDoc();
                p.name = name;
                return p;
            });

            String relativeSourceFile = safeRelativize(projectRoot, javaFile);

            for (TypeDeclaration<?> type : cu.getTypes()) {
                if (!type.isPublic()) {
                    continue;
                }
                packageDoc.types.add(parseType(type, packageName, null, relativeSourceFile, true));
            }
        }

        for (Model.PackageDoc packageDoc : packageMap.values()) {
            packageDoc.types.sort(Comparator.comparing(t -> t.qualifiedName));
        }

        Model.LibraryDump dump = new Model.LibraryDump();
        dump.schemaVersion = "1.0";
        dump.generatedAt = OffsetDateTime.now().toString();
        dump.project = createProjectInfo(projectRoot, projectPom);
        dump.sourceRoots.add(safeRelativize(projectRoot, sourceRoot));
        dump.packages.addAll(packageMap.values());

        List<Model.TypeDoc> allTypes = new ArrayList<>();
        for (Model.PackageDoc pkg : dump.packages) {
            for (Model.TypeDoc type : pkg.types) {
                flattenTypes(type, allTypes);
            }
        }

        dump.apiIndex = buildApiIndex(allTypes);
        dump.entryPoints = buildEntryPoints(dump.packages);

        return dump;
    }

    private static List<Path> scanJavaFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .collect(Collectors.toList());
        }
    }

    private static Model.ProjectInfo createProjectInfo(Path projectRoot, Model.ProjectPom projectPom) {
        Model.ProjectInfo info = new Model.ProjectInfo();
        info.rootDirectory = projectRoot.toString();
        info.folderName = projectRoot.getFileName().toString();
        info.groupId = projectPom.groupId;
        info.artifactId = projectPom.artifactId;
        info.version = projectPom.version;
        info.name = projectPom.name;
        info.description = projectPom.description;
        return info;
    }

    private static Model.TypeDoc parseType(TypeDeclaration<?> type,
                                           String packageName,
                                           String enclosingQualifiedName,
                                           String relativeSourceFile,
                                           boolean topLevel) {
        Model.TypeDoc doc = new Model.TypeDoc();
        doc.kind = typeKind(type);
        doc.name = type.getNameAsString();
        doc.qualifiedName = buildQualifiedName(packageName, enclosingQualifiedName, doc.name);
        doc.sourceFile = relativeSourceFile;
        doc.topLevel = topLevel;
        doc.modifiers = modifiers(type.getModifiers());
        doc.annotations = annotations(type.getAnnotations());
        doc.typeParameters = typeParametersOf(type);
        doc.javadoc = readJavadoc(type.getJavadocComment().orElse(null));

        if (type instanceof ClassOrInterfaceDeclaration c) {
            doc.extendsType = c.getExtendedTypes().isEmpty() ? null : c.getExtendedTypes().get(0).toString();
            doc.implementsTypes = c.getImplementedTypes().stream().map(Object::toString).collect(Collectors.toList());
        } else if (type instanceof EnumDeclaration e) {
            doc.implementsTypes = e.getImplementedTypes().stream().map(Object::toString).collect(Collectors.toList());
            for (EnumConstantDeclaration constant : e.getEntries()) {
                doc.enumConstants.add(constant.getNameAsString());
            }
        }

        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof FieldDeclaration field) {
                if (field.isPublic()) {
                    doc.fields.addAll(parseField(field, doc.qualifiedName));
                }
            } else if (member instanceof ConstructorDeclaration constructor) {
                if (constructor.isPublic()) {
                    doc.constructors.add(parseConstructor(constructor));
                }
            } else if (member instanceof MethodDeclaration method) {
                if (method.isPublic()) {
                    doc.methods.add(parseMethod(method, doc.name, doc.qualifiedName));
                }
            } else if (member instanceof TypeDeclaration<?> nestedType) {
                if (nestedType.isPublic()) {
                    doc.nestedTypes.add(parseType(
                        nestedType,
                        packageName,
                        doc.qualifiedName,
                        relativeSourceFile,
                        false
                    ));
                }
            }
        }

        doc.fields.sort(Comparator.comparing(f -> f.name));
        doc.constructors.sort(Comparator.comparing(c -> c.signature));
        doc.methods.sort(Comparator.comparing(m -> m.shortSignature));
        doc.nestedTypes.sort(Comparator.comparing(t -> t.qualifiedName));
        doc.hints = buildTypeHints(doc);

        return doc;
    }

    private static List<Model.FieldDoc> parseField(FieldDeclaration field, String declaringType) {
        List<Model.FieldDoc> result = new ArrayList<>();

        for (var variable : field.getVariables()) {
            Model.FieldDoc doc = new Model.FieldDoc();
            doc.name = variable.getNameAsString();
            doc.type = variable.getType().toString();
            doc.declaringType = declaringType;
            doc.modifiers = modifiers(field.getModifiers());
            doc.annotations = annotations(field.getAnnotations());
            doc.initializer = variable.getInitializer()
                .map(Object::toString)
                .filter(s -> s.length() <= 160)
                .orElse(null);
            doc.javadoc = readJavadoc(field.getJavadocComment().orElse(null));
            result.add(doc);
        }

        return result;
    }

    private static Model.ConstructorDoc parseConstructor(ConstructorDeclaration constructor) {
        Model.ConstructorDoc doc = new Model.ConstructorDoc();
        doc.signature = constructor.getDeclarationAsString(false, false, false);
        doc.modifiers = modifiers(constructor.getModifiers());
        doc.annotations = annotations(constructor.getAnnotations());
        doc.parameters = constructor.getParameters().stream()
            .map(ProjectParser::parseParameter)
            .collect(Collectors.toList());
        doc.throwsTypes = constructor.getThrownExceptions().stream()
            .map(Object::toString)
            .collect(Collectors.toList());
        doc.javadoc = readJavadoc(constructor.getJavadocComment().orElse(null));
        return doc;
    }

    private static Model.MethodDoc parseMethod(MethodDeclaration method,
                                               String simpleTypeName,
                                               String qualifiedTypeName) {
        Model.MethodDoc doc = new Model.MethodDoc();
        doc.name = method.getNameAsString();
        doc.signature = method.getDeclarationAsString(false, false, false);
        doc.shortSignature = shortSignature(method);
        doc.returnType = method.getType().toString();
        doc.declaringType = qualifiedTypeName;
        doc.modifiers = modifiers(method.getModifiers());
        doc.annotations = annotations(method.getAnnotations());
        doc.typeParameters = method.getTypeParameters().stream()
            .map(Object::toString)
            .collect(Collectors.toList());
        doc.parameters = method.getParameters().stream()
            .map(ProjectParser::parseParameter)
            .collect(Collectors.toList());
        doc.throwsTypes = method.getThrownExceptions().stream()
            .map(Object::toString)
            .collect(Collectors.toList());
        doc.isStatic = method.isStatic();
        doc.returnsSelfType = returnsSelfType(method.getType().toString(), simpleTypeName, qualifiedTypeName);
        doc.isChainable = !doc.isStatic && doc.returnsSelfType;
        doc.javadoc = readJavadoc(method.getJavadocComment().orElse(null));
        return doc;
    }

    private static Model.ParameterDoc parseParameter(Parameter parameter) {
        Model.ParameterDoc doc = new Model.ParameterDoc();
        doc.name = parameter.getNameAsString();
        doc.type = parameter.getType().toString();
        doc.varArgs = parameter.isVarArgs();
        doc.annotations = annotations(parameter.getAnnotations());
        return doc;
    }

    private static Model.JavadocDoc readJavadoc(JavadocComment comment) {
        if (comment == null) {
            return null;
        }

        Javadoc parsed = comment.parse();
        String rawText = normalizeText(parsed.getDescription().toText().trim());

        Model.JavadocDoc doc = new Model.JavadocDoc();
        doc.raw = rawText;

        if (!rawText.isBlank()) {
            int split = firstSentenceEnd(rawText);
            if (split >= 0) {
                doc.summary = rawText.substring(0, split + 1).trim();
                String details = rawText.substring(split + 1).trim();
                doc.details = details.isBlank() ? null : details;
            } else {
                doc.summary = rawText;
            }
        }

        for (JavadocBlockTag tag : parsed.getBlockTags()) {
            switch (tag.getType()) {
                case PARAM -> {
                    Model.ParamTagDoc param = new Model.ParamTagDoc();
                    param.name = tag.getName().orElse("");
                    param.description = tag.getContent().toText().trim();
                    doc.params.add(param);
                }
                case RETURN -> doc.returns = tag.getContent().toText().trim();
                case THROWS, EXCEPTION -> {
                    Model.ThrowsTagDoc throwsDoc = new Model.ThrowsTagDoc();
                    throwsDoc.name = tag.getName().orElse("");
                    throwsDoc.description = tag.getContent().toText().trim();
                    doc.throwsDocs.add(throwsDoc);
                }
                case DEPRECATED -> doc.deprecated = tag.getContent().toText().trim();
                case SINCE -> doc.since = tag.getContent().toText().trim();
                case SEE -> doc.seeAlso.add(tag.getContent().toText().trim());
                default -> {
                    // intentionally ignored
                }
            }
        }

        return doc;
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        return text
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static int firstSentenceEnd(String text) {
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '.' && Character.isWhitespace(text.charAt(i + 1))) {
                return i;
            }
        }
        return -1;
    }

    private static Model.TypeHints buildTypeHints(Model.TypeDoc type) {
        Model.TypeHints hints = new Model.TypeHints();
        hints.hasPublicConstructor = !type.constructors.isEmpty();

        for (Model.MethodDoc method : type.methods) {
            if (method.isStatic && returnsSelfType(method.returnType, type.name, type.qualifiedName)) {
                hints.staticFactoryMethods.add(method.shortSignature);
            }
            if (method.isChainable) {
                hints.chainableMethods.add(method.shortSignature);
            }
        }

        hints.hasStaticFactoryMethod = !hints.staticFactoryMethods.isEmpty();
        hints.hasChainableMethods = !hints.chainableMethods.isEmpty();

        return hints;
    }

    private static List<Model.EntryPoint> buildEntryPoints(List<Model.PackageDoc> packages) {
        List<Model.EntryPoint> result = new ArrayList<>();

        for (Model.PackageDoc pkg : packages) {
            for (Model.TypeDoc type : pkg.types) {
                if (!type.topLevel) {
                    continue;
                }
                if (!"class".equals(type.kind) && !"interface".equals(type.kind)) {
                    continue;
                }

                List<String> reasons = new ArrayList<>();
                if (type.hints != null && type.hints.hasPublicConstructor) {
                    reasons.add("has public constructor");
                }
                if (type.hints != null && type.hints.hasStaticFactoryMethod) {
                    reasons.add("has static factory methods");
                }
                if (!type.methods.isEmpty()) {
                    reasons.add("has public methods");
                }

                if (!reasons.isEmpty()) {
                    Model.EntryPoint entryPoint = new Model.EntryPoint();
                    entryPoint.qualifiedName = type.qualifiedName;
                    entryPoint.kind = type.kind;
                    entryPoint.summary = type.javadoc != null ? type.javadoc.summary : null;
                    entryPoint.reasons = reasons;
                    result.add(entryPoint);
                }
            }
        }

        result.sort(Comparator.comparing(e -> e.qualifiedName));
        return result;
    }

    private static List<Model.ApiIndexItem> buildApiIndex(List<Model.TypeDoc> allTypes) {
        List<Model.ApiIndexItem> result = new ArrayList<>();

        for (Model.TypeDoc type : allTypes) {
            Model.ApiIndexItem item = new Model.ApiIndexItem();
            item.qualifiedName = type.qualifiedName;
            item.kind = type.kind;
            item.summary = type.javadoc != null ? type.javadoc.summary : null;
            item.constructors = type.constructors.stream()
                .map(c -> c.signature)
                .collect(Collectors.toList());
            item.methods = type.methods.stream()
                .map(m -> m.shortSignature)
                .collect(Collectors.toList());
            result.add(item);
        }

        result.sort(Comparator.comparing(i -> i.qualifiedName));
        return result;
    }

    private static void flattenTypes(Model.TypeDoc type, List<Model.TypeDoc> sink) {
        sink.add(type);
        for (Model.TypeDoc nested : type.nestedTypes) {
            flattenTypes(nested, sink);
        }
    }

    private static String buildQualifiedName(String packageName, String enclosingQualifiedName, String simpleName) {
        if (enclosingQualifiedName != null && !enclosingQualifiedName.isBlank()) {
            return enclosingQualifiedName + "." + simpleName;
        }
        if (packageName == null || packageName.isBlank()) {
            return simpleName;
        }
        return packageName + "." + simpleName;
    }

    private static String typeKind(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration c) {
            return c.isInterface() ? "interface" : "class";
        }
        if (type instanceof EnumDeclaration) {
            return "enum";
        }
        if (type instanceof AnnotationDeclaration) {
            return "annotation";
        }
        return "type";
    }

    private static List<String> modifiers(NodeList<Modifier> modifiers) {
        return modifiers.stream()
            .map(Modifier::getKeyword)
            .map(Object::toString)
            .collect(Collectors.toList());
    }

    private static List<String> annotations(Collection<AnnotationExpr> annotations) {
        return annotations.stream()
            .map(a -> a.getName().asString())
            .collect(Collectors.toList());
    }

    private static List<String> typeParametersOf(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration c) {
            return c.getTypeParameters().stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        }
        return List.of();
    }

    private static String shortSignature(MethodDeclaration method) {
        String params = method.getParameters().stream()
            .map(p -> p.getType().toString() + (p.isVarArgs() ? "..." : ""))
            .collect(Collectors.joining(", "));
        return method.getNameAsString() + "(" + params + ")";
    }

    private static boolean returnsSelfType(String returnType, String simpleTypeName, String qualifiedTypeName) {
        if (returnType == null) {
            return false;
        }
        return returnType.equals(simpleTypeName)
            || returnType.equals(qualifiedTypeName)
            || returnType.endsWith("." + simpleTypeName);
    }

    private static String safeRelativize(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (Exception ignored) {
            return file.toString().replace('\\', '/');
        }
    }
}
