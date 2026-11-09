package local.jcc.probe;
import javax.imageio.ImageIO;import java.nio.file.*;import java.awt.image.BufferedImage;import java.util.*;
public class PortraitCheck {
 public static void main(String[] args)throws Exception {
  if(args.length<1||args.length>2)throw new IllegalArgumentException("Pass the local first-row fixture image path");
  int rowIndex=args.length==2?Integer.parseInt(args[1]):0;
  String[] expected=rowIndex==2?new String[]{"13508","13500","15454","15450","15452","14502","13515","11509","11501"}:new String[]{"11510","12511","12502","12512","12509","12505","11502","13510"};
  List<PortraitMatcher.Reference> refs=new ArrayList<>();
  try(var paths=Files.list(Path.of("app/src/main/assets/portraits"))){for(Path p:paths.toList()){
   BufferedImage b=ImageIO.read(p.toFile());int[] pixels=b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth());
   String id=p.getFileName().toString().replace(".png","");refs.add(new PortraitMatcher.Reference(id,id,PortraitMatcher.descriptor(pixels,b.getWidth(),0,0,b.getWidth(),b.getHeight())));
  }}
  BufferedImage b=ImageIO.read(Path.of(args[0]).toFile());int[] pixels=b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth());
  PortraitMatcher.Row row=PortraitMatcher.readRow(pixels,b.getWidth(),b.getHeight(),refs,rowIndex);
  System.out.println("count="+row.count+" unknown="+row.unknown+" names="+row.names+" origin="+row.x+","+row.y);
  if(!row.complete()||!row.names.equals(Arrays.asList(expected)))throw new AssertionError("First-row portraits mismatch");
  System.out.println("Portrait checks passed.");
 }
}
