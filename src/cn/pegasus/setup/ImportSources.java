package cn.pegasus.setup;
import java.io.*;
import java.util.*;
public final class ImportSources {
 public File mainZip,androidZip,lists;
 public boolean grantStorage=true;
 public void discover(File dir){
  mainZip=null;androidZip=null;lists=null;
  try{mainZip=SetupEngine.findConfigZip(dir);}catch(Exception ignored){}
  try{androidZip=SourceDiscovery.unique(dir,3);}catch(Exception ignored){}
  lists=SourceDiscovery.findLists(dir);
 }
}
