package com.snowglobe.overlay;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SceneSnapshotTest {
    @Test public void roundTripPreservesEveryVisualField(){Map<String,Object> settings=new HashMap<>();settings.put("speed",73);settings.put("snow",false);settings.put("particleDirection",7);SceneSnapshot s=SceneSnapshot.capture(settings);assertEquals(s.values(),SceneSnapshot.decode(s.encode()).values());}
    @Test public void neverIncludesRuntimeSafetyOrPurchaseState(){Map<String,Object> s=new HashMap<>();s.put("effectsEnabled",true);s.put("qualityMode",2);s.put("reduceMotion",false);s.put("_serviceState","visible");s.put("premium",true);Map<String,Object> result=SceneSnapshot.capture(s).values();for(String key:s.keySet())assertFalse(result.containsKey(key));}
    @Test public void captureBoundsInvalidValues(){Map<String,Object>s=new HashMap<>();s.put("speed",200);s.put("particleDirection",999);assertEquals(100,SceneSnapshot.capture(s).values().get("speed"));assertEquals(7,SceneSnapshot.capture(s).values().get("particleDirection"));}
    @Test(expected=IllegalArgumentException.class) public void corruptedSnapshotRejected(){SceneSnapshot.decode("SNOWGLOBE_SCENE_1\nsnow=true\nlights=true\neffectsEnabled=true");}
    @Test(expected=IllegalArgumentException.class) public void truncatedSnapshotRejected(){SceneSnapshot.decode("SNOWGLOBE_SCENE_1\nsnow=true\nlights=true");}
    @Test(expected=IllegalArgumentException.class) public void unknownSchemaRejected(){SceneSnapshot.decode("SNOWGLOBE_SCENE_99\nsnow=true\nlights=true");}
    @Test public void newSnapshotsAvoidTerminalNewlineXmlIndentation(){
        String encoded=SceneSnapshot.capture(Collections.emptyMap()).encode();
        assertFalse(encoded.endsWith("\n"));
        assertEquals(SceneSnapshot.capture(Collections.emptyMap()).values(),SceneSnapshot.decode(encoded).values());
    }
    @Test public void previouslyPersistedTrailingBlankLinesRemainReadable(){
        SceneSnapshot scene=SceneSnapshot.capture(Collections.singletonMap("density",73));
        assertEquals(scene.values(),SceneSnapshot.decode(scene.encode()+"\n    \n\t\r\n").values());
    }
    @Test(expected=IllegalArgumentException.class) public void trailingWhitespaceCannotHideAnUnknownField(){
        SceneSnapshot.decode(SceneSnapshot.capture(Collections.emptyMap()).encode()+"\neffectsEnabled=true\n    ");
    }
    @Test(expected=IllegalArgumentException.class) public void interiorBlankLinesRemainInvalid(){
        SceneSnapshot.decode(SceneSnapshot.capture(Collections.emptyMap()).encode().replace("snow=true\n","snow=true\n    \n"));
    }

}
