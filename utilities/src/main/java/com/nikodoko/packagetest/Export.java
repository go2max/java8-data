package com.nikodoko.packagetest;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.Files;
import com.nikodoko.packagetest.exporters.Exporter;
import com.nikodoko.packagetest.exporters.ExporterFactory;
import com.nikodoko.packagetest.exporters.Kind;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class Export {
  private Export() {}

  /**
   * Writes a test directory given a kind of exporter and system agnostic module descriptions.
   *
   * <p>Returns an {@link Exported} object containing the results of the export.
   *
   * @param exporterKind a type of exporter
   * @param modules a list of modules to export
   * @return informations about the successful export
   * @throws IOException if an I/O error occurs
   */
  public static Exported of(Kind exporterKind, List<Module> modules) throws IOException {
    File temp = Files.createTempDir();
    Exported exported = new Exported(temp.toPath());
    Exporter exporter = ExporterFactory.create(exporterKind);
    for (Module m : modules) {
      exportModule(exported, exporter, m);
    }

    return exported;
  }

  private static void exportModule(Exported exported, Exporter exporter, Module module)
      throws IOException {
    for (Module.File f : module.files()) {
      Path fullpath = exporter.filename(exported.root(), module.name(), f.fragment());
      Files.createParentDirs(fullpath.toFile());
      Files.write(f.content(), fullpath.toFile(), UTF_8);
      exported.markAsWritten(module.name(), f.fragment(), fullpath);
    }
  }
}
