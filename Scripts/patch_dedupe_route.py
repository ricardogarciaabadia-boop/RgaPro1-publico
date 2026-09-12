from pathlib import Path

MAIN=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=MAIN.read_text(encoding='utf-8')

def end_method(src,start):
    brace=src.find('{',start)
    if brace<0: raise SystemExit('brace not found')
    depth=0
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:return i+1
    raise SystemExit('unbalanced method')

sig='    private void routeOcrText(String text){'
while True:
    p=s.find(sig)
    if p<0:break
    s=s[:p]+s[end_method(s,p):]

route='''    private void routeOcrText(String text){
        String raw=text==null?"":text;
        if(isLikelyDniText(raw)){frontText=raw;showIdentityReview(parseEssentialRobust(raw));return;}
        try{showPolicyReview(OcasoPolicyParser.parse(raw),raw);}catch(Exception e){Toast.makeText(this,"No se pudo interpretar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
'''
marker='    private void processCurrentDocument(){'
p=s.find(marker)
if p<0: raise SystemExit('processCurrentDocument marker not found')
s=s[:p]+route+s[p:]
MAIN.write_text(s,encoding='utf-8')
print('Removed duplicate routeOcrText and restored single PDF routing method')
