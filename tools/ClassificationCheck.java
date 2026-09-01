import com.yagay.intentcleaner.domain.IntentClassification;
import com.yagay.intentcleaner.domain.ManagerIdentity;
import java.util.Objects;

public final class ClassificationCheck {
    public static void main(String[] args) {
        String view = "android.intent.action.VIEW";
        String[][] cases = {
            {view,"content","application/pdf","OPEN"},
            {view,"content","*/*","OPEN"},
            {view,"content",null,null},
            {view,"content","vnd.android.cursor.item/contact",null},
            {view,"file",null,"OPEN"},
            {view,"https",null,"BROWSER"},
            {view,"http","text/html","BROWSER"},
            {view,"https","application/xhtml+xml","BROWSER"},
            {view,"https","application/pdf","OPEN"},
            {view,"https","video/mp4","OPEN"},
            {view,"https","image/*","OPEN"},
            {view,"HTTPS","Application/PDF; charset=utf-8","OPEN"},
            {view,"https","*/*","BROWSER"},
            {view,"upi",null,null}, {view,"msauth",null,null},
            {view,"geo",null,null}, {view,"tel",null,null},
            {view,"custom","application/pdf",null},
            {view,null,"text/plain","OPEN"}, {view,null,null,null},
            {"android.intent.action.SEND",null,"text/plain","SHARE"},
            {"android.intent.action.SEND_MULTIPLE",null,"image/jpeg","SHARE_MULTIPLE"},
            {"android.intent.action.PROCESS_TEXT",null,"text/plain","PROCESS_TEXT"},
            {"android.intent.action.EDIT","file","text/plain",null},
            {null,null,null,null}
        };
        for (String[] c : cases) {
            String actual = IntentClassification.classify(c[0], c[1], c[2]);
            if (!Objects.equals(c[3], actual)) throw new AssertionError(java.util.Arrays.toString(c) + " got " + actual);
        }
        int[][] identityCases = {{10715,10715,1},{1010715,10715,1},{10716,10715,0},
            {10715,-1,0},{-1,-1,0},{1000,1000,0},{10715,110715,0}};
        for (int[] c : identityCases) {
            if (ManagerIdentity.matches(c[0],c[1]) != (c[2] == 1)) throw new AssertionError("identity");
        }
        System.out.println("PASS: " + cases.length + " classification + " + identityCases.length + " manager identity cases");
    }
}
