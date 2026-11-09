package local.jcc.probe;

import java.util.*;

/** RGB descriptors of inner portrait areas, excluding stars, borders and equipment. */
final class PortraitMatcher {
    private static final int GRID=12;
    static final class Reference {
        final String id, name; final int[] values;
        Reference(String id,String name,int[] values) { this.id=id;this.name=name;this.values=values; }
    }
    static final class Match {
        String name=""; double distance=Double.MAX_VALUE, margin, variance; int x,y;
        boolean accepted() { return distance <= 58 && margin >= 12; }
    }
    static final class Row {
        final List<String> names=new ArrayList<>();
        int unknown, count, x,y;
        boolean complete() { return count>0 && unknown==0; }
    }
    static int[] descriptor(int[] pixels,int imageWidth,int x,int y,int width,int height) {
        int[] out=new int[GRID*GRID*3]; int k=0;
        for(int j=0;j<GRID;j++) for(int i=0;i<GRID;i++) {
            int px=x+(int)((.08+.84*(i+.5)/GRID)*width), py=y+(int)((.15+.70*(j+.5)/GRID)*height);
            int c=pixels[py*imageWidth+px]; out[k++]=(c>>16)&255;out[k++]=(c>>8)&255;out[k++]=c&255;
        }
        return out;
    }
    private static Match match(int[] value,List<Reference> references,int x,int y) {
        Match result=new Match();result.x=x;result.y=y;double second=Double.MAX_VALUE;
        double[] mean=new double[3];for(int i=0;i<value.length;i++)mean[i%3]+=value[i]/(double)(GRID*GRID);
        double variance=0;for(int i=0;i<value.length;i++){double d=value[i]-mean[i%3];variance+=d*d;}
        result.variance=Math.sqrt(variance/value.length);
        for(Reference r:references) {
            double sum=0;for(int i=0;i<value.length;i++){double d=value[i]-r.values[i];sum+=d*d;}
            double distance=Math.sqrt(sum/value.length);
            if(distance<result.distance){second=result.distance;result.distance=distance;result.name=r.name;}
            else second=Math.min(second,distance);
        }
        result.margin=second-result.distance;return result;
    }
    static Row readRow(int[] pixels,int width,int height,List<Reference> refs) { return readRow(pixels,width,height,refs,0); }
    static Row readRow(int[] pixels,int width,int height,List<Reference> refs,int rowIndex) {
        int rowOffset=rowIndex*304;
        Row row=new Row(); Match first=null;
        for(int y=290+rowOffset;y<=390+rowOffset;y+=6)for(int x=480;x<=620;x+=6) {
            Match m=match(descriptor(pixels,width,x,y,108,108),refs,x,y);
            if(first==null || m.distance<first.distance)first=m;
        }
        if(first==null)return row;
        int baseX=first.x,baseY=first.y;
        for(int y=baseY-5;y<=baseY+5;y+=2)for(int x=baseX-5;x<=baseX+5;x+=2) {
            Match m=match(descriptor(pixels,width,x,y,108,108),refs,x,y);
            if(m.distance<first.distance)first=m;
        }
        if(!first.accepted())return row;
        row.x=first.x;row.y=first.y;
        for(int slot=0;slot<10;slot++) {
            int x=row.x+slot*154;
            if(x+108>2035)break;
            Match best=null;
            for(int dy=-4;dy<=4;dy+=2)for(int dx=-4;dx<=4;dx+=2) {
                Match m=match(descriptor(pixels,width,x+dx,row.y+dy,108,108),refs,x+dx,row.y+dy);
                if(best==null || m.distance<best.distance)best=m;
            }
            if(best.variance<18)break;
            row.count++;
            if(best.accepted()) { if(!row.names.contains(best.name))row.names.add(best.name); }
            else row.unknown++;
        }
        return row;
    }
}
