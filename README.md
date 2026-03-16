# aidump

`aidump` is a small Java command-line tool that scans a Maven-based Java library project and generates a single structured JSON file describing its public API.

The output is intended for AI-assisted code generation. Instead of feeding a language model raw source files or incomplete fragments of documentation, you can give it one normalized JSON document containing:

- packages
- public classes, interfaces, enums, and annotations
- public fields, constructors, and methods
- JavaDoc summaries and details
- method signatures
- basic usage hints such as chainable methods and likely entry points

## Why this tool exists

When a language model has to generate code against a custom Java library, it usually performs much better if it receives a compact, explicit description of the API instead of the full source tree.

This tool solves that problem by converting a Java library into a machine-friendly JSON dump.

Typical use cases:

- generate Java code using your own internal library
- provide a library description to an LLM without exposing the entire source code
- build AI coding assistants specialized for a specific framework
- create a searchable structured API snapshot from Java sources and JavaDoc

## What the tool does

Given the root directory of a Maven project, `aidump`:

1. checks that `pom.xml` exists in the project root
2. scans Java sources, normally under `src/main/java`
3. parses public API declarations using JavaParser
4. extracts JavaDoc text and normalizes it
5. writes a JSON file into the current working directory

The output file name is the same as the input project folder name.

For example, if you run the tool on:

```text
D:\Projects\my-ui-library
````

the resulting file will be:

```text
my-ui-library.json
```

## Requirements

* Java 17 or newer
* Maven 3.8+ recommended

## Build

From the `aidump` project directory:

```bash
mvn clean package
```

If the project is configured with a shaded executable JAR, this will produce a runnable JAR in:

```text
target/aidump-1.0.0.jar
```

## Run

### Using the executable JAR

```bash
java -jar target/aidump-1.0.0.jar /path/to/target-maven-project
```

Windows example:

```powershell
java -jar .\target\aidump-1.0.0.jar D:\Projects\my-library
```

### Using Maven directly

```bash
mvn -q exec:java "-Dexec.args=/path/to/target-maven-project"
```

Windows example:

```powershell
mvn -q exec:java "-Dexec.args=D:\Projects\my-library"
```

## Input requirements

The input path must point to the root directory of a Maven project.

That directory must contain:

* `pom.xml`

Normally the Java sources are expected in:

* `src/main/java`

## Output

The tool generates one JSON file in the current working directory.

The JSON contains:

* project metadata from `pom.xml`
* source root information
* package list
* detailed type descriptions
* API index for quick lookup
* entry point candidates
* normalized JavaDoc content

## Example workflow

You have a Java UI framework with good JavaDoc and want an LLM to generate code using it.

1. Run `aidump` on the framework project
2. Get a single JSON file describing the framework API
3. Provide that JSON to the language model as reference context
4. Ask the model to generate Java code using only the documented API

This usually gives better results than dumping random source files into the prompt like a raccoon sorting cutlery.

## Notes

* only public API members are exported
* JavaDoc line breaks are normalized into plain spaces
* the tool is designed for libraries, frameworks, and reusable components
* it does not compile the target library, it only parses source files

## Limitations

Current version limitations include:

* only public API is exported
* external type resolution is limited
* Maven properties are not fully expanded from `pom.xml`
* the tool is focused on source parsing, not semantic compilation

## Project package

All source files of this tool are located under:

```text
com.kniazkov.aidump
```

## Purpose in one sentence

`aidump` turns a documented Java library into a single AI-friendly JSON API description.
