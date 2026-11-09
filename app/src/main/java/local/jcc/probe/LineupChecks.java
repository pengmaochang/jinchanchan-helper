package local.jcc.probe;

import android.app.Instrumentation;
import android.graphics.*;
import android.os.Bundle;
import java.io.*;
import java.util.*;

/** Reads fixture screens and isolated preferences only; never invokes game gestures. */
public class LineupChecks extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private Bitmap fixture(String name){return BitmapFactory.decodeFile(new File(getTargetContext().getFilesDir(),"lineup-fixture-"+name+".jpg").getAbsolutePath());}
    private static void clear(LineupVision.Screen screen){if(screen.preview!=null)screen.preview.recycle();}
    private void checkPreview(LineupVision.Screen screen) {
        check(screen.preview!=null&&screen.previewBounds!=null,"Preview crop");
        check(screen.targetRow==2&&screen.previewBounds.top>800,"Preview must be the third row");
        check(screen.previewBounds.right<2035&&screen.preview.getWidth()<1500,"Exclude application button and trailing empty area");
    }
    @Override public void onStart(){Bundle result=new Bundle();
        String[] third={"崔丝塔娜","赫卡里姆","艾希","茂凯","纳尔","莉莉娅","拉莫斯","霞","洛"};
        String[] karma={"卡尔玛","乐芙兰","瑟庄妮","凯尔","芸阿娜","约里克","蕾欧娜","阿兹尔"};
        try(LineupVision vision=new LineupVision(getTargetContext())) {
            for(String name:new String[]{"list","replace","applied","board"}) {
                Bitmap image=fixture(name);check(image!=null,"Missing fixture "+name);
                try {
                    LineupVision.Screen screen=vision.read(image,"仙灵崔丝塔娜",true);
                    result.putString(name,"menu="+screen.menu+" replacement="+screen.replacement+" targetRow="+screen.targetRow+" title="+screen.targetMatches+
                        " applied="+screen.targetApplied+" board="+screen.board+" replace="+screen.replaceButton+" apply="+screen.applyButton+
                        (screen.heroes==null?"":" heroes="+screen.heroes.names+" unknown="+screen.heroes.unknown+" crop="+screen.previewBounds));
                    if(name.equals("list")||name.equals("applied")) {
                        check(screen.menu&&screen.targetMatches&&screen.importButton!=null&&screen.closeButton!=null,"Formation list fields");
                        check(screen.heroes!=null&&screen.heroes.complete()&&screen.heroes.names.equals(Arrays.asList(third)),"Third-row portraits");
                        check(!screen.targetApplied&&screen.applyButton!=null&&screen.applyButton.y>900,"First-row Cancel must not be mistaken for the third row");
                        checkPreview(screen);
                        if(name.equals("list"))try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"lineup-preview-check.png"))){screen.preview.compress(Bitmap.CompressFormat.PNG,100,out);}
                        check(!screen.previewBlocked,"Clean third-row preview must not be blocked");
                    }
                    if(name.equals("replace"))check(screen.replacement&&screen.replaceButton!=null&&screen.replaceButton.y>900&&screen.closeButton!=null,"Third-row replacement button");
                    if(name.equals("board"))check(screen.board&&screen.formationIcon!=null&&!screen.menu,"Board entry icon");
                    clear(screen);
                    if(name.equals("applied")) {
                        Bitmap changed=image.copy(Bitmap.Config.ARGB_8888,true);Bitmap cancel=Bitmap.createBitmap(image,2030,345,210,95);
                        try {
                            new Canvas(changed).drawBitmap(cancel,2030,953,null);
                            LineupVision.Screen applied=vision.read(changed,"仙灵崔丝塔娜",true);
                            check(applied.targetMatches&&applied.targetApplied&&applied.applyButton==null,"Recognize third-row application confirmation");
                            LineupVision.Screen original=vision.read(image,"仙灵崔丝塔娜",false);
                            check(LineupImporter.sameRow(original.rowSignature,applied.rowSignature),"Unchanged portrait row survives applied state");
                            check(!LineupImporter.sameRow(original.rowSignature,new int[432]),"Different portrait row cannot confirm application");clear(original);
                            checkPreview(applied);clear(applied);
                        }finally{cancel.recycle();changed.recycle();}
                    }
                    if(name.equals("list")) {
                        Bitmap changed=image.copy(Bitmap.Config.ARGB_8888,true);Bitmap first=Bitmap.createBitmap(image,470,220,1775,275);
                        try {
                            new Canvas(changed).drawBitmap(first,470,828,null);
                            LineupVision.Screen imported=vision.read(changed,"林小北-日蚀卡尔玛",true);
                            check(imported.targetRow==2&&imported.targetMatches&&imported.heroes.complete()&&imported.heroes.names.equals(Arrays.asList(karma)),"Prefer imported third row over a duplicate name in the first row");
                            checkPreview(imported);clear(imported);
                            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.rgb(20,25,35));new Canvas(changed).drawRect(1090,960,1500,1040,paint);
                            paint.setColor(Color.WHITE);paint.setTextSize(54);new Canvas(changed).drawText("收藏成功",1110,1020,paint);
                            LineupVision.Screen blocked=vision.read(changed,"林小北-日蚀卡尔玛",true);
                            check(blocked.previewBlocked,"Wait if a success toast overlaps the preview");clear(blocked);
                        }finally{first.recycle();changed.recycle();}
                    }
                }finally{image.recycle();}
            }
            android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("lineup_checks_only",0);prefs.edit().clear().putString("targets","可酷伯").commit();
            try {
                DeckStore store=new DeckStore(prefs);String old=store.current().id;
                String code="【阵容码】#林小北-日蚀卡尔玛#MjE5OTY5MDM5MDAxMzM4NDQxNzkyMDMyNDk3MDQ3";
                DeckStore.Deck added=store.addCodes(code+"\n"+code).get(0);
                check(store.all().size()==2&&added.isCode()&&!added.verified&&added.targets.isEmpty(),"Unparsed initial selection remains identifiable");
                check(added.name.equals("林小北-日蚀卡尔玛"),"Extract lineup display name");
                added=store.save(added.id,added.name,"卡尔玛");check(!added.verified,"Implicit settings save cannot confirm incomplete heroes");
                added=store.saveDerived(added.id,String.join("\n",karma),"lineups/example-row3.png",true,"");
                DeckStore.Deck persisted=new DeckStore(prefs).current();check(persisted.verified&&persisted.targets.equals(String.join("\n",karma))&&persisted.preview.endsWith("-row3.png"),"Persist heroes and compact preview");
                store.select(old);check(store.current().targets.equals("可酷伯"),"Preserve legacy profiles");
            }finally{prefs.edit().clear().commit();}
            result.putString("result","PASS");finish(-1,result);
        }catch(Throwable e){result.putString("error",e.toString());finish(0,result);}
    }
}
