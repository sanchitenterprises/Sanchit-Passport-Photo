package com.sanchit.passportphoto;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    private ImageView preview;
    private Bitmap original, result;
    private SeekBar brightness, smooth;
    private static final int PICK=10;

    @Override public void onCreate(Bundle b){ super.onCreate(b); buildUi(); }

    private TextView text(String s,int sp){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setPadding(16,10,16,10); return t; }
    private Button button(String s){ Button b=new Button(this); b.setText(s); return b; }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(20,20,20,20);
        TextView title=text("Sanchit Passport Photo",24); title.setGravity(Gravity.CENTER); root.addView(title);
        root.addView(text("ऑफलाइन • बिना AI/API • 35×45 mm",14));
        preview=new ImageView(this); preview.setBackgroundColor(Color.rgb(230,230,230)); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        Button pick=button("फोटो चुनें"); root.addView(pick); pick.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),PICK));
        root.addView(text("Fairness / Brightness",14)); brightness=new SeekBar(this); brightness.setMax(80); brightness.setProgress(24); root.addView(brightness);
        root.addView(text("Smooth",14)); smooth=new SeekBar(this); smooth.setMax(6); smooth.setProgress(2); root.addView(smooth);
        Button process=button("PROCESS — 35×45 READY"); root.addView(process); process.setOnClickListener(v->process());
        Button save=button("फोटो सेव करें"); root.addView(save); save.setOnClickListener(v->save());
        setContentView(root);
    }

    @Override protected void onActivityResult(int r,int c,Intent d){ super.onActivityResult(r,c,d); if(r==PICK&&c==RESULT_OK&&d!=null){ try{ original=MediaStore.Images.Media.getBitmap(getContentResolver(),d.getData()); preview.setImageBitmap(original); }catch(Exception e){toast(e.getMessage());} } }

    private void process(){
        if(original==null){toast("पहले फोटो चुनें");return;}
        Bitmap crop=crop79(original);
        int w=700,h=900; Bitmap src=Bitmap.createScaledBitmap(crop,w,h,true); Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        int add=brightness.getProgress(); int rad=smooth.getProgress(); int[] p=new int[w*h]; src.getPixels(p,0,w,0,0,w,h);
        for(int i=0;i<p.length;i++){
            int c=p[i],r=Color.red(c),g=Color.green(c),b=Color.blue(c);
            // Non-AI corner/plain-background key: near-white or green-dominant pixels become fixed CSC blue.
            boolean white=r>205&&g>205&&b>205&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<35;
            boolean green=g>r*1.22&&g>b*1.18&&g>80;
            if(white||green){p[i]=Color.rgb(74,144,194);continue;}
            r=Math.min(255,r+add); g=Math.min(255,g+add); b=Math.min(255,b+add);
            p[i]=Color.rgb(r,g,b);
        }
        out.setPixels(p,0,w,0,0,w,h);
        // lightweight non-AI smoothing
        if(rad>0){ for(int n=0;n<rad;n++) out=boxBlur(out); }
        result=out; preview.setImageBitmap(result); toast("35×45 फोटो तैयार");
    }

    private Bitmap crop79(Bitmap b){ int w=b.getWidth(),h=b.getHeight(); float target=7f/9f; int nw=w,nh=h; if((float)w/h>target) nw=(int)(h*target); else nh=(int)(w/target); return Bitmap.createBitmap(b,(w-nw)/2,(h-nh)/2,nw,nh); }
    private Bitmap boxBlur(Bitmap s){ int w=s.getWidth(),h=s.getHeight(); Bitmap d=s.copy(Bitmap.Config.ARGB_8888,true); int[] a=new int[w*h],o=new int[w*h]; s.getPixels(a,0,w,0,0,w,h); System.arraycopy(a,0,o,0,a.length); for(int y=1;y<h-1;y+=2)for(int x=1;x<w-1;x+=2){long rr=0,gg=0,bb=0;for(int yy=-1;yy<=1;yy++)for(int xx=-1;xx<=1;xx++){int c=a[(y+yy)*w+x+xx];rr+=Color.red(c);gg+=Color.green(c);bb+=Color.blue(c);} int c=Color.rgb((int)(rr/9),(int)(gg/9),(int)(bb/9)); o[y*w+x]=c;} d.setPixels(o,0,w,0,0,w,h); return d; }
    private void save(){ if(result==null){toast("पहले फोटो Process करें");return;} try{ ContentValues v=new ContentValues(); v.put(MediaStore.Images.Media.DISPLAY_NAME,"Sanchit_Passport_"+System.currentTimeMillis()+".jpg"); v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg"); if(Build.VERSION.SDK_INT>=29)v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Sanchit Passport Photo"); Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v); OutputStream os=getContentResolver().openOutputStream(u); result.compress(Bitmap.CompressFormat.JPEG,96,os); os.close(); toast("फोटो सेव हो गई"); }catch(Exception e){toast("Save error: "+e.getMessage());} }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
