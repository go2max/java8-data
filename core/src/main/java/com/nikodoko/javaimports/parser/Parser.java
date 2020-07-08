package com.nikodoko.javaimports.parser;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.nikodoko.javaimports.ImporterException;
import com.nikodoko.javaimports.parser.internal.UnresolvedIdentifierScanner;
import java.io.IOError;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.openjdk.javax.tools.Diagnostic;
import org.openjdk.javax.tools.DiagnosticCollector;
import org.openjdk.javax.tools.DiagnosticListener;
import org.openjdk.javax.tools.JavaFileObject;
import org.openjdk.javax.tools.SimpleJavaFileObject;
import org.openjdk.javax.tools.StandardLocation;
import org.openjdk.tools.javac.file.JavacFileManager;
import org.openjdk.tools.javac.parser.JavacParser;
import org.openjdk.tools.javac.parser.ParserFactory;
import org.openjdk.tools.javac.tree.JCTree.JCCompilationUnit;
import org.openjdk.tools.javac.util.Context;
import org.openjdk.tools.javac.util.Log;

/**
 * An "improved" Java parser, that parses the code and analyzes the resulting AST using an {@link
 * UnresolvedIdentifierScanner} to find all declared variables, all unresolved identifiers as well
 * as classes extending another class not declared in the same file.
 */
public class Parser {
  private ParserOptions options;
  private static Logger log = Logger.getLogger(Parser.class.getName());

  /**
   * A {@code Parser} constructor.
   *
   * @param options its options
   */
  public Parser(ParserOptions options) {
    this.options = options;
  }

  /**
   * Parse the given input (Java code) into a {@link ParsedFile}.
   *
   * @param javaCode the input code
   * @throws ImporterException if the input cannot be parsed
   */
  public ParsedFile parse(final Path filename, final String javaCode) throws ImporterException {
    // Parse the code into a compilation unit containing the AST
    JCCompilationUnit unit = getCompilationUnit(filename.toString(), javaCode);

    // Scan the AST
    UnresolvedIdentifierScanner scanner = new UnresolvedIdentifierScanner();
    scanner.scan(unit, null);

    // Wrap the results in a ParsedFile
    ParsedFile f = ParsedFile.fromCompilationUnit(unit);
    f.topScope(scanner.topScope());
    f.classHierarchy(scanner.topClass());
    if (options.debug()) {
      log.info("completed parsing: " + f.toString());
    }

    return f;
  }

  /** Return true if the diagnostic is an error diagnostic. */
  private static boolean isErrorDiagnostic(Diagnostic<?> d) {
    return d.getKind() == Diagnostic.Kind.ERROR;
  }

  // This should not be public, but is used in test
  public static JCCompilationUnit getCompilationUnit(final String filename, final String javaCode)
      throws ImporterException {
    Context ctx = new Context();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    ctx.put(DiagnosticListener.class, diagnostics);
    JCCompilationUnit unit;
    JavacFileManager fileManager = new JavacFileManager(ctx, true, UTF_8);

    try {
      fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, ImmutableList.of());
    } catch (IOException e) {
      // impossible
      throw new IOError(e);
    }

    // This is used by the parser to report syntax errors (the parser will refer to this file)
    SimpleJavaFileObject source =
        new SimpleJavaFileObject(URI.create("source"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return javaCode;
          }
        };
    Log.instance(ctx).useSource(source);

    ParserFactory parserFactory = ParserFactory.instance(ctx);
    // It is necessary to set keepEndPos to true in order to retrieve the end position of
    // expressions like the package clause, etc.
    JavacParser parser = parserFactory.newParser(javaCode, false, /*keepEndPos=*/ true, false);
    unit = parser.parseCompilationUnit();
    unit.sourcefile = source;

    List<Diagnostic<? extends JavaFileObject>> errorDiagnostics =
        diagnostics.getDiagnostics().stream()
            .filter(Parser::isErrorDiagnostic)
            .collect(Collectors.toList());

    if (!errorDiagnostics.isEmpty()) {
      throw ImporterException.fromDiagnostics(filename, errorDiagnostics);
    }

    return unit;
  }
}