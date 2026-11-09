package local.jcc.probe;

import android.content.Context;
import android.graphics.*;
import com.google.android.gms.tasks.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
import java.io.*;

final class LineupVision implements AutoCloseable {
    private final TextRecognizer ocr=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    private int[] menuAnchor;
    private final List<PortraitMatcher.Reference> portraits=new ArrayList<>();
    static String normalize(String s) { return s.replaceAll("[^\\p{IsHan}A-Za-z0-9]", ""); }
    static final class Entry { String text;Rect box; Entry(String t,Rect b){text=normalize(t);box=new Rect(b);} }
    static final class Screen {
        final List<Entry> entries=new ArrayList<>();
        boolean layoutValid=true,shopOpen,board,menu,replacement,targetMatches,targetApplied,previewBlocked;
        int favoriteRows,targetRow=2;
        Rect targetTitle,previewBounds;
        Point formationIcon,closeButton,importButton,replaceButton,applyButton,goldButton;
        PortraitMatcher.Row heroes;Bitmap preview;int[] rowSignature;
        Rect find(String word,int left,int top,int right,int bottom) {
            String key=normalize(word); Rect area=new Rect(left,top,right,bottom);Rect best=null;
            for(Entry e:entries)if(e.text.contains(key)&&area.contains(e.box.centerX(),e.box.centerY())&&
                (best==null || e.box.width()*e.box.height()<best.width()*best.height()))best=e.box;
            return best;
        }
        boolean has(String word){return find(word,0,0,2670,1200)!=null;}
    }
    LineupVision(Context context) {
        try(InputStream in=context.getAssets().open("templates/lineup-button.bin")) {
            byte[] bytes=in.readAllBytes();menuAnchor=new int[bytes.length];for(int i=0;i<bytes.length;i++)menuAnchor[i]=bytes[i]&255;
        }catch(IOException e){throw new IllegalStateException("缺少阵容入口模板",e);}
        try(InputStream in=context.getAssets().open("champions_s18.json")) {
            JSONArray rows=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("champions");
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);String id=row.getString("id");
                try(InputStream icon=context.getAssets().open("portraits/"+id+".png")) {
                    Bitmap b=BitmapFactory.decodeStream(icon);if(b==null)continue;
                    int[] pixels=new int[b.getWidth()*b.getHeight()];b.getPixels(pixels,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());
                    portraits.add(new PortraitMatcher.Reference(id,row.getString("name"),PortraitMatcher.descriptor(pixels,b.getWidth(),0,0,b.getWidth(),b.getHeight())));b.recycle();
                }
            }
        }catch(IOException|JSONException e){throw new IllegalStateException("阵容头像目录读取失败",e);}
    }
    Screen read(Bitmap image,String expected,boolean wantPortraits) { return read(image,expected,wantPortraits,false); }
    Screen read(Bitmap image,String expected,boolean wantPortraits,boolean forceThird) {
        Screen out=new Screen();Bitmap copy=image.copy(Bitmap.Config.ARGB_8888,false);Task<Text> task=null;
        try {
            task=ocr.process(InputImage.fromBitmap(copy,0)); Text text=Tasks.await(task,3,TimeUnit.SECONDS);
            for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()) {
                if(line.getBoundingBox()!=null)out.entries.add(new Entry(line.getText(),line.getBoundingBox()));
                for(Text.Element element:line.getElements())if(element.getBoundingBox()!=null)out.entries.add(new Entry(element.getText(),element.getBoundingBox()));
                StringBuilder joined=new StringBuilder();List<Text.Element> elements=line.getElements();
                for(Text.Element e:elements)joined.append(normalize(e.getText()));
                for(String word:new String[]{"导入代码","已达到收藏上限","请选择已收藏的阵容进行替换","购买经验","请选择一个强化符文"}) {
                    int start=joined.indexOf(word);if(start<0)continue;int pos=0;Rect bound=null;
                    for(Text.Element e:elements){int end=pos+normalize(e.getText()).length();if(end>start&&pos<start+word.length()&&e.getBoundingBox()!=null){if(bound==null)bound=new Rect(e.getBoundingBox());else bound.union(e.getBoundingBox());}pos=end;}
                    if(bound!=null)out.entries.add(new Entry(word,bound));
                }
            }
        }catch(Exception e){throw new IllegalStateException("阵容界面识别暂未成功",e);}
        finally { if(task==null||task.isComplete())copy.recycle();else task.addOnCompleteListener(Runnable::run,t->copy.recycle()); }
        Rect header=out.find("阵容",400,75,850,230), prompt=out.find("提示",400,75,1700,230);
        Rect importBox=out.find("导入代码",1200,75,1750,240);
        if(importBox==null)importBox=out.find("代码",1200,75,1750,240);
        if(importBox==null)importBox=out.find("导入",1200,75,1750,240);
        if(importBox==null)importBox=out.find("导人",1200,75,1750,240);
        out.importButton=center(importBox);
        out.replacement=out.has("已达到收藏上限")||out.has("请选择已收藏");
        out.menu=header!=null&&out.importButton!=null&&!out.replacement;
        Rect closeHeader=out.replacement?prompt:header;
        if(closeHeader!=null)out.closeButton=new Point(2215+closeHeader.left-525,157);
        out.replaceButton=center(out.find("替换",1900,820,2310,1140));
        int[][] titleBands={{210,325},{510,640},{810,940}};
        int[][] buttonBands={{220,500},{510,810},{820,1140}};
        Rect[] titles=new Rect[3],buttons=new Rect[3];boolean[] applied=new boolean[3];
        for(int row=0;row<3;row++) {
            for(Entry entry:out.entries) if(entry.box.centerX()>=480&&entry.box.centerX()<1950&&entry.box.centerY()>=titleBands[row][0]&&entry.box.centerY()<titleBands[row][1]&&LineupCode.titleMatches(expected,entry.text))titles[row]=entry.box;
            buttons[row]=out.find("应用",1900,buttonBands[row][0],2310,buttonBands[row][1]);
            Rect cancel=out.find("取消",1900,buttonBands[row][0],2310,buttonBands[row][1]);
            applied[row]=cancel!=null;if(buttons[row]==null)buttons[row]=cancel;
            if(buttons[row]!=null)out.favoriteRows++;
        }
        // A full collection always targets slot 3, even if another row has the same name.
        if(!forceThird&&out.favoriteRows<3 && titles[2]==null)for(int row=0;row<3;row++)if(titles[row]!=null){out.targetRow=row;break;}
        out.targetTitle=titles[out.targetRow];out.targetMatches=out.targetTitle!=null;out.targetApplied=applied[out.targetRow];
        if(!out.targetApplied)out.applyButton=center(buttons[out.targetRow]);
        int[] screenPixels=new int[image.getWidth()*image.getHeight()];image.getPixels(screenPixels,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());
        out.formationIcon=findIcon(screenPixels,image.getWidth());
        if(out.menu)out.rowSignature=PortraitMatcher.descriptor(screenPixels,image.getWidth(),535,335+out.targetRow*304,1350,90);
        out.board=(out.has("购买经验")||out.formationIcon!=null)&&!out.has("请选择一个强化符文")&&!out.has("对局结束")&&!out.has("选择强化符文")&&!out.has("再来一局")&&!out.has("返回大厅")&&!out.has("退出对局");
        if(out.menu&&out.targetMatches&&wantPortraits) {
            int[] pixels=new int[image.getWidth()*image.getHeight()];image.getPixels(pixels,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());
            out.heroes=PortraitMatcher.readRow(pixels,image.getWidth(),image.getHeight(),portraits,out.targetRow);
            int top=out.targetTitle.top-8;
            int bottom=out.heroes.count>0?out.heroes.y+152:out.targetTitle.bottom+210;
            int left=out.heroes.count>0?Math.min(out.targetTitle.left,out.heroes.x)-8:out.targetTitle.left-8;
            Point targetButton=center(buttons[out.targetRow]);
            int buttonLeft=targetButton==null?2035:targetButton.x-100;
            int right=out.heroes.count>0?out.heroes.x+(out.heroes.count-1)*154+108+10:buttonLeft-2;
            right=Math.min(right,buttonLeft-2);bottom=Math.min(bottom,1110);
            out.previewBounds=new Rect(Math.max(0,left),Math.max(0,top),right,bottom);
            for(String message:new String[]{"收藏成功","导入成功","阵容收藏成功"}) {
                Rect toast=out.find(message,0,0,image.getWidth(),image.getHeight());
                if(toast!=null&&Rect.intersects(toast,out.previewBounds))out.previewBlocked=true;
            }
            out.preview=Bitmap.createBitmap(image,out.previewBounds.left,out.previewBounds.top,out.previewBounds.width(),out.previewBounds.height());
        }
        return out;
    }
    private Point findIcon(int[] pixels,int width) {
        double best=Double.MAX_VALUE;Point result=null;
        for(int y=25;y<=53;y+=2)for(int x=2200;x<=2340;x+=2) {
            int[] candidate=PortraitMatcher.descriptor(pixels,width,x,y,64,64);double sum=0;
            for(int i=0;i<candidate.length;i++){double d=candidate[i]-menuAnchor[i];sum+=d*d;}
            double score=Math.sqrt(sum/candidate.length);if(score<best){best=score;result=new Point(x+32,y+32);}
        }
        return best<=55?result:null;
    }
    private static Point center(Rect rect) { return rect==null?null:new Point(rect.centerX(),rect.centerY()); }
    @Override public void close(){ocr.close();}
}
