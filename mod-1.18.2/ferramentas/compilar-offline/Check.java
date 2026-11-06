import org.objectweb.asm.*; import java.util.*; import java.util.zip.*; import java.io.*;
public class Check {
  static Map<String,Set<String>> mem=new HashMap<>(); static Map<String,String[]> sup=new HashMap<>();
  public static void main(String[] a) throws Exception {
    for(String j:a[0].split(":")) idx(j);
    Set<String> bad=new TreeSet<>();
    try(ZipFile z=new ZipFile(a[1])){ for(var e:Collections.list(z.entries())) if(e.getName().endsWith(".class")){
      new ClassReader(z.getInputStream(e).readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
        String me;
        public void visit(int v,int ac,String n,String s,String su,String[] i){me=n;}
        public MethodVisitor visitMethod(int ac,String n,String d,String s,String[] ex){ return new MethodVisitor(Opcodes.ASM9){
          public void visitMethodInsn(int op,String o,String nm,String ds,boolean itf){ chk(o,nm+ds,me); }
          public void visitFieldInsn(int op,String o,String nm,String ds){ chk(o,nm+":"+ds,me); }
          void chk(String o,String k,String me){ if(!(o.startsWith("net/minecraft/")||o.startsWith("com/mojang/"))) return; if(o.startsWith("net/minecraftforge")) return; if(!mem.containsKey(o)) return; if(!find(o,k,new HashSet<>())) bad.add(me+" -> "+o+"."+k);} };}
      },0);}}
    bad.forEach(System.out::println); System.out.println("refs ruins: "+bad.size());
  }
  static boolean find(String o,String k,Set<String> seen){ if(o==null||!seen.add(o)) return false; Set<String> s=mem.get(o); if(s==null) return true; if(s.contains(k)) return true; for(String p:sup.getOrDefault(o,new String[0])) if(find(p,k,seen)) return true; return false; }
  static void idx(String j) throws Exception { try(ZipFile z=new ZipFile(j)){ for(var e:Collections.list(z.entries())) if(e.getName().endsWith(".class")){
    new ClassReader(z.getInputStream(e).readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){ String c;
      public void visit(int v,int ac,String n,String s,String su,String[] i){c=n; mem.put(n,new HashSet<>()); String[] x=new String[1+(i==null?0:i.length)]; x[0]=su; if(i!=null) System.arraycopy(i,0,x,1,i.length); sup.put(n,x);}
      public FieldVisitor visitField(int ac,String n,String d,String s,Object v){mem.get(c).add(n+":"+d);return null;}
      public MethodVisitor visitMethod(int ac,String n,String d,String s,String[] ex){mem.get(c).add(n+d);return null;} },ClassReader.SKIP_CODE);}}}
}
