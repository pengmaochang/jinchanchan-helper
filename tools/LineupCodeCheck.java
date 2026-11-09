package local.jcc.probe;
import java.util.*;
public class LineupCodeCheck {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  String sample="【阵容码】#林小北-日蚀卡尔玛#MjE5OTY5MDM5MDAxMzM4NDQxNzkyMDMyNDk3MDQ3";
  LineupCode code=LineupCode.parse(sample);check(code.name.equals("林小北-日蚀卡尔玛"),"Extract display name");check(code.text.equals(sample),"Preserve complete clipboard text");
  check(LineupCode.parseAll("分享给你："+sample+"\n"+sample).size()==1,"Deduplicate pasted codes");
  String second="【阵容码】#第二套#MTIzNDU2Nzg=";check(LineupCode.parseAll(sample+"\n"+second).size()==2,"Bulk input");
  boolean rejected=false;try{LineupCode.parse("#坏码#!!!!!!!!");}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Reject malformed input");
  rejected=false;try{LineupCode.parse(sample+"\n"+second);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Single selection cannot be multiple codes");
  check(LineupCode.titleMatches("林小北-日蚀卡尔玛","林小北日蚀卡尔码"),"Single OCR substitution in a long title");
  check(!LineupCode.titleMatches("霞洛","霞落"),"Do not fuzzy match short titles");
  check(!LineupCode.titleMatches("林小北-日蚀卡尔玛","另一个完全不同阵容"),"Reject a different lineup title");
  System.out.println("Lineup code checks passed.");
 }
}
