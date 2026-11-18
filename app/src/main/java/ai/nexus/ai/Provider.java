package ai.nexus.ai;
import org.json.*;
import java.util.function.Consumer;
public interface Provider {
    JSONObject turn(JSONArray conversation,JSONArray declarations,String context,String language,Consumer<String> partial)throws Exception;
    void cancel();
}
