package local.jcc.probe;

import java.util.*;
import java.util.regex.*;

/** Share text is data, never an executable instruction. Keep its payload opaque. */
final class LineupCode {
    private static final Pattern CODE = Pattern.compile("#([^#\\r\\n]{1,80})#([A-Za-z0-9+/_=-]{8,512})");
    final String name, payload, text;
    private LineupCode(String name, String payload) { this.name = name; this.payload = payload; text = "【阵容码】#"+name+"#"+payload; }
    static List<LineupCode> parseAll(String input) {
        if (input == null || input.length() > 131072) throw new IllegalArgumentException("阵容码内容为空或过长");
        Matcher matcher = CODE.matcher(input); LinkedHashMap<String, LineupCode> codes = new LinkedHashMap<>();
        while (matcher.find()) {
            String name = matcher.group(1).trim(), payload = matcher.group(2);
            if (name.isEmpty()) throw new IllegalArgumentException("阵容名称不能为空");
            try { if (Base64.getDecoder().decode(payload.replace('-', '+').replace('_', '/')).length == 0) throw new IllegalArgumentException(); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("阵容码格式不正确："+name); }
            codes.put(payload, new LineupCode(name, payload));
        }
        if (codes.isEmpty()) throw new IllegalArgumentException("没有找到有效阵容码，请粘贴完整的【阵容码】#名称#代码");
        if (codes.size() > 200) throw new IllegalArgumentException("单次最多添加 200 套阵容");
        return new ArrayList<>(codes.values());
    }
    static boolean titleMatches(String expected,String observed) {
        String key=expected.replaceAll("[^\\p{IsHan}A-Za-z0-9]", "");
        String value=observed.replaceAll("[^\\p{IsHan}A-Za-z0-9]", "");
        if(key.length()>16){key=key.substring(0,16);if(value.length()>=16)value=value.substring(0,16);}
        if(key.equals(value))return !key.isEmpty();
        if(key.length()<6||key.length()!=value.length())return false;
        int differences=0;for(int i=0;i<key.length();i++)if(key.charAt(i)!=value.charAt(i))differences++;
        return differences==1;
    }
    static LineupCode parse(String input) {
        List<LineupCode> list = parseAll(input);
        if (list.size() != 1) throw new IllegalArgumentException("请选择一套阵容码");
        return list.get(0);
    }
}
