from pathlib import Path
p=Path('app/src/main/java/com/rgapro1/ocaso/Client360Activity.java')
s=p.read_text(encoding='utf-8')
# The final patch adds methods that need these activity fields. Keep this idempotent.
cls='public class Client360Activity extends FragmentActivity {'
fields='    private JSONObject client;\n    private android.content.SharedPreferences prefs;\n    private String originalIdentityFinal="";\n'
if 'private JSONObject client;' not in s:
    s=s.replace(cls,cls+'\n'+fields,1)
# The generated final patch calls render(); this activity renders through show().
s=s.replace('persistFinal();render();','persistFinal();show(client);')
s=s.replace('persistFinal();render();}catch','persistFinal();show(client);}catch')
s=s.replace('persistFinal();render();}catch(Exception z)','persistFinal();show(client);}catch(Exception z)')
p.write_text(s,encoding='utf-8')
print('Client360Activity state fixed')
