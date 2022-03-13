package app.cash.paparazzi.agent;

import java.util.Arrays;
import java.util.List;

public class MemoryDatabase {
  public static List<String> load(String info) {
    return Arrays.asList(info + ": foo", info + ": bar");
  }
}
