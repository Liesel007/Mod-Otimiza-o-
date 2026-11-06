import org.objectweb.asm.*; import org.objectweb.asm.commons.*;
import java.util.*; import java.util.zip.*; import java.io.*; import java.nio.file.*;
public class Remap {
  // mode deobf: in jars (later override earlier) -> out ; names by srg2off.tsv
  // mode reobf: in classes dir/jar, mcjar(official) , tsrg -> out
  static Map<String,String> g=new HashMap<>();
  static Map<String,String> fm=new HashMap<>(), mm=new HashMap<>(); // cls.name -> srg ; cls.name.desc -> srg
  static Map<String,String[]> sup=new HashMap<>();
  public static void main(String[] a) throws Exception {
    if(a[0].equals("deobf")){
      for(String l:Files.readAllLines(Path.of(a[1]))){String[] p=l.split("\t");g.put(p[0],p[1]);}
      Map<String,byte[]> out=new LinkedHashMap<>();
      for(int i=3;i<a.length;i++) read(a[i],out);
      Remapper r=new Remapper(){
        public String mapMethodName(String o,String n,String d){return g.getOrDefault(n,n);}
        public String mapFieldName(String o,String n,String d){return g.getOrDefault(n,n);}
        public String mapInvokeDynamicMethodName(String n,String d){return n;}
      };
      write(a[2],out,r);
    } else {
      for(String l:Files.readAllLines(Path.of(a[1]))){ if(l.startsWith("\t")){String[] p=l.trim().split(" "); if(p.length==2) fm.put(cur+"."+p[0],p[1]); else mm.put(cur+"."+p[0]+p[1],p[2]);} else cur=l.split(" ")[0]; }
      Map<String,byte[]> mc=new HashMap<>(); for(String j:a[3].split(":")) read(j,mc);
      Map<String,byte[]> in=new LinkedHashMap<>(); read(a[4],in);
      for(Map<String,byte[]> m:List.of(mc,in)) for(var e:m.entrySet()) if(e.getKey().endsWith(".class")){ClassReader cr=new ClassReader(e.getValue()); String[] s=new String[1+cr.getInterfaces().length]; s[0]=cr.getSuperName(); System.arraycopy(cr.getInterfaces(),0,s,1,s.length-1); sup.put(cr.getClassName(),s);}
      Remapper r=new Remapper(){
        public String mapMethodName(String o,String n,String d){ if(n.startsWith("<")) return n; String x=find(o,n+d,true,new HashSet<>()); return x==null?n:x;}
        public String mapFieldName(String o,String n,String d){ String x=find(o,n,false,new HashSet<>()); return x==null?n:x;}
        public String mapInvokeDynamicMethodName(String n,String d){return n;}
      };
      write(a[2],in,r);
    }
  }
  static String cur;
  static String find(String o,String k,boolean m,Set<String> seen){
    if(o==null||!seen.add(o)) return null;
    String x=(m?mm:fm).get(o+"."+k); if(x!=null) return x;
    String[] s=sup.get(o); if(s==null) return null;
    for(String p:s){ x=find(p,k,m,seen); if(x!=null) return x;} return null;
  }
  static void read(String f,Map<String,byte[]> out) throws Exception{
    File F=new File(f);
    if(F.isDirectory()){ try(var w=Files.walk(F.toPath())){ for(Path p:(Iterable<Path>)w::iterator) if(Files.isRegularFile(p)) out.put(F.toPath().relativize(p).toString().replace('\\','/'),Files.readAllBytes(p)); } return; }
    try(ZipFile z=new ZipFile(F)){ for(var e:Collections.list(z.entries())) if(!e.isDirectory()) out.put(e.getName(),z.getInputStream(e).readAllBytes()); }
  }
  static void write(String f,Map<String,byte[]> in,Remapper r) throws Exception{
    try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
      for(var e:in.entrySet()){ if(e.getKey().startsWith("META-INF/")&&(e.getKey().endsWith(".SF")||e.getKey().endsWith(".RSA"))) continue; byte[] b=e.getValue();
        if(e.getKey().endsWith(".class")){ ClassReader cr=new ClassReader(b); ClassWriter cw=new ClassWriter(0); cr.accept(new ClassRemapper(cw,r),0); b=cw.toByteArray(); }
        z.putNextEntry(new ZipEntry(e.getKey())); z.write(b); z.closeEntry(); }
    }
  }
}
