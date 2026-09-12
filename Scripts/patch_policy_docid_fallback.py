from pathlib import Path

P = Path('app/src/main/java/com/rgapro1/ocaso/OcasoPolicyParser.java')
s = P.read_text(encoding='utf-8')

def replace_method(src, sig, new):
    a = src.find(sig)
    if a < 0:
        raise SystemExit('method not found: ' + sig)
    b = src.find('{', a)
    d = 0
    for i in range(b, len(src)):
        if src[i] == '{': d += 1
        elif src[i] == '}':
            d -= 1
            if d == 0:
                return src[:a] + new + src[i+1:]
    raise SystemExit('unbalanced method')

new_method = r'''    private static String extractIdentity(String text,String[] lines){
        int[] b=policyholderBounds(lines);
        String[] labels={"DOC. ID","DOC ID","DOCUMENTO DE IDENTIDAD","DOCUMENTO","DNI","NIE","IDENTIFICACION","IDENTIFICACIÓN","TOMADOR"};
        ArrayList<Candidate> c=new ArrayList<>();
        if(b[0]>=0){
            for(int i=b[0];i<b[1];i++){
                String u=lines[i].replace(".","").replace(":","").toUpperCase(Locale.ROOT);
                boolean near=false;
                for(String l:labels) if(u.contains(l.replace(".",""))){near=true;break;}
                if(near) for(int j=i;j<Math.min(i+4,b[1]);j++) addIdentityCandidates(c,lines[j],j,labels);
            }
        }
        Collections.sort(c,new Comparator<Candidate>(){public int compare(Candidate a,Candidate b){return b.score-a.score;}});
        for(Candidate x:c) if(isValidIdentity(x.value)) return x.value;

        // Ocaso policy PDFs commonly print the holder ID as "DOC. ID.: 12345678X".
        // OCR can keep punctuation, so do a direct label-to-value pass over all lines.
        Pattern docId=Pattern.compile("(?i)DOC\\s*\\.?\\s*ID\\s*\\.?\\s*[:.-]?\\s*([0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])");
        for(String line:lines){
            Matcher m=docId.matcher(line.toUpperCase(Locale.ROOT));
            while(m.find()) if(isValidIdentity(m.group(1))) return m.group(1);
            addIdentityCandidates(c,line,0,labels);
        }
        for(Candidate x:c) if(isValidIdentity(x.value)) return x.value;
        return "";
    }
'''
s = replace_method(s, '    private static String extractIdentity(String text,String[] lines){', new_method)
P.write_text(s, encoding='utf-8')
print('Policy DOC. ID fallback extraction complete')
