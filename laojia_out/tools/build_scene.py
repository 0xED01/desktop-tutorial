"""usage: python3 build_scene.py <workdir with mark_old.jpg> <frame-version>  -> <workdir>/fenjing.html"""
import os,re,sys,base64
here=os.path.dirname(os.path.abspath(__file__)); work=sys.argv[1]
t=open(os.path.join(os.path.dirname(here),'laojia_compare.html')).read()
new_b64=re.search(r'NEW_SRC="(data:image/jpeg;base64,[^"]+)"',t).group(1)
m=open(os.path.join(here,'scene_tpl.html')).read().replace('__FRAME__','aligned-'+sys.argv[2])
m=m.replace('__OLD__','data:image/jpeg;base64,'+base64.b64encode(open(os.path.join(work,'mark_old.jpg'),'rb').read()).decode()).replace('__NEW__',new_b64)
open(os.path.join(work,'fenjing.html'),'w').write(m)
