package app.cash.paparazzi.agent;

public class SomeDelegate {
  public SomeDelegate() {
  }

  public static boolean log(String s) {
    InterceptorRegistrar2Test.Companion.getLogs().add(s);
    return true;
  }
}



