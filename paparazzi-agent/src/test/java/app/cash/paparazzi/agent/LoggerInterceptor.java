package app.cash.paparazzi.agent;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.DefaultCall;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.Super;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;

class LoggerInterceptor {
  public static List<String> log(@SuperCall Callable<List<String>> zuper)
      throws Exception {
    System.out.println("Calling database");
    try {
      return zuper.call();
    } finally {
      System.out.println("Returned from database");
    }
  }
}
