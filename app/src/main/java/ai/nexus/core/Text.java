package ai.nexus.core;

import java.text.Normalizer;
import java.util.Locale;

/** Comparison only; never normalize message bodies or user-visible contact names. */
public final class Text {
    private Text() {}
    public static String digits(String s) {
        StringBuilder b=new StringBuilder();
        for(char c:s.toCharArray()){int d=Character.digit(c,10); b.append(d>=0?(char)('0'+d):c);} return b.toString();
    }
    public static String norm(String s) {
        return digits(Normalizer.normalize(s==null?"":s,Normalizer.Form.NFKC)).toLowerCase(Locale.ROOT)
            .replace('ي','ی').replace('ى','ی').replace('ك','ک').replace('\u200c',' ').replace("ـ","")
            .replaceAll("\\p{M}+","").replaceAll("[\\s\\p{Z}]+"," ").trim();
    }
    public static boolean fa(String s){return s.matches("(?s).*[\\u0600-\\u06ff].*");}
    public static int distance(String a,String b){
        if(Math.abs(a.length()-b.length())>2 || a.length()>150 || b.length()>150)return 999;
        int[] dp=new int[b.length()+1]; for(int j=0;j<dp.length;j++)dp[j]=j;
        for(int i=1;i<=a.length();i++){int prev=dp[0];dp[0]=i;for(int j=1;j<=b.length();j++){int k=dp[j];dp[j]=Math.min(Math.min(dp[j]+1,dp[j-1]+1),prev+(a.charAt(i-1)==b.charAt(j-1)?0:1));prev=k;}}return dp[b.length()];
    }
    public static String clipped(String s,int n){return s.length()<=n?s:s.substring(0,n-1)+"…";}
    public static boolean secret(String s){return s.matches("(?is).*(AIza[0-9A-Za-z_-]{15,}|sk-[0-9A-Za-z_-]{12,}|gh[pousr]_[A-Za-z0-9]{12,}|bearer\\s+\\S{8,}|api[ _-]?key|password|passphrase|رمز عبور|گذرواژه|کلید api).*");}
    public static boolean phone(String s){return digits(s).matches("\\+?[0-9][0-9 ()-]{1,22}[0-9]");}
    public static boolean contactMatches(String name,String query){String n=norm(name), q=norm(query);return !q.isEmpty() && (n.equals(q)||n.contains(q));}
}
