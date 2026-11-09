package local.jcc.probe;

import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** One user-selected import. Every gesture requires a newly recognized game screen. */
final class LineupImporter {
    private final CaptureService service;
    private final DeckStore.Deck deck;
    private final LineupCode code;
    private final LineupFlow flow=new LineupFlow();
    private LineupVision vision;
    private volatile boolean active=true;
    private boolean inFlight,finishing;
    private volatile boolean requireThirdRow;
    private final boolean needsPreview;
    private long phaseAt=SystemClock.elapsedRealtime();
    private int failures;
    private Bitmap preview;
    private int[] expectedRowSignature;
    private List<String> heroes=new ArrayList<>();
    private boolean heroesComplete;
    private final Runnable tick=this::tick;
    LineupImporter(CaptureService service,DeckStore.Deck deck) {this.service=service;this.deck=deck;this.code=LineupCode.parse(deck.code);needsPreview=true;}
    boolean active(){return active;}
    void begin(){copyCode();service.importStatus("阵容码已复制："+deck.name+" · 收起面板并切回游戏后导入");schedule(350);}
    private void copyCode(){android.content.ClipboardManager manager=(android.content.ClipboardManager)service.getSystemService(Context.CLIPBOARD_SERVICE);manager.setPrimaryClip(ClipData.newPlainText(deck.name,code.text));}
    private void schedule(long ms){if(active)service.scheduleLineup(tick,ms);}
    private void tick(){
        if(!active||inFlight||finishing)return;
        if(!service.importAllowed()) {phaseAt=SystemClock.elapsedRealtime();service.importStatus("阵容导入待命：请收起面板并保持游戏前台");schedule(400);return;}
        if(SystemClock.elapsedRealtime()-phaseAt>25000){fail("阵容导入等待超时，未继续点击；请检查游戏界面后重试");return;}
        inFlight=true;
        boolean started=service.captureForLineup(image->{
            if(!PhoneLayout.accepts(image.getWidth(),image.getHeight())){LineupVision.Screen s=new LineupVision.Screen();s.layoutValid=false;return s;}
            if(vision==null)vision=new LineupVision(service);
            boolean want=preview==null&&needsPreview&&flow.stage()!=LineupFlow.Stage.PREPARE&&flow.stage()!=LineupFlow.Stage.WAIT_MENU;
            LineupVision.Screen screen=vision.read(image,code.name,want,requireThirdRow);
            if(!screen.menu&&!screen.replacement) {
                BuyPolicy.Frame shop=service.readShopForLineup(image);screen.shopOpen=shop.shopOpen;
                screen.goldButton=new Point(PhoneLayout.GOLD[0]+56+shop.offsetX,1067);
            }
            return screen;
        },(screen,error)->{
            inFlight=false;
            if(!active){recycle(screen);return;}
            if(error!=null){if(++failures>=4){fail("阵容画面暂未读清："+error.getMessage());return;}schedule(500);return;}
            failures=0;
            if(!screen.layoutValid){phaseAt=SystemClock.elapsedRealtime();service.importStatus("阵容导入：等待 2670×1200 横屏");schedule(500);return;}
            if(!service.importAllowed()){recycle(screen);phaseAt=SystemClock.elapsedRealtime();schedule(400);return;}
            if(needsPreview&&screen.previewBlocked){recycle(screen);service.importStatus("阵容导入：等待收藏成功提示消失后截图");schedule(400);return;}
            if(screen.favoriteRows>=3||screen.replacement)requireThirdRow=true;
            LineupFlow.View state=new LineupFlow.View();state.board=screen.board;state.shop=screen.shopOpen;state.menu=screen.menu;
            state.full=requireThirdRow||screen.favoriteRows>=3;state.replacement=screen.replacement;state.targetMatches=screen.targetMatches;state.applied=screen.targetApplied;
            // Only after this exact row was verified and Apply was dispatched may a
            // transient title OCR failure be confirmed by the unchanged portrait.
            if(flow.stage()==LineupFlow.Stage.WAIT_CLOSED_FINAL && screen.menu && screen.targetApplied && sameRow(expectedRowSignature,screen.rowSignature))state.targetMatches=true;
            LineupFlow.Stage before=flow.stage();LineupFlow.Action action=flow.next(state);
            if(action==LineupFlow.Action.APPLY_TARGET)expectedRowSignature=screen.rowSignature;
            if(flow.stage()!=before||flow.stage()==LineupFlow.Stage.PREPARE&&action==LineupFlow.Action.WAIT)phaseAt=SystemClock.elapsedRealtime();
            if(screen.preview!=null && (action==LineupFlow.Action.APPLY_TARGET || action==LineupFlow.Action.CLOSE_EXISTING&&screen.menu&&screen.targetMatches)) {
                if(preview==null){preview=screen.preview;screen.preview=null;heroes=new ArrayList<>(screen.heroes.names);heroesComplete=screen.heroes.complete();}
            }
            recycle(screen);
            switch(action){
                case FINISH:finish();return;
                case WAIT:service.importStatus("阵容导入："+phaseLabel());schedule(400);return;
                default:break;
            }
            Point target=null;String label="";
            switch(action){
                case CLOSE_EXISTING:target=screen.closeButton;label="关闭阵容面板";break;
                case CLOSE_SHOP:target=screen.goldButton;label="点击总金币收起商店";break;
                case OPEN_MENU:target=screen.formationIcon;label="打开游戏阵容";break;
                case IMPORT:copyCode();target=screen.importButton;label="导入剪贴板阵容码";break;
                case REPLACE_THIRD:target=screen.replaceButton;label="替换第三套收藏阵容";break;
                case APPLY_TARGET:target=screen.applyButton;label="应用第三套阵容";break;
                default:break;
            }
            if(target==null){fail("没有明确识别到“"+label+"”按钮，已停止导入");return;}
            service.importStatus("阵容导入："+label);inFlight=true;
            if(!service.tapForLineup(target,this,()->{inFlight=false;schedule(450);})) {inFlight=false;fail("当前界面或动作状态已变化，请重试导入");}
        });
        if(!started){inFlight=false;schedule(150);}
    }
    private String phaseLabel(){switch(flow.stage()){
        case WAIT_CLOSED:return "等待商店收起";case WAIT_MENU:return "等待阵容列表";case WAIT_IMPORTED:return "等待导入或替换提示";
        case WAIT_APPLY:return "核对第三套阵容";case WAIT_CLOSED_FINAL:return "确认应用并关闭面板";default:return "等待游戏棋盘";}}
    private void finish(){
        finishing=true;service.importStatus("阵容已应用，正在保存本地预览");
        service.runLineupWork(()->{
            String error=null;boolean verified=deck.verified;String note="";
            try {
                DeckStore store=new DeckStore(service);DeckStore.Deck latest=store.current();
                if(!latest.id.equals(deck.id)||!latest.code.equals(deck.code))throw new IOException("选中的阵容码已变化");
                if(needsPreview){
                    if(preview==null)throw new IOException("没有取得阵容预览，请重新导入");
                    File directory=new File(service.getFilesDir(),"lineups");if(!directory.exists()&&!directory.mkdirs())throw new IOException("无法创建预览目录");
                    String safe=deck.id.replaceAll("[^A-Za-z0-9-]","");File temporary=new File(directory,safe+".tmp"),destination=new File(directory,safe+"-row3.png");
                    try(FileOutputStream out=new FileOutputStream(temporary)){if(!preview.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("预览保存失败");}
                    Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);
                    verified=heroesComplete;note=verified?"头像已识别":"头像识别不完整，请补齐英雄后保存确认";
                    if(deck.verified&&!deck.targets.isEmpty()){verified=true;store.saveDerived(deck.id,latest.targets,"lineups/"+safe+"-row3.png",true,latest.note);}
                    else store.saveDerived(deck.id,String.join("\n",heroes),"lineups/"+safe+"-row3.png",verified,note);
                }
            }catch(Exception e){error=e.getMessage();}
            finally {if(preview!=null){preview.recycle();preview=null;}if(vision!=null){vision.close();vision=null;}}
            final String failure=error;final boolean ready=verified;final String message=note;
            service.scheduleLineup(()->{if(!active)return;active=false;service.lineupFinished(this,failure==null,
                failure!=null?"游戏已应用，预览保存失败："+failure:ready?"已应用阵容："+deck.name: "已应用阵容，请在助手补齐英雄后保存确认" );},0);
        });
    }
    static boolean sameRow(int[] expected,int[] actual){
        if(expected==null||actual==null||expected.length!=actual.length)return false;
        double sum=0;for(int i=0;i<expected.length;i++){double d=expected[i]-actual[i];sum+=d*d;}
        return Math.sqrt(sum/expected.length)<16;
    }
    private static void recycle(LineupVision.Screen screen){if(screen!=null&&screen.preview!=null){screen.preview.recycle();screen.preview=null;}}
    void fail(String reason){if(!active)return;cancel();service.lineupFinished(this,false,reason);}
    void cancel(){
        if(!active)return;active=false;service.removeLineup(tick);
        service.runLineupWork(()->{if(preview!=null){preview.recycle();preview=null;}if(vision!=null){vision.close();vision=null;}});
    }
}
