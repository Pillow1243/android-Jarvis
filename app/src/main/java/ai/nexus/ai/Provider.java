package ai.nexus.ai;

import java.util.function.Consumer;
import org.json.*;

public interface Provider {
  JSONObject turn(
    JSONArray conversation,
    JSONArray declarations,
    String context,
    String language,
    Consumer<String> partial
  ) throws Exception;
  void cancel();
}
