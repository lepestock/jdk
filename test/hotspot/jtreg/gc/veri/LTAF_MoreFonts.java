import java.io.*;
import java.util.Arrays;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.awt.*; 
import java.awt.geom.*; 
import java.awt.font.*; 
import java.awt.color.*; 
import java.awt.image.*; 

public class LTAF_MoreFonts {


    static Float getWeightedFloat(Random r) {
        int i = r.nextInt(7);
        switch (i) {
            case 0 : return Float.MAX_VALUE;  
            case 1 : return Float.MIN_VALUE;  
            case 2 : return Float.NaN;  
            case 3 : return Float.POSITIVE_INFINITY;  
            case 4 : return Float.NEGATIVE_INFINITY;  
            case 5 : return Float.MIN_NORMAL;  
            case 6 : return r.nextBoolean() ? 1 : -1 * r.nextFloat(Float.MAX_VALUE);  
        }
        return 0f;
    }

    static AffineTransform getAf(Random r) {
        float[] f_arr = new float[6];
        for (int i  = 0 ; i < 6 ; i++ ) {
            f_arr[i]=getWeightedFloat(r);
        }
        return new AffineTransform(f_arr);
    }


    static class WrapRandom extends Random {
        boolean verbose = false;
        int count = 0; 
        public WrapRandom(int seed) {
            super(seed);
        }
        
        public WrapRandom() {
            super();
        }

        public int nextInt() { 
            int val = super.nextInt();
            count ++;
            if (verbose) 
                System.out.println(count+":"+val);
            return val; 
        }

        public void setVerbose(boolean verb) {
            verbose = verb;
        }
    }
/* 

#  SIGSEGV (0xb) at pc=0x00007ff80f31f958, pid=13980, tid=1117443
#
# JRE version: Java(TM) SE Runtime Environment (23.0+37) (build 23+37-2369)
# Java VM: Java HotSpot(TM) 64-Bit Server VM (23+37-2369, mixed mode, sharing, tiered, compressed oops, compressed class ptrs, g1 gc, bsd-amd64)
# Problematic frame:
# C  [libsystem_malloc.dylib+0x2d958]  medium_free_scan_madvise_free+0x1a5

  with 

MallocScribble=1 java AWTFontCrash /System/Library/Fonts/Times.ttc

*/

public static void main(String[] args) throws Exception {    


    int seed = 0; 
    try {
        seed = Integer.parseInt(args[1]);
    }
    catch (Exception e) {};
    final Random rnd = new WrapRandom(seed);
    final BufferedImage bi  = new BufferedImage(1024,768,12);
    byte[] fontdata = new FileInputStream(args[0]).readAllBytes();
    Font awtFont = Font.createFont(Font.TRUETYPE_FONT,new ByteArrayInputStream(fontdata));
    final Font awtFont2 = awtFont.deriveFont(147.7f+(float)Math.PI);
    final char[] thebuf = new char[65536];
    String theString = new String(thebuf);
    for (int i = 0 ; i < thebuf.length;i++) {
        thebuf[i]=(char)i;
    } 
   // Arrays.fill(thebuf, '\u2222');
    int[] count = new int[3]; 
    Runnable r = () -> {
        count[2]++;
        //Boolean b1 =  rnd.nextBoolean();
        //Boolean b2 = rnd.nextBoolean();
        int start =  rnd.nextInt(65536);
        int limit =  start+rnd.nextInt(65536-start);
        //double[] afarr = new double[6];
        //for (int i = 0 ; i< 6 ; i++) 
        //    afarr[i]= rnd.nextDouble();
     
        AffineTransform af = getAf(rnd);
        int flags =  rnd.nextInt() % 8  ; // Font.LAYOUT_RIGHT_TO_LEFT | Font.LAYOUT_NO_START_CONTEXT | Font.LAYOUT_NO_LIMIT_CONTEXT
        
        try {
            byte[] newdata = fontdata.clone();
            for (int i = 0 ; i < newdata.length * 0.001f; i++) {
                int pos = 256+rnd.nextInt(newdata.length-256 ); 
                newdata[pos] ^= (byte) (1<<rnd.nextInt(8));
            }  
            Font ff0= Font.createFont(Font.TRUETYPE_FONT,new ByteArrayInputStream(newdata));
            Font ff1 = ff0.deriveFont(rnd.nextFloat(4096f));
            Font ff2 = ff1.deriveFont(getAf(rnd));
            GlyphVector gv = ff2.layoutGlyphVector(new FontRenderContext(af, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP, RenderingHints.VALUE_FRACTIONALMETRICS_ON), thebuf, start, limit,flags );
            Graphics2D g2d = (Graphics2D) bi.getGraphics();  
            g2d.drawGlyphVector(gv,rnd.nextInt(),rnd.nextInt());
            //gv.getVisualBounds();
            gv.getOutline();
            //gv.getOutline();
            count[1]++;
            g2d.setFont(ff2);
            g2d.drawString(theString ,rnd.nextInt(),rnd.nextInt());

        }
        catch (Exception e) {
            e.printStackTrace();
            // count the fails, to ensure we have enough coverage
            //System.out.println("s:"+start);
            //System.out.println("l:"+limit);
            //System.out.println("g:"+(start+limit));
            count[0]++; 
            if (count[0] % 500 ==0) 
                System.out.println(count[0]);
            System.gc(); 
        }

        if (rnd.nextFloat() >.9f) {
            System.gc();  
        }
        System.out.println(Arrays.toString(count));

       // if (rnd.nextDouble(1.0) > 0.9) {
       //         System.gc();
       // }
    };


    ExecutorService executorService = Executors.newScheduledThreadPool(750);

    for (int i = 0 ; i < 10000; i++) {
        try {
            executorService.submit(r);
        }
        catch (Throwable e) {
            // move on 
        }
        
        if (i % 5000 == 0) {
            System.out.println(i+":"+count[0]);
        }
       
    }

}
}
