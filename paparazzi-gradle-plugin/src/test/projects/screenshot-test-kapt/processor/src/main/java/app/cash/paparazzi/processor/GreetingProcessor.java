package app.cash.paparazzi.processor;

import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("app.cash.paparazzi.processor.GenerateGreeting")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class GreetingProcessor extends AbstractProcessor {
  private boolean generated = false;

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (generated || roundEnv.getElementsAnnotatedWith(GenerateGreeting.class).isEmpty()) {
      return false;
    }
    generated = true;
    try {
      JavaFileObject file =
          processingEnv.getFiler().createSourceFile("app.cash.paparazzi.plugin.test.Greeting");
      try (Writer writer = file.openWriter()) {
        writer.write("package app.cash.paparazzi.plugin.test;\n");
        writer.write("public final class Greeting {\n");
        writer.write("  public static String text() { return \"generated-by-kapt\"; }\n");
        writer.write("}\n");
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return true;
  }
}
