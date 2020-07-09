package com.nikodoko.javaimports.resolver;

import com.google.common.io.Files;
import com.nikodoko.javaimports.parser.Import;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

class MavenDependencyResolver {
  private static final String SUBCLASS_SEPARATOR = "$";
  final Path fileBeingResolved;
  final Path repository;

  MavenDependencyResolver(Path fileBeingResolved, Path repository) {
    this.fileBeingResolved = fileBeingResolved;
    this.repository = repository;
  }

  List<ImportWithDistance> resolve(List<MavenDependency> dependencies) throws IOException {
    List<ImportWithDistance> imports = new ArrayList<>();
    for (MavenDependency dependency : dependencies) {
      imports.addAll(resolveDependency(dependency));
    }

    return imports;
  }

  private List<ImportWithDistance> resolveDependency(MavenDependency dependency)
      throws IOException {
    Path relative = relativePath(dependency);
    Path dependencyJar = Paths.get(repository.toString(), relative.toString());
    return scanJar(dependencyJar);
  }

  private List<ImportWithDistance> scanJar(Path jar) throws IOException {
    List<ImportWithDistance> imports = new ArrayList<>();
    try (JarInputStream in = new JarInputStream(new FileInputStream(jar.toString()))) {
      JarEntry entry;
      while ((entry = in.getNextJarEntry()) != null) {
        // XXX: this will get all classes, including private and protected ones
        if (entry.getName().endsWith(".class")) {
          Import i = parseImport(Paths.get(entry.getName()));
          imports.add(ImportWithDistance.of(i, jar, fileBeingResolved));
        }
      }
    }

    return imports;
  }

  private Import parseImport(Path jarEntry) {
    String pkg = jarEntry.getParent().toString().replace("/", ".");
    String name = Files.getNameWithoutExtension(jarEntry.getFileName().toString());
    if (!name.contains(SUBCLASS_SEPARATOR)) {
      return new Import(pkg, name, false);
    }

    // Make the subclass addressable by its name
    String extraPkg =
        name.substring(0, name.lastIndexOf(SUBCLASS_SEPARATOR)).replace(SUBCLASS_SEPARATOR, ".");
    String subclassName = name.substring(name.lastIndexOf(SUBCLASS_SEPARATOR) + 1, name.length());
    return new Import(String.join(".", pkg, extraPkg), subclassName, false);
  }

  private Path relativePath(MavenDependency dependency) {
    return Paths.get(
        dependency.groupId.replace(".", "/"),
        dependency.artifactId,
        dependency.version,
        jarName(dependency.artifactId, dependency.version));
  }

  private String jarName(String artifactId, String version) {
    return String.format("%s-%s.jar", artifactId, version);
  }
}
